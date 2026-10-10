package com.github.tartaricacid.tlm_sincerely.ai.guard;

import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardRules.Placement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GuardRules} 与 {@link GuardEntry} 的单元测试：顺序就是优先级、同一目标只有一项、容量上限。
 */
class GuardRulesTest {
    private static GuardEntry entry(UUID id, String name) {
        return new GuardEntry(id, name, GuardCondition.ALWAYS, 5.0);
    }

    @Test
    void with_lastAppendsAndFirstPrepends() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        GuardRules rules = GuardRules.EMPTY.with(entry(a, "a"), Placement.LAST)
                .with(entry(b, "b"), Placement.LAST)
                .with(entry(c, "c"), Placement.FIRST);
        assertEquals(List.of(c, a, b), rules.entries().stream().map(GuardEntry::targetId).toList());
    }

    @Test
    void with_sameTargetKeepsOneEntryAndReplacesIt() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        GuardRules rules = GuardRules.EMPTY.with(entry(a, "a"), Placement.LAST).with(entry(b, "b"), Placement.LAST);
        GuardRules updated = rules.with(entry(a, "a").withCondition(GuardCondition.hpBelow(20)), Placement.KEEP);
        assertEquals(2, updated.entries().size());
        assertEquals(a, updated.entries().get(0).targetId(), "KEEP 不动位置");
        assertEquals(GuardCondition.hpBelow(20), updated.entries().get(0).condition());
    }

    @Test
    void with_existingTargetCanBeMovedToTheFrontOrBack() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        GuardRules rules = GuardRules.EMPTY.with(entry(a, "a"), Placement.LAST).with(entry(b, "b"), Placement.LAST);
        assertEquals(b, rules.with(entry(b, "b"), Placement.FIRST).entries().get(0).targetId());
        assertEquals(a, rules.with(entry(a, "a"), Placement.LAST).entries().get(1).targetId());
    }

    @Test
    void with_keepOfANewTargetAppendsIt() {
        UUID a = UUID.randomUUID();
        GuardRules rules = GuardRules.EMPTY.with(entry(UUID.randomUUID(), "x"), Placement.LAST).with(entry(a, "a"), Placement.KEEP);
        assertEquals(a, rules.entries().get(1).targetId());
    }

    @Test
    void without_removesOnlyThatTarget() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        GuardRules rules = GuardRules.EMPTY.with(entry(a, "a"), Placement.LAST).with(entry(b, "b"), Placement.LAST);
        GuardRules after = rules.without(a);
        assertFalse(after.contains(a));
        assertTrue(after.contains(b));
        assertEquals(rules, rules.without(UUID.randomUUID()));
    }

    @Test
    void shift_movesOneStepAndStopsAtTheEnds() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        GuardRules rules = GuardRules.EMPTY.with(entry(a, "a"), Placement.LAST)
                .with(entry(b, "b"), Placement.LAST).with(entry(c, "c"), Placement.LAST);
        assertEquals(List.of(b, a, c), rules.shift(b, -1).entries().stream().map(GuardEntry::targetId).toList());
        assertEquals(List.of(a, c, b), rules.shift(b, 1).entries().stream().map(GuardEntry::targetId).toList());
        assertEquals(rules, rules.shift(a, -1), "已经在最前面");
        assertEquals(rules, rules.shift(c, 1), "已经在最后面");
        assertEquals(rules, rules.shift(UUID.randomUUID(), -1), "不在名单里");
        assertEquals(List.of(c, a, b), rules.shift(c, -5).entries().stream().map(GuardEntry::targetId).toList());
    }

    @Test
    void capacityIsLimitedAndANewTargetBeyondItIsIgnored() {
        GuardRules rules = GuardRules.EMPTY;
        for (int i = 0; i < GuardRules.MAX_ENTRIES; i++) {
            rules = rules.with(entry(UUID.randomUUID(), "t" + i), Placement.LAST);
        }
        assertTrue(rules.isFull());
        GuardRules same = rules.with(entry(UUID.randomUUID(), "extra"), Placement.FIRST);
        assertEquals(rules, same);
        // 已有的目标满员时仍然可以更新
        UUID existing = rules.entries().get(3).targetId();
        assertEquals(GuardCondition.hpBelow(5),
                rules.with(entry(existing, "t3").withCondition(GuardCondition.hpBelow(5)), Placement.KEEP).entries().get(3).condition());
    }

    @Test
    void constructorDropsDuplicatesAndOverflow() {
        UUID a = UUID.randomUUID();
        List<GuardEntry> raw = new ArrayList<>(List.of(entry(a, "a"), entry(a, "again")));
        for (int i = 0; i < 20; i++) {
            raw.add(entry(UUID.randomUUID(), "t" + i));
        }
        GuardRules rules = new GuardRules(raw);
        assertEquals(GuardRules.MAX_ENTRIES, rules.entries().size());
        assertEquals("a", rules.entries().get(0).name());
    }

    @Test
    void entryClampsDistanceAndKeepsDefaults() {
        UUID a = UUID.randomUUID();
        assertEquals(1.0, new GuardEntry(a, "a", GuardCondition.ALWAYS, 0).distance());
        assertEquals(64.0, new GuardEntry(a, "a", GuardCondition.ALWAYS, 500).distance());
        assertEquals(GuardEntry.DEFAULT_DISTANCE, new GuardEntry(a, null, null, Double.NaN).distance());
        assertEquals(GuardCondition.ALWAYS, new GuardEntry(a, null, null, 5).condition());
        assertEquals("", new GuardEntry(a, null, null, 5).name());
    }
}
