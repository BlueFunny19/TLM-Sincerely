package com.github.tartaricacid.tlm_sincerely.ai.move;

import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.MoveConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.github.tartaricacid.tlm_sincerely.ai.move.GoHome.hasWorkPos;
import static com.github.tartaricacid.tlm_sincerely.ai.move.MoveLog.fmt;
import static com.github.tartaricacid.tlm_sincerely.ai.move.MoveLog.fmtVec;
import static com.github.tartaricacid.tlm_sincerely.ai.move.MoveLog.shortId;
import static com.github.tartaricacid.tlm_sincerely.ai.move.MoveNavigation.POINT_CLOSE_ENOUGH;
import static com.github.tartaricacid.tlm_sincerely.ai.move.MoveNavigation.findStandable;
import static com.github.tartaricacid.tlm_sincerely.ai.move.MoveNavigation.hasCantReachMemory;
import static com.github.tartaricacid.tlm_sincerely.ai.move.MoveNavigation.teleportMaid;
import static com.github.tartaricacid.tlm_sincerely.ai.move.MoveNavigation.toPoint;
import static com.github.tartaricacid.tlm_sincerely.ai.move.MoveNavigation.walkToEntity;
import static com.github.tartaricacid.tlm_sincerely.ai.move.MoveNavigation.walkToPoint;

/**
 * {@code maid_move} 的服务端指令管理器（内存态 + 女仆实体上的持久化）。
 *
 * <p>职责：
 * <ol>
 *   <li>保存每只女仆的移动指令（见 {@link MoveOrders} / {@link MoveOrderData}），在服务端主线程
 *       每 10 tick 处理一次，下发给女仆的移动与（守护时的）攻击目标；</li>
 *   <li>"路走不通"时的兜底（传送 / 放弃，见 {@link #handleStuck}）；</li>
 *   <li>暂停跟随（{@link FollowSuspension}）：stay / goto / retreat 与"目标不是主人"的
 *       approach / guard 在生效期间把女仆临时切到居家模式，结束（到期 / cancel / 被替换 /
 *       目标失效 / 死亡 / 外因）时精确恢复原状态；</li>
 *   <li>临时放宽工作时间（{@link ScheduleRelief}）：任何移动类指令生效期间，只要女仆不在睡觉，
 *       就把她的日程临时改成 TLM 的 {@code MaidSchedule.ALL}（全天工作），结束时恢复原日程；</li>
 *   <li>永久指令的定期提醒与时限指令的到期提示（女仆自己说话）；</li>
 *   <li>跨服务器重启恢复：指令、"暂停前原状态"与"放宽日程前的原日程"一起写进女仆
 *       {@code getPersistentData()} 的 {@link MoveOrderNbt#ROOT_TAG} 子标签，重启后由
 *       {@link #onMaidLoaded} 重新登记或立刻恢复。</li>
 * </ol>
 *
 * <p><b>移动实现</b>：用 Brain 的 {@code WALK_TARGET} 记忆下发，与主模组自己的走动行为一致，
 * 具体的走路 / 找落脚点 / 传送见 {@link MoveNavigation}，守护时的攻击见 {@link GuardCombat}，
 * 回家见 {@link GoHome}。
 *
 * <p><b>为什么要暂停跟随</b>：{@code MaidFollowOwnerTask} 在 {@code MaidBrain.registerCoreGoals} 里
 * 无条件注册到 CORE，条件是运行时判的 {@code !maid.isHomeModeEnable() && maid.canBrainMoving()}；
 * 只要居家模式为 false，它就会覆盖 {@code WALK_TARGET} 指向主人，距离超过
 * {@code getRestrictRadius() - 2 + 4} 时还会直接 {@code teleportToOwner}。所以"不要跟着主人"的指令
 * 必须在生效期间让居家模式为 true（见 {@link FollowSuspension} 的类注释里的 javap 证据）。
 *
 * <p>所有方法都必须在服务端主线程调用（{@code TickEvent.ServerTickEvent} END 阶段、
 * {@code LLMCallback.runOnServerThread} 或 {@code EntityJoinLevelEvent}）。
 */
public final class MoveDirectiveManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoveDirectiveManager.class);

    /** 处理周期（tick）。指令本身是限时的，这里只是重新下发移动的节奏。 */
    public static final int TICK_INTERVAL = 10;

    /** goto 到位后允许漂出的额外距离（格）：超过到位半径 + 该值才重新下发 WALK_TARGET。 */
    private static final double GOTO_DRIFT_MARGIN = 2.0D;

    private static final MoveOrders ORDERS = new MoveOrders();

    /** 每只女仆的卡住检测 / 传送预算；指令被覆盖、取消、到期、异常丢弃时一并移除。 */
    private static final Map<UUID, MoveProgress> PROGRESS = new HashMap<>();

    /** 每只女仆"暂停跟随"期间保存的原状态；只有真正暂停过的女仆才有记录。 */
    private static final Map<UUID, FollowSuspension> SUSPENSIONS = new HashMap<>();

    /**
     * 每只女仆"临时放宽工作时间"期间保存的原日程（{@link MaidSchedule#name()}）；
     * 只有真正放宽过的女仆才有记录。与 {@link #SUSPENSIONS} 同一套"保存 → 恢复 → 外部改动检测 →
     * 替换时不重复保存"机制，详见 {@link ScheduleRelief}。
     */
    private static final Map<UUID, String> SCHEDULE_RELIEF = new HashMap<>();

    /** 永久指令的提醒计时。 */
    private static final Map<UUID, MoveReminder> REMINDERS = new HashMap<>();

    private static int tickCounter;

    private MoveDirectiveManager() {
    }

    /** 该女仆当前是否有生效中的移动指令（供自动工作调度器等外部判断）。 */
    public static boolean isActive(@Nullable UUID maidId) {
        return ORDERS.isActive(maidId);
    }

    /** 当前生效的指令条数（调试/排查用）。 */
    public static int activeCount() {
        return ORDERS.size();
    }

    /** 当前指令（没有则返回 null）。 */
    @Nullable
    public static MoveOrderData orderOf(@Nullable UUID maidId) {
        return ORDERS.get(maidId);
    }

    /** 该女仆正在生效的"暂停跟随前原状态"记录；没有暂停过时返回 null。 */
    @Nullable
    public static FollowSuspension suspensionOf(@Nullable UUID maidId) {
        return maidId == null ? null : SUSPENSIONS.get(maidId);
    }

    // ===== 指令登记 / 替换 / 恢复 =====

    /**
     * 写入一条指令（覆盖本人旧指令），并把指令与"暂停前原状态"写进女仆实体的持久化标签
     * （供服务器重启后恢复）。返回 {@link MoveOrders.PutResult#LIMIT_REACHED} 时什么也不写、
     * 什么也不改——调用方必须在写入成功后才让暂停跟随的副作用留下。
     *
     * @param maid 女仆实体；为 null 时只写内存（理论上不会发生，留作防御）
     */
    public static MoveOrders.PutResult putOrder(@Nullable EntityMaid maid, MoveOrderData order, int maxPerOwner) {
        MoveOrders.PutResult result = ORDERS.put(order, maxPerOwner);
        if (result == MoveOrders.PutResult.OK && order != null) {
            // 新指令（含覆盖旧指令）从零开始计时，旧的卡住记录与提醒计时不能带过来
            PROGRESS.remove(order.maidId());
            MoveReminder reminder = new MoveReminder();
            reminder.arm(0L, MoveReminder.intervalTicks(reminderMinutes()));
            REMINDERS.put(order.maidId(), reminder);
            if (maid != null) {
                // 把"指令 + 暂停前原状态"写进实体持久化标签，供服务器重启后恢复
                MoveOrderNbt.write(maid.getPersistentData(), order);
            }
        }
        return result;
    }

    /** {@link #onOrderReplaced} 的结果：要不要给新指令挂上暂停记录，以及原状态里有没有工作坐标。 */
    public record SuspensionChange(boolean suspended, boolean clearedWorkPos, @Nullable FollowSuspension record) {
        static SuspensionChange none() {
            return new SuspensionChange(false, false, null);
        }
    }

    /**
     * 新指令要覆盖旧指令时的收尾：旧指令暂停过、而新指令不需要暂停 → 先恢复原状态；
     * 旧指令不需要暂停、新指令需要 → 先按当前状态建立暂停记录。
     *
     * <p><b>关键约定</b>：新指令"也需要暂停"时<b>沿用旧记录</b>，绝不把"已经暂停过的状态"
     * （居家模式已为 true、工作坐标已清）当成原状态再存一次，否则结束后永远恢复不回去。
     *
     * @return 新指令该挂的暂停记录（见 {@link SuspensionChange}）
     */
    public static SuspensionChange onOrderReplaced(EntityMaid maid, MoveOrderData newOrder) {
        if (maid == null || newOrder == null || !needsSuspension(maid, newOrder)) {
            if (maid != null) {
                restoreSuspension(maid.getUUID(), maid, "replaced-by-unsuspending-order");
            }
            return SuspensionChange.none();
        }
        FollowSuspension existing = SUSPENSIONS.get(maid.getUUID());
        if (existing != null) {
            LOGGER.info("[maid_move] replacing a suspending order, keeping the original saved state. maid={} action={} saved={}",
                    shortId(maid.getUUID()), newOrder.action().id(), existing.describe());
            return new SuspensionChange(true, existing.hadRestriction(), existing);
        }
        beginSuspension(maid, newOrder);
        FollowSuspension created = SUSPENSIONS.get(maid.getUUID());
        return new SuspensionChange(true, created != null && created.hadRestriction(), created);
    }

    /**
     * 该指令是否需要暂停跟随。
     * <p>判定目标是不是主人本人：{@code targetId} 与女仆的 owner UUID 相同即为"主人"。
     */
    public static boolean needsSuspension(EntityMaid maid, MoveOrderData order) {
        if (maid == null || order == null) {
            return false;
        }
        boolean targetIsOwner = order.targetId() != null && order.targetId().equals(maid.getOwnerUUID());
        return order.action().suspendsFollow(targetIsOwner);
    }

    /**
     * 开始暂停跟随：先保存原状态（居家模式、有没有工作坐标、坐标、半径），再清掉工作坐标、
     * 把居家模式打开。已经有保存记录时直接沿用（见 {@link #onOrderReplaced}）。
     *
     * @return 本指令是否建立在"有工作坐标"的原状态上（stay 的结果文案要用）
     */
    public static boolean beginSuspension(EntityMaid maid, MoveOrderData order) {
        UUID maidId = maid.getUUID();
        boolean hadWorkPos = hasWorkPos(maid);
        FollowSuspension existing = SUSPENSIONS.get(maidId);
        if (existing != null) {
            LOGGER.info("[maid_move] suspension already recorded, keeping it. maid={} saved={}",
                    shortId(maidId), existing.describe());
            return existing.hadRestriction();
        }
        FollowSuspension suspension = captureSuspension(maid);
        SUSPENSIONS.put(maidId, suspension);
        LOGGER.info("[maid_move] follow suspended: maid={} action={} saved={}",
                shortId(maidId), order.action().id(), suspension.describe());

        // 先清工作坐标：EntityMaid#restrictTo 会把同步数据 RESTRICT_RADIUS 置为 -1，
        // 让 isWithinRestriction 恒为 true，女仆不会因为活动范围被限制走不到目标。
        // 注意 EntityMaid 覆写了整组限制访问器（读写同步数据），所以清空同样走覆写后的方法。
        maid.clearRestriction();
        // 再打开居家模式：MaidFollowOwnerTask 的 maidStateConditions 会变成 false，跟随行为不再运行。
        // setHomeModeEnable 只写同步数据 DATA_HOME_MODE，不会反过来把工作坐标写回去。
        maid.setHomeModeEnable(true);
        LOGGER.info("[maid_move] follow suspension applied: maid={} hadWorkPos={} homeMode={}",
                shortId(maidId), hadWorkPos, maid.isHomeModeEnable());
        return hadWorkPos;
    }

    /** 读取女仆当前状态做成恢复记录。 */
    private static FollowSuspension captureSuspension(EntityMaid maid) {
        boolean homeMode = maid.isHomeModeEnable();
        if (!hasWorkPos(maid)) {
            return new FollowSuspension(homeMode, false, 0L, 0L, 0L, -1.0F);
        }
        BlockPos center = maid.getRestrictCenter();
        return new FollowSuspension(homeMode, true,
                center.getX(), center.getY(), center.getZ(), maid.getRestrictRadius());
    }

    /**
     * 结束指令时恢复暂停前的状态。
     *
     * <p>恢复语义：先把工作坐标写回去（{@code restrictTo(center, radius)}），再把居家模式写回保存时的值
     * ——<b>精确恢复</b>，不是一律关掉居家模式。若暂停期间居家模式被别处改成了别的值（玩家在界面里
     * 或用跟随工具改了），视为玩家接管：不恢复、清掉记录，只写 INFO 日志。
     *
     * @return 是否真的执行了恢复
     */
    public static boolean restoreSuspension(@Nullable UUID maidId, @Nullable EntityMaid maid, String reason) {
        if (maidId == null) {
            return false;
        }
        FollowSuspension suspension = SUSPENSIONS.remove(maidId);
        if (suspension == null) {
            return false;
        }
        if (maid == null || maid.isRemoved()) {
            // 女仆不在（区块卸载 / 被移除）：标签里还有保存好的原状态，等重新加载时再恢复
            SUSPENSIONS.put(maidId, suspension);
            LOGGER.info("[maid_move] cannot restore suspension now (maid unavailable), keeping it for reload. maid={} reason={}",
                    shortId(maidId), reason);
            return false;
        }
        if (suspension.decideRestore(maid.isHomeModeEnable()) == FollowSuspension.RestoreAction.EXTERNAL_CHANGE) {
            LOGGER.info("[maid_move] external change detected, order dropped without restoring. maid={} reason={} saved={} currentHomeMode={}",
                    shortId(maidId), reason, suspension.describe(), maid.isHomeModeEnable());
            return false;
        }
        if (suspension.hadRestriction()) {
            maid.restrictTo(new BlockPos((int) suspension.centerX(), (int) suspension.centerY(),
                    (int) suspension.centerZ()), Math.round(suspension.radius()));
        }
        maid.setHomeModeEnable(suspension.homeMode());
        MoveOrderNbt.clear(maid.getPersistentData());
        LOGGER.info("[maid_move] follow suspension restored: maid={} reason={} restored={}",
                shortId(maidId), reason, suspension.describe());
        return true;
    }

    // ===== 临时放宽工作时间（把日程改成全天工作） =====

    /** {@link #onScheduleRelief} 的结果：日程放宽做了什么，以及新指令要持久化的"原日程"。 */
    public record ScheduleChange(ScheduleRelief.Action action, @Nullable String originalSchedule, boolean sleeping) {
    }

    /**
     * 新指令登记时的"临时放宽工作时间"处理：按 {@link ScheduleRelief#decide} 的决策表
     * 保存原日程并把日程改成 {@link MaidSchedule#ALL}，或者沿用已有的原日程记录，
     * 或者什么都不做（睡觉 / 本来就是全天工作 / 配置关闭）。
     *
     * <p><b>关键约定</b>：这只女仆已经有"原日程"记录时沿用旧记录，绝不把"已经被改成 ALL 的日程"
     * 当成原日程再存一次，否则指令结束后永远恢复不回去（与暂停跟随的"不重复保存"同一套规则）。
     *
     * <p>动作本身不需要放宽（{@link MoveAction#needsScheduleRelief()} 为 false，即 go_home / cancel）
     * 时立即恢复并清记录。
     *
     * @return 见 {@link ScheduleChange}；{@code originalSchedule} 要挂到新指令上一起持久化
     */
    public static ScheduleChange onScheduleRelief(EntityMaid maid, MoveOrderData order) {
        if (maid == null || order == null || !order.action().needsScheduleRelief()) {
            if (maid != null) {
                restoreScheduleRelief(maid.getUUID(), maid, "replaced-by-order-without-relief");
            }
            return new ScheduleChange(ScheduleRelief.Action.UNCHANGED, null, false);
        }
        UUID maidId = maid.getUUID();
        // 先处理"玩家接管"：放宽期间日程被改成别的值就丢掉旧记录，后面按当前日程重新判定
        reconcileScheduleRelief(maidId, maid, "replaced");
        boolean sleeping = maid.isSleeping();
        boolean currentlyAll = isScheduleAll(maid);
        boolean hasSaved = SCHEDULE_RELIEF.containsKey(maidId);
        // work_time 自己就是为了放宽日程，不受 MoveForceWorkSchedule 影响；其余指令都要看配置
        boolean configAllows = order.action().isWorkTime() || MoveConfig.MOVE_FORCE_WORK_SCHEDULE.get();
        ScheduleRelief.Action action = ScheduleRelief.decide(sleeping, currentlyAll, hasSaved, configAllows);
        return switch (action) {
            case FORCE_ALL -> {
                String original = currentScheduleName(maid);
                if (original == null) {
                    LOGGER.warn("[maid_move] cannot read the current schedule, work schedule not widened. maid={} action={}",
                            shortId(maidId), order.action().id());
                    yield new ScheduleChange(ScheduleRelief.Action.UNCHANGED, null, sleeping);
                }
                SCHEDULE_RELIEF.put(maidId, original);
                maid.setSchedule(MaidSchedule.ALL);
                LOGGER.info("[maid_move] work schedule widened: maid={} action={} from={} to={}",
                        shortId(maidId), order.action().id(), original, MaidSchedule.ALL.name());
                yield new ScheduleChange(action, original, sleeping);
            }
            case REUSE -> {
                String original = SCHEDULE_RELIEF.get(maidId);
                LOGGER.info("[maid_move] work schedule already widened, keeping the saved original. maid={} action={} saved={}",
                        shortId(maidId), order.action().id(), original);
                yield new ScheduleChange(action, original, sleeping);
            }
            default -> {
                if (sleeping) {
                    LOGGER.info("[maid_move] work schedule not widened: the maid is sleeping. maid={} action={}",
                            shortId(maidId), order.action().id());
                } else if (currentlyAll) {
                    LOGGER.info("[maid_move] work schedule not widened: it is already {}. maid={} action={}",
                            MaidSchedule.ALL.name(), shortId(maidId), order.action().id());
                } else if (!configAllows) {
                    LOGGER.info("[maid_move] work schedule not widened: MoveForceWorkSchedule is disabled. maid={} action={}",
                            shortId(maidId), order.action().id());
                }
                yield new ScheduleChange(action, null, sleeping);
            }
        };
    }

    /**
     * 外部改动检测：放宽期间如果女仆的日程已经不是 {@link MaidSchedule#ALL}（玩家在界面里或用主模组
     * 的 {@code switch_schedule} 工具改了），视为玩家接管——<b>不恢复</b>，清掉该部分保存记录，只写 INFO。
     *
     * @return 本次是否真的丢弃了一份记录
     */
    public static boolean reconcileScheduleRelief(@Nullable UUID maidId, @Nullable EntityMaid maid, String reason) {
        if (maidId == null || maid == null || !SCHEDULE_RELIEF.containsKey(maidId) || isScheduleAll(maid)) {
            return false;
        }
        String saved = SCHEDULE_RELIEF.remove(maidId);
        LOGGER.info("[maid_move] work schedule was changed externally, dropping the saved original (no restore). maid={} reason={} saved={} current={}",
                shortId(maidId), reason, saved, currentScheduleName(maid));
        return true;
    }

    /**
     * 结束指令时恢复原日程。
     *
     * <p>与 {@link #restoreSuspension} 同构：没有记录 / 女仆不在场（记录留到重新加载）返回 false；
     * 当前日程已经不是 ALL 时视为玩家接管，不恢复、清记录、写 INFO；否则 {@code setSchedule(原日程)}。
     *
     * @return 是否真的执行了恢复
     */
    public static boolean restoreScheduleRelief(@Nullable UUID maidId, @Nullable EntityMaid maid, String reason) {
        if (maidId == null) {
            return false;
        }
        String saved = SCHEDULE_RELIEF.remove(maidId);
        if (saved == null) {
            return false;
        }
        if (maid == null || maid.isRemoved()) {
            // 女仆不在（区块卸载 / 被移除）：标签里还有保存好的原日程，等重新加载时再恢复
            SCHEDULE_RELIEF.put(maidId, saved);
            LOGGER.info("[maid_move] cannot restore the work schedule now (maid unavailable), keeping it for reload. maid={} reason={}",
                    shortId(maidId), reason);
            return false;
        }
        if (ScheduleRelief.decideRestore(saved, isScheduleAll(maid)) == ScheduleRelief.RestoreAction.EXTERNAL_CHANGE) {
            LOGGER.info("[maid_move] work schedule changed externally, dropping the order without restoring the schedule. maid={} reason={} saved={} current={}",
                    shortId(maidId), reason, saved, currentScheduleName(maid));
            return false;
        }
        MaidSchedule original = parseSchedule(saved);
        if (original == null) {
            LOGGER.warn("[maid_move] unknown saved schedule '{}', not restoring the work schedule. maid={} reason={}",
                    saved, shortId(maidId), reason);
            return false;
        }
        maid.setSchedule(original);
        LOGGER.info("[maid_move] work schedule restored: maid={} reason={} from={} to={}",
                shortId(maidId), reason, MaidSchedule.ALL.name(), original.name());
        return true;
    }

    /** 女仆当前日程是否已经是"全天工作"。 */
    public static boolean isScheduleAll(@Nullable EntityMaid maid) {
        return maid != null && maid.getSchedule() == MaidSchedule.ALL;
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

    /** {@link #finishOrder} 的结果：两套状态守卫里各自有没有真的执行恢复。 */
    private record RestoredState(boolean suspension, boolean schedule) {
    }

    /**
     * 有记录就恢复；不论有没有记录，都顺手清掉实体上的指令标签。
     * 指令的每一种结束路径都必须走这里，否则女仆会一直停留在"居家 + 无工作坐标 + 全天工作"的状态。
     * （女仆不在场时清不了标签，但这没关系：标签里的"原状态"仍然完整，
     * 下次加载时由 {@link #onMaidLoaded} 重新登记或恢复。）
     */
    private static RestoredState finishOrder(@Nullable UUID maidId, @Nullable EntityMaid maid, String reason) {
        if (maidId == null) {
            return new RestoredState(false, false);
        }
        if (maid != null && !maid.isRemoved()) {
            MoveOrderNbt.clear(maid.getPersistentData());
        }
        boolean suspension = restoreSuspension(maidId, maid, reason);
        boolean schedule = restoreScheduleRelief(maidId, maid, reason);
        clearOrderRuntimeState(maidId);
        return new RestoredState(suspension, schedule);
    }

    /** 清掉一只女仆的卡住记录与提醒计时（不碰 ORDERS、SUSPENSIONS 与 SCHEDULE_RELIEF）。 */
    private static void clearOrderRuntimeState(UUID maidId) {
        PROGRESS.remove(maidId);
        REMINDERS.remove(maidId);
    }

    /** 移除内存指令并做结束收尾（恢复原状态、清标签与运行时状态）。 */
    static void removeOrder(@Nullable UUID maidId, @Nullable EntityMaid maid, String reason) {
        if (maidId == null) {
            return;
        }
        if (ORDERS.remove(maidId)) {
            LOGGER.info("[maid_move] order dropped: reason={} maid={}", reason, shortId(maidId));
        }
        finishOrder(maidId, maid, reason);
    }

    /** {@link #cancelAndRestore} 的结果：是否本来有指令，以及两套状态守卫里各自有没有真的恢复。 */
    public record CancelResult(boolean hadOrder, boolean suspensionRestored, boolean scheduleRestored) {
    }

    /**
     * 取消指令（工具 cancel）：先恢复原状态（暂停跟随 + 日程），再清记录。
     * 工具据此决定要不要在返回文字后追加"已恢复原状态"的说明。
     */
    public static CancelResult cancelAndRestore(@Nullable UUID maidId, @Nullable EntityMaid maid) {
        if (maidId == null) {
            return new CancelResult(false, false, false);
        }
        boolean had = ORDERS.remove(maidId);
        RestoredState restored = finishOrder(maidId, maid, "cancelled");
        return new CancelResult(had, restored.suspension(), restored.schedule());
    }

    /**
     * 服务端停止时清空内存态。<b>不执行恢复动作</b>：此刻世界正在保存，暂停前的原状态与放宽前的
     * 原日程已经随指令标签一起持久化，下次加载时会由 {@link #onMaidLoaded} 恢复或重新登记。
     */
    public static void clear() {
        ORDERS.clear();
        PROGRESS.clear();
        SUSPENSIONS.clear();
        SCHEDULE_RELIEF.clear();
        REMINDERS.clear();
        tickCounter = 0;
    }

    // ===== 跨重启恢复 =====

    /**
     * 女仆进入世界（{@code EntityJoinLevelEvent}）时调用：
     * 内存里没有她的指令、而实体标签里有指令时，
     * <ul>
     *   <li>已经到期（且不是永久）→ 立刻按"结束时恢复"处理：恢复暂停前的原状态并清标签，
     *       不重新登记（到期提示不必补说，重启期间玩家看不到）；</li>
     *   <li>还没到期 / 永久 → 重新登记回管理器，剩余时间照算，处理周期按 tick 计（{@link #TICK_INTERVAL}
     *       一个周期 = 10 tick），永久指令的提醒计时从重启后重新开始；</li>
     *   <li>数据读不出来 → 拿不准就按"恢复原状态并丢弃"处理。</li>
     * </ul>
     * 日程放宽（{@link ScheduleRelief}）跟着一起恢复：标签里有"原日程"且重新登记时，日程仍是
     * {@link MaidSchedule#ALL} 就保持并装回内存记录；已经不是 ALL 说明玩家自己改过，视为玩家接管，
     * 不恢复、丢掉该字段。已经到期 / 读不出来 / 没名额而丢弃指令时，有记录就一并恢复原日程。
     */
    public static void onMaidLoaded(ServerLevel level, EntityMaid maid) {
        if (level == null || maid == null) {
            return;
        }
        UUID maidId = maid.getUUID();
        MoveOrderData stored = MoveOrderNbt.read(maid.getPersistentData());
        if (stored == null) {
            // 读不出来（动作无法识别 / UUID 非法 / 字段缺失）：拿不准就是"丢弃并恢复"——
            // 只清标签，不记得原状态时没有可恢复的东西，但至少不会让指令一直挂在那儿
            if (maid.getPersistentData().contains(MoveOrderNbt.ROOT_TAG)) {
                LOGGER.info("[maid_move] reload: unreadable order data, dropping the tag. maid={}", shortId(maidId));
                MoveOrderNbt.clear(maid.getPersistentData());
            }
            return;
        }
        if (ORDERS.isActive(maidId)) {
            // 内存里已经有（同一会话内区块反复卸载/加载）：什么都不做，内存态才是最新的
            return;
        }
        long nowGameTick = level.getGameTime();
        if (!stored.isPermanent() && stored.expireAtGameTick() <= 0L) {
            LOGGER.info("[maid_move] reload: unreadable expire time, restoring and dropping. maid={}", shortId(maidId));
            restoreFromReload(maidId, maid, stored, "reload-unreadable");
            return;
        }
        if (stored.isExpired(nowGameTick)) {
            LOGGER.info("[maid_move] reload: order already expired, restoring the saved state and dropping it. maid={} action={} saved={}",
                    shortId(maidId), stored.action().id(),
                    stored.followSuspension() == null ? "none" : stored.followSuspension().describe());
            restoreFromReload(maidId, maid, stored, "reload-expired");
            return;
        }
        long remaining = stored.isPermanent() ? MoveOrderData.PERMANENT : stored.expireAtGameTick() - nowGameTick;
        // 放宽日程的记录：当前日程已经不是 ALL 说明玩家在重启前后自己改过，视为玩家接管，
        // 不恢复也不重新登记（等于"没有放宽过"），并把标签里的该字段一并去掉
        String originalSchedule = stored.originalSchedule();
        if (originalSchedule != null && !isScheduleAll(maid)) {
            LOGGER.info("[maid_move] reload: work schedule was changed externally, dropping the saved original. maid={} saved={} current={}",
                    shortId(maidId), originalSchedule, currentScheduleName(maid));
            originalSchedule = null;
        }
        MoveOrderData reRegistered = stored.isPermanent()
                ? MoveOrderData.permanent(stored.maidId(), stored.ownerId(), stored.action(), stored.targetId(),
                        stored.x(), stored.y(), stored.z(), stored.distance(), nowGameTick,
                        stored.followSuspension(), originalSchedule)
                : MoveOrderData.timed(stored.maidId(), stored.ownerId(), stored.action(), stored.targetId(),
                        stored.x(), stored.y(), stored.z(), stored.distance(), stored.expireAtGameTick(),
                        nowGameTick, stored.followSuspension(), originalSchedule);
        if (ORDERS.put(reRegistered, MoveConfig.MOVE_MAX_ACTIVE_PER_OWNER.get()) != MoveOrders.PutResult.OK) {
            // 名额被这次会话里的新指令占满了：按"拿不准就恢复并丢弃"处理，不让女仆留在被改过的状态里
            LOGGER.info("[maid_move] reload: no free order slot, restoring and dropping. maid={}", shortId(maidId));
            restoreFromReload(maidId, maid, stored, "reload-no-slot");
            return;
        }
        PROGRESS.remove(maidId);
        MoveReminder reminder = new MoveReminder();
        reminder.arm(nowGameTick, MoveReminder.intervalTicks(reminderMinutes()));
        REMINDERS.put(maidId, reminder);
        if (stored.followSuspension() != null) {
            SUSPENSIONS.put(maidId, stored.followSuspension());
        }
        if (originalSchedule != null) {
            SCHEDULE_RELIEF.put(maidId, originalSchedule);
            LOGGER.info("[maid_move] reload: keeping the widened work schedule. maid={} original={}",
                    shortId(maidId), originalSchedule);
        }
        LOGGER.info("[maid_move] reload: order re-registered. maid={} action={} permanent={} remainingTicks={} followSuspended={} scheduleRelieved={}",
                shortId(maidId), stored.action().id(), stored.isPermanent(), remaining,
                stored.followSuspension() != null, originalSchedule != null);
    }

    /**
     * 重新加载时"恢复原状态并丢弃指令"的统一入口（不重新登记，只清标签与内存态）。
     * 暂停跟随与日程放宽两套记录都要走一遍恢复。
     */
    private static void restoreFromReload(UUID maidId, EntityMaid maid, MoveOrderData stored, String reason) {
        if (stored.followSuspension() != null) {
            SUSPENSIONS.put(maidId, stored.followSuspension());
            restoreSuspension(maidId, maid, reason);
        } else {
            SUSPENSIONS.remove(maidId);
        }
        if (stored.originalSchedule() != null) {
            SCHEDULE_RELIEF.put(maidId, stored.originalSchedule());
            restoreScheduleRelief(maidId, maid, reason);
        } else {
            SCHEDULE_RELIEF.remove(maidId);
        }
        clearOrderRuntimeState(maidId);
        MoveOrderNbt.clear(maid.getPersistentData());
    }

    // ===== tick 驱动 =====

    /**
     * 服务端 tick 驱动：每 {@link #TICK_INTERVAL} tick 处理一次全部指令。
     * 清理顺序为先到期、再处理提醒，最后逐只解析实体与目标并下发移动。
     */
    public static void onServerTick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        tickCounter++;
        if (tickCounter % TICK_INTERVAL != 0) {
            return;
        }
        long nowTick = server.getTickCount();
        for (UUID maidId : ORDERS.maidIds()) {
            MoveOrderData order = ORDERS.get(maidId);
            if (order == null) {
                continue;
            }
            EntityMaid maid = findMaid(server, maidId);
            if (maid == null || maid.isRemoved() || !maid.isAlive()
                    || !(maid.level() instanceof ServerLevel level)) {
                // 女仆死亡/被移除/所在维度已卸载：指令失效（恢复记录保留在标签里，重新加载时处理）
                LOGGER.info("[maid_move] order dropped: reason=maid-unavailable maid={}", shortId(maidId));
                removeOrder(maidId, maid != null && !maid.isRemoved() ? maid : null, "maid-unavailable");
                continue;
            }
            long nowGameTick = level.getGameTime();
            if (order.isExpired(nowGameTick)) {
                LOGGER.info("[maid_move] order expired: maid={} action={} permanent={}",
                        shortId(maidId), order.action().id(), order.isPermanent());
                removeOrder(maidId, maid, "expired");
                noticeExpired(level, maid, order);
                continue;
            }
            tickReminder(level, maid, order, nowTick);
            try {
                step(level, maid, order, nowTick, nowGameTick);
            } catch (RuntimeException e) {
                LOGGER.warn("[maid_move] step failed, order dropped. maid={} action={}",
                        shortId(maidId), order.action().id(), e);
                removeOrder(maidId, maid, "step-error");
            }
        }
    }

    /** 永久指令的定期提醒（配置为 0 分钟即关闭）。 */
    private static void tickReminder(ServerLevel level, EntityMaid maid, MoveOrderData order, long nowTick) {
        MoveReminder reminder = REMINDERS.get(maid.getUUID());
        if (reminder == null) {
            return;
        }
        int intervalTicks = MoveReminder.intervalTicks(reminderMinutes());
        if (intervalTicks <= 0) {
            return;
        }
        if (reminder.due(nowTick, intervalTicks)) {
            boolean zh = PromptLang.isChinese(PromptLang.of(maid));
            String detail = MoveDescribe.describe(order, targetName(level, maid, order), zh);
            MaidSpeech.say(maid, MoveText.saidReminder(zh, detail));
            LOGGER.info("[maid_move] permanent order reminder said: maid={} action={} intervalMinutes={}",
                    shortId(maid.getUUID()), order.action().id(), reminderMinutes());
        }
    }

    /** 时限指令自然到期时，女仆自己说一句（cancel / 被替换 / 因故丢弃时不用说）。 */
    private static void noticeExpired(ServerLevel level, EntityMaid maid, MoveOrderData order) {
        if (order.isPermanent()) {
            return;
        }
        boolean zh = PromptLang.isChinese(PromptLang.of(maid));
        String detail = MoveDescribe.describe(order, targetName(level, maid, order), zh);
        String text = MoveText.saidExpired(zh, detail);
        if (order.followSuspension() != null) {
            text = text + " " + MoveText.restoredNote(zh);
        }
        MaidSpeech.say(maid, text);
        LOGGER.info("[maid_move] timed order expired, maid said it: maid={} action={}",
                shortId(maid.getUUID()), order.action().id());
    }

    // ===== 单个指令的处理 =====

    /**
     * 处理一条指令：先解析"目的地 + 是否已满足目标"，据此跑卡住检测，再决定兜底还是下发移动。
     */
    private static void step(ServerLevel level, EntityMaid maid, MoveOrderData order, long nowTick, long nowGameTick) {
        if (order.action() == MoveAction.CANCEL) {
            removeOrder(maid.getUUID(), maid, "cancel-order");
            return;
        }
        // 放宽日程期间的"外部改动检测"：玩家在界面里或用主模组的 switch_schedule 工具把日程
        // 改成了别的值。视为玩家接管：丢掉保存的原日程、不恢复；移动类指令本身照旧继续。
        boolean scheduleTakenOver = reconcileScheduleRelief(maid.getUUID(), maid, "external-change");
        if (order.action() == MoveAction.WORK_TIME) {
            // work_time 的唯一作用就是放宽日程，被玩家覆盖后它已经没有意义：结束指令
            if (scheduleTakenOver) {
                removeOrder(maid.getUUID(), maid, "external-change");
                return;
            }
            // 不移动、不下发 WALK_TARGET、也不做卡住检测（没有任何目的地）
            return;
        }
        if (order.action() == MoveAction.STAY) {
            // stay：什么都不下发，原地不动；这里只做"外部改动检测"。
            // 若暂停跟随期间玩家把居家模式改回了 false（界面 / 跟随工具），视为玩家接管：
            // 结束指令、不恢复、清记录（恢复函数内部会写 INFO 日志）。
            if (order.followSuspension() != null
                    && order.followSuspension().decideRestore(maid.isHomeModeEnable())
                    == FollowSuspension.RestoreAction.EXTERNAL_CHANGE) {
                removeOrder(maid.getUUID(), maid, "external-change");
                return;
            }
            if (!SUSPENSIONS.containsKey(maid.getUUID())) {
                // 跨重启后重新登记的 stay 应当带着暂停记录；真丢了就补一份，避免指令结束后把状态改错
                LOGGER.info("[maid_move] stay order lost its suspension record, re-recording. maid={}",
                        shortId(maid.getUUID()));
                beginSuspension(maid, order);
            }
            return;
        }
        Goal goal = resolveGoal(level, maid, order);
        if (goal == null) {
            // 目标丢失，resolveGoal 里已经丢掉指令
            return;
        }
        if (order.followSuspension() != null
                && order.followSuspension().decideRestore(maid.isHomeModeEnable())
                == FollowSuspension.RestoreAction.EXTERNAL_CHANGE) {
            removeOrder(maid.getUUID(), maid, "external-change");
            return;
        }
        boolean paused = !maid.canBrainMoving();
        MoveProgress progress = PROGRESS.computeIfAbsent(maid.getUUID(), id -> new MoveProgress());
        // !goal.walking() 表示"本周期没打算走"（已到位、或漂出量还在 goto 的滞回带内）：
        // 与暂停同样处理——不算卡住、重置计时，否则刚到位又漂出一点点的女仆会被误判并传送
        MoveProgress.StuckReason reason = progress.tick(goal.satisfied(), paused || !goal.walking(),
                goal.distance(), hasCantReachMemory(maid), nowTick);
        if (reason != MoveProgress.StuckReason.NONE
                && handleStuck(level, maid, order, goal, progress, reason, nowTick)) {
            // 已传送或已放弃指令：本周期不再下发移动
            return;
        }
        if (!paused) {
            drive(level, maid, order, goal, nowTick);
        }
    }

    /**
     * 本周期要走去哪里。
     *
     * @param entity      目标实体（approach / retreat / guard），goto 为 null
     * @param destination 目的地坐标；retreat 算不出后退点时为 null
     * @param satisfied   是否已满足指令目标（goto 到位 / 已在 distance 内 / 已不需要后退）
     * @param walking     本周期是否要重新下发 WALK_TARGET
     * @param distance    女仆到目的地的距离（格）；目的地未知时为 0
     */
    private record Goal(@Nullable LivingEntity entity, @Nullable Vec3 destination,
                        boolean satisfied, boolean walking, double distance) {
    }

    /**
     * 解析指令的"目的地 / 是否已满足目标 / 本周期是否要走路"；目标实体消失时丢弃指令并返回 null。
     *
     * <ul>
     *   <li>approach / guard：目的地 = 目标实体位置，满足条件 = 已在 {@code distance} 内；</li>
     *   <li>retreat：目的地 = 算出来的后退点，满足条件 = 已不需要后退；水平重合算不出后退点则没有目的地；</li>
     *   <li>goto：目的地 = 坐标，满足条件 = 已进入到位半径 {@link MoveMath#ARRIVE_RADIUS}；
     *       漂出到位半径 + {@link #GOTO_DRIFT_MARGIN} 格才重新下发移动（滞回带内不算没到位）。</li>
     * </ul>
     */
    @Nullable
    private static Goal resolveGoal(ServerLevel level, EntityMaid maid, MoveOrderData order) {
        switch (order.action()) {
            case GOTO -> {
                double distanceSqr = maid.distanceToSqr(order.x(), order.y(), order.z());
                double holdRadius = MoveMath.ARRIVE_RADIUS + GOTO_DRIFT_MARGIN;
                return new Goal(null, new Vec3(order.x(), order.y(), order.z()),
                        MoveMath.hasArrived(distanceSqr), distanceSqr >= holdRadius * holdRadius,
                        Math.sqrt(distanceSqr));
            }
            case APPROACH, GUARD -> {
                LivingEntity target = resolveTarget(level, order);
                if (target == null) {
                    removeOrder(maid.getUUID(), maid, "target-lost");
                    return null;
                }
                double distanceSqr = maid.distanceToSqr(target);
                boolean needsWalk = MoveMath.needsApproach(distanceSqr, order.distance());
                return new Goal(target, target.position(), !needsWalk, needsWalk, Math.sqrt(distanceSqr));
            }
            case RETREAT -> {
                LivingEntity target = resolveTarget(level, order);
                if (target == null) {
                    removeOrder(maid.getUUID(), maid, "target-lost");
                    return null;
                }
                double distanceSqr = maid.distanceToSqr(target);
                MoveMath.Point retreat = MoveMath.retreatPoint(
                        new MoveMath.Point(maid.getX(), maid.getY(), maid.getZ()),
                        new MoveMath.Point(target.getX(), target.getY(), target.getZ()),
                        order.distance() + MoveMath.RETREAT_EXTRA);
                Vec3 destination = retreat == null
                        ? null : new Vec3(retreat.x(), retreat.y(), retreat.z());
                boolean needsWalk = destination != null
                        && MoveMath.needsRetreat(distanceSqr, order.distance());
                return new Goal(target, destination,
                        !MoveMath.needsRetreat(distanceSqr, order.distance()), needsWalk,
                        destination == null ? 0.0D : Math.sqrt(maid.distanceToSqr(destination)));
            }
            default -> {
                return null;
            }
        }
    }

    /** 下发本周期的移动（守护时顺带处理攻击目标）。 */
    private static void drive(ServerLevel level, EntityMaid maid, MoveOrderData order,
                              Goal goal, long nowTick) {
        switch (order.action()) {
            case GOTO -> {
                // 已到位不结束指令（"在这儿等着我"要有效到到期），漂出滞回带才重新下发
                if (goal.walking()) {
                    walkToPoint(maid, goal.destination(), POINT_CLOSE_ENOUGH);
                }
            }
            case APPROACH -> {
                if (goal.walking()) {
                    walkToEntity(maid, goal.entity(), order.distance());
                }
                // 已到位：保持不动，指令继续有效到到期；目标再移动时下一个处理周期会重新下发
            }
            case GUARD -> {
                if (goal.walking()) {
                    walkToEntity(maid, goal.entity(), order.distance());
                }
                if (nowTick % GuardCombat.SCAN_INTERVAL == 0) {
                    GuardCombat.tryAttack(level, maid, goal.entity());
                }
            }
            case RETREAT -> {
                if (!goal.walking()) {
                    return;
                }
                // 沿用旧行为：后退点附近找不到可站立位置就保持不动（指令继续有效，目标移动后可能就有位置了）
                BlockPos standable = findStandable(level, toPoint(goal.destination()));
                if (standable != null) {
                    walkToPoint(maid, Vec3.atBottomCenterOf(standable), POINT_CLOSE_ENOUGH);
                }
            }
            default -> {
            }
        }
    }

    // ===== 寻路失败兜底（传送 / 放弃） =====

    /**
     * 判定卡住后的兜底：距离不太远就找落脚点传送，太远 / 没地方站 / 传送次数用尽就取消指令并让女仆说一句话。
     *
     * @return 本周期是否已经处理完毕（传送或取消指令）；{@code false} 表示"什么都不做，继续照旧下发移动"
     */
    private static boolean handleStuck(ServerLevel level, EntityMaid maid, MoveOrderData order,
                                       Goal goal, MoveProgress progress,
                                       MoveProgress.StuckReason reason, long nowTick) {
        String action = order.action().id();
        String reasonId = reasonId(reason);
        double distance = goal.distance();
        if (!MoveConfig.MOVE_TELEPORT_FALLBACK.get()) {
            // 兜底关闭：保持旧行为，只记日志，不传送也不说话
            LOGGER.info("[maid_move] stuck ({}) but teleport fallback is disabled. maid={} action={} distance={}",
                    reasonId, shortId(maid.getUUID()), action, fmt(distance));
            progress.backOff(nowTick);
            return false;
        }
        boolean zh = PromptLang.isChinese(PromptLang.of(maid));
        int maxDistance = MoveConfig.MOVE_TELEPORT_MAX_DISTANCE.get();
        if (distance > maxDistance) {
            LOGGER.info("[maid_move] give up: destination too far. maid={} action={} distance={} max={}",
                    shortId(maid.getUUID()), action, fmt(distance), maxDistance);
            removeOrder(maid.getUUID(), maid, "too-far");
            MaidSpeech.say(maid, MoveText.saidTooFar(zh, maxDistance));
            return true;
        }
        if (progress.teleportsExhausted()) {
            LOGGER.info("[maid_move] give up: teleport budget exhausted. maid={} action={} teleports={}",
                    shortId(maid.getUUID()), action, progress.teleportCount());
            removeOrder(maid.getUUID(), maid, "teleport-budget-exhausted");
            MaidSpeech.say(maid, MoveText.saidGaveUp(zh));
            return true;
        }
        if (!progress.canTeleport(nowTick)) {
            // 理论上到不了这里（冷却期内 MoveProgress 不会判定卡住），留作防御
            LOGGER.info("[maid_move] stuck ({}) but teleport is cooling down. maid={} action={} teleports={}",
                    reasonId, shortId(maid.getUUID()), action, progress.teleportCount());
            progress.backOff(nowTick);
            return false;
        }
        BlockPos landing = findLanding(level, maid, order, goal);
        if (landing == null) {
            LOGGER.info("[maid_move] give up: no standable spot. maid={} action={} destination={}",
                    shortId(maid.getUUID()), action, fmtVec(goal.destination()));
            removeOrder(maid.getUUID(), maid, "no-standable-spot");
            MaidSpeech.say(maid, MoveText.saidNoSpot(zh));
            return true;
        }
        int attempt = progress.teleportCount() + 1;
        LOGGER.info("[maid_move] stuck ({}), teleport #{}: maid={} action={} from={} to={} distance={}",
                reasonId, attempt, shortId(maid.getUUID()), action, fmtVec(maid.position()),
                fmtVec(Vec3.atBottomCenterOf(landing)), fmt(distance));
        teleportMaid(maid, landing);
        progress.recordTeleport(nowTick);
        progress.reset(nowTick);
        MaidSpeech.say(maid, MoveText.saidTeleported(zh, action));
        return true;
    }

    /**
     * 找传送的落脚点：approach / guard 在目标周围 {@code distance} 格的环上（优先女仆原来的方向），
     * retreat 用后退点，goto 用坐标；每个候选再交给 {@link #findStandable} 做附近偏移采样。
     */
    @Nullable
    private static BlockPos findLanding(ServerLevel level, EntityMaid maid, MoveOrderData order, Goal goal) {
        MoveMath.Point destination = toPoint(goal.destination());
        if (destination == null) {
            return null;
        }
        MoveMath.Point from = new MoveMath.Point(maid.getX(), maid.getY(), maid.getZ());
        for (MoveMath.Point candidate : MoveMath.landingCandidates(from, destination, order.action(),
                order.distance())) {
            BlockPos standable = findStandable(level, candidate);
            if (standable != null) {
                return standable;
            }
        }
        return null;
    }

    // ===== 目标与查找工具方法 =====

    /** 解析指令的目标实体；目标离线/被移除/跨维度/已死亡时返回 null（由调用方清除指令）。 */
    @Nullable
    private static LivingEntity resolveTarget(ServerLevel level, MoveOrderData order) {
        UUID targetId = order.targetId();
        if (targetId == null) {
            return null;
        }
        // 玩家不在实体查找表里，必须走 PlayerList；同时保证同维度
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(targetId);
        if (player != null) {
            return player.level() == level ? player : null;
        }
        Entity entity = level.getEntity(targetId);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /**
     * 目标显示名：主人 → {@link MoveText#ownerName}；其他存在的实体 → 名字；已经不在 → null。
     * 供提醒 / 到期提示 / 上下文描述使用。
     */
    @Nullable
    private static String targetName(ServerLevel level, EntityMaid maid, MoveOrderData order) {
        if (order.targetId() == null) {
            return null;
        }
        if (order.targetId().equals(maid.getOwnerUUID())) {
            return MoveText.ownerName(PromptLang.isChinese(PromptLang.of(maid)));
        }
        LivingEntity target = resolveTarget(level, order);
        return target == null ? null : target.getName().getString();
    }

    @Nullable
    private static EntityMaid findMaid(MinecraftServer server, UUID maidId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(maidId);
            if (entity instanceof EntityMaid maid) {
                return maid;
            }
        }
        return null;
    }

    private static String reasonId(MoveProgress.StuckReason reason) {
        return switch (reason) {
            case NO_PROGRESS -> "no-progress";
            case CANT_REACH -> "cant-reach";
            default -> "none";
        };
    }

    /** 当前配置的永久提醒间隔（分钟）。 */
    private static int reminderMinutes() {
        return MoveConfig.MOVE_PERMANENT_REMINDER_MINUTES.get();
    }
}
