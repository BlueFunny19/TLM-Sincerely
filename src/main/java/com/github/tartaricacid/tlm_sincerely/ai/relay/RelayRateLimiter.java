package com.github.tartaricacid.tlm_sincerely.ai.relay;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 转告防刷/防回环状态。刻意不依赖任何 Minecraft 类型，便于纯 Java 单元测试。
 *
 * <p>只保留内存状态，服务器运行期有效；服务端停止时由 {@link #clear()} 清空。
 * 所有方法都设计为在服务端线程调用（工具的 dispatch 已保证），因此内部不做同步。
 *
 * <p>两条限制：
 * <ul>
 *   <li>同一对女仆（无方向）在冷却窗口内不可再次转告；
 *   <li>同一主人每分钟的转告总次数上限。
 * </ul>
 *
 * <p>清理策略：每次调用时先对相关键做惰性清理，另外每 {@code SWEEP_INTERVAL} 次调用做一次
 * 全表清扫，避免长期只访问少数键导致过期条目无限堆积。
 */
public final class RelayRateLimiter {
    /** 限流窗口：每分钟计数与冷却过期的判定基准。 */
    public static final long WINDOW_MILLIS = 60_000L;
    private static final int SWEEP_INTERVAL = 64;

    private final Map<PairKey, Long> pairCooldownUntil = new HashMap<>();
    private final Map<UUID, Deque<Long>> ownerCallTimestamps = new HashMap<>();
    private int callsSinceSweep;

    /**
     * 预检查 + 记录。成功返回 {@link Decision#allowed()} 为 true 的判定；被拒绝时不写入任何状态。
     *
     * @param ownerId       女仆主人的 UUID
     * @param fromId        发起转告的女仆
     * @param toId          接收转告的女仆
     * @param nowMillis     当前时间（毫秒）
     * @param cooldownMillis 同一对女仆的冷却时长（≤0 表示不限制）
     * @param maxPerMinute  同一主人每分钟上限（≤0 表示不限制）
     */
    public Decision checkAndRecord(UUID ownerId, UUID fromId, UUID toId, long nowMillis,
                                   long cooldownMillis, int maxPerMinute) {
        if ((++callsSinceSweep % SWEEP_INTERVAL) == 0) {
            sweep(nowMillis);
        }

        long cooldownUntil = 0L;
        if (cooldownMillis > 0 && fromId != null && toId != null) {
            PairKey key = PairKey.of(fromId, toId);
            Long until = pairCooldownUntil.get(key);
            if (until != null) {
                if (until > nowMillis) {
                    return Decision.cooldown(until - nowMillis);
                }
                pairCooldownUntil.remove(key);
            }
            cooldownUntil = nowMillis + cooldownMillis;
        }

        if (ownerId != null && maxPerMinute > 0) {
            Deque<Long> timestamps = ownerCallTimestamps.computeIfAbsent(ownerId, k -> new ArrayDeque<>());
            pruneTimestamps(timestamps, nowMillis);
            if (timestamps.size() >= maxPerMinute) {
                Long oldest = timestamps.peekFirst();
                long retryAfter = oldest == null ? WINDOW_MILLIS : (oldest + WINDOW_MILLIS) - nowMillis;
                return Decision.limit(Math.max(0L, retryAfter));
            }
            timestamps.addLast(nowMillis);
        }

        if (cooldownUntil > 0) {
            pairCooldownUntil.put(PairKey.of(fromId, toId), cooldownUntil);
        }
        return Decision.ok();
    }

    /** 清空全部状态；服务端停止或测试用例复位时调用。 */
    public void clear() {
        pairCooldownUntil.clear();
        ownerCallTimestamps.clear();
        callsSinceSweep = 0;
    }

    /** 仅用于诊断：当前记录的“对”数量。 */
    public int trackedPairCount() {
        return pairCooldownUntil.size();
    }

    private void sweep(long nowMillis) {
        Iterator<Map.Entry<PairKey, Long>> pairIterator = pairCooldownUntil.entrySet().iterator();
        while (pairIterator.hasNext()) {
            if (pairIterator.next().getValue() <= nowMillis) {
                pairIterator.remove();
            }
        }
        Iterator<Map.Entry<UUID, Deque<Long>>> ownerIterator = ownerCallTimestamps.entrySet().iterator();
        while (ownerIterator.hasNext()) {
            Deque<Long> timestamps = ownerIterator.next().getValue();
            pruneTimestamps(timestamps, nowMillis);
            if (timestamps.isEmpty()) {
                ownerIterator.remove();
            }
        }
    }

    private static void pruneTimestamps(Deque<Long> timestamps, long nowMillis) {
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= nowMillis - WINDOW_MILLIS) {
            timestamps.removeFirst();
        }
    }

    /** 无方向的“女仆对”键：小的 UUID 在前，保证 A→B 与 B→A 命中同一条目。 */
    private record PairKey(UUID first, UUID second) {
        static PairKey of(UUID a, UUID b) {
            return a.compareTo(b) <= 0 ? new PairKey(a, b) : new PairKey(b, a);
        }
    }

    /**
     * @param allowed          是否放行
     * @param reason           拒绝原因；放行时为 {@link Reason#OK}
     * @param retryAfterMillis 建议重试等待毫秒数（仅用于日志/说明）
     */
    public record Decision(boolean allowed, Reason reason, long retryAfterMillis) {
        private static final Decision OK = new Decision(true, Reason.OK, 0L);

        public enum Reason {
            OK,
            COOLDOWN,
            OWNER_LIMIT
        }

        public static Decision ok() {
            return OK;
        }

        public static Decision cooldown(long retryAfterMillis) {
            return new Decision(false, Reason.COOLDOWN, retryAfterMillis);
        }

        public static Decision limit(long retryAfterMillis) {
            return new Decision(false, Reason.OWNER_LIMIT, retryAfterMillis);
        }
    }
}
