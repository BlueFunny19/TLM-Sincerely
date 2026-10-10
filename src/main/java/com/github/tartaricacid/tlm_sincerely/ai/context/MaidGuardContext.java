package com.github.tartaricacid.tlm_sincerely.ai.context;

import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardData;
import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardTaskDriver;
import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardText;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveDirectiveManager;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveOrderData;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.AbstractMaidContext;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;

/**
 * 把女仆的守护任务状态（守护名单、此刻守着谁、剩余时间）作为一栏上下文交给模型，
 * 这样玩家说"换成先守我"之类的话时，模型知道名单现在是什么样。
 */
public class MaidGuardContext extends AbstractMaidContext {
    public MaidGuardContext() {
        super("tlm_sincerely_guard_task", GuardText.contextLabel());
    }

    @Override
    public String getValue(EntityMaid maid) {
        boolean zh = PromptLang.isChinese(PromptLang.of(maid));
        if (maid == null || !GuardTaskDriver.isGuarding(maid)) {
            return GuardText.contextNotGuarding(zh);
        }
        GuardData data = GuardTaskDriver.dataOf(maid);
        String current = null;
        MoveOrderData order = MoveDirectiveManager.orderOf(maid.getUUID());
        if (order != null && order.targetId() != null && data.rules().contains(order.targetId())) {
            current = data.rules().entries().get(data.rules().indexOf(order.targetId())).name();
        }
        int remaining = 0;
        if (data.isTemporary() && maid.level() instanceof ServerLevel level) {
            remaining = (int) Math.max(0L, (data.expireAtGameTick() - level.getGameTime()) / 20L);
        }
        return GuardText.contextGuarding(zh, data.rules(), current, data.isTemporary(), remaining);
    }
}
