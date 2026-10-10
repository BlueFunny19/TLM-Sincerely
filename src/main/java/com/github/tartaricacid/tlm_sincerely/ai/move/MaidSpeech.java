package com.github.tartaricacid.tlm_sincerely.ai.move;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/**
 * 让女仆"自己说一句话"（头顶气泡 + 聊天栏 {@code <女仆名> 文字}，灰色）。
 *
 * <p>不走 LLM：移动指令的兜底提示（传送了 / 太远了 / 没地方站 / 放弃）需要在判定当刻就说出来，
 * 文字全部来自 {@link MoveText} 的 {@code said*} 方法。
 *
 * <p>实现直接复用主模组的 {@code ChatBubbleManager#addLLMChatText(String, long)}。javap 证据
 * （mapped TLM 1.5.3）：
 *
 * <ul>
 *   <li>{@code addLLMChatText} 内部用 {@code TextChatBubbleData.type2(Component.literal(text))} 建气泡，
 *       先 {@code getChatBubbleDataCollection().remove(id)} 再 {@code add(...)}，然后
 *       {@code forceUpdateChatBubble()}；</li>
 *   <li>只有当 {@code maid.getOwner()} 是 {@link net.minecraft.server.level.ServerPlayer} 时，才把
 *       {@code "<" + 女仆名 + "> " + text} 以 {@code ChatFormatting.GRAY} 发给主人。</li>
 * </ul>
 *
 * <p>"只发主人还是广播全服"不用在这里再判断：本模组的 {@code MaidChatBroadcastMixin} 已经重定向了这一次
 * {@code ServerPlayer#sendSystemMessage}，按 {@code ChatBarPlayerState.isGlobalVisible(owner)} 决定
 * 广播还是私聊，并按 {@code SplitReplyLines} 分条。因此这里只需要调用一次，气泡与聊天栏都会得到。
 * 第二参数是"先移除哪条气泡"（主模组传的是 thought 气泡的 id），我们没有 thought 气泡，传
 * {@link #NO_BUBBLE_ID}（-1，集合里不存在，等同于不删任何东西）。
 *
 * <p>必须在服务端主线程调用（现有 tick 驱动与 {@code LLMCallback} 都满足）。
 */
public final class MaidSpeech {
    /** 没有需要先移除的气泡时传给 {@code addLLMChatText} 的 id。 */
    private static final long NO_BUBBLE_ID = -1L;

    private MaidSpeech() {
    }

    /**
     * 让女仆说出 {@code text}：脚下弹一个头顶气泡，同时在聊天栏显示 {@code <女仆名> text}。
     * 主人不在线 / 不是服务端玩家时只会留下气泡，不会发聊天消息（与主模组一致）。
     */
    public static void say(EntityMaid maid, String text) {
        if (maid == null || text == null || text.isEmpty()) {
            return;
        }
        maid.getChatBubbleManager().addLLMChatText(text, NO_BUBBLE_ID);
    }
}
