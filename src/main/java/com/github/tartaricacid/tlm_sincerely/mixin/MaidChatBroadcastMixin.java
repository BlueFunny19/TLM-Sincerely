package com.github.tartaricacid.tlm_sincerely.mixin;

import com.github.tartaricacid.tlm_sincerely.chatbar.ChatLineSender;
import com.github.tartaricacid.tlm_sincerely.chatbar.PendingReplyPauses;
import com.github.tartaricacid.tlm_sincerely.chatbar.ReplyDelivery;
import com.github.tartaricacid.tlm_sincerely.chatbar.ReplyPauses;
import com.github.tartaricacid.tlm_sincerely.chatbar.ReplySplitter;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.ChatBarConfig;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.ReplyConfig;
import com.github.tartaricacid.tlm_sincerely.memory.MemoryMaintenanceManager;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.ChatBubbleManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.UUID;

@Mixin(value = ChatBubbleManager.class, remap = false)
public class MaidChatBroadcastMixin {

    @Shadow
    @Final
    private EntityMaid maid;

    @Redirect(
            method = "addLLMChatText",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;sendSystemMessage(Lnet/minecraft/network/chat/Component;)V"
            ),
            remap = true
    )
    private void redirectMaidReply(ServerPlayer player, Component message) {
        if (MemoryMaintenanceManager.isMaintaining(maid.getUUID())) {
            return;
        }
        String fullText = message.getString();
        // 回复里带了停顿标记（解析时登记在 PendingReplyPauses）：按停顿错开显示
        if (ReplyConfig.PAUSE_MARKER_ENABLED.get() && tlmSincerelyDeliverWithPauses(player, message, fullText)) {
            return;
        }
        // 多段回复拆成多条聊天消息，让女仆看起来是连续说了几句话（只影响聊天栏显示）
        if (ChatBarConfig.SPLIT_REPLY_LINES.get()) {
            List<String> lines = ReplySplitter.split(fullText);
            if (lines.size() > 1) {
                for (String line : lines) {
                    ChatLineSender.send(player, Component.literal(line).withStyle(message.getStyle()));
                }
                return;
            }
        }
        ChatLineSender.send(player, message);
    }

    /**
     * 这条回复有登记过的停顿安排就按安排显示：第一段马上显示，后面的段按累计停顿晚一点显示。
     *
     * @return 是否已经处理（没有登记 / 正文对不上 / 只有一段时返回 false，调用方照常显示）
     */
    private boolean tlmSincerelyDeliverWithPauses(ServerPlayer player, Component message, String fullText) {
        PendingReplyPauses.Pending pending = PendingReplyPauses.take(maid.getUUID(), ReplySplitter.bodyOf(fullText));
        if (pending == null || pending.segments().size() < 2) {
            return false;
        }
        List<ReplyPauses.Delivery> plan = ReplyPauses.plan(ReplySplitter.prefixOf(fullText), pending.segments(),
                ChatBarConfig.SPLIT_REPLY_LINES.get(), ReplyConfig.PAUSE_MAX_TOTAL_SECONDS.get());
        MinecraftServer server = player.getServer();
        UUID playerId = player.getUUID();
        UUID maidId = maid.getUUID();
        Style style = message.getStyle();
        for (ReplyPauses.Delivery delivery : plan) {
            Component line = Component.literal(delivery.text()).withStyle(style);
            if (delivery.delayTicks() <= 0 || server == null) {
                ChatLineSender.send(player, line);
                continue;
            }
            ReplyDelivery.schedule(server, delivery.delayTicks(), () -> {
                // 晚显示的行：玩家下线了就算了；记忆整理期间女仆是静默的，同样不显示
                ServerPlayer target = server.getPlayerList().getPlayer(playerId);
                if (target != null && !MemoryMaintenanceManager.isMaintaining(maidId)) {
                    ChatLineSender.send(target, line);
                }
            });
        }
        return true;
    }
}
