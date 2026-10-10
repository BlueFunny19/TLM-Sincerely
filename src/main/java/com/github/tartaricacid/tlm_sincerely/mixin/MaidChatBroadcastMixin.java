package com.github.tartaricacid.tlm_sincerely.mixin;

import com.github.tartaricacid.tlm_sincerely.chatbar.ChatBarPlayerState;
import com.github.tartaricacid.tlm_sincerely.chatbar.ReplySplitter;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.ChatBarConfig;
import com.github.tartaricacid.tlm_sincerely.memory.MemoryMaintenanceManager;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.ChatBubbleManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

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
        // 多段回复拆成多条聊天消息，让女仆看起来是连续说了几句话（只影响聊天栏显示）
        if (ChatBarConfig.SPLIT_REPLY_LINES.get()) {
            List<String> lines = ReplySplitter.split(message.getString());
            if (lines.size() > 1) {
                for (String line : lines) {
                    tlmSincerelyDeliver(player, Component.literal(line).withStyle(message.getStyle()));
                }
                return;
            }
        }
        tlmSincerelyDeliver(player, message);
    }

    private void tlmSincerelyDeliver(ServerPlayer player, Component message) {
        // 目标方法中 player 即 maid.getOwner()（对话主人本人）；
        // 是否广播按主人自己的全局/私聊偏好决定，与 /tlmchat global 一致
        if (ChatBarPlayerState.isGlobalVisible(player)) {
            MinecraftServer server = player.getServer();
            if (server != null) {
                server.getPlayerList().broadcastSystemMessage(message, false);
            }
        } else {
            player.sendSystemMessage(message);
        }
    }
}
