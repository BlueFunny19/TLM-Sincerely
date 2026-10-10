package com.github.tartaricacid.tlm_sincerely.ai.context;

import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.MemoryConfig;
import com.github.tartaricacid.tlm_sincerely.memory.MaidMemory;
import com.github.tartaricacid.tlm_sincerely.memory.MaidMemoryManager;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.AbstractMaidContext;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

public class MaidMemoryContext extends AbstractMaidContext {
    /**
     * 栏目标题由主 Mod 在构造时固定、不能按女仆切换语言，所以中英并列；
     * 记忆指引（MemoryGuidanceMixin）里引用的就是这个标题的对应语言部分。
     */
    public MaidMemoryContext() {
        super("tlm_sincerely_memory_list", "女仆的长期记忆 (Maid persistent memories)");
    }

    @Override
    public String getValue(EntityMaid maid) {
        boolean chinese = PromptLang.isChinese(PromptLang.of(maid));
        if (!MemoryConfig.ENABLED.get()) {
            return chinese ? "记忆系统未启用" : "Memory system disabled";
        }

        MaidMemory memory = MaidMemoryManager.load(maid.getUUID());
        if (memory.isEmpty()) {
            return chinese ? "无" : "None";
        }

        return memory.generateContextPreview(
                MemoryConfig.CORE_LIMIT.get(),
                MemoryConfig.CONTEXT_PREVIEW_LENGTH.get(),
                MemoryConfig.PREVIEW_MODE.get(),
                MemoryConfig.SHOW_SOURCE.get(),
                chinese
        );
    }
}
