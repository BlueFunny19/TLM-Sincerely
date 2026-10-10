package com.github.tartaricacid.tlm_sincerely.chatbar;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 按停顿错开显示聊天行的服务端调度：任务排进 {@link DelayedQueue}，每个服务端 tick 把到点的执行掉。
 * 只在服务端主线程使用；服务端停止时清空，没显示完的行直接丢弃。
 */
public final class ReplyDelivery {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReplyDelivery.class);
    private static final DelayedQueue QUEUE = new DelayedQueue();

    private ReplyDelivery() {
    }

    /** {@code delayTicks} 个 tick 之后在服务端主线程执行 {@code task}。 */
    public static void schedule(MinecraftServer server, int delayTicks, Runnable task) {
        QUEUE.add(server.getTickCount() + Math.max(0, delayTicks), task);
    }

    public static void onServerTick(MinecraftServer server) {
        if (server == null || QUEUE.isEmpty()) {
            return;
        }
        QUEUE.runDue(server.getTickCount(),
                e -> LOGGER.warn("[reply] a delayed chat line failed to show", e));
    }

    public static void clear() {
        QUEUE.clear();
        PendingReplyPauses.clear();
    }
}
