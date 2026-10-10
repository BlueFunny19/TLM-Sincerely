package com.github.tartaricacid.tlm_sincerely.mixin;

import com.github.tartaricacid.tlm_sincerely.chatbar.PendingReplyPauses;
import com.github.tartaricacid.tlm_sincerely.chatbar.ReplyPauses;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.ReplyConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在主模组处理回复之前，把回复里的停顿标记 {@code <等待>} 剥掉。
 *
 * <p>这一步放在 {@code LLMCallback.onSuccess} 的最开头：此后主模组写历史、显示气泡、合成语音用到的
 * 都是已经没有标记的正文，标记永远不会落盘、不会被念出来。<b>开关关着也剥</b>——模型自己乱写标记
 * 也不能露在外面。标记所在的位置换成换行（前后是两句话）。
 *
 * <p>停顿功能开着且回复确有标记时，顺手把"各段正文 + 各段之间的停顿"登记到 {@link PendingReplyPauses}，
 * 稍后聊天栏发这条回复时（{@code MaidChatBroadcastMixin}）据此错开显示。
 *
 * <p>这个方法可能在 LLM 的响应线程上执行，所以只做字符串处理与线程安全的登记，不碰世界状态。
 * Self Talk 的自话 / 欢迎语回调在自己的收尾里调用 {@code super.onSuccess}，同样经过这里。
 */
@Mixin(value = LLMCallback.class, remap = false)
public abstract class LLMCallbackReplyMarkerMixin {
    @Shadow
    @Final
    protected EntityMaid maid;

    @Inject(method = "onSuccess(Lcom/github/tartaricacid/touhoulittlemaid/ai/manager/response/ResponseChat;)V",
            at = @At("HEAD"), remap = false, require = 0)
    private void tlmSincerely$stripPauseMarkers(ResponseChat response, CallbackInfo ci) {
        if (response == null) {
            return;
        }
        String tts = response.ttsText;
        if (ReplyPauses.containsMarker(tts)) {
            response.ttsText = ReplyPauses.strip(tts);
        }
        String chat = response.chatText;
        if (!ReplyPauses.containsMarker(chat)) {
            return;
        }
        ReplyPauses.Parsed parsed = ReplyPauses.parse(chat, ReplyConfig.PAUSE_SECONDS.get());
        response.chatText = parsed.strippedText();
        if (ReplyConfig.PAUSE_MARKER_ENABLED.get() && parsed.segments().size() > 1 && maid != null) {
            PendingReplyPauses.put(maid.getUUID(), parsed.strippedText(), parsed.segments());
        }
    }
}
