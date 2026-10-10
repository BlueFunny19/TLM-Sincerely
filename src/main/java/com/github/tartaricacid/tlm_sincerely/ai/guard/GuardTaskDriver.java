package com.github.tartaricacid.tlm_sincerely.ai.guard;

import com.github.tartaricacid.tlm_sincerely.SincerelyExtension;
import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardDecider.TargetStatus;
import com.github.tartaricacid.tlm_sincerely.ai.move.MaidSpeech;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveAction;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveDirectiveManager;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveMath;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveOrderData;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveOrderPlacer;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.github.tartaricacid.tlm_sincerely.ai.move.MoveLog.shortId;

/**
 * 守护任务的驱动：把守护名单"翻译"成已经验证过的守护指令（{@link MoveAction#GUARD}）。
 *
 * <p>做法是不另造一套行为，而是每隔 {@link #RECONCILE_INTERVAL} tick 对每只处于守护任务的女仆做一次对账：
 * 用 {@link GuardDecider} 算出此刻该守谁，跟她身上的守护指令比，不一致就换（登记新指令即覆盖旧指令），
 * 没人可守就撤掉指令。跟随、攻击、卡住传送、加班等机制全部沿用指令那一套。
 *
 * <p>几条边界：
 * <ul>
 *   <li>她身上有别人下的指令（不是守护名单里的人）时不插手——那是玩家 / 模型明确下的命令；</li>
 *   <li>重新加载后内存里没有记录，但指令已由 {@link MoveDirectiveManager} 恢复：名单里的守护指令直接认领；</li>
 *   <li>自己下的指令被中途丢弃（目标掉线、路走不通放弃等）后，等 {@link #RETRY_COOLDOWN_TICKS} 再重下，
 *       免得"放弃 → 马上重下 → 又放弃"循环刷屏；</li>
 *   <li>任务被换走（玩家在界面里切走、自动工作切走）时，撤掉自己下的指令并忘掉状态；</li>
 *   <li>限时守护到期：撤指令、回到进入前的任务、女仆说一句。</li>
 * </ul>
 * 只在服务端主线程运行（{@link MaidTickEvent} 与工具回调都是）。
 */
@Mod.EventBusSubscriber(modid = SincerelyExtension.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GuardTaskDriver {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuardTaskDriver.class);

    /** 对账周期（tick）。 */
    public static final int RECONCILE_INTERVAL = 20;
    /** 自己下的指令被丢弃之后，多久之内不再重下（20 秒）。 */
    public static final long RETRY_COOLDOWN_TICKS = 400L;

    private static final Map<UUID, State> STATES = new HashMap<>();

    /** 一只女仆的运行时状态（内存态，重启后由对账重建）。 */
    private static final class State {
        final GuardDecider decider = new GuardDecider();
        /** 现在由我们下着指令的目标；没有则为 null。 */
        @Nullable
        UUID driven;
        long lostAtGameTick = Long.MIN_VALUE / 2;
    }

    /** 一次对账的结果，工具据此拼回给模型的文字。 */
    public record Outcome(boolean expired, @Nullable GuardEntry chosen,
                          @Nullable MoveOrderPlacer.Placement placement) {
        static final Outcome NOTHING = new Outcome(false, null, null);
    }

    private GuardTaskDriver() {
    }

    // ===== 数据读写 =====

    public static GuardData dataOf(EntityMaid maid) {
        GuardData data = maid.getData(GuardTaskDataKeys.GUARD_KEY);
        return data == null ? GuardData.EMPTY : data;
    }

    public static void save(EntityMaid maid, GuardData data) {
        maid.setAndSyncData(GuardTaskDataKeys.GUARD_KEY, data);
    }

    public static boolean isGuarding(@Nullable EntityMaid maid) {
        return maid != null && maid.getTask() instanceof GuardTask;
    }

    // ===== 进入 / 离开守护任务 =====

    /**
     * 让她进入守护任务并写入新数据：不在守护任务时记下当前任务（结束 / 取消时回去），再切到守护任务。
     */
    public static void enter(EntityMaid maid, GuardData data) {
        GuardData toSave = data;
        if (!isGuarding(maid)) {
            toSave = data.withRevertTask(maid.getTask().getUid().toString());
            maid.setTask(taskInstance());
            LOGGER.info("[guard] entered guard task: maid={} name={} from={}",
                    shortId(maid.getUUID()), maid.getName().getString(), toSave.revertTaskUid());
        }
        save(maid, toSave);
    }

    /**
     * 离开守护任务（取消 / 到期）：撤掉自己下的指令，回到进入前的任务，清掉到期时间。名单保留，下次接着用。
     *
     * @return 她原本是不是在守护任务里（不在则什么也没做）
     */
    public static boolean leave(EntityMaid maid, String reason) {
        if (!isGuarding(maid)) {
            return false;
        }
        GuardData data = dataOf(maid);
        STATES.remove(maid.getUUID());
        cancelOwnOrder(maid, data.rules());
        maid.setTask(revertTarget(data));
        save(maid, data.withExpireAt(0L).withRevertTask(""));
        LOGGER.info("[guard] left guard task: maid={} name={} reason={} backTo={}",
                shortId(maid.getUUID()), maid.getName().getString(), reason, maid.getTask().getUid());
        return true;
    }

    /** 任务已经被别人换走了：只收尾（撤指令、忘状态、清到期时间），不再改任务。 */
    private static void release(EntityMaid maid) {
        STATES.remove(maid.getUUID());
        GuardData data = dataOf(maid);
        if (cancelOwnOrder(maid, data.rules())) {
            LOGGER.info("[guard] task changed away, driven order cancelled: maid={}", shortId(maid.getUUID()));
        }
        if (data.isTemporary() || !data.revertTaskUid().isEmpty()) {
            save(maid, data.withExpireAt(0L).withRevertTask(""));
        }
    }

    /**
     * 玩家明确要她守护时，她身上原有的其他指令（走位、待命……）让位：撤掉并恢复它改过的状态。
     * 属于守护名单的守护指令不算"其他指令"。
     */
    public static void dropForeignOrder(EntityMaid maid, GuardRules rules) {
        MoveOrderData order = MoveDirectiveManager.orderOf(maid.getUUID());
        if (order != null && !isGuardOrderOnList(order, rules)) {
            MoveDirectiveManager.cancelAndRestore(maid.getUUID(), maid);
            LOGGER.info("[guard] other order replaced by guarding: maid={} action={}",
                    shortId(maid.getUUID()), order.action().id());
        }
    }

    /** 撤掉她身上属于守护名单的守护指令（别人下的指令不动）。 */
    private static boolean cancelOwnOrder(EntityMaid maid, GuardRules rules) {
        MoveOrderData order = MoveDirectiveManager.orderOf(maid.getUUID());
        if (order == null || !isGuardOrderOnList(order, rules)) {
            return false;
        }
        MoveDirectiveManager.cancelAndRestore(maid.getUUID(), maid);
        return true;
    }

    private static IMaidTask taskInstance() {
        return TaskManager.findTask(GuardTask.UID).orElseGet(TaskManager::getIdleTask);
    }

    private static IMaidTask revertTarget(GuardData data) {
        if (data.revertTaskUid().isEmpty()) {
            return TaskManager.getIdleTask();
        }
        try {
            IMaidTask task = TaskManager.findTask(new ResourceLocation(data.revertTaskUid())).orElse(null);
            if (task != null && !(task instanceof GuardTask)) {
                return task;
            }
        } catch (RuntimeException ignored) {
            // 记录的 UID 非法：回到默认任务
        }
        return TaskManager.getIdleTask();
    }

    // ===== 对账 =====

    @SubscribeEvent
    public static void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide() || maid.isRemoved() || !maid.isAlive()
                || (maid.tickCount + maid.getId()) % RECONCILE_INTERVAL != 0) {
            return;
        }
        if (!isGuarding(maid)) {
            if (STATES.containsKey(maid.getUUID())) {
                release(maid);
            }
            return;
        }
        try {
            reconcile(maid);
        } catch (RuntimeException e) {
            LOGGER.warn("[guard] reconcile failed: maid={}", shortId(maid.getUUID()), e);
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            // 只忘内存态；她身上的指令与任务数据是持久的，回来时由对账认领
            STATES.remove(maid.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
    }

    /**
     * 对账一次：到期就收尾；否则算出该守谁、与现有指令比较、不一致就换。
     * 工具修改名单后会立刻调用一次，让变化马上生效。
     */
    public static Outcome reconcile(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level) || !isGuarding(maid)) {
            return Outcome.NOTHING;
        }
        UUID maidId = maid.getUUID();
        GuardData data = dataOf(maid);
        long now = level.getGameTime();
        if (data.isExpired(now)) {
            boolean zh = PromptLang.isChinese(PromptLang.of(maid));
            leave(maid, "expired");
            MaidSpeech.say(maid, GuardText.expired(zh));
            return new Outcome(true, null, null);
        }
        UUID ownerId = maid.getOwnerUUID();
        if (ownerId == null) {
            return Outcome.NOTHING;
        }
        State state = STATES.computeIfAbsent(maidId, id -> new State());
        GuardRules rules = data.rules();

        MoveOrderData order = MoveDirectiveManager.orderOf(maidId);
        if (order == null) {
            if (state.driven != null) {
                state.driven = null;
                state.lostAtGameTick = now;
            }
        } else if (isGuardOrderOnList(order, rules) && (state.driven == null || order.targetId().equals(state.driven))) {
            state.driven = order.targetId();
        } else {
            // 别人下的指令：不插手
            state.driven = null;
            return Outcome.NOTHING;
        }

        GuardEntry chosen = state.decider.decide(rules, targetId -> statusOf(level, targetId));
        if (chosen == null) {
            if (state.driven != null) {
                MoveDirectiveManager.cancelAndRestore(maidId, maid);
                state.driven = null;
                LOGGER.info("[guard] nobody to guard right now, order cancelled: maid={}", shortId(maidId));
            }
            return Outcome.NOTHING;
        }
        double distance = MoveMath.clampDistance(chosen.distance(), MoveAction.GUARD.defaultDistance());
        if (order != null && chosen.targetId().equals(state.driven) && Math.abs(order.distance() - distance) < 0.01D) {
            return new Outcome(false, chosen, null);
        }
        if (state.driven == null && now - state.lostAtGameTick < RETRY_COOLDOWN_TICKS) {
            return Outcome.NOTHING;
        }
        MoveOrderPlacer.Placement placement = MoveOrderPlacer.place(new MoveOrderPlacer.Request(
                maid, ownerId, MoveAction.GUARD, chosen.targetId(), 0.0D, 0.0D, 0.0D, distance, true, 0));
        if (placement.limitReached()) {
            state.lostAtGameTick = now;
            return new Outcome(false, chosen, placement);
        }
        state.driven = chosen.targetId();
        LOGGER.info("[guard] now guarding: maid={} name={} target={} ({}) condition={} distance={}",
                shortId(maidId), maid.getName().getString(), shortId(chosen.targetId()), chosen.name(),
                chosen.condition().kind(), distance);
        return new Outcome(false, chosen, placement);
    }

    private static boolean isGuardOrderOnList(MoveOrderData order, GuardRules rules) {
        return order.action() == MoveAction.GUARD && order.targetId() != null && rules.contains(order.targetId());
    }

    private static TargetStatus statusOf(ServerLevel level, UUID targetId) {
        LivingEntity target;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(targetId);
        if (player != null) {
            target = player.level() == level ? player : null;
        } else {
            target = level.getEntity(targetId) instanceof LivingEntity living ? living : null;
        }
        if (target == null || !target.isAlive()) {
            return TargetStatus.ABSENT;
        }
        return new TargetStatus(true, target.getHealth(), target.getMaxHealth());
    }
}
