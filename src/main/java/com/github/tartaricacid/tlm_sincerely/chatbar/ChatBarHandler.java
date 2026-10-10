package com.github.tartaricacid.tlm_sincerely.chatbar;

import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.ChatBarConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.ChatClientInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mod.EventBusSubscriber
public final class ChatBarHandler {
    private static final String DEFAULT_LANGUAGE = "en_us";
    private static volatile String cachedPrefix = "";
    private static volatile Pattern cachedPrefixPattern;

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (!ChatBarPlayerState.isChatModeEnabled(player)) {
            return;
        }

        String rawMessage = event.getRawText();
        String prefix = ChatBarConfig.PREFIX_PATTERN.get();
        boolean multiTargetEnabled = ChatBarConfig.MULTI_TARGET_ENABLED.get();

        List<EntityMaid> targets = new ArrayList<>();
        String chatMessage = rawMessage;
        boolean handledByMultiTarget = false;

        // 1. 多目标解析（@a @b 正文 / @全体 正文）。未启用或解析无效时回退到旧单目标逻辑。
        if (multiTargetEnabled && prefix != null && !prefix.isEmpty()) {
            AtTargetParser.Result parsed = AtTargetParser.parse(rawMessage, prefix,
                    ChatBarConfig.ALL_KEYWORDS.get());
            if (parsed.valid()) {
                handledByMultiTarget = true;
                chatMessage = parsed.message();
                MaidFinder.MultiFindResult result = MaidFinder.findByNames(player, parsed.names(),
                        parsed.all(), ChatBarConfig.MAX_TARGETS.get());
                targets.addAll(result.maids());
                if (result.truncated()) {
                    player.sendSystemMessage(Component.translatable(
                            "chat.tlm_sincerely.multi_target_truncated",
                            result.maids().size()
                    ).withStyle(ChatFormatting.YELLOW));
                }
                if (!result.missing().isEmpty()) {
                    player.sendSystemMessage(Component.translatable(
                            "chat.tlm_sincerely.maid_not_found_names",
                            String.join("、", result.missing())
                    ).withStyle(ChatFormatting.RED));
                }
            }
        }

        // 2. 旧单目标逻辑：只在多目标没有接手时执行。
        if (!handledByMultiTarget && prefix != null && !prefix.isEmpty()) {
            Pattern pattern = getPrefixPattern(prefix);
            Matcher matcher = pattern.matcher(rawMessage);
            if (matcher.find()) {
                handledByMultiTarget = true;
                String name = matcher.group(1);
                chatMessage = matcher.group(2);
                MaidFinder.FindResult result = MaidFinder.findByName(player, name);
                if (result.hasMaid()) {
                    targets.add(result.maid());
                    if (result.hasMultipleMatches()) {
                        String uuidShort = result.maid().getUUID().toString().substring(0, 8);
                        player.sendSystemMessage(Component.translatable(
                                "chat.tlm_sincerely.multiple_same_name", name, uuidShort
                        ).withStyle(ChatFormatting.YELLOW));
                    }
                } else {
                    player.sendSystemMessage(Component.translatable("chat.tlm_sincerely.maid_not_found")
                            .withStyle(ChatFormatting.RED));
                }
            }
        }

        // 3. 无前缀消息 + REQUIRE_PREFIX=false → 自动匹配范围内最近女仆
        // 只在配置的前缀完全未被使用时生效（与旧行为一致）。
        if (targets.isEmpty() && !handledByMultiTarget && !ChatBarConfig.REQUIRE_PREFIX.get()) {
            double range = ChatBarConfig.AUTO_CHAT_RANGE.get();
            if (range > 0) {
                List<EntityMaid> maids = MaidFinder.getOwnedMaids(player, range);
                if (!maids.isEmpty()) {
                    EntityMaid nearest = MaidFinder.findNearest(player, maids);
                    if (nearest != null) {
                        targets.add(nearest);
                    }
                }
            }
        }

        if (targets.isEmpty()) {
            return;
        }

        dispatchToMaids(targets, player, chatMessage, multiTargetEnabled);

        // 是否取消原始聊天消息由该玩家自己的私聊可见性偏好决定
        if (!ChatBarPlayerState.isGlobalVisible(player)) {
            event.setCanceled(true);
            String names = joinNames(targets);
            String format = "<%s -> %s> %s".formatted(
                    player.getScoreboardName(),
                    names,
                    chatMessage
            );
            player.sendSystemMessage(Component.literal(format).withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * 逐只派发。多目标（含 @全体 收到多只）时在正文前加一行“同时对谁说话”的标注，
     * 让每只女仆知道还有哪些同伴在场，从而自行判断要不要转告；单目标不加标注，保持旧行为。
     *
     * @param targets            目标女仆（保序去重）
     * @param multiTargetMessage 本句是否由多目标解析产生（决定标注格式；单目标时为 false）
     */
    private static void dispatchToMaids(List<EntityMaid> targets, ServerPlayer player,
                                        String message, boolean multiTargetMessage) {
        boolean annotated = multiTargetMessage && targets.size() > 1;
        List<String> allNames = annotated ? namesOf(targets) : List.of();
        for (EntityMaid maid : targets) {
            String payload = annotated ? withAudienceNote(player, allNames, message) : message;
            sendChatToMaid(maid, player, payload);
        }
    }

    /**
     * 多目标标注，按玩家语言选中/英文：
     * 中文：{@code （玩家同时对「女仆甲」「女仆乙」说）正文}；
     * 其余语言：{@code (The player is speaking to X, Y at the same time) text}。
     */
    private static String withAudienceNote(ServerPlayer player, List<String> allNames, String message) {
        if (PromptLang.isChinese(resolveLanguage(player))) {
            StringBuilder audience = new StringBuilder();
            for (int i = 0; i < allNames.size(); i++) {
                if (i > 0) {
                    audience.append('、');
                }
                audience.append('「').append(allNames.get(i)).append('」');
            }
            return "（%s同时对%s说）%s".formatted(player.getScoreboardName(), audience, message);
        }
        return "(%s is speaking to %s at the same time) %s".formatted(
                player.getScoreboardName(), String.join(", ", allNames), message);
    }

    private static String joinNames(List<EntityMaid> targets) {
        return String.join("、", namesOf(targets));
    }

    private static List<String> namesOf(List<EntityMaid> targets) {
        List<String> names = new ArrayList<>(targets.size());
        for (EntityMaid maid : targets) {
            names.add(maid.getName().getString());
        }
        return names;
    }

    private static Pattern getPrefixPattern(String prefix) {
        Pattern pattern = cachedPrefixPattern;
        if (pattern != null && prefix.equals(cachedPrefix)) {
            return pattern;
        }
        synchronized (ChatBarHandler.class) {
            pattern = cachedPrefixPattern;
            if (pattern != null && prefix.equals(cachedPrefix)) {
                return pattern;
            }
            cachedPrefix = prefix;
            cachedPrefixPattern = Pattern.compile("^" + Pattern.quote(prefix) + "(.+?)\\s+(.+)$");
            return cachedPrefixPattern;
        }
    }

    /**
     * 对话语言取玩家的客户端语言，与主 Mod 自己的聊天界面（ChatClientInfo.fromMaid）一致。
     * 注意不能用女仆的 TTS 语言：那是语音合成用的语言，拿来当对话语言会让女仆被要求用 TTS 语言回复。
     */
    private static String resolveLanguage(ServerPlayer player) {
        String language = player.getLanguage();
        if (language == null || language.isEmpty()) {
            return DEFAULT_LANGUAGE;
        }
        return language;
    }

    private static void sendChatToMaid(EntityMaid maid, ServerPlayer player, String message) {
        String language = resolveLanguage(player);
        String name = maid.getName().getString();
        List<String> description = List.of();
        ChatClientInfo clientInfo = new ChatClientInfo(language, name, description);
        maid.getAiChatManager().chat(message, clientInfo, player);
    }
}
