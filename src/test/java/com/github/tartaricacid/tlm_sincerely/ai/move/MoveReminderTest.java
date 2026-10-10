package com.github.tartaricacid.tlm_sincerely.ai.move;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MoveReminder} 的单元测试：永久指令的提醒间隔判定。
 * 时间全部由测试给出 tick，不依赖真实时钟。
 */
class MoveReminderTest {
    private static final int TEN_MINUTES = MoveReminder.intervalTicks(10);

    @Test
    void intervalTicks_convertsMinutes() {
        assertEquals(0, MoveReminder.intervalTicks(0));
        assertEquals(0, MoveReminder.intervalTicks(-5));
        assertEquals(1200, MoveReminder.intervalTicks(1));
        assertEquals(12000, MoveReminder.intervalTicks(10));
        assertEquals(144000, MoveReminder.intervalTicks(120));
    }

    @Test
    void due_isFalseBeforeTheInterval() {
        MoveReminder reminder = new MoveReminder();
        reminder.arm(1000L, TEN_MINUTES);
        assertFalse(reminder.due(1000L, TEN_MINUTES));
        assertFalse(reminder.due(1000L + TEN_MINUTES - 1, TEN_MINUTES));
    }

    @Test
    void due_firesAtTheInterval() {
        MoveReminder reminder = new MoveReminder();
        reminder.arm(1000L, TEN_MINUTES);
        assertTrue(reminder.due(1000L + TEN_MINUTES, TEN_MINUTES));
    }

    @Test
    void due_repeatsEveryInterval() {
        MoveReminder reminder = new MoveReminder();
        reminder.arm(0L, TEN_MINUTES);
        assertTrue(reminder.due(TEN_MINUTES, TEN_MINUTES));
        assertFalse(reminder.due(TEN_MINUTES + 1, TEN_MINUTES));
        assertFalse(reminder.due(2L * TEN_MINUTES - 1, TEN_MINUTES));
        assertTrue(reminder.due(2L * TEN_MINUTES, TEN_MINUTES));
        assertTrue(reminder.due(3L * TEN_MINUTES, TEN_MINUTES));
    }

    @Test
    void due_isFalseWhenDisabled() {
        MoveReminder reminder = new MoveReminder();
        reminder.arm(0L, 0);
        assertFalse(reminder.due(100000L, 0));
    }

    @Test
    void due_isFalseBeforeArming() {
        MoveReminder reminder = new MoveReminder();
        assertFalse(reminder.due(100000L, TEN_MINUTES));
    }

    @Test
    void clear_stopsTheTimer() {
        MoveReminder reminder = new MoveReminder();
        reminder.arm(0L, TEN_MINUTES);
        reminder.clear();
        assertFalse(reminder.due(10L * TEN_MINUTES, TEN_MINUTES));
    }

    @Test
    void lateFiringDoesNotFireTwiceForSkippedIntervals() {
        // 服务器卡顿/区块卸载导致错过好几次间隔时，只在该周期提醒一次，并把下一次推到"现在 + 间隔"
        MoveReminder reminder = new MoveReminder();
        reminder.arm(0L, TEN_MINUTES);
        assertTrue(reminder.due(5L * TEN_MINUTES, TEN_MINUTES));
        assertFalse(reminder.due(5L * TEN_MINUTES + 1, TEN_MINUTES));
        assertTrue(reminder.due(6L * TEN_MINUTES, TEN_MINUTES));
    }
}
