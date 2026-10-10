package com.github.tartaricacid.tlm_sincerely.ai.line;

/**
 * 标记接口：本 mod 自己发起的"静默"LLM 请求的回调（女仆累了的抱怨、要点心……）。
 *
 * <p>它们继承主模组的 {@code LLMCallback}，但自己接管成功 / 失败的收尾（不写聊天历史、不播 TTS、不弹等待气泡），
 * 不走主模组的完成流程，所以不能被"记忆聊天追踪"登记——登记了就永远等不到完成，十分钟后看门狗必然误报
 * （{@code LLMCallbackTrackingMixin} 据此跳过它们）。
 */
public interface SilentCallback {
}
