package com.github.tartaricacid.tlm_sincerely.config.subconfig;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 女仆回复的节奏配置：让模型分句、让聊天栏按停顿标记错开显示。两个开关彼此独立，默认都关。
 *
 * <p>无论开关如何，回复里的 {@code <等待>} 标记都会被剥掉，不会出现在气泡、语音、聊天栏或历史里
 * （兜底：模型自己乱写也不会露馅）；开关只决定"是否告诉模型可以这样写"以及"是否按标记错开显示"。
 */
public final class ReplyConfig {
    public static ForgeConfigSpec.BooleanValue MODEL_SPLIT_ENABLED;
    public static ForgeConfigSpec.BooleanValue PAUSE_MARKER_ENABLED;
    public static ForgeConfigSpec.DoubleValue PAUSE_SECONDS;
    public static ForgeConfigSpec.DoubleValue PAUSE_MAX_TOTAL_SECONDS;

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("reply");

        builder.comment("Tell the model to split its replies into a few short sentences, one per line, like messages in a chat app",
                "Each line is then shown as its own chat line (see chatbar SplitReplyLines, which should stay on); adds a short instruction to the maid's system prompt",
                "Off by default; independent of ReplyPauseMarkerEnabled");
        MODEL_SPLIT_ENABLED = builder.define("ReplyModelSplitEnabled", false);

        builder.comment("Tell the model it may write <等待> (or <wait>) between two sentences to pause there, and show the part after it a moment later in the chat",
                "The marker itself is never shown, spoken or stored, even when this is off (a marker the model writes anyway is always removed)",
                "The bubble and the voice still appear all at once; only the chat lines are staggered. Adds a short instruction to the maid's system prompt",
                "Off by default; independent of ReplyModelSplitEnabled");
        PAUSE_MARKER_ENABLED = builder.define("ReplyPauseMarkerEnabled", false);

        builder.comment("How long a pause marker without its own number holds the next part back, in seconds");
        PAUSE_SECONDS = builder.defineInRange("ReplyPauseSeconds", 1.5D, 0.3D, 10.0D);

        builder.comment("The most the pauses of one reply may add up to, in seconds; markers beyond it no longer delay the rest");
        PAUSE_MAX_TOTAL_SECONDS = builder.defineInRange("ReplyPauseMaxTotalSeconds", 12.0D, 1.0D, 60.0D);

        builder.pop();
    }
}
