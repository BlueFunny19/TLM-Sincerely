package com.github.tartaricacid.tlm_sincerely.ai.guard;

import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardDecider.TargetStatus;
import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardRules.Placement;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link GuardDecider} 的单元测试，核心场景就是需求里的例子：
 * 女仆 1 的优先级高于女仆 2；女仆 1 血量低于 20 点才守，女仆 2 一直守。
 * 平时守女仆 2，女仆 1 残血时守女仆 1，她恢复之后再回去守女仆 2。
 */
class GuardDeciderTest {
    private final UUID maid1 = UUID.randomUUID();
    private final UUID maid2 = UUID.randomUUID();
    private final Map<UUID, TargetStatus> world = new HashMap<>();

    private GuardRules userExample() {
        return GuardRules.EMPTY
                .with(new GuardEntry(maid1, "女仆1", GuardCondition.hpBelow(20), 5), Placement.LAST)
                .with(new GuardEntry(maid2, "女仆2", GuardCondition.ALWAYS, 5), Placement.LAST);
    }

    private GuardEntry decide(GuardDecider decider, GuardRules rules) {
        return decider.decide(rules, id -> world.getOrDefault(id, TargetStatus.ABSENT));
    }

    @Test
    void usualStateGuardsTheAlwaysEntry_andTheWoundedHigherPriorityOneTakesOver() {
        GuardDecider decider = new GuardDecider();
        GuardRules rules = userExample();
        world.put(maid1, new TargetStatus(true, 40, 40));
        world.put(maid2, new TargetStatus(true, 30, 40));
        assertEquals(maid2, decide(decider, rules).targetId(), "女仆 1 血量正常：守女仆 2");

        world.put(maid1, new TargetStatus(true, 12, 40));
        assertEquals(maid1, decide(decider, rules).targetId(), "女仆 1 低于 20 点：优先守她");

        world.put(maid1, new TargetStatus(true, 22, 40));
        assertEquals(maid1, decide(decider, rules).targetId(), "回升到 22 还在滞回宽度（24）以内：继续守她，不来回切");

        world.put(maid1, new TargetStatus(true, 25, 40));
        assertEquals(maid2, decide(decider, rules).targetId(), "回升到 24 以上：回去守女仆 2");
    }

    @Test
    void anAbsentTargetIsSkippedWithoutBlockingTheRest() {
        GuardDecider decider = new GuardDecider();
        GuardRules rules = userExample();
        world.put(maid2, new TargetStatus(true, 30, 40));
        // 女仆 1 不在场（下线 / 离得太远 / 没加载）：直接跳过
        assertEquals(maid2, decide(decider, rules).targetId());
    }

    @Test
    void nobodyToGuardGivesNoAnswer() {
        GuardDecider decider = new GuardDecider();
        GuardRules rules = GuardRules.EMPTY
                .with(new GuardEntry(maid1, "女仆1", GuardCondition.hpBelow(20), 5), Placement.LAST);
        world.put(maid1, new TargetStatus(true, 40, 40));
        assertNull(decide(decider, rules));
        assertNull(decide(decider, GuardRules.EMPTY));
    }

    @Test
    void theHysteresisSurvivesBeingShadowedByAHigherPriorityEntry() {
        UUID owner = UUID.randomUUID();
        GuardRules rules = GuardRules.EMPTY
                .with(new GuardEntry(owner, "主人", GuardCondition.hpBelow(6), 5), Placement.LAST)
                .with(new GuardEntry(maid1, "女仆1", GuardCondition.hpBelow(20), 5), Placement.LAST);
        GuardDecider decider = new GuardDecider();
        world.put(owner, new TargetStatus(true, 4, 20));
        world.put(maid1, new TargetStatus(true, 15, 40));
        assertEquals(owner, decide(decider, rules).targetId(), "主人残血，优先级最高");
        // 主人恢复了；女仆 1 在被挡住期间已经算"成立"，血量 22 在滞回宽度以内，继续守她
        world.put(owner, new TargetStatus(true, 20, 20));
        world.put(maid1, new TargetStatus(true, 22, 40));
        assertEquals(maid1, decide(decider, rules).targetId());
    }

    @Test
    void removingAnEntryForgetsItsTriggeredState() {
        GuardDecider decider = new GuardDecider();
        GuardRules rules = userExample();
        world.put(maid1, new TargetStatus(true, 12, 40));
        world.put(maid2, new TargetStatus(true, 30, 40));
        assertEquals(maid1, decide(decider, rules).targetId());
        GuardRules withoutMaid1 = rules.without(maid1);
        assertEquals(maid2, decide(decider, withoutMaid1).targetId());
        // 再加回来：血量 22 不低于 20，而且她已经不是"已成立"，所以不守她
        world.put(maid1, new TargetStatus(true, 22, 40));
        assertEquals(maid2, decide(decider, rules).targetId());
    }

    @Test
    void resetClearsTheTriggeredState() {
        GuardDecider decider = new GuardDecider();
        GuardRules rules = userExample();
        world.put(maid1, new TargetStatus(true, 12, 40));
        world.put(maid2, new TargetStatus(true, 30, 40));
        decide(decider, rules);
        decider.reset();
        world.put(maid1, new TargetStatus(true, 22, 40));
        assertEquals(maid2, decide(decider, rules).targetId());
    }
}
