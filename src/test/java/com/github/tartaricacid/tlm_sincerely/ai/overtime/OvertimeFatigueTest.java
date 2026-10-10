package com.github.tartaricacid.tlm_sincerely.ai.overtime;

import com.github.tartaricacid.tlm_sincerely.ai.overtime.OvertimeFatigue.Event;
import com.github.tartaricacid.tlm_sincerely.ai.overtime.OvertimeFatigue.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OvertimeFatigue} 的单元测试：只在休息时段累积、累了的时机、点心 / 硬撑把上限往后推、
 * 要东西超时、歇够了清零。
 */
class OvertimeFatigueTest {
    private static final long CAP = 3600L; // 180 秒
    private static final long DT = 20L;

    /** 以 DT 为步长推进到 {@code untilTick}（含），返回最后一步的事件。 */
    private static Event run(OvertimeFatigue fatigue, long fromTick, long untilTick, boolean rest) {
        Event last = Event.NONE;
        for (long tick = fromTick; tick <= untilTick; tick += DT) {
            Event event = fatigue.advance(tick, DT, rest, CAP);
            if (event != Event.NONE) {
                return event;
            }
            last = event;
        }
        return last;
    }

    @Test
    void notTiredBeforeTheCap() {
        OvertimeFatigue fatigue = new OvertimeFatigue();
        assertEquals(Event.NONE, run(fatigue, 20, CAP - DT, true));
        assertEquals(Stage.FINE, fatigue.stage());
    }

    @Test
    void becomesTiredExactlyWhenTheCapIsReached() {
        OvertimeFatigue fatigue = new OvertimeFatigue();
        assertEquals(Event.BECAME_TIRED, run(fatigue, DT, CAP + DT, true));
        assertEquals(Stage.ASKING, fatigue.stage());
        assertTrue(fatigue.isAsking());
        assertEquals(CAP, fatigue.tiredTicks());
    }

    @Test
    void doesNotAccumulateOutsideTheRestPhase() {
        OvertimeFatigue fatigue = new OvertimeFatigue();
        assertEquals(Event.NONE, run(fatigue, DT, CAP * 5, false));
        assertEquals(0L, fatigue.tiredTicks());
        assertEquals(Stage.FINE, fatigue.stage());
    }

    @Test
    void onlyRestTimeCounts_whenTheNightIsMixedWithOtherPhases() {
        OvertimeFatigue fatigue = new OvertimeFatigue();
        run(fatigue, DT, CAP / 2, true);
        run(fatigue, CAP / 2 + DT, CAP / 2 + 1000, false);
        // 休息时段只攒了一半，再攒另一半才累
        assertEquals(Stage.FINE, fatigue.stage());
        assertEquals(Event.BECAME_TIRED, run(fatigue, CAP, CAP * 2, true));
    }

    @Test
    void whileAsking_noFurtherAccumulationAndNoSecondTiredEvent() {
        OvertimeFatigue fatigue = new OvertimeFatigue();
        run(fatigue, DT, CAP + DT, true);
        long tired = fatigue.tiredTicks();
        assertEquals(Event.NONE, fatigue.advance(CAP + 2 * DT, DT, true, CAP));
        assertEquals(tired, fatigue.tiredTicks());
    }

    @Test
    void treatPushesTheLimitBackAndReturnsToFine() {
        OvertimeFatigue fatigue = new OvertimeFatigue();
        run(fatigue, DT, CAP + DT, true);
        fatigue.treat(6000L); // 300 秒
        assertEquals(Stage.FINE, fatigue.stage());
        // 还要再撑 6000 tick 才会再累
        assertEquals(Event.NONE, run(fatigue, CAP + 2 * DT, CAP + 6000, true));
        assertEquals(Event.BECAME_TIRED, run(fatigue, CAP + 6000 + DT, CAP + 6000 + 3 * DT, true));
    }

    @Test
    void severalTreatsStack() {
        OvertimeFatigue fatigue = new OvertimeFatigue();
        fatigue.treat(1000L);
        fatigue.treat(500L);
        assertEquals(1500L, fatigue.extraTicks());
    }

    @Test
    void postponeBehavesLikeATreatOfThatLength() {
        OvertimeFatigue fatigue = new OvertimeFatigue();
        run(fatigue, DT, CAP + DT, true);
        fatigue.postpone(CAP);
        assertEquals(Stage.FINE, fatigue.stage());
        assertEquals(CAP, fatigue.extraTicks());
    }

    @Test
    void negativeTreatIsIgnored() {
        OvertimeFatigue fatigue = new OvertimeFatigue();
        fatigue.treat(-500L);
        assertEquals(0L, fatigue.extraTicks());
    }

    @Test
    void askTimesOutOnlyWhileAskingAndAfterTheWait() {
        OvertimeFatigue fatigue = new OvertimeFatigue();
        assertFalse(fatigue.askTimedOut(100_000L, 1800L), "没在要东西时不会超时");
        run(fatigue, DT, CAP + DT, true);
        long askedAt = CAP; // 在这一步变累
        assertFalse(fatigue.askTimedOut(askedAt + 1799L, 1800L));
        assertTrue(fatigue.askTimedOut(askedAt + 1800L, 1800L));
        fatigue.treat(1000L);
        assertFalse(fatigue.askTimedOut(askedAt + 99999L, 1800L), "吃到点心后不再等待");
    }

    @Test
    void recoveredOnlyAfterEnoughTimeWithoutOvertime() {
        OvertimeFatigue fatigue = new OvertimeFatigue();
        assertFalse(fatigue.recovered(1_000_000L), "从没加班过没有可恢复的");
        fatigue.touch(1000L);
        assertFalse(fatigue.recovered(1000L + OvertimeFatigue.RECOVERY_TICKS - 1));
        assertTrue(fatigue.recovered(1000L + OvertimeFatigue.RECOVERY_TICKS));
    }

    @Test
    void advanceCountsAsTouching() {
        OvertimeFatigue fatigue = new OvertimeFatigue();
        fatigue.advance(5000L, DT, false, CAP);
        assertFalse(fatigue.recovered(5000L + OvertimeFatigue.RECOVERY_TICKS - 1));
        assertTrue(fatigue.recovered(5000L + OvertimeFatigue.RECOVERY_TICKS));
    }

    @Test
    void resetClearsEverything() {
        OvertimeFatigue fatigue = new OvertimeFatigue();
        run(fatigue, DT, CAP + DT, true);
        fatigue.treat(1000L);
        fatigue.reset();
        assertEquals(0L, fatigue.tiredTicks());
        assertEquals(0L, fatigue.extraTicks());
        assertEquals(Stage.FINE, fatigue.stage());
        assertFalse(fatigue.recovered(Long.MAX_VALUE / 2));
    }
}
