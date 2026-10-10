package com.github.tartaricacid.tlm_sincerely.schedule;

import java.util.ArrayList;
import java.util.List;

/**
 * "冒险"日程的时间表（纯逻辑，不引用 MC 类型，便于单元测试）。
 *
 * <p>冒险日程几乎全天工作，只在每天的一小段时间里休息（睡觉）。一天 24000 tick，tick 0 是早上 6 点：
 * 默认从 17000（23:00）开始睡 4000 tick（到次日 03:00）。睡眠窗口可以跨过一天的尾巴
 * （例如从 22000 睡 4000 tick，到次日的 2000）。
 *
 * <p>没有"休闲"时段：和 TLM 自带的三种日程不同，冒险日程里没有 IDLE。
 *
 * @param startTick     睡眠窗口开始的 tick（一天内，0~23999）
 * @param durationTicks 睡眠窗口的长度（tick，1~23999）
 */
public record AdventureWindow(int startTick, int durationTicks) {
    /** 一天的 tick 数。 */
    public static final int DAY_LENGTH = 24000;
    /** 默认值：23:00 开始睡，睡 4 小时。 */
    public static final AdventureWindow DEFAULT = new AdventureWindow(17000, 4000);

    public AdventureWindow {
        startTick = Math.floorMod(startTick, DAY_LENGTH);
        durationTicks = Math.max(1, Math.min(DAY_LENGTH - 1, durationTicks));
    }

    /** 一个时间段：{@code [from, to)}，{@code rest} 为 true 表示睡觉，否则工作。 */
    public record Segment(int from, int to, boolean rest) {
    }

    /** 某个世界时间此刻是否处在睡眠窗口里。 */
    public boolean isRest(long dayTime) {
        int time = (int) Math.floorMod(dayTime, (long) DAY_LENGTH);
        int end = startTick + durationTicks;
        if (end <= DAY_LENGTH) {
            return time >= startTick && time < end;
        }
        return time >= startTick || time < end - DAY_LENGTH;
    }

    /**
     * 把一天切成按时间顺序排列的若干段，从 tick 0 铺到 24000，相邻两段的状态一定不同。
     * 可直接转成原版 {@code Schedule} 的关键帧：每段的 {@code from} 就是一个关键帧的时间。
     */
    public List<Segment> segments() {
        List<Segment> result = new ArrayList<>(3);
        int end = startTick + durationTicks;
        if (end <= DAY_LENGTH) {
            if (startTick > 0) {
                result.add(new Segment(0, startTick, false));
            }
            result.add(new Segment(startTick, end, true));
            if (end < DAY_LENGTH) {
                result.add(new Segment(end, DAY_LENGTH, false));
            }
        } else {
            int wrapEnd = end - DAY_LENGTH;
            result.add(new Segment(0, wrapEnd, true));
            result.add(new Segment(wrapEnd, startTick, false));
            result.add(new Segment(startTick, DAY_LENGTH, true));
        }
        return result;
    }

    /** tick 换算成"时:分"（tick 0 = 06:00）。24000 显示为次日的 06:00。 */
    public static String formatTick(int tick) {
        int clamped = Math.floorMod(tick, DAY_LENGTH);
        int hour = (clamped / 1000 + 6) % 24;
        int minute = clamped % 1000 * 60 / 1000;
        return String.format("%02d:%02d", hour, minute);
    }

    /** 睡眠窗口的文字，例如 {@code 23:00-03:00}。 */
    public String restRangeText() {
        return formatTick(startTick) + "-" + formatTick(startTick + durationTicks);
    }
}
