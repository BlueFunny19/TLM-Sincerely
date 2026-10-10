package com.github.tartaricacid.tlm_sincerely.schedule;

import com.github.tartaricacid.tlm_sincerely.schedule.AdventureWindow.Segment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AdventureWindow} 的单元测试：睡眠窗口的边界、跨天窗口、分段、时间文字。
 */
class AdventureWindowTest {

    @Test
    void defaultWindow_is2300To0300() {
        AdventureWindow window = AdventureWindow.DEFAULT;
        assertEquals(17000, window.startTick());
        assertEquals(4000, window.durationTicks());
        assertEquals("23:00-03:00", window.restRangeText());
    }

    @Test
    void isRest_boundariesOfAnOrdinaryWindow() {
        AdventureWindow window = new AdventureWindow(17000, 4000);
        assertFalse(window.isRest(16999));
        assertTrue(window.isRest(17000));
        assertTrue(window.isRest(20999));
        assertFalse(window.isRest(21000));
        assertFalse(window.isRest(0));
    }

    @Test
    void isRest_worksAcrossDays() {
        AdventureWindow window = new AdventureWindow(17000, 4000);
        assertTrue(window.isRest(24000L * 7 + 18000));
        assertFalse(window.isRest(24000L * 7 + 5000));
    }

    @Test
    void isRest_windowThatWrapsPastTheEndOfTheDay() {
        AdventureWindow window = new AdventureWindow(22000, 4000);
        assertTrue(window.isRest(22000));
        assertTrue(window.isRest(23999));
        assertTrue(window.isRest(0));
        assertTrue(window.isRest(1999));
        assertFalse(window.isRest(2000));
        assertFalse(window.isRest(21999));
    }

    @Test
    void isRest_negativeDayTimeDoesNotCrash() {
        assertTrue(AdventureWindow.DEFAULT.isRest(-24000L + 18000));
    }

    @Test
    void segments_ordinaryWindowHasThreeSegmentsCoveringTheDay() {
        List<Segment> segments = new AdventureWindow(17000, 4000).segments();
        assertEquals(List.of(
                new Segment(0, 17000, false),
                new Segment(17000, 21000, true),
                new Segment(21000, 24000, false)), segments);
    }

    @Test
    void segments_windowStartingAtZeroHasNoLeadingWork() {
        assertEquals(List.of(
                new Segment(0, 3000, true),
                new Segment(3000, 24000, false)), new AdventureWindow(0, 3000).segments());
    }

    @Test
    void segments_windowEndingAtMidnightHasNoTrailingWork() {
        assertEquals(List.of(
                new Segment(0, 20000, false),
                new Segment(20000, 24000, true)), new AdventureWindow(20000, 4000).segments());
    }

    @Test
    void segments_wrappingWindowStartsAndEndsWithRest() {
        assertEquals(List.of(
                new Segment(0, 2000, true),
                new Segment(2000, 22000, false),
                new Segment(22000, 24000, true)), new AdventureWindow(22000, 4000).segments());
    }

    @Test
    void segments_agreeWithIsRestAtEveryTick() {
        for (int start : new int[]{0, 1, 5000, 17000, 22000, 23999}) {
            for (int duration : new int[]{1, 1000, 4000, 12000, 23999}) {
                AdventureWindow window = new AdventureWindow(start, duration);
                List<Segment> segments = window.segments();
                assertEquals(0, segments.get(0).from());
                assertEquals(24000, segments.get(segments.size() - 1).to());
                for (int i = 0; i < segments.size(); i++) {
                    Segment segment = segments.get(i);
                    if (i > 0) {
                        assertEquals(segments.get(i - 1).to(), segment.from());
                        assertTrue(segments.get(i - 1).rest() != segment.rest(), "neighbours must differ");
                    }
                    for (int tick : new int[]{segment.from(), segment.to() - 1}) {
                        assertEquals(segment.rest(), window.isRest(tick),
                                "start=" + start + " duration=" + duration + " tick=" + tick);
                    }
                }
            }
        }
    }

    @Test
    void constructor_normalizesOutOfRangeValues() {
        assertEquals(1000, new AdventureWindow(25000, 4000).startTick());
        assertEquals(23000, new AdventureWindow(-1000, 4000).startTick());
        assertEquals(1, new AdventureWindow(0, 0).durationTicks());
        assertEquals(23999, new AdventureWindow(0, 99999).durationTicks());
    }

    @Test
    void formatTick_tickZeroIsSixInTheMorning() {
        assertEquals("06:00", AdventureWindow.formatTick(0));
        assertEquals("12:00", AdventureWindow.formatTick(6000));
        assertEquals("00:00", AdventureWindow.formatTick(18000));
        assertEquals("03:00", AdventureWindow.formatTick(21000));
        assertEquals("06:00", AdventureWindow.formatTick(24000));
        assertEquals("23:30", AdventureWindow.formatTick(17500));
    }
}
