package com.github.tartaricacid.tlm_sincerely.ai.guard;

import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardRules.Placement;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GuardData} 的时限判定与 {@link GuardText} 的名单描述。
 */
class GuardDataTest {
    @Test
    void zeroExpiryMeansForever() {
        assertFalse(GuardData.EMPTY.isTemporary());
        assertFalse(GuardData.EMPTY.isExpired(Long.MAX_VALUE));
    }

    @Test
    void temporaryExpiresAtOrAfterTheGivenTick() {
        GuardData data = GuardData.EMPTY.withExpireAt(100L);
        assertTrue(data.isTemporary());
        assertFalse(data.isExpired(99L));
        assertTrue(data.isExpired(100L));
        assertTrue(data.isExpired(101L));
    }

    @Test
    void negativeExpiryAndNullsAreNormalised() {
        GuardData data = new GuardData(null, -5L, null);
        assertEquals(0L, data.expireAtGameTick());
        assertEquals("", data.revertTaskUid());
        assertTrue(data.rules().isEmpty());
    }

    @Test
    void withersKeepTheOtherFields() {
        GuardRules rules = GuardRules.EMPTY.with(
                new GuardEntry(UUID.randomUUID(), "A", GuardCondition.ALWAYS, 5), Placement.LAST);
        GuardData data = new GuardData(rules, 50L, "touhou_little_maid:idle");
        assertEquals(rules, data.withExpireAt(0L).rules());
        assertEquals("touhou_little_maid:idle", data.withRules(GuardRules.EMPTY).revertTaskUid());
        assertEquals(50L, data.withRevertTask("").expireAtGameTick());
    }

    @Test
    void rulesBlockListsEntriesInPriorityOrderWithConditions() {
        GuardRules rules = GuardRules.EMPTY
                .with(new GuardEntry(UUID.randomUUID(), "女仆1", GuardCondition.hpBelow(20), 5), Placement.LAST)
                .with(new GuardEntry(UUID.randomUUID(), "女仆2", GuardCondition.ALWAYS, 8.5), Placement.LAST);
        String zh = GuardText.rulesBlock(true, rules);
        assertTrue(zh.indexOf("1. 女仆1") < zh.indexOf("2. 女仆2"));
        assertTrue(zh.contains("低于 20 点"));
        assertTrue(zh.contains("8.5"));
        String en = GuardText.rulesBlock(false, rules);
        assertTrue(en.contains("below 20 points"));
        assertEquals(GuardText.emptyList(true), GuardText.rulesBlock(true, GuardRules.EMPTY));
    }

    @Test
    void conditionLabelsCoverEveryKind() {
        assertTrue(GuardText.conditionLabel(true, GuardCondition.hpPercentBelow(30)).contains("30%"));
        assertTrue(GuardText.conditionLabel(false, GuardCondition.hpPercentBelow(30)).contains("30%"));
        assertEquals("always", GuardText.conditionLabel(false, GuardCondition.ALWAYS));
    }
}
