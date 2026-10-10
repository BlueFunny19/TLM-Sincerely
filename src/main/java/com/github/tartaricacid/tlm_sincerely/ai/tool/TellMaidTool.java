package com.github.tartaricacid.tlm_sincerely.ai.tool;

import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.tlm_sincerely.ai.relay.RelayRateLimiter;
import com.github.tartaricacid.tlm_sincerely.ai.relay.SelfTalkBridge;
import com.github.tartaricacid.tlm_sincerely.chatbar.MaidFinder;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.RelayConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ChatCompletion;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AI tool that lets a maid relay one sentence to another maid of the same owner and ask her to respond.
 *
 * <p>转告本身不经过 TLM 的聊天链，而是交给附属 Mod「Self Talk」的互聊流程
 * （见 {@link SelfTalkBridge}）；本工具只负责选目标、限流、桥接与可见性回显。
 * 目标解析沿用聊天栏的 {@link MaidFinder#findByName} 规则（精确优先/否则子串/多命中取最近）。
 */
public class TellMaidTool implements ITool<TellMaidTool.Result> {
    private static final String TOOL_ID = "tell_maid";

    /** 转告文本长度上限（与聊天栏一致量级，避免把整段历史塞给对方）。 */
    private static final int MAX_MESSAGE_LENGTH = 512;

    /** 可见性回显的附近玩家半径，与聊天栏模块的喊话范围（TargetRange）不同，取更小的“身边”语义。 */
    private static final double BROADCAST_RANGE = 32.0;

    private static final String TOOL_DESC_EN = """
            Relay one sentence to another maid of the same owner and ask her to respond.
            Use it ONLY when the player's current message was addressed to several maids at the same time, or when the player explicitly asks you to pass something on to / ask another maid.
            Do not use it for small talk, and do not use it when the other maid does not need to take part.
            Call it at most once per conversation, and only for the part that the other maid is actually needed for.
            The 'target' must be another maid's name; never the player's name and never yourself.
            The 'message' must be what you want to say to her, in your own words; it is relayed as-is.
            Never invent content that the player did not say and present it as the player's request.
            This tool needs the optional 'Maid Self-Talk' mod with its inter-chat switch enabled; if it is unavailable or rate limited the result is an error and the relay does not happen.
            """.trim();

    private static final String TOOL_DESC_ZH = """
            把一句话转告给同一主人名下的另一只女仆，并请她回应。
            只在玩家当前这句话同时说给多只女仆、或玩家明确要求你转告/询问另一只女仆时使用。
            不要用于闲聊，也不要在不需要另一只女仆参与时使用。
            每次对话最多调用一次，而且只转告确实需要另一只女仆参与的那部分。
            'target' 必须是另一只女仆的名字，不能是玩家的名字，也不能是你自己。
            'message' 是你想对她说的话，用你自己的话表达；会被原样转告。
            绝不编造玩家没说过的内容并当成玩家的请求。
            本工具需要可选模组「Maid Self-Talk」并开启其互聊开关；如果它不可用或被限流，会返回错误且不会发生转告。
            """.trim();

    private static final Codec<Result> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("target", "").forGetter(Result::target),
            Codec.STRING.optionalFieldOf("message", "").forGetter(Result::message)
    ).apply(instance, Result::new));

    private static final RelayRateLimiter RATE_LIMITER = new RelayRateLimiter();

    /** 服务端停止时清空转告限流状态（内存态，不跨存档保留）。 */
    public static void clearRateLimitState() {
        RATE_LIMITER.clear();
    }

    @Override
    public String id() {
        return TOOL_ID;
    }

    @Override
    public String summary(EntityMaid maid) {
        return PromptLang.pick(maid, TOOL_DESC_ZH, TOOL_DESC_EN);
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        boolean zh = PromptLang.isChinese(PromptLang.of(maid));
        StringParameter target = StringParameter.create()
                .setDescription(zh
                        ? "要转告的另一只女仆的名字，例如 '爱丽丝'。她必须和你属于同一个主人；名字匹配规则与聊天栏一致"
                        + "（先精确匹配，否则按子串匹配，多命中时取最近的那只）。"
                        : "The name of the other maid to relay to, for example 'Alice'. "
                        + "She must belong to the same owner as you; partial names are matched the same way "
                        + "as the chat bar does (exact name first, otherwise substring, nearest wins).")
                .setMinLength(1)
                .setMaxLength(64);
        root.addProperties("target", target);

        StringParameter message = StringParameter.create()
                .setDescription(zh ? "你想转告给她的一句话，用她的语言、纯文本、不带换行。"
                        : "The sentence you want to relay to her, in her language, as plain text without line breaks.")
                .setMinLength(1)
                .setMaxLength(MAX_MESSAGE_LENGTH);
        root.addProperties("message", message);
        return root;
    }

    @Override
    public Codec<Result> codec() {
        return CODEC;
    }

    /**
     * 只在配置开启时把工具暴露给模型；关闭后该工具不会出现在模型工具列表中。
     */
    @Override
    public boolean trigger(EntityMaid maid, ChatCompletion chatCompletion) {
        return RelayConfig.RELAY_ENABLED.get();
    }

    /**
     * Safety net only: TLM 1.5.3 调用 {@link #onCallAsync}，同步路径没有服务端线程保证，直接拒绝。
     */
    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        return callback.addToolResult(PromptLang.pick(callback.getMaid(),
                "本工具只能通过异步工具路径调用；没有发生转告。",
                "This tool is only available through the asynchronous tool path; nothing was relayed."),
                toolCallId);
    }

    @Override
    public CompletableFuture<LLMCallback> onCallAsync(String toolCallId, Result result,
                                                      LLMCallback callback, LLMClient client) {
        if (callback.isOnServerThread()) {
            return dispatch(toolCallId, result, callback);
        }
        // 防御性：LLM 回调可能落在异步线程，而 Self Talk API 与实体查询都要求服务端主线程
        EntityMaid maid = callback.getMaid();
        if (maid == null || !(maid.level() instanceof ServerLevel)) {
            return CompletableFuture.completedFuture(callback);
        }
        CompletableFuture<LLMCallback> scheduled = new CompletableFuture<>();
        callback.runOnServerThread(() -> {
            try {
                dispatch(toolCallId, result, callback).whenComplete((completed, throwable) -> {
                    if (throwable != null) {
                        scheduled.completeExceptionally(throwable);
                    } else {
                        scheduled.complete(completed);
                    }
                });
            } catch (Throwable throwable) {
                scheduled.completeExceptionally(throwable);
            }
        });
        return scheduled;
    }

    @Override
    public String invocationSummary(Result result) {
        return "%s { %s }".formatted(TOOL_ID, summarize(result.target()));
    }

    private CompletableFuture<LLMCallback> dispatch(String toolCallId, Result result, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        if (maid == null) {
            return completed(callback.addToolResult(
                    "No maid context is available for this tool call.", toolCallId));
        }
        boolean zh = PromptLang.isChinese(PromptLang.of(maid));
        if (!(maid.level() instanceof ServerLevel serverLevel)) {
            return completed(callback.addToolResult(
                    zh ? "只能在服务端进行转告。" : "Relaying is only available on the server side.", toolCallId));
        }
        if (!RelayConfig.RELAY_ENABLED.get()) {
            return completed(callback.addToolResult(
                    zh ? "本服务器已禁用 tell_maid 工具；没有发生转告。"
                            : "The tell_maid tool is disabled on this server; nothing was relayed.", toolCallId));
        }

        String targetName = safe(result.target()).trim();
        if (targetName.isEmpty()) {
            return completed(callback.addToolResult(ITool.invalidParam("target",
                    List.of(zh ? "<另一只女仆的名字>" : "<another maid's name>"),
                    zh ? "必须提供 target" : "target is required"), toolCallId));
        }
        String message = normalizeMessage(result.message());
        if (message == null) {
            return completed(callback.addToolResult(ITool.invalidParam("message",
                    List.of(zh ? "<一句话，最多 " + MAX_MESSAGE_LENGTH + " 个字符，不含换行>"
                            : "<one sentence, at most " + MAX_MESSAGE_LENGTH + " characters, no line breaks>"),
                    zh ? "必须提供 message，且必须是一行、最多 " + MAX_MESSAGE_LENGTH + " 个字符"
                            : "message is required and must be a single line of at most "
                            + MAX_MESSAGE_LENGTH + " characters"), toolCallId));
        }

        List<EntityMaid> candidates = MaidFinder.getOwnedMaidsOfSameOwner(maid, MaidFinder.targetRange(), maid);
        List<EntityMaid> matches = MaidFinder.matchByName(candidates, targetName, maid);
        if (matches.isEmpty()) {
            return completed(callback.addToolResult(noMatchMessage(zh, targetName, candidates), toolCallId));
        }
        EntityMaid target = MaidFinder.findNearest(serverLevel, maid, matches);
        if (target == null) {
            return completed(callback.addToolResult(noMatchMessage(zh, targetName, candidates), toolCallId));
        }

        if (!SelfTalkBridge.isModLoaded()) {
            return completed(callback.addToolResult(
                    zh ? "未安装 Self Talk（maid_self_talk）；没有发生转告。"
                            : "Self Talk (maid_self_talk) is not installed; nothing was relayed.", toolCallId));
        }
        if (!SelfTalkBridge.isAvailable()) {
            return completed(callback.addToolResult(
                    zh ? "已安装 Self Talk，但它的转告 API 不可用（互聊功能可能已关闭）；没有发生转告。"
                            : "Self Talk is installed but its relay API is unavailable (its inter-chat feature may be "
                            + "disabled); nothing was relayed.", toolCallId));
        }

        UUID ownerUuid = maid.getOwnerUUID();
        if (ownerUuid == null) {
            return completed(callback.addToolResult(
                    zh ? "这只女仆没有主人，无法转告给其他女仆。"
                            : "This maid has no owner, so relaying to another maid is not possible.", toolCallId));
        }

        long now = System.currentTimeMillis();
        long cooldownMillis = RelayConfig.RELAY_COOLDOWN_SECONDS.get() * 1000L;
        int maxPerMinute = RelayConfig.RELAY_MAX_PER_MINUTE_PER_OWNER.get();
        RelayRateLimiter.Decision decision = RATE_LIMITER.checkAndRecord(
                ownerUuid, maid.getUUID(), target.getUUID(), now, cooldownMillis, maxPerMinute);
        if (!decision.allowed()) {
            long seconds = Math.max(1L, (decision.retryAfterMillis() + 999L) / 1000L);
            if (decision.reason() == RelayRateLimiter.Decision.Reason.COOLDOWN) {
                return completed(callback.addToolResult(zh
                        ? "这两只女仆刚刚才转告过；请约 " + seconds + " 秒后再试。没有发生转告。"
                        : "This pair of maids relayed too recently; try again in about " + seconds
                        + " seconds. Nothing was relayed.", toolCallId));
            }
            return completed(callback.addToolResult(zh
                    ? "主人每分钟最多转告 " + maxPerMinute + " 次，已达上限；请约 " + seconds
                    + " 秒后再试。没有发生转告。"
                    : "Your owner reached the relay limit of " + maxPerMinute
                    + " per minute; try again in about " + seconds
                    + " seconds. Nothing was relayed.", toolCallId));
        }

        boolean relayed = SelfTalkBridge.relayToMaid(maid, target, message);
        if (!relayed) {
            return completed(callback.addToolResult(
                    zh ? "Self Talk 拒绝了本次转告（互聊开关可能已关闭，或对方正忙）；没有发生转告。"
                            : "Self Talk rejected the relay (its inter-chat switch may be off, or the maid is busy); "
                            + "nothing was relayed.", toolCallId));
        }

        broadcastRelay(maid, target, message);
        String targetDisplay = target.getName().getString();
        return completed(callback.addToolResult(zh
                ? "已转告给 " + targetDisplay + "。" + targetDisplay + " 会自己回应。"
                : "Relayed to " + targetDisplay + ". " + targetDisplay + " will respond on their own.", toolCallId));
    }

    /**
     * 向主人与附近玩家发一行系统消息 {@code <女仆A -> 女仆B> message}；
     * 主人不在线时跳过主人，附近玩家仍可见（与聊天栏回显同为灰色）。
     */
    private static void broadcastRelay(EntityMaid from, EntityMaid to, String message) {
        Component line = Component.literal("<%s -> %s> %s".formatted(
                from.getName().getString(), to.getName().getString(), message)).withStyle(ChatFormatting.GRAY);

        List<UUID> sent = new ArrayList<>();
        LivingEntity owner = from.getOwner();
        if (owner instanceof ServerPlayer ownerPlayer) {
            ownerPlayer.sendSystemMessage(line);
            sent.add(ownerPlayer.getUUID());
        }
        // 主人离线时 getOwner() 仍可能指向已下线的玩家实体，上面的 instanceof 判定已覆盖该情况
        for (ServerPlayer nearby : from.level().getEntitiesOfClass(ServerPlayer.class,
                from.getBoundingBox().inflate(BROADCAST_RANGE))) {
            if (sent.contains(nearby.getUUID())) {
                continue;
            }
            nearby.sendSystemMessage(line);
        }
    }

    private static String noMatchMessage(boolean zh, String targetName, List<EntityMaid> candidates) {
        return zh
                ? "主人的其他女仆中没有匹配 '" + targetName + "' 的。可选的女仆：" + describeMaids(candidates, true)
                : "No other maid of your owner matches '" + targetName + "'. Available maids: "
                + describeMaids(candidates, false);
    }

    private static String describeMaids(List<EntityMaid> maids, boolean zh) {
        if (maids.isEmpty()) {
            return zh ? "（无）" : "(none)";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < maids.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(maids.get(i).getName().getString());
        }
        return builder.toString();
    }

    /** @return 去首尾空白后仍非空且不含换行的文本；否则 null */
    private static String normalizeMessage(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || value.length() > MAX_MESSAGE_LENGTH) {
            return null;
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\t') >= 0) {
            return null;
        }
        return value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String summarize(String raw) {
        String value = safe(raw).trim();
        if (value.length() > 40) {
            return value.substring(0, 40) + "...";
        }
        return value;
    }

    private static CompletableFuture<LLMCallback> completed(LLMCallback callback) {
        return CompletableFuture.completedFuture(callback);
    }

    public record Result(String target, String message) {
    }
}
