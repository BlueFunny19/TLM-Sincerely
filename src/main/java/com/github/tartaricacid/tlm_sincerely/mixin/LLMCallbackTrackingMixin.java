package com.github.tartaricacid.tlm_sincerely.mixin;

import com.github.tartaricacid.tlm_sincerely.ai.line.SilentCallback;
import com.github.tartaricacid.tlm_sincerely.memory.MemoryChatTracker;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.http.HttpRequest;
import java.util.List;

@Mixin(value = LLMCallback.class, remap = false)
public abstract class LLMCallbackTrackingMixin {
    /** 附属 Mod Self Talk 的包前缀；按类名判断，避免对它产生编译期依赖。 */
    private static final String SELF_TALK_PACKAGE = "com.maidmod.selftalk.";

    @Shadow
    @Final
    protected EntityMaid maid;

    @Inject(method = "<init>(Lcom/github/tartaricacid/touhoulittlemaid/ai/manager/entity/MaidAIChatManager;Ljava/util/List;Z)V",
            at = @At("RETURN"), remap = false)
    private void tlmSincerely$trackRequest(MaidAIChatManager manager, List<LLMMessage> messages,
                                            boolean silent, CallbackInfo ci) {
        // 附属 Mod「Self Talk」的回调（自话/互聊/梦话）继承 LLMCallback 但不走主 Mod 的完成流程
        // （onSuccess 不调 super），登记了就永远等不到完成，10 分钟后看门狗必然误报；它们不是玩家聊天，直接不追踪。
        if (this.getClass().getName().startsWith(SELF_TALK_PACKAGE)) {
            return;
        }
        // 本 mod 自己发起的静默请求（女仆累了的抱怨等）同样自己接管收尾，不走主模组的完成流程
        if ((Object) this instanceof SilentCallback) {
            return;
        }
        MemoryChatTracker.register((LLMCallback) (Object) this, maid, messages);
    }

    @Inject(method = "onSuccess(Lcom/github/tartaricacid/touhoulittlemaid/ai/manager/response/ResponseChat;)V",
            at = @At("RETURN"), remap = false)
    private void tlmSincerely$completeSuccess(ResponseChat response, CallbackInfo ci) {
        MemoryChatTracker.complete((LLMCallback) (Object) this, maid);
    }

    @Inject(method = "onFailure(Ljava/net/http/HttpRequest;Ljava/lang/Throwable;I)V",
            at = @At("RETURN"), remap = false)
    private void tlmSincerely$completeFailure(HttpRequest request, Throwable throwable, int code, CallbackInfo ci) {
        MemoryChatTracker.complete((LLMCallback) (Object) this, maid);
    }
}
