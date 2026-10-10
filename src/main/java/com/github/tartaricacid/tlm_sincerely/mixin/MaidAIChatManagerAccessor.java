package com.github.tartaricacid.tlm_sincerely.mixin;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * 撬开 {@code MaidAIChatManager.getMessages}（private）：组装"系统设定 + 摘要 + 历史"消息前缀的唯一入口，
 * 与玩家聊天路径同构。本 mod 请模型"按人设说一句话"（{@code ai.line.MaidLines}）时用它拿到带人设的前缀。
 *
 * <p>方法名特意带了本 mod 的前缀：附属 Self Talk 也有一个撬同一个方法的访问器，两个访问器的方法名相同时，
 * 混入合并到同一个目标类里会冲突。
 */
@Mixin(MaidAIChatManager.class)
public interface MaidAIChatManagerAccessor {

    @Invoker(value = "getMessages", remap = false)
    List<LLMMessage> tlmSincerely$invokeGetMessages(MaidAIChatManager chatManager, String language);
}
