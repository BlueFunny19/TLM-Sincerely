package com.github.tartaricacid.tlm_sincerely.ai.line;

import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.tlm_sincerely.ai.move.MaidSpeech;
import com.github.tartaricacid.tlm_sincerely.chatbar.ReplyDelivery;
import com.github.tartaricacid.tlm_sincerely.mixin.MaidAIChatManagerAccessor;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.HistoryMessagesCheck;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMSite;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.AIConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 让女仆按人设说"此刻该说的话"，由模型生成；<b>生成不出来时一定说出备用台词</b>。
 *
 * <p>这类话（累了的抱怨、向主人要点心）是提醒，不能因为模型不可用就悄无声息：
 * 大语言模型总开关关着、站点不可用、女仆没有人设、前缀拼不出来、请求同步出错、回复为空、请求失败、
 * 等了 {@link #TIMEOUT_SECONDS} 秒还没回来——任何一种都改说备用台词（调用方传入的写死文字）。
 * 每次请求只会说一次：模型的回复和超时兜底谁先到谁说，后到的丢弃。
 *
 * <p>话只用于显示（头顶气泡 + 聊天栏），不写进聊天历史、不播 TTS、不弹"思考中"气泡。
 * 必须在服务端主线程调用；回调里的显示动作会切回主线程。
 */
public final class MaidLines {
    private static final Logger LOGGER = LoggerFactory.getLogger(MaidLines.class);
    /** 模型多久没回就改说备用台词（秒）。 */
    public static final int TIMEOUT_SECONDS = 15;

    private MaidLines() {
    }

    /**
     * @param overtimeMinutes 她已经连续熬了大约多少分钟（写进提示词）
     * @param fallback        模型用不了时说的备用台词
     */
    public static void say(EntityMaid maid, LinePrompts.Situation situation, int overtimeMinutes, String fallback) {
        if (maid == null || !(maid.level() instanceof ServerLevel level)) {
            return;
        }
        LineCallback callback;
        try {
            callback = prepare(maid, situation, overtimeMinutes, fallback);
        } catch (RuntimeException e) {
            LOGGER.warn("[line] failed to prepare the {} line for maid {}, using the fallback", situation,
                    maid.getUUID(), e);
            callback = null;
        }
        if (callback == null) {
            MaidSpeech.say(maid, fallback);
            return;
        }
        LineCallback request = callback;
        // 超时兜底：模型迟迟不回就改说备用台词（回复之后到的话被 done 标志挡掉）
        ReplyDelivery.schedule(level.getServer(), TIMEOUT_SECONDS * 20, () -> request.finish(fallback, false));
        try {
            maid.getAiChatManager().getLLMSite().client().chat(request);
        } catch (RuntimeException e) {
            // 同步阶段就出错（例如站点地址非法）：直接说备用台词
            LOGGER.warn("[line] failed to send the {} line request for maid {}, using the fallback", situation,
                    maid.getUUID(), e);
            request.finish(fallback, false);
        }
    }

    /** 前置条件不满足、消息拼不出来时返回 {@code null}（调用方说备用台词）。 */
    private static LineCallback prepare(EntityMaid maid, LinePrompts.Situation situation, int overtimeMinutes,
                                        String fallback) {
        MaidAIChatManager chatManager = maid.getAiChatManager();
        if (chatManager == null || !AIConfig.LLM_ENABLED.get()) {
            return null;
        }
        LLMSite site = chatManager.getLLMSite();
        if (site == null || !site.enabled()) {
            return null;
        }
        // 没有人设（既没有自定义设定、也没有模型默认设定）：主模组会自动生成人设，这里不触发它，直接说备用台词
        if (chatManager.customSetting.isBlank() && chatManager.getSetting().isEmpty()) {
            return null;
        }
        String language = PromptLang.of(maid);
        List<LLMMessage> messages = ((MaidAIChatManagerAccessor) (Object) chatManager)
                .tlmSincerely$invokeGetMessages(chatManager, language);
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        HistoryMessagesCheck.checkMessages(messages);
        String prompt = LinePrompts.of(situation, PromptLang.isChinese(language), overtimeMinutes);
        String directive = PromptLang.replyDirective(language);
        if (!directive.isEmpty()) {
            prompt = prompt + "\n" + directive;
        }
        messages.add(LLMMessage.userChat(maid, prompt));
        return new LineCallback(chatManager, messages, situation, fallback);
    }

    /** 静默回调：自己接管收尾，不调父类（不写历史、不播 TTS、不弹气泡、失败不给主人发红字）。 */
    static final class LineCallback extends LLMCallback implements SilentCallback {
        private final LinePrompts.Situation situation;
        private final String fallback;
        private final AtomicBoolean done = new AtomicBoolean();

        LineCallback(MaidAIChatManager chatManager, List<LLMMessage> messages, LinePrompts.Situation situation,
                     String fallback) {
            super(chatManager, messages, true);
            this.situation = situation;
            this.fallback = fallback;
            this.needAddTools = false;
        }

        @Override
        public void onSuccess(ResponseChat responseChat) {
            String text = LineText.normalize(responseChat == null ? null : responseChat.getChatText(),
                    LinePrompts.MAX_CHARS);
            String line = text.isBlank() ? fallback : text;
            finishOnServerThread(line, !text.isBlank());
        }

        @Override
        public void onFailure(HttpRequest request, Throwable throwable, int errorCode) {
            finishOnServerThread(fallback, false);
        }

        private void finishOnServerThread(String line, boolean generated) {
            if (isOnServerThread()) {
                finish(line, generated);
            } else {
                runOnServerThread(() -> finish(line, generated));
            }
        }

        /** 说出这句话；这次请求已经说过了（回复与超时兜底只算先到的一个）就什么都不做。 */
        void finish(String line, boolean generated) {
            if (!done.compareAndSet(false, true)) {
                return;
            }
            EntityMaid maid = getMaid();
            if (maid == null || !maid.isAlive()) {
                return;
            }
            // 去睡觉的那句话迟到了、她已经睡着：不再出声（气泡浮在睡着的女仆头上很怪，睡着本身就说明了一切）
            if (situation == LinePrompts.Situation.GO_REST && maid.isSleeping()) {
                return;
            }
            LOGGER.debug("[line] maid={} situation={} generated={}", maid.getUUID(), situation, generated);
            MaidSpeech.say(maid, line);
        }
    }
}
