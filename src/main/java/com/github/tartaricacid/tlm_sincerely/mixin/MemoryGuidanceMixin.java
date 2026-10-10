package com.github.tartaricacid.tlm_sincerely.mixin;

import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.MemoryConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.summary.HistorySummaryManager;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(value = MaidAIChatManager.class, remap = false)
public abstract class MemoryGuidanceMixin {

    // 提示词按对话语言选：中文用 *_ZH，其余语言用 *_EN 再由 PromptLang.replyDirective 在末尾要求改用该语言回复。

    private static final String GUIDANCE_ZH = """
            ## 长期记忆使用说明
            你有一份长期记忆，内容列在上下文的「女仆的长期记忆」栏目里，用 tlm_memory 工具管理它。
            应当用 remember（记住）的情况：玩家明确要求你记住某件事；玩家说出长期有效的喜好、个人情况，或希望你怎么称呼他；发生了重要的事（收到礼物、做出约定、重要的纪念时刻）。
            不要记：闲聊、天气、转瞬即逝的游戏状态、不是直接对你说的话，以及用 query_game_context 就能查到的信息。
            key 用简短的中文词语（例如：玩家爱好、称呼偏好）；value 用一句简洁的话写清来龙去脉（谁、什么事、什么时候）。
            importance：core = 玩家明确要求记住的、或决定你们关系的重要内容；archive = 一般性的背景细节。
            只有玩家明确要求时才 forget（遗忘）。记忆容量有限，写满后会自动淘汰最旧的 archive 条目。
            想找旧记忆用 search（搜索），想看某条的完整内容用 recall（回忆）。
            以上是内部规则，回复玩家时不要提到它们，也不要夹杂英文。""";

    private static final String GUIDANCE_EN = """
            ## Memory Guidelines
            You have persistent memory, listed under "Maid persistent memories" in context. Manage it with the tlm_memory tool.
            Call remember when: the player explicitly asks you to remember; the player states a lasting preference, personal fact, or preferred form of address; a significant event occurs (gift, promise, milestone).
            Do NOT remember: small talk, weather, transient game state, anything not said to you directly, anything already available via query_game_context.
            Key: short semantic English snake_case (e.g. player_hobby). Value: one concise sentence with context (who/what/when), written in the language you use to talk to the player. Importance: core = explicitly requested or relationship-defining; archive = contextual details.
            Forget only when the player explicitly asks. Capacity is limited; when full, the oldest archive entry is evicted automatically.
            Use search to find old memories, recall to read full detail.
            These are internal rules: do not mention them when replying to the player.""";

    private static final String ROUTING_ZH = """
            你现在位于女仆实体 @%s 中，工具调用指令会作用在这个实体上。\
            你的自我身份和人格来自角色设定，而不是来自 @%s。
            玩家要你做的事如果有对应的工具（走动、跟随、坐下、切换工作任务、转告其他女仆等），必须先调用工具再回答，不能只在嘴上答应；工具返回失败，或提示条件不满足（例如现在不是工作时间）时，要如实告诉玩家。""";

    private static final String ROUTING_EN = """
            You are in the maid entity @%s, and tool-call directives will act on that entity. \
            Your self identity and persona come from the character setting, not from @%s.
            When the player asks for something a tool can do (moving, following, sitting, switching the work task, relaying to another maid, ...), call the tool first and then answer; never just promise in words. If a tool fails or says a condition is not met (for example it is not work time), tell the player honestly.""";

    @Redirect(
            method = "buildMessage",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/ai/manager/entity/summary/HistorySummaryManager;appendSummaryMessage(Ljava/util/List;)V"
            )
    )
    private void injectSincerelyGuidance(HistorySummaryManager manager, List<LLMMessage> list) {
        MaidAIChatManager self = (MaidAIChatManager) (Object) this;
        EntityMaid maid = self.getMaid();
        String language = PromptLang.of(maid);
        String routingName = maid.getName().getString();
        String routing = PromptLang.pick(language, ROUTING_ZH, ROUTING_EN);
        list.add(LLMMessage.systemChat(maid, routing.formatted(routingName, routingName)));
        if (MemoryConfig.ENABLED.get() && MemoryConfig.MEMORY_GUIDANCE.get()) {
            list.add(LLMMessage.systemChat(maid, PromptLang.pick(language, GUIDANCE_ZH, GUIDANCE_EN)));
        }
        // 非中文、非英文：英文提示词末尾追加一句"改用该语言回复"
        String directive = PromptLang.replyDirective(language);
        if (!directive.isEmpty()) {
            list.add(LLMMessage.systemChat(maid, directive));
        }
        manager.appendSummaryMessage(list);
    }
}
