package com.github.tartaricacid.tlm_sincerely.config.subconfig;

import com.github.tartaricacid.tlm_sincerely.schedule.AdventureWindow;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * "冒险"日程配置。
 *
 * <p>冒险日程是本 mod 给女仆加的第四种日程（原来只有白班 / 夜班 / 全天）：几乎全天工作、没有休闲时段，
 * 只在每天的一小段时间里睡觉。适合带着女仆长途冒险的玩家。睡眠窗口的修改在女仆下一次刷新大脑
 * （切换日程、重新进入世界）时生效。
 */
public final class AdventureConfig {
    public static ForgeConfigSpec.IntValue SLEEP_START_TICK;
    public static ForgeConfigSpec.IntValue SLEEP_DURATION_TICKS;
    public static ForgeConfigSpec.BooleanValue VANILLA_BEDS;

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("adventure");

        builder.comment("When the adventure schedule's sleeping window starts, in game ticks of a day (0 = 06:00, 6000 = 12:00, 12000 = 18:00, 18000 = 00:00)",
                "The default 17000 is 23:00. The window may run past the end of the day (22000 with a length of 4000 sleeps until 02:00 the next morning)");
        SLEEP_START_TICK = builder.defineInRange("AdventureSleepStartTick", AdventureWindow.DEFAULT.startTick(), 0, 23999);

        builder.comment("How long the adventure schedule's sleeping window lasts, in game ticks (1000 = one hour, default 4000 = four hours)",
                "The rest of the day she works; there is no leisure time in this schedule");
        SLEEP_DURATION_TICKS = builder.defineInRange("AdventureSleepDurationTicks", AdventureWindow.DEFAULT.durationTicks(), 1000, 20000);

        builder.comment("Let a maid on the adventure schedule sleep in ordinary vanilla beds when there is no maid bed within reach",
                "She never takes a bed that is occupied, and always prefers a maid bed when one is available");
        VANILLA_BEDS = builder.define("AdventureVanillaBeds", true);

        builder.pop();
    }

    /** 当前配置的睡眠窗口。 */
    public static AdventureWindow window() {
        return new AdventureWindow(SLEEP_START_TICK.get(), SLEEP_DURATION_TICKS.get());
    }
}
