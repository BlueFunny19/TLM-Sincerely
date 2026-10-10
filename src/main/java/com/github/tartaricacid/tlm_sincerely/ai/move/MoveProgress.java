package com.github.tartaricacid.tlm_sincerely.ai.move;

/**
 * 单条移动指令的「女仆到底有没有在动」判定，以及寻路失败兜底的传送预算。
 *
 * <p>纯 Java、不引用任何 Minecraft 类型，时间全部由调用方以服务器 tick 传入，因此可以直接单测。
 * 每个处理周期（{@code MoveDirectiveManager} 里是 10 tick）调用一次 {@link #tick}：
 *
 * <ul>
 *   <li>已经满足指令目标（approach / guard 已在 distance 内，retreat 已不需要后退，goto 已到位）→ 不算卡住，重置计时；</li>
 *   <li>因坐下 / 睡觉 / 骑乘 / 被拴住暂停移动（{@code EntityMaid#canBrainMoving()} 为 false）→ 同样不计时；</li>
 *   <li>距离目的地比「最近最佳距离」至少近 {@link #PROGRESS_THRESHOLD} 格算有进展，连续
 *       {@link #NO_PROGRESS_TICKS} tick 没有进展判定为 {@link StuckReason#NO_PROGRESS}；</li>
 *   <li>女仆 Brain 里存在 {@code MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE}（原版
 *       {@code MoveToTargetSink} 寻路失败时写入）且连续 {@link #CANT_REACH_CYCLES} 个处理周期都没有进展，
 *       立刻判定为 {@link StuckReason#CANT_REACH}（比 no-progress 快得多的另一条路径）。</li>
 * </ul>
 *
 * <p>传送预算：同一条指令最多 {@link #MAX_TELEPORTS} 次、相邻两次至少间隔
 * {@link #TELEPORT_INTERVAL_TICKS} tick；冷却期内不重复判定卡住（否则会连着传送）。
 * 判定卡住本身也做节流：第一次判定可以走 CANT_REACH 快路径，之后每 {@link #NO_PROGRESS_TICKS}
 * tick 才再报一次，避免兜底关闭时日志刷屏。
 */
public final class MoveProgress {
    /** 距离目的地比「最近最佳距离」至少近这么多格才算有进展。 */
    public static final double PROGRESS_THRESHOLD = 1.0D;

    /** 连续多少个 tick 没有进展判定为卡住（10 tick 一个处理周期时约 5 秒）。 */
    public static final int NO_PROGRESS_TICKS = 100;

    /** CANT_REACH_WALK_TARGET_SINCE 连续出现多少个处理周期判定为卡住。 */
    public static final int CANT_REACH_CYCLES = 2;

    /** 同一条指令最多传送几次。 */
    public static final int MAX_TELEPORTS = 3;

    /** 同一条指令相邻两次传送至少间隔多少 tick。 */
    public static final int TELEPORT_INTERVAL_TICKS = 100;

    /** {@link #tick} 的判定结果。 */
    public enum StuckReason {
        /** 正常：有进展、已满足目标、暂停中、冷却中，或还没到判定阈值。 */
        NONE,
        /** 连续 {@link #NO_PROGRESS_TICKS} tick 没有靠近目的地。 */
        NO_PROGRESS,
        /** 原版寻路失败标记（CANT_REACH_WALK_TARGET_SINCE）连续出现。 */
        CANT_REACH
    }

    private boolean started;
    private double bestDistance = Double.MAX_VALUE;
    private long lastProgressTick;
    private int cantReachCycles;
    private int teleportCount;
    private long lastTeleportTick = Long.MIN_VALUE;
    /** 上一次「判定卡住」的 tick；用于给判定本身做节流（见类注释）。 */
    private boolean stuckReported;
    private long lastStuckTick;

    /**
     * 处理一个周期。
     *
     * @param goalSatisfied 本周期是否已满足指令目标（到位 / 已在距离内 / 已不需要后退）
     * @param paused        是否因不可移动状态暂停（暂停期间不计时）
     * @param distance      女仆到「目的地」的当前距离（格）；目的地未知时调用方应传 {@code paused=true}
     * @param cantReach     女仆 Brain 里是否存在 {@code CANT_REACH_WALK_TARGET_SINCE}
     * @param nowTick       当前服务器 tick
     * @return 本周期是否判定为卡住，以及判定理由
     */
    public StuckReason tick(boolean goalSatisfied, boolean paused, double distance,
                            boolean cantReach, long nowTick) {
        if (!started) {
            started = true;
            bestDistance = distance;
            lastProgressTick = nowTick;
            cantReachCycles = 0;
            return StuckReason.NONE;
        }
        // 刚传送完的冷却期内不重新判定，避免连着传送同一只女仆
        if (inTeleportCooldown(nowTick)) {
            bestDistance = Math.min(bestDistance, distance);
            lastProgressTick = nowTick;
            cantReachCycles = 0;
            return StuckReason.NONE;
        }
        // 已满足目标 / 暂停中：不算卡住，重置计时
        if (goalSatisfied || paused) {
            bestDistance = distance;
            lastProgressTick = nowTick;
            cantReachCycles = 0;
            return StuckReason.NONE;
        }
        // 有进展：刷新记录与计时
        if (distance <= bestDistance - PROGRESS_THRESHOLD) {
            bestDistance = distance;
            lastProgressTick = nowTick;
            cantReachCycles = 0;
            return StuckReason.NONE;
        }
        // 无进展：CANT_REACH 快路径优先
        cantReachCycles = cantReach ? cantReachCycles + 1 : 0;
        boolean stuck = cantReachCycles >= CANT_REACH_CYCLES
                || nowTick - lastProgressTick >= NO_PROGRESS_TICKS;
        if (!stuck) {
            return StuckReason.NONE;
        }
        // 判定节流：报过一次之后，至少要再等一个 NO_PROGRESS_TICKS 才允许再报
        if (stuckReported && nowTick - lastStuckTick < NO_PROGRESS_TICKS) {
            return StuckReason.NONE;
        }
        stuckReported = true;
        lastStuckTick = nowTick;
        return cantReachCycles >= CANT_REACH_CYCLES ? StuckReason.CANT_REACH : StuckReason.NO_PROGRESS;
    }

    /** 已传送次数。 */
    public int teleportCount() {
        return teleportCount;
    }

    /** 传送次数是否已用尽（用尽后仍卡住就放弃指令）。 */
    public boolean teleportsExhausted() {
        return teleportCount >= MAX_TELEPORTS;
    }

    /** 现在是否还允许传送（次数未用尽且冷却已过）。 */
    public boolean canTeleport(long nowTick) {
        return !teleportsExhausted() && !inTeleportCooldown(nowTick);
    }

    /** 记录一次传送。 */
    public void recordTeleport(long nowTick) {
        teleportCount++;
        lastTeleportTick = nowTick;
    }

    /** 传送成功后重置进度计时（继续跟踪同一条指令，但传送次数保留）。 */
    public void reset(long nowTick) {
        started = true;
        bestDistance = Double.MAX_VALUE;
        lastProgressTick = nowTick;
        cantReachCycles = 0;
        stuckReported = false;
    }

    /** 判定卡住但本周期不做任何兜底动作（兜底关闭 / 传送冷却中）时调用，重新开始计时。 */
    public void backOff(long nowTick) {
        reset(nowTick);
    }

    private boolean inTeleportCooldown(long nowTick) {
        return teleportCount > 0 && nowTick - lastTeleportTick < TELEPORT_INTERVAL_TICKS;
    }
}
