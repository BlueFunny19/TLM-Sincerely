package com.github.tartaricacid.tlm_sincerely.ai.guard;

import java.util.UUID;

/**
 * 守护名单里的一项：守谁、什么条件下守、守的时候待在离他多近的地方。
 * 名单里的先后顺序就是优先级（越靠前越优先），所以这里不再单独存优先级数字。
 *
 * @param targetId  目标的 UUID：主人（玩家）或同一主人名下另一只女仆
 * @param name      目标的名字（只用于显示，目标改名后下次编辑时更新）
 * @param condition 什么条件下才守
 * @param distance  守护半径（格）：待在目标身边这么远以内
 */
public record GuardEntry(UUID targetId, String name, GuardCondition condition, double distance) {
    public static final double MIN_DISTANCE = 1.0D;
    public static final double MAX_DISTANCE = 64.0D;
    public static final double DEFAULT_DISTANCE = 5.0D;

    public GuardEntry {
        name = name == null ? "" : name;
        condition = condition == null ? GuardCondition.ALWAYS : condition;
        distance = Double.isNaN(distance) ? DEFAULT_DISTANCE : Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance));
    }

    public GuardEntry withCondition(GuardCondition newCondition) {
        return new GuardEntry(targetId, name, newCondition, distance);
    }

    public GuardEntry withDistance(double newDistance) {
        return new GuardEntry(targetId, name, condition, newDistance);
    }
}
