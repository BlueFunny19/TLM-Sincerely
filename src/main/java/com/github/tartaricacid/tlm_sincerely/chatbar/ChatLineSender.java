package com.github.tartaricacid.tlm_sincerely.chatbar;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 把女仆的一条回复（或其中一行）发到聊天栏。
 *
 * <p>{@code player} 是对话的主人本人；是否向全服广播按主人自己的全局 / 私聊偏好决定，与 {@code /tlmchat global} 一致。
 * 立即显示与按停顿错开显示都经过这里，保证两条路径的可见范围相同。
 */
public final class ChatLineSender {
    private ChatLineSender() {
    }

    public static void send(ServerPlayer player, Component message) {
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
