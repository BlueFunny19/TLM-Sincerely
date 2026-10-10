package com.github.tartaricacid.tlm_sincerely.ai.overtime;

import com.github.tartaricacid.tlm_sincerely.ai.overtime.ScheduleTimeline.Phase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ScheduleTimeline} 的单元测试：三种日程在各个关键帧边界上的时段。
 */
class ScheduleTimelineTest {

    @Test
    void daySchedule_boundaries() {
        assertEquals(Phase.WORK, ScheduleTimeline.phaseAt("DAY", 0));
        assertEquals(Phase.WORK, ScheduleTimeline.phaseAt("DAY", 11999));
        assertEquals(Phase.IDLE, ScheduleTimeline.phaseAt("DAY", 12000));
        assertEquals(Phase.IDLE, ScheduleTimeline.phaseAt("DAY", 15999));
        assertEquals(Phase.REST, ScheduleTimeline.phaseAt("DAY", 16000));
        assertEquals(Phase.REST, ScheduleTimeline.phaseAt("DAY", 23999));
    }

    @Test
    void nightSchedule_boundaries() {
        assertEquals(Phase.REST, ScheduleTimeline.phaseAt("NIGHT", 0));
        assertEquals(Phase.REST, ScheduleTimeline.phaseAt("NIGHT", 7999));
        assertEquals(Phase.IDLE, ScheduleTimeline.phaseAt("NIGHT", 8000));
        assertEquals(Phase.IDLE, ScheduleTimeline.phaseAt("NIGHT", 11999));
        assertEquals(Phase.WORK, ScheduleTimeline.phaseAt("NIGHT", 12000));
        assertEquals(Phase.WORK, ScheduleTimeline.phaseAt("NIGHT", 23999));
    }

    @Test
    void allSchedule_isAlwaysWork() {
        assertEquals(Phase.WORK, ScheduleTimeline.phaseAt("ALL", 0));
        assertEquals(Phase.WORK, ScheduleTimeline.phaseAt("ALL", 18000));
    }

    @Test
    void unknownOrMissingName_isTreatedAsAllDayWork() {
        assertEquals(Phase.WORK, ScheduleTimeline.phaseAt(null, 18000));
        assertEquals(Phase.WORK, ScheduleTimeline.phaseAt("SOMETHING_ELSE", 18000));
    }

    @Test
    void dayTimeLargerThanOneDay_wrapsAround() {
        // 第 3 天的 17000 tick 和第 1 天的 17000 tick 一样是休息时段
        assertEquals(Phase.REST, ScheduleTimeline.phaseAt("DAY", 24000L * 3 + 17000));
        assertEquals(Phase.WORK, ScheduleTimeline.phaseAt("DAY", 24000L * 3 + 100));
    }

    @Test
    void negativeDayTime_doesNotCrash() {
        assertEquals(Phase.REST, ScheduleTimeline.phaseAt("DAY", -1));
    }
}
