package com.github.tartaricacid.tlm_sincerely.ai.context;

import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveDescribe;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveDirectiveManager;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveOrderData;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveText;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.AbstractMaidContext;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * 把女仆"当前生效的移动指令"作为一栏上下文交给模型。
 *
 * <p>解决的正是永久指令最容易出的问题：指令没有时限，模型在后续对话里不知道女仆还在执行哪条指令，
 * 于是玩家说"停下"时它可能答非所问。这一栏由本模组在服务端管理，值里带上动作描述与剩余时间，
 * 并明确告诉模型"玩家要停下时调用 maid_cancel"（文案见
 * {@link MoveText#contextActive}）。
 *
 * <p>栏目标题由主 Mod 在构造时固定、不能按女仆切换语言，所以中英并列；文案本身按女仆语言选择。
 */
public class MaidMoveContext extends AbstractMaidContext {
    public MaidMoveContext() {
        super("tlm_sincerely_move_order", MoveText.contextLabel());
    }

    @Override
    public String getValue(EntityMaid maid) {
        boolean zh = PromptLang.isChinese(PromptLang.of(maid));
        MoveOrderData order = maid == null ? null : MoveDirectiveManager.orderOf(maid.getUUID());
        if (order == null) {
            return MoveText.contextNone(zh);
        }
        String detail = MoveDescribe.describe(order, targetName(maid, order), zh);
        int remainingSeconds = remainingSeconds(maid, order);
        return MoveText.contextActive(zh, detail, order.isPermanent(), remainingSeconds);
    }

    /**
     * 剩余秒数。有服务端世界时按 gameTime 差值算；拿不到世界（例如上下文在客户端侧构造）时
     * 用墙钟折算成 tick，只用于展示，不做任何状态判断。
     */
    private static int remainingSeconds(EntityMaid maid, MoveOrderData order) {
        if (order.isPermanent()) {
            return 0;
        }
        if (maid.level() instanceof ServerLevel level) {
            return order.remainingSeconds(level.getGameTime());
        }
        return order.remainingSeconds(System.currentTimeMillis() / 50L);
    }

    /**
     * 目标显示名：主人用 {@code MoveText.ownerName}，其他实体用名字，解析不到时返回 null
     * （{@link MoveDescribe} 会退回"主人"的说法，仅影响描述可读性，不影响指令本身）。
     */
    @Nullable
    private static String targetName(EntityMaid maid, MoveOrderData order) {
        if (order.targetId() == null) {
            return null;
        }
        boolean zh = PromptLang.isChinese(PromptLang.of(maid));
        if (order.targetId().equals(maid.getOwnerUUID())) {
            return MoveText.ownerName(zh);
        }
        if (maid.level() instanceof ServerLevel level) {
            var entity = level.getEntity(order.targetId());
            if (entity != null) {
                return entity.getName().getString();
            }
        }
        return null;
    }
}
