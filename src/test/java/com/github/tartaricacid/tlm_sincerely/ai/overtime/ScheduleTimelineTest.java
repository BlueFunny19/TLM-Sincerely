package com.github.tartaricacid.tlm_sincerely.ai.overtime;

import com.github.tartaricacid.tlm_sincerely.ai.overtime.ScheduleTimeline.Phase;
import com.github.tartaricacid.tlm_sincerely.schedule.AdventureWindow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

    @Test
    void ticksUntilPhaseChange_dayScheduleRestRunsToTheEndOfTheDay() {
        assertEquals(7000L, ScheduleTimeline.ticksUntilPhaseChange("DAY", 17000));
        assertEquals(1L, ScheduleTimeline.ticksUntilPhaseChange("DAY", 23999));
        assertEquals(12000L, ScheduleTimeline.ticksUntilPhaseChange("DAY", 0));
    }

    @Test
    void ticksUntilPhaseChange_nightScheduleRestEndsAt8000() {
        assertEquals(1L, ScheduleTimeline.ticksUntilPhaseChange("NIGHT", 7999));
        assertEquals(8000L, ScheduleTimeline.ticksUntilPhaseChange("NIGHT", 0));
    }

    @Test
    void ticksUntilPhaseChange_allDayWorkNeverChanges() {
        assertEquals(24000L, ScheduleTimeline.ticksUntilPhaseChange("ALL", 5000));
        assertEquals(24000L, ScheduleTimeline.ticksUntilPhaseChange(null, 5000));
    }

    @Test
    void adventureSchedule_worksAlmostAllDayAndRestsInTheWindow() {
        AdventureWindow window = new AdventureWindow(17000, 4000);
        assertEquals(Phase.WORK, ScheduleTimeline.phaseAt("ADVENTURE", 0, window));
        assertEquals(Phase.WORK, ScheduleTimeline.phaseAt("ADVENTURE", 16999, window));
        assertEquals(Phase.REST, ScheduleTimeline.phaseAt("ADVENTURE", 17000, window));
        assertEquals(Phase.REST, ScheduleTimeline.phaseAt("ADVENTURE", 20999, window));
        assertEquals(Phase.WORK, ScheduleTimeline.phaseAt("ADVENTURE", 21000, window));
    }

    @Test
    void adventureSchedule_twoArgumentFormUsesTheDefaultWindow() {
        assertEquals(Phase.REST, ScheduleTimeline.phaseAt("ADVENTURE", 18000));
        assertEquals(Phase.WORK, ScheduleTimeline.phaseAt("ADVENTURE", 5000));
    }

    @Test
    void adventureSchedule_hasNoIdlePhase() {
        AdventureWindow window = new AdventureWindow(22000, 4000);
        for (int tick = 0; tick < 24000; tick += 250) {
            assertNotEquals(Phase.IDLE, ScheduleTimeline.phaseAt("ADVENTURE", tick, window));
        }
    }

    @Test
    void adventureSchedule_ticksUntilPhaseChange() {
        AdventureWindow window = new AdventureWindow(17000, 4000);
        assertEquals(3000L, ScheduleTimeline.ticksUntilPhaseChange("ADVENTURE", 18000, window));
        assertEquals(1L, ScheduleTimeline.ticksUntilPhaseChange("ADVENTURE", 16999, window));
        assertEquals(17000L, ScheduleTimeline.ticksUntilPhaseChange("ADVENTURE", 0, window));
    }

    @Test
    void ticksUntilPhaseChange_worksAcrossDays() {
        assertEquals(7000L, ScheduleTimeline.ticksUntilPhaseChange("DAY", 24000L * 5 + 17000));
    }
}
