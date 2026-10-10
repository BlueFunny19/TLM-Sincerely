package com.github.tartaricacid.tlm_sincerely.ai.overtime;

import com.github.tartaricacid.tlm_sincerely.schedule.AdventureWindow;
import org.jetbrains.annotations.Nullable;

/**
 * 主模组几种日程在一天里各个时段是"工作 / 休闲 / 休息"的对照表（纯逻辑，不引用 MC 类型）。
 *
 * <p>数值来自 TLM 1.5.3 {@code InitEntities} 里注册的三个原版 {@code Schedule} 的关键帧（读字节码得到）。
 * 一天 24000 tick，tick 0 是早上 6 点，12000 是傍晚 6 点，18000 是午夜：
 * <ul>
 *   <li>{@code DAY}（白班）：0 起工作，12000 起休闲，16000 起休息；</li>
 *   <li>{@code NIGHT}（夜班）：0 起休息，8000 起休闲，12000 起工作；</li>
 *   <li>{@code ALL}（全天）：一直工作；</li>
 *   <li>{@code ADVENTURE}（冒险，本 mod 加的）：几乎全天工作，只在 {@link AdventureWindow} 里休息，没有休闲时段。</li>
 * </ul>
 * 用来回答"如果她没有在加班，这个时刻本来应该是什么状态"——加班的疲劳只在本来该休息的时段累积。
 */
public final class ScheduleTimeline {
    /** 一天的 tick 数。 */
    public static final int DAY_LENGTH = 24000;

    private ScheduleTimeline() {
    }

    /** 某个时刻女仆本该处于的状态。 */
    public enum Phase {
        /** 工作时间。 */
        WORK,
        /** 休闲时间（不工作，但也不睡觉）。 */
        IDLE,
        /** 休息时间（该睡觉了）。 */
        REST
    }

    /** 同 {@link #phaseAt(String, long, AdventureWindow)}，冒险日程按默认睡眠窗口算。 */
    public static Phase phaseAt(@Nullable String scheduleName, long dayTime) {
        return phaseAt(scheduleName, dayTime, AdventureWindow.DEFAULT);
    }

    /**
     * @param scheduleName 日程枚举名（{@code MaidSchedule#name()}）；{@code null} 或认不出的名字按全天工作处理
     * @param dayTime      世界时间（{@code Level#getDayTime()}），可以超过 24000
     * @param adventure    冒险日程的睡眠窗口（配置里可改）；只有 {@code ADVENTURE} 用到它
     */
    public static Phase phaseAt(@Nullable String scheduleName, long dayTime, AdventureWindow adventure) {
        int time = (int) Math.floorMod(dayTime, (long) DAY_LENGTH);
        if ("ADVENTURE".equals(scheduleName)) {
            return adventure.isRest(time) ? Phase.REST : Phase.WORK;
        }
        if ("DAY".equals(scheduleName)) {
            if (time < 12000) {
                return Phase.WORK;
            }
            return time < 16000 ? Phase.IDLE : Phase.REST;
        }
        if ("NIGHT".equals(scheduleName)) {
            if (time < 8000) {
                return Phase.REST;
            }
            return time < 12000 ? Phase.IDLE : Phase.WORK;
        }
        return Phase.WORK;
    }

    /**
     * 当前所处的时段还要持续多少 tick（到下一个不同的时段为止）；一整天都不变（全天工作）时返回一整天。
     * 用来算"这段休息时间什么时候结束"。
     */
    public static long ticksUntilPhaseChange(@Nullable String scheduleName, long dayTime) {
        return ticksUntilPhaseChange(scheduleName, dayTime, AdventureWindow.DEFAULT);
    }

    /** 同上，冒险日程使用给定的睡眠窗口。 */
    public static long ticksUntilPhaseChange(@Nullable String scheduleName, long dayTime, AdventureWindow adventure) {
        Phase current = phaseAt(scheduleName, dayTime, adventure);
        for (int step = 1; step <= DAY_LENGTH; step++) {
            if (phaseAt(scheduleName, dayTime + step, adventure) != current) {
                return step;
            }
        }
        return DAY_LENGTH;
    }
}
