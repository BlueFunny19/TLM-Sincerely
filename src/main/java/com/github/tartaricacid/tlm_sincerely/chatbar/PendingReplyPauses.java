package com.github.tartaricacid.tlm_sincerely.chatbar;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 回复里的停顿位置在"解析回复"和"把回复发到聊天栏"两步之间的交接处（按女仆）。
 *
 * <p>解析在 {@code LLMCallback.onSuccess} 的开头（可能在 LLM 的响应线程上），发聊天栏在稍后的服务端主线程里
 * （{@code ChatBubbleManager.addLLMChatText}），两处隔着主模组的代码，没法直接传参，所以先登记在这里，
 * 发聊天栏时按"同一只女仆 + 同样的正文"取走。正文对不上（别的路径发的消息、或正文在中间被改过）就不取，
 * 照常即时显示；登记超过 {@link #TTL_MILLIS} 没被取走也当作作废，不让旧的登记碰到后来的消息。
 * 线程安全。
 */
public final class PendingReplyPauses {
    /** 登记的有效时间（毫秒）：解析到发出之间只隔一次服务端任务，远用不了这么久。 */
    static final long TTL_MILLIS = 30_000L;

    /** 一条待发回复的停顿安排：{@code body} 是规整过空白的正文（用来对账）。 */
    public record Pending(String body, List<ReplyPauses.Segment> segments, long createdMillis) {
    }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private PendingReplyPauses() {
    }

    /** 登记（覆盖同一只女仆还没取走的上一条）。 */
    public static void put(UUID maidId, String strippedBody, List<ReplyPauses.Segment> segments) {
        put(maidId, strippedBody, segments, System.currentTimeMillis());
    }

    static void put(UUID maidId, String strippedBody, List<ReplyPauses.Segment> segments, long nowMillis) {
        PENDING.put(maidId, new Pending(ReplyPauses.normalizeForMatch(strippedBody), List.copyOf(segments), nowMillis));
    }

    /** 取走：这只女仆有登记、没过期、正文对得上才返回（并移除）；对不上时登记留着，等它过期。 */
    public static Pending take(UUID maidId, String chatBody) {
        return take(maidId, chatBody, System.currentTimeMillis());
    }

    static Pending take(UUID maidId, String chatBody, long nowMillis) {
        Pending pending = PENDING.get(maidId);
        if (pending == null) {
            return null;
        }
        if (nowMillis - pending.createdMillis() > TTL_MILLIS) {
            PENDING.remove(maidId, pending);
            return null;
        }
        if (!pending.body().equals(ReplyPauses.normalizeForMatch(chatBody))) {
            return null;
        }
        return PENDING.remove(maidId, pending) ? pending : null;
    }

    /** 清空（服务端停止）。 */
    public static void clear() {
        PENDING.clear();
    }

    static int size() {
        return PENDING.size();
    }
}
