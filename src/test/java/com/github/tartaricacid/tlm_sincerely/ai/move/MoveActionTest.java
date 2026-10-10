package com.github.tartaricacid.tlm_sincerely.ai.move;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link MoveAction} 的单元测试：动作识别与各动作的默认距离。
 */
class MoveActionTest {
    @Test
    void parse_isCaseInsensitiveAndTrims() {
        assertEquals(MoveAction.APPROACH, MoveAction.parse("approach"));
        assertEquals(MoveAction.APPROACH, MoveAction.parse("  Approach "));
        assertEquals(MoveAction.GOTO, MoveAction.parse("GOTO"));
        assertEquals(MoveAction.RETREAT, MoveAction.parse("retreat"));
        assertEquals(MoveAction.GUARD, MoveAction.parse("guard"));
        assertEquals(MoveAction.STAY, MoveAction.parse("STAY"));
        assertEquals(MoveAction.GO_HOME, MoveAction.parse(" go_home "));
        assertEquals(MoveAction.WORK_TIME, MoveAction.parse("work_time"));
        assertEquals(MoveAction.WORK_TIME, MoveAction.parse(" Work_Time "));
        assertEquals(MoveAction.CANCEL, MoveAction.parse("cancel"));
    }

    @Test
    void parse_unknownOrNull_returnsNull() {
        assertNull(MoveAction.parse(null));
        assertNull(MoveAction.parse(""));
        assertNull(MoveAction.parse("walk"));
        assertNull(MoveAction.parse("靠近"));
    }

    @Test
    void defaultDistances_matchTaskSpec() {
        assertEquals(3.0D, MoveAction.APPROACH.defaultDistance());
        assertEquals(16.0D, MoveAction.RETREAT.defaultDistance());
        assertEquals(5.0D, MoveAction.GUARD.defaultDistance());
        assertEquals(0.0D, MoveAction.GOTO.defaultDistance());
        assertEquals(0.0D, MoveAction.STAY.defaultDistance());
        assertEquals(0.0D, MoveAction.GO_HOME.defaultDistance());
        assertEquals(0.0D, MoveAction.WORK_TIME.defaultDistance());
        assertEquals(0.0D, MoveAction.CANCEL.defaultDistance());
    }

    @Test
    void needsTarget_onlyForEntityActions() {
        assertEquals(true, MoveAction.APPROACH.needsTarget());
        assertEquals(true, MoveAction.RETREAT.needsTarget());
        assertEquals(true, MoveAction.GUARD.needsTarget());
        assertEquals(false, MoveAction.GOTO.needsTarget());
        assertEquals(false, MoveAction.STAY.needsTarget());
        assertEquals(false, MoveAction.GO_HOME.needsTarget());
        assertEquals(false, MoveAction.WORK_TIME.needsTarget());
        assertEquals(false, MoveAction.CANCEL.needsTarget());
    }

    @Test
    void id_isTheMachineIdentifier() {
        assertEquals("approach", MoveAction.APPROACH.id());
        assertEquals("retreat", MoveAction.RETREAT.id());
        assertEquals("goto", MoveAction.GOTO.id());
        assertEquals("guard", MoveAction.GUARD.id());
        assertEquals("stay", MoveAction.STAY.id());
        assertEquals("go_home", MoveAction.GO_HOME.id());
        assertEquals("work_time", MoveAction.WORK_TIME.id());
        assertEquals("cancel", MoveAction.CANCEL.id());
    }

    @Test
    void needsCoords_onlyForGoto() {
        assertEquals(true, MoveAction.GOTO.needsCoords());
        assertEquals(false, MoveAction.WORK_TIME.needsCoords());
        assertEquals(false, MoveAction.STAY.needsCoords());
    }

    /** 会登记、会到期 / 取消的动作（go_home 是永久性改变，不产生指令）。 */
    @Test
    void isOrder_isTrueForEverythingExceptGoHome() {
        assertEquals(true, MoveAction.APPROACH.isOrder());
        assertEquals(true, MoveAction.RETREAT.isOrder());
        assertEquals(true, MoveAction.GOTO.isOrder());
        assertEquals(true, MoveAction.GUARD.isOrder());
        assertEquals(true, MoveAction.STAY.isOrder());
        assertEquals(true, MoveAction.WORK_TIME.isOrder());
        assertEquals(false, MoveAction.GO_HOME.isOrder());
        assertEquals(true, MoveAction.CANCEL.isOrder());
    }

    @Test
    void acceptsDuration_excludesGoHomeAndCancel() {
        assertEquals(true, MoveAction.WORK_TIME.acceptsDuration());
        assertEquals(true, MoveAction.STAY.acceptsDuration());
        assertEquals(false, MoveAction.GO_HOME.acceptsDuration());
        assertEquals(false, MoveAction.CANCEL.acceptsDuration());
    }

    @Test
    void isWorkTime_onlyForWorkTime() {
        assertEquals(true, MoveAction.WORK_TIME.isWorkTime());
        assertEquals(false, MoveAction.STAY.isWorkTime());
        assertEquals(false, MoveAction.CANCEL.isWorkTime());
    }

    /**
     * 临时放宽工作时间的动作集合：approach / retreat / goto / guard / stay / work_time 需要，
     * go_home 与 cancel 不需要。
     */
    @Test
    void needsScheduleRelief_matrix() {
        assertEquals(true, MoveAction.APPROACH.needsScheduleRelief());
        assertEquals(true, MoveAction.RETREAT.needsScheduleRelief());
        assertEquals(true, MoveAction.GOTO.needsScheduleRelief());
        assertEquals(true, MoveAction.GUARD.needsScheduleRelief());
        assertEquals(true, MoveAction.STAY.needsScheduleRelief());
        assertEquals(true, MoveAction.WORK_TIME.needsScheduleRelief());
        assertEquals(false, MoveAction.GO_HOME.needsScheduleRelief());
        assertEquals(false, MoveAction.CANCEL.needsScheduleRelief());
    }

    /** work_time 不移动，因此不暂停跟随（也不该把女仆切到居家模式）。 */
    @Test
    void suspendsFollow_workTimeIsFalse() {
        assertEquals(false, MoveAction.WORK_TIME.suspendsFollow(false));
        assertEquals(false, MoveAction.WORK_TIME.suspendsFollow(true));
    }
}
