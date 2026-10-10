package com.github.tartaricacid.tlm_sincerely.ai.guard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GuardCondition} 的单元测试：三种条件与滞回。
 */
class GuardConditionTest {

    @Test
    void always_holdsWhateverTheHealth() {
        assertTrue(GuardCondition.ALWAYS.holds(20, 20, false));
        assertTrue(GuardCondition.ALWAYS.holds(0.5, 20, true));
    }

    @Test
    void hpBelow_startsBelowTheThreshold() {
        GuardCondition condition = GuardCondition.hpBelow(20);
        assertTrue(condition.holds(19.5, 40, false));
        assertFalse(condition.holds(20, 40, false), "恰好等于门槛不算低于");
        assertFalse(condition.holds(30, 40, false));
    }

    @Test
    void hpBelow_staysTriggeredUntilItRecoversPastTheMargin() {
        GuardCondition condition = GuardCondition.hpBelow(20);
        assertTrue(condition.holds(23.9, 40, true), "已经在守了：回升到 24 点之前还不算好");
        assertFalse(condition.holds(24, 40, true));
        assertFalse(condition.holds(23.9, 40, false), "没在守的时候不带滞回");
    }

    @Test
    void hpPercentBelow_usesTheRatioToMaxHealth() {
        GuardCondition condition = GuardCondition.hpPercentBelow(50);
        assertTrue(condition.holds(9, 20, false));
        assertFalse(condition.holds(10, 20, false));
        assertTrue(condition.holds(12, 20, true), "60% 以内：50 + 15 的滞回");
        assertFalse(condition.holds(13, 20, true));
    }

    @Test
    void hpPercentBelow_zeroMaxHealthCountsAsFullHealth() {
        assertFalse(GuardCondition.hpPercentBelow(50).holds(0, 0, false));
    }

    @Test
    void thresholdsAreClamped() {
        assertEquals(1.0, GuardCondition.hpBelow(-5).threshold());
        assertEquals(1000.0, GuardCondition.hpBelow(99999).threshold());
        assertEquals(1.0, GuardCondition.hpPercentBelow(0).threshold());
        assertEquals(99.0, GuardCondition.hpPercentBelow(150).threshold());
        assertEquals(0.0, new GuardCondition(GuardCondition.Kind.ALWAYS, 77).threshold());
    }

    @Test
    void recoveryMarginPerKind() {
        assertEquals(0.0, GuardCondition.ALWAYS.recoveryMargin());
        assertEquals(GuardCondition.HP_MARGIN, GuardCondition.hpBelow(10).recoveryMargin());
        assertEquals(GuardCondition.PERCENT_MARGIN, GuardCondition.hpPercentBelow(10).recoveryMargin());
    }
}
