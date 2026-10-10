package com.github.tartaricacid.tlm_sincerely.ai.move;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MoveProgress} 的单元测试：进展判定、100 tick 阈值、CANT_REACH 快路径、
 * 已满足目标 / 暂停时的重置、判定节流、传送次数与间隔限制。
 * 全部为纯逻辑，不依赖 Minecraft。
 */
class MoveProgressTest {
    private static final long TICK = 10L;

    // ===== 进展判定 =====

    @Test
    void firstTick_neverStuck() {
        MoveProgress progress = new MoveProgress();
        assertEquals(MoveProgress.StuckReason.NONE,
                progress.tick(false, false, 50.0D, true, 0L));
    }

    @Test
    void approachingByMoreThanOneBlock_isProgress() {
        MoveProgress progress = new MoveProgress();
        // t=0 起点 50 格；此后每周期靠近 2 格 → 永远不会卡住
        long now = 0L;
        progress.tick(false, false, 50.0D, false, now);
        for (int i = 0; i < 40; i++) {
            now += TICK;
            double distance = 50.0D - 2.0D * (i + 1);
            assertEquals(MoveProgress.StuckReason.NONE,
                    progress.tick(false, false, distance, false, now), "cycle " + i);
        }
    }

    @Test
    void slowButSteadyProgress_neverStuck() {
        MoveProgress progress = new MoveProgress();
        // 每 2 个周期才靠近 1.4 格（0.7 格/周期）：仍然算有进展，只是记录刷新得慢
        long now = 0L;
        double distance = 50.0D;
        progress.tick(false, false, distance, false, now);
        for (int i = 1; i <= 200; i++) {
            now += TICK;
            if (i % 2 == 0) {
                distance -= 1.4D;
            }
            assertEquals(MoveProgress.StuckReason.NONE,
                    progress.tick(false, false, distance, false, now), "cycle " + i);
        }
    }

    @Test
    void stayingStill_isStuckAfterNoProgressTicks() {
        MoveProgress progress = new MoveProgress();
        progress.tick(false, false, 50.0D, false, 0L);
        // 5 秒（100 tick）之内不判定
        for (long now = TICK; now < MoveProgress.NO_PROGRESS_TICKS; now += TICK) {
            assertEquals(MoveProgress.StuckReason.NONE,
                    progress.tick(false, false, 50.0D, false, now), "tick " + now);
        }
        assertEquals(MoveProgress.StuckReason.NO_PROGRESS,
                progress.tick(false, false, 50.0D, false, MoveProgress.NO_PROGRESS_TICKS));
    }

    @Test
    void movingAway_isNotProgress() {
        MoveProgress progress = new MoveProgress();
        progress.tick(false, false, 50.0D, false, 0L);
        long now = 0L;
        for (int i = 0; i < 11; i++) {
            now += TICK;
            // 越走越远：距离变大不算进展
            MoveProgress.StuckReason reason = progress.tick(false, false, 50.0D + i, false, now);
            if (now == MoveProgress.NO_PROGRESS_TICKS) {
                assertEquals(MoveProgress.StuckReason.NO_PROGRESS, reason);
            } else {
                assertEquals(MoveProgress.StuckReason.NONE, reason);
            }
        }
    }

    // ===== 已满足目标 / 暂停 =====

    @Test
    void goalSatisfied_resetsTheClock() {
        MoveProgress progress = new MoveProgress();
        progress.tick(false, false, 50.0D, false, 0L);
        // 一直到 90 tick 都没动，然后到位一次：计时清零
        progress.tick(false, false, 50.0D, false, 90L);
        assertEquals(MoveProgress.StuckReason.NONE, progress.tick(true, false, 50.0D, false, 100L));
        // 再过 90 tick（总计 190，距上次到位只有 90）仍不该判定
        assertEquals(MoveProgress.StuckReason.NONE, progress.tick(false, false, 50.0D, false, 190L));
        // 距上次到位满 100 tick 才判定
        assertEquals(MoveProgress.StuckReason.NO_PROGRESS,
                progress.tick(false, false, 50.0D, false, 200L));
    }

    @Test
    void paused_doesNotCountTime() {
        MoveProgress progress = new MoveProgress();
        progress.tick(false, false, 50.0D, false, 0L);
        // 坐着睡了 1000 tick：期间一直"暂停"
        long now = 0L;
        double distance = 50.0D;
        for (int i = 0; i < 100; i++) {
            now += TICK;
            assertEquals(MoveProgress.StuckReason.NONE,
                    progress.tick(false, true, distance, false, now), "paused " + now);
        }
        // 醒来后再站 90 tick 也不该判定
        assertEquals(MoveProgress.StuckReason.NONE, progress.tick(false, false, distance, false, now + 90));
        assertEquals(MoveProgress.StuckReason.NO_PROGRESS,
                progress.tick(false, false, distance, false, now + 100));
    }

    @Test
    void satisfied_clearsCantReachCounter() {
        MoveProgress progress = new MoveProgress();
        progress.tick(false, false, 50.0D, false, 0L);
        progress.tick(false, false, 50.0D, true, TICK);          // cantReachCycles = 1
        progress.tick(true, false, 50.0D, false, 2 * TICK);      // 到位一次清零
        // 之后即使马上又能看到 CANT_REACH，也要重新数满 2 个周期
        assertEquals(MoveProgress.StuckReason.NONE,
                progress.tick(false, false, 50.0D, true, 3 * TICK));
        assertEquals(MoveProgress.StuckReason.CANT_REACH,
                progress.tick(false, false, 50.0D, true, 4 * TICK));
    }

    // ===== CANT_REACH 快路径 =====

    @Test
    void cantReach_afterTwoCycles_isStuckFasterThanNoProgress() {
        MoveProgress progress = new MoveProgress();
        progress.tick(false, false, 50.0D, false, 0L);
        assertEquals(MoveProgress.StuckReason.NONE, progress.tick(false, false, 50.0D, true, TICK));
        assertEquals(MoveProgress.StuckReason.CANT_REACH, progress.tick(false, false, 50.0D, true, 2 * TICK));
    }

    @Test
    void cantReach_mustBeConsecutive() {
        MoveProgress progress = new MoveProgress();
        progress.tick(false, false, 50.0D, false, 0L);
        assertEquals(MoveProgress.StuckReason.NONE, progress.tick(false, false, 50.0D, true, TICK));
        // 中间一个周期没有该标记 → 重新计数
        assertEquals(MoveProgress.StuckReason.NONE, progress.tick(false, false, 50.0D, false, 2 * TICK));
        assertEquals(MoveProgress.StuckReason.NONE, progress.tick(false, false, 50.0D, true, 3 * TICK));
        assertEquals(MoveProgress.StuckReason.CANT_REACH, progress.tick(false, false, 50.0D, true, 4 * TICK));
    }

    @Test
    void cantReach_withProgress_isNotStuck() {
        MoveProgress progress = new MoveProgress();
        progress.tick(false, false, 50.0D, false, 0L);
        // 每周期都有进展：即使 CANT_REACH 常驻（只走通部分路径）也不判定卡住
        long now = 0L;
        double distance = 50.0D;
        for (int i = 0; i < 30; i++) {
            now += TICK;
            distance -= 1.5D;
            assertEquals(MoveProgress.StuckReason.NONE,
                    progress.tick(false, false, distance, true, now), "cycle " + i);
        }
    }

    @Test
    void cantReach_reportsAreThrottled() {
        MoveProgress progress = new MoveProgress();
        progress.tick(false, false, 50.0D, false, 0L);
        progress.tick(false, false, 50.0D, true, TICK);
        assertEquals(MoveProgress.StuckReason.CANT_REACH, progress.tick(false, false, 50.0D, true, 2 * TICK));
        // 报过一次后，下一个 NO_PROGRESS_TICKS 内不再重复报（否则兜底关闭时日志会刷屏）
        assertEquals(MoveProgress.StuckReason.NONE,
                progress.tick(false, false, 50.0D, true, 2 * TICK + TICK));
        assertEquals(MoveProgress.StuckReason.CANT_REACH,
                progress.tick(false, false, 50.0D, true, 2 * TICK + MoveProgress.NO_PROGRESS_TICKS));
    }

    @Test
    void backOff_restartsTheClock() {
        MoveProgress progress = new MoveProgress();
        progress.tick(false, false, 50.0D, false, 0L);
        assertEquals(MoveProgress.StuckReason.NO_PROGRESS,
                progress.tick(false, false, 50.0D, false, MoveProgress.NO_PROGRESS_TICKS));
        progress.backOff(MoveProgress.NO_PROGRESS_TICKS);
        // 重新计时：backOff 后的第一个周期作为新基线（算有进展），之后要重新数满 100 tick
        assertEquals(MoveProgress.StuckReason.NONE,
                progress.tick(false, false, 50.0D, false, 190L));
        assertEquals(MoveProgress.StuckReason.NONE,
                progress.tick(false, false, 50.0D, false, 280L));
        assertEquals(MoveProgress.StuckReason.NO_PROGRESS,
                progress.tick(false, false, 50.0D, false, 290L));
    }

    // ===== 传送次数与间隔 =====

    @Test
    void teleportBudget_allowsThreeTeleports() {
        MoveProgress progress = new MoveProgress();
        progress.tick(false, false, 50.0D, false, 0L);
        assertFalse(progress.teleportsExhausted());
        for (int i = 0; i < MoveProgress.MAX_TELEPORTS; i++) {
            long now = 1000L * (i + 1);
            assertTrue(progress.canTeleport(now), "teleport " + i);
            progress.recordTeleport(now);
        }
        assertEquals(MoveProgress.MAX_TELEPORTS, progress.teleportCount());
        assertTrue(progress.teleportsExhausted());
        assertFalse(progress.canTeleport(100_000L));
    }

    @Test
    void teleportCooldown_blocksTheSecondTeleportTooEarly() {
        MoveProgress progress = new MoveProgress();
        progress.tick(false, false, 50.0D, false, 0L);
        assertTrue(progress.canTeleport(0L));
        progress.recordTeleport(0L);
        assertFalse(progress.canTeleport(TICK));
        assertFalse(progress.canTeleport(MoveProgress.TELEPORT_INTERVAL_TICKS - 1));
        assertTrue(progress.canTeleport(MoveProgress.TELEPORT_INTERVAL_TICKS));
    }

    @Test
    void teleportCooldown_suppressesStuckDetection() {
        MoveProgress progress = new MoveProgress();
        progress.tick(false, false, 50.0D, false, 0L);
        progress.recordTeleport(0L);
        // 冷却期内即使站着不动、CANT_REACH 常驻，也不判定卡住（否则会连着传送）
        for (long now = TICK; now < MoveProgress.TELEPORT_INTERVAL_TICKS; now += TICK) {
            assertEquals(MoveProgress.StuckReason.NONE,
                    progress.tick(false, false, 50.0D, true, now), "tick " + now);
        }
    }

    @Test
    void reset_keepsTeleportBudgetButRestartsClock() {
        MoveProgress progress = new MoveProgress();
        progress.tick(false, false, 50.0D, false, 0L);
        progress.recordTeleport(0L);
        progress.reset(MoveProgress.TELEPORT_INTERVAL_TICKS);
        assertEquals(1, progress.teleportCount());
        // 重置后从新起点开始：重置后的第一个周期作为新基线，再连续无进展要重新数满 100 tick
        assertEquals(MoveProgress.StuckReason.NONE,
                progress.tick(false, false, 50.0D, false, 110L));
        assertEquals(MoveProgress.StuckReason.NONE,
                progress.tick(false, false, 50.0D, false, 200L));
        assertEquals(MoveProgress.StuckReason.NO_PROGRESS,
                progress.tick(false, false, 50.0D, false, 210L));
    }

    @Test
    void reset_letsAnyDistanceCountAsANewBaseline() {
        MoveProgress progress = new MoveProgress();
        progress.tick(false, false, 50.0D, false, 0L);
        progress.reset(TICK);
        // 重置后距离仍是 50：第一周期作为新基线（算有进展），之后才重新累计 CANT_REACH
        assertEquals(MoveProgress.StuckReason.NONE, progress.tick(false, false, 50.0D, true, 2 * TICK));
        assertEquals(MoveProgress.StuckReason.NONE, progress.tick(false, false, 50.0D, true, 3 * TICK));
        assertEquals(MoveProgress.StuckReason.CANT_REACH, progress.tick(false, false, 50.0D, true, 4 * TICK));
    }
}
