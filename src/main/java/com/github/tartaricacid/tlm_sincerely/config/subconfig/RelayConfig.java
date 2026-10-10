package com.github.tartaricacid.tlm_sincerely.config.subconfig;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 女仆转告（{@code tell_maid} 工具）配置。
 *
 * <p>转告本身通过附属 Mod「Self Talk」的 API 完成，本模组只负责解析目标、限流与桥接；
 * 未安装 Self Talk 或对方的互聊总开关关闭时，工具会返回失败说明而不会改变任何状态。
 */
public final class RelayConfig {
    public static ForgeConfigSpec.BooleanValue RELAY_ENABLED;
    public static ForgeConfigSpec.IntValue RELAY_COOLDOWN_SECONDS;
    public static ForgeConfigSpec.IntValue RELAY_MAX_PER_MINUTE_PER_OWNER;

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("relay");

        builder.comment("Expose the tell_maid AI tool so a maid can relay one sentence to another maid of the same owner",
                "Requires the optional 'Maid Self-Talk' (maid_self_talk) mod and its inter-chat switch to be enabled");
        RELAY_ENABLED = builder.define("RelayEnabled", true);

        builder.comment("Seconds before the same pair of maids may relay to each other again (direction does not matter)");
        RELAY_COOLDOWN_SECONDS = builder.defineInRange("RelayCooldownSeconds", 30, 0, 3600);

        builder.comment("Maximum relays per owner per minute across all of their maids");
        RELAY_MAX_PER_MINUTE_PER_OWNER = builder.defineInRange("RelayMaxPerMinutePerOwner", 6, 1, 120);

        builder.pop();
    }
}
