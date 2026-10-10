package com.github.tartaricacid.tlm_sincerely.config.subconfig;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天栏配置。其中的 ChatModeEnabled / GlobalChatVisible 为 COMMON 配置，
 * 仅作为新玩家（无 /tlmchat mode|global 个人记录时）的默认值；
 * 玩家通过命令改写的是各自的运行时状态（见 {@link com.github.tartaricacid.tlm_sincerely.chatbar.ChatBarPlayerState}），
 * 管理员只能在此调整默认行为，其余条目仍为全服生效的服务端配置。
 */
public final class ChatBarConfig {
    /** 多目标“全体”关键词默认值；比较时忽略大小写。 */
    public static final List<String> DEFAULT_ALL_KEYWORDS = List.of("全体", "all");

    public static ForgeConfigSpec.BooleanValue CHAT_MODE;
    public static ForgeConfigSpec.BooleanValue GLOBAL_VISIBLE;
    public static ForgeConfigSpec.BooleanValue REQUIRE_PREFIX;
    public static ForgeConfigSpec.DoubleValue AUTO_CHAT_RANGE;
    public static ForgeConfigSpec.ConfigValue<String> PREFIX_PATTERN;
    public static ForgeConfigSpec.BooleanValue MULTI_TARGET_ENABLED;
    public static ForgeConfigSpec.IntValue MAX_TARGETS;
    public static ForgeConfigSpec.DoubleValue TARGET_RANGE;
    public static ForgeConfigSpec.BooleanValue SPLIT_REPLY_LINES;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> ALL_KEYWORDS;

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("chatbar");

        builder.comment("Default maid chat mode for players without a personal /tlmchat mode setting");
        CHAT_MODE = builder.define("ChatModeEnabled", true);

        builder.comment("Default chat visibility for players without a personal /tlmchat global setting (global mode)");
        GLOBAL_VISIBLE = builder.define("GlobalChatVisible", true);

        builder.comment("Whether to require @ prefix for maid chat");
        REQUIRE_PREFIX = builder.define("RequirePrefix", true);

        builder.comment("Auto chat range when no prefix (0 = disabled)");
        AUTO_CHAT_RANGE = builder.defineInRange("AutoChatRange", 5.0, 0.0, 64.0);

        builder.comment("Prefix pattern for specifying maid name");
        PREFIX_PATTERN = builder.define("PrefixPattern", "@");

        builder.comment("Allow one chat message to address several maids at once, e.g. '@Alice @Bob hello' or '@all hello'",
                "When disabled, only the legacy single-target '@name message' form is parsed");
        MULTI_TARGET_ENABLED = builder.define("MultiTargetEnabled", true);

        builder.comment("Show a multi-paragraph maid reply as several chat lines (one per paragraph) instead of one block",
                "Only changes how the reply is displayed in chat; the bubble and the stored history are untouched");
        SPLIT_REPLY_LINES = builder.define("SplitReplyLines", true);

        builder.comment("Maximum number of maids one chat message is delivered to (1-20)");
        MAX_TARGETS = builder.defineInRange("MaxTargets", 5, 1, 20);

        builder.comment("How far away (in blocks) a maid can still hear the player: used to find the maids named with '@name' / '@all',",
                "by the /tlmchat and /tlmmemory maid selectors, and by the tell_maid and maid_move tools to find another maid of the same owner",
                "Measured as a straight-line distance in the same dimension; the no-prefix AutoChatRange below is a separate setting");
        TARGET_RANGE = builder.defineInRange("TargetRange", 64.0, 1.0, 256.0);

        builder.comment("Keywords that mean 'every maid in range' when used as a target, compared case-insensitively");
        // 与 MaidCommandConfig 一致：默认值用可变 List，避免 Forge 在缺失键时对不可变集合做写入
        ALL_KEYWORDS = builder.defineList("AllKeywords", new ArrayList<>(DEFAULT_ALL_KEYWORDS),
                value -> value instanceof String);

        builder.pop();
    }
}
