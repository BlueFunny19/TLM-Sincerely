package com.github.tartaricacid.tlm_sincerely.ai.guard;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * 决定此刻该守名单里的谁（纯逻辑）。每只女仆一个实例：它记着哪些目标的条件"已经成立"，
 * 好让血量条件带上滞回（见 {@link GuardCondition}）。
 *
 * <p>规则：从名单上往下找，第一个"目标在场并且条件成立"的项就是答案；没有就没有答案（她没人可守）。
 * 目标不在场（下线、离得太远、没加载、死了）的项直接跳过，不会卡住后面的项。
 * 所有项的"已成立"状态每次都会更新，被更高优先级的项挡住的项也一样，这样轮到它时滞回是连续的。
 */
public final class GuardDecider {
    /** 目标此刻的状态（由调用方从世界里读出来）。 */
    public record TargetStatus(boolean present, double hp, double maxHp) {
        public static final TargetStatus ABSENT = new TargetStatus(false, 0.0D, 0.0D);
    }

    private final Set<UUID> triggered = new HashSet<>();

    @Nullable
    public GuardEntry decide(GuardRules rules, Function<UUID, TargetStatus> lookup) {
        GuardEntry chosen = null;
        Set<UUID> inRules = new HashSet<>();
        for (GuardEntry entry : rules.entries()) {
            UUID id = entry.targetId();
            inRules.add(id);
            TargetStatus status = lookup.apply(id);
            if (status == null || !status.present()) {
                triggered.remove(id);
                continue;
            }
            boolean holds = entry.condition().holds(status.hp(), status.maxHp(), triggered.contains(id));
            if (holds) {
                triggered.add(id);
                if (chosen == null) {
                    chosen = entry;
                }
            } else {
                triggered.remove(id);
            }
        }
        // 已经从名单里删掉的目标，不要留着它的"已成立"状态
        triggered.retainAll(inRules);
        return chosen;
    }

    /** 清掉全部"已成立"状态（名单被整体换掉、任务被切走时）。 */
    public void reset() {
        triggered.clear();
    }
}
