package com.github.tartaricacid.tlm_sincerely.ai.overtime;

import com.github.tartaricacid.tlm_sincerely.ai.MaidLookup;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveDirectiveManager;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveOrderNbt;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.OvertimeConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.github.tartaricacid.tlm_sincerely.ai.move.MoveLog.shortId;

/**
 * 女仆"加班"的服务端管理器：玩家要她干活、而她本来不在工作时间时，把她的日程临时改成
 * TLM 的 {@code MaidSchedule.ALL}（全天工作），干完再恢复原日程。
 *
 * <p><b>谁让她加班</b>（{@link OvertimeHolder}）：一条生效中的移动类指令，或者玩家（通过模型）在非工作时间
 * 切换了工作任务。可以同时有多个持有者；<b>最后一个</b>持有者释放时才恢复原日程。
 *
 * <p><b>这套"状态守卫"的约定</b>（与暂停跟随同一套）：
 * <ul>
 *   <li>开始加班时保存原日程；已经在加班时<b>沿用旧记录</b>，绝不把已经被改成 ALL 的日程当成原日程再存一次；</li>
 *   <li>正在睡觉、或日程本来就是 ALL 时不动日程（见 {@link ScheduleRelief#decide}）；</li>
 *   <li>加班期间日程被玩家改成了别的值（女仆界面 / 主模组的 {@code switch_schedule} 工具）视为<b>玩家接管</b>：
 *       不恢复、直接丢掉记录；</li>
 *   <li>状态和原日程一起写进女仆实体的持久化标签（{@link OvertimeNbt}），服务器重启后能接着加班或恢复；
 *       服务端停止时 {@link #clear()} 只清内存、<b>不恢复</b>（此刻世界正在保存，标签已经写好）；</li>
 *   <li>女仆不在场（区块卸载 / 被移除）时无法恢复日程，记录原样保留到她重新加载。</li>
 * </ul>
 *
 * <p>所有方法都必须在服务端主线程调用。
 */
public final class OvertimeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(OvertimeManager.class);

    /** 处理周期（tick）。 */
    private static final int TICK_INTERVAL = 20;

    /** 一只女仆当前的加班运行状态（内存里的权威值；变化时同步写进实体标签）。 */
    private static final class State {
        final String originalSchedule;
        final EnumSet<OvertimeHolder> holders;
        long taskDeadlineGameTick;

        State(String originalSchedule, EnumSet<OvertimeHolder> holders, long taskDeadlineGameTick) {
            this.originalSchedule = originalSchedule;
            this.holders = holders;
            this.taskDeadlineGameTick = taskDeadlineGameTick;
        }

        OvertimeRecord toRecord() {
            return new OvertimeRecord(originalSchedule, holders, taskDeadlineGameTick);
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static int tickCounter;

    private OvertimeManager() {
    }

    /**
     * {@link #acquire} 的结果。
     *
     * @param action           对日程做了什么（{@link ScheduleRelief.Action#UNCHANGED} 表示没有开始加班）
     * @param originalSchedule 加班前的日程名；没有开始加班时为 null
     * @param sleeping         没开始加班是不是因为她正在睡觉（给模型的说明文字要区分）
     */
    public record Acquisition(ScheduleRelief.Action action, @Nullable String originalSchedule, boolean sleeping) {
        /** 她现在是否处在加班里（本次新开始，或沿用已有的）。 */
        public boolean active() {
            return action != ScheduleRelief.Action.UNCHANGED;
        }
    }

    // ===== 查询 =====

    /** 这只女仆是否正在加班（有任何持有者）。 */
    public static boolean isActive(@Nullable UUID maidId) {
        return maidId != null && STATES.containsKey(maidId);
    }

    /** 这只女仆的加班是否被某个持有者占着。 */
    public static boolean holds(@Nullable UUID maidId, OvertimeHolder holder) {
        State state = maidId == null ? null : STATES.get(maidId);
        return state != null && state.holders.contains(holder);
    }

    /** 女仆当前日程是否已经是"全天工作"。 */
    public static boolean isScheduleAll(@Nullable EntityMaid maid) {
        return maid != null && maid.getSchedule() == MaidSchedule.ALL;
    }

    // ===== 开始 / 结束加班 =====

    /**
     * 让某个持有者开始（或加入）这只女仆的加班。
     *
     * @param configAllows 配置是否允许加班（移动指令看 {@code MoveForceWorkSchedule}；
     *                     {@code maid_work_time} 和切换工作任务恒为 true）
     */
    public static Acquisition acquire(EntityMaid maid, OvertimeHolder holder, boolean configAllows) {
        UUID maidId = maid.getUUID();
        // 先处理"玩家接管"：加班期间日程被改成别的值就丢掉旧记录，后面按当前日程重新判定
        reconcile(maidId, maid, "acquire");
        State state = STATES.get(maidId);
        boolean sleeping = maid.isSleeping();
        boolean currentlyAll = isScheduleAll(maid);
        ScheduleRelief.Action action = ScheduleRelief.decide(sleeping, currentlyAll, state != null, configAllows);
        switch (action) {
            case FORCE_ALL -> {
                String original = currentScheduleName(maid);
                if (original == null) {
                    LOGGER.warn("[overtime] cannot read the current schedule, overtime not started. maid={} holder={}",
                            shortId(maidId), holder.id());
                    return new Acquisition(ScheduleRelief.Action.UNCHANGED, null, sleeping);
                }
                State created = new State(original, EnumSet.of(holder), OvertimeRecord.NO_DEADLINE);
                STATES.put(maidId, created);
                maid.setSchedule(MaidSchedule.ALL);
                persist(maid, created);
                LOGGER.info("[overtime] started: maid={} holder={} from={} to={}",
                        shortId(maidId), holder.id(), original, MaidSchedule.ALL.name());
                return new Acquisition(action, original, sleeping);
            }
            case REUSE -> {
                state.holders.add(holder);
                persist(maid, state);
                LOGGER.info("[overtime] already working overtime, holder joined: maid={} holder={} holders={} saved={}",
                        shortId(maidId), holder.id(), state.holders, state.originalSchedule);
                return new Acquisition(action, state.originalSchedule, sleeping);
            }
            default -> {
                if (sleeping) {
                    LOGGER.info("[overtime] not started: the maid is sleeping. maid={} holder={}",
                            shortId(maidId), holder.id());
                } else if (currentlyAll) {
                    LOGGER.info("[overtime] not started: the schedule is already {}. maid={} holder={}",
                            MaidSchedule.ALL.name(), shortId(maidId), holder.id());
                } else if (!configAllows) {
                    LOGGER.info("[overtime] not started: disabled by config. maid={} holder={}",
                            shortId(maidId), holder.id());
                }
                return new Acquisition(action, null, sleeping);
            }
        }
    }

    /**
     * 某个持有者不再需要加班。它是最后一个持有者时恢复原日程。幂等：没有这个持有者就什么都不做。
     *
     * @return 这次调用是否真的把日程写回了原日程
     */
    public static boolean release(@Nullable UUID maidId, @Nullable EntityMaid maid, OvertimeHolder holder, String reason) {
        if (maidId == null) {
            return false;
        }
        State state = STATES.get(maidId);
        if (state == null || !state.holders.contains(holder)) {
            return false;
        }
        if (maid == null || maid.isRemoved()) {
            // 女仆不在（区块卸载 / 被移除）：标签里还有保存好的状态，等重新加载时再处理
            LOGGER.info("[overtime] cannot release now (maid unavailable), keeping it for reload. maid={} holder={} reason={}",
                    shortId(maidId), holder.id(), reason);
            return false;
        }
        state.holders.remove(holder);
        if (holder == OvertimeHolder.WORK_TASK) {
            state.taskDeadlineGameTick = OvertimeRecord.NO_DEADLINE;
        }
        if (!state.holders.isEmpty()) {
            persist(maid, state);
            LOGGER.info("[overtime] holder released, others remain: maid={} holder={} reason={} holders={}",
                    shortId(maidId), holder.id(), reason, state.holders);
            return false;
        }
        return finish(maidId, maid, state, reason);
    }

    /** 最后一个持有者走了：按决策表恢复原日程并清掉状态与标签。 */
    private static boolean finish(UUID maidId, EntityMaid maid, State state, String reason) {
        STATES.remove(maidId);
        OvertimeNbt.clear(maid.getPersistentData());
        if (ScheduleRelief.decideRestore(state.originalSchedule, isScheduleAll(maid))
                == ScheduleRelief.RestoreAction.EXTERNAL_CHANGE) {
            LOGGER.info("[overtime] ended, but the schedule was changed externally, not restoring. maid={} reason={} saved={} current={}",
                    shortId(maidId), reason, state.originalSchedule, currentScheduleName(maid));
            return false;
        }
        MaidSchedule original = parseSchedule(state.originalSchedule);
        if (original == null) {
            LOGGER.warn("[overtime] unknown saved schedule '{}', not restoring. maid={} reason={}",
                    state.originalSchedule, shortId(maidId), reason);
            return false;
        }
        maid.setSchedule(original);
        LOGGER.info("[overtime] ended, schedule restored: maid={} reason={} from={} to={}",
                shortId(maidId), reason, MaidSchedule.ALL.name(), original.name());
        return true;
    }

    /**
     * 外部改动检测：加班期间日程已经不是 ALL（玩家在界面里或用 {@code switch_schedule} 工具改了），
     * 视为玩家接管——<b>不恢复</b>，丢掉记录并清标签。
     *
     * @return 本次是否真的丢弃了一份记录
     */
    private static boolean reconcile(UUID maidId, @Nullable EntityMaid maid, String reason) {
        State state = STATES.get(maidId);
        if (state == null || maid == null || isScheduleAll(maid)) {
            return false;
        }
        STATES.remove(maidId);
        OvertimeNbt.clear(maid.getPersistentData());
        LOGGER.info("[overtime] schedule was changed externally, overtime dropped (no restore). maid={} reason={} saved={} current={} holders={}",
                shortId(maidId), reason, state.originalSchedule, currentScheduleName(maid), state.holders);
        return true;
    }

    // ===== 切换工作任务 =====

    /**
     * 玩家（通过模型）让女仆切换工作任务，在主模组判断"是不是工作时间"之前调用。
     *
     * <ul>
     *   <li>切到待命任务：活干完了，释放 {@link OvertimeHolder#WORK_TASK}；</li>
     *   <li>已经在加班：让这个任务也成为持有者并重新计时（指令结束后她不会立刻被打回休息）；</li>
     *   <li>现在不是工作时间且没在加班：开始加班（睡觉中或配置关闭时不开始）。</li>
     * </ul>
     *
     * @param inWorkTime 主模组此刻认为她是不是工作时间（{@code getScheduleDetail() == WORK}）
     * @return 是否应该让主模组把这次当作工作时间（调用方据此把活动当成 WORK）
     */
    public static boolean onWorkTaskSwitched(EntityMaid maid, boolean inWorkTime) {
        UUID maidId = maid.getUUID();
        if (!(maid.level() instanceof ServerLevel level)) {
            return false;
        }
        if (isIdleTask(maid)) {
            release(maidId, maid, OvertimeHolder.WORK_TASK, "task-idle");
            return false;
        }
        if (!OvertimeConfig.TASK_SWITCH_ENABLED.get()) {
            return false;
        }
        reconcile(maidId, maid, "task-switch");
        long deadline = level.getGameTime() + OvertimeConfig.TASK_HOLD_SECONDS.get() * 20L;
        State state = STATES.get(maidId);
        if (state != null) {
            if (ScheduleTimeline.phaseAt(state.originalSchedule, level.getDayTime()) != ScheduleTimeline.Phase.WORK) {
                state.holders.add(OvertimeHolder.WORK_TASK);
                state.taskDeadlineGameTick = deadline;
                persist(maid, state);
                LOGGER.info("[overtime] work task keeps the overtime going: maid={} holders={} holdSeconds={}",
                        shortId(maidId), state.holders, OvertimeConfig.TASK_HOLD_SECONDS.get());
            }
            return true;
        }
        if (inWorkTime) {
            return false;
        }
        if (!acquire(maid, OvertimeHolder.WORK_TASK, true).active()) {
            return false;
        }
        State created = STATES.get(maidId);
        if (created != null) {
            created.taskDeadlineGameTick = deadline;
            persist(maid, created);
        }
        return true;
    }

    private static boolean isIdleTask(EntityMaid maid) {
        return maid.getTask().getUid().equals(TaskManager.getIdleTask().getUid());
    }

    // ===== tick / 加载 / 停止 =====

    /** 服务端 tick 驱动：每 {@value #TICK_INTERVAL} tick 处理一次。 */
    public static void onServerTick(@Nullable MinecraftServer server) {
        if (server == null || STATES.isEmpty()) {
            return;
        }
        tickCounter++;
        if (tickCounter % TICK_INTERVAL != 0) {
            return;
        }
        for (UUID maidId : new ArrayList<>(STATES.keySet())) {
            State state = STATES.get(maidId);
            if (state == null) {
                continue;
            }
            EntityMaid maid = MaidLookup.find(server, maidId);
            if (maid == null) {
                continue;
            }
            if (!maid.isAlive()) {
                // 女仆死了：不会再加载，也没有日程需要恢复
                STATES.remove(maidId);
                continue;
            }
            if (maid.isRemoved() || !(maid.level() instanceof ServerLevel level)) {
                continue;
            }
            if (reconcile(maidId, maid, "external-change")) {
                continue;
            }
            if (state.holders.contains(OvertimeHolder.ORDER) && !MoveDirectiveManager.isActive(maidId)) {
                // 指令已经没了却没释放（理论上不会发生，留作兜底，免得女仆一直卡在全天工作）
                release(maidId, maid, OvertimeHolder.ORDER, "order-missing");
                continue;
            }
            if (state.holders.contains(OvertimeHolder.WORK_TASK)) {
                boolean idle = isIdleTask(maid);
                boolean expired = state.taskDeadlineGameTick != OvertimeRecord.NO_DEADLINE
                        && level.getGameTime() >= state.taskDeadlineGameTick;
                if (idle || expired) {
                    release(maidId, maid, OvertimeHolder.WORK_TASK, idle ? "task-idle" : "task-hold-expired");
                }
            }
        }
    }

    /**
     * 女仆进入世界（含服务端重启后从存档读出）时调用，<b>必须早于</b>
     * {@link MoveDirectiveManager#onMaidLoaded}（指令到期时会来释放这里的持有者）。
     *
     * <p>标签里有加班状态、而日程仍是"全天工作"→ 装回内存继续加班；日程已经不是 ALL 说明玩家自己改过，
     * 视为玩家接管，丢掉标签。旧版本把原日程存在移动指令标签里（见 {@link MoveOrderNbt}），这里一并接收过来。
     */
    public static void onMaidLoaded(@Nullable ServerLevel level, @Nullable EntityMaid maid) {
        if (level == null || maid == null) {
            return;
        }
        UUID maidId = maid.getUUID();
        if (STATES.containsKey(maidId)) {
            // 同一会话内区块反复卸载 / 加载：内存态才是最新的
            return;
        }
        CompoundTag data = maid.getPersistentData();
        OvertimeRecord record = OvertimeNbt.read(data);
        boolean legacy = false;
        if (record == null) {
            String legacySchedule = MoveOrderNbt.readLegacyOriginalSchedule(data);
            if (legacySchedule == null) {
                if (data.contains(OvertimeNbt.ROOT_TAG)) {
                    LOGGER.info("[overtime] reload: unreadable overtime data, dropping the tag. maid={}", shortId(maidId));
                    OvertimeNbt.clear(data);
                }
                return;
            }
            record = new OvertimeRecord(legacySchedule, EnumSet.of(OvertimeHolder.ORDER), OvertimeRecord.NO_DEADLINE);
            legacy = true;
        }
        if (legacy) {
            // 旧标签里的字段只接收这一次，免得之后重复接收
            MoveOrderNbt.stripLegacyOriginalSchedule(data);
        }
        if (!isScheduleAll(maid)) {
            LOGGER.info("[overtime] reload: schedule was changed externally, dropping the saved original. maid={} saved={} current={}",
                    shortId(maidId), record.originalSchedule(), currentScheduleName(maid));
            OvertimeNbt.clear(data);
            return;
        }
        State state = new State(record.originalSchedule(), EnumSet.copyOf(record.holders()), record.taskDeadlineGameTick());
        STATES.put(maidId, state);
        persist(maid, state);
        LOGGER.info("[overtime] reload: still working overtime. maid={} holders={} original={} legacy={}",
                shortId(maidId), state.holders, state.originalSchedule, legacy);
    }

    /**
     * 服务端停止时清空内存态。<b>不执行恢复动作</b>：此刻世界正在保存，原日程已经随标签持久化，
     * 下次加载时会由 {@link #onMaidLoaded} 接着加班或交给指令恢复。
     */
    public static void clear() {
        STATES.clear();
        tickCounter = 0;
    }

    // ===== 小工具 =====

    private static void persist(EntityMaid maid, State state) {
        OvertimeNbt.write(maid.getPersistentData(), state.toRecord());
    }

    @Nullable
    private static String currentScheduleName(@Nullable EntityMaid maid) {
        if (maid == null) {
            return null;
        }
        MaidSchedule schedule = maid.getSchedule();
        return schedule == null ? null : schedule.name();
    }

    /** 解析保存的日程名；枚举值对不上（旧版本 / 手改存档）时返回 null，由调用方放弃恢复。 */
    @Nullable
    private static MaidSchedule parseSchedule(@Nullable String name) {
        if (name == null) {
            return null;
        }
        try {
            return MaidSchedule.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
