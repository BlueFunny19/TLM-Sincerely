package com.github.tartaricacid.tlm_sincerely.ai.move;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MoveOrderData} 的单元测试：永久 / 限时的判定、到期判断与
 * gameTime 与 tick 基准的换算（跨重启恢复用）。
 */
class MoveOrderDataTest {
    private static final UUID MAID = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();

    private static MoveOrderData timed(long expireAt, long created) {
        return MoveOrderData.timed(MAID, OWNER, MoveAction.GOTO, null,
                0, 0, 0, 0, expireAt, created, null);
    }

    @Test
    void permanent_neverExpires() {
        MoveOrderData order = MoveOrderData.permanent(MAID, OWNER, MoveAction.STAY, null,
                0, 0, 0, 0, 1000L, null);
        assertEquals(MoveOrderData.PERMANENT, order.expireAtGameTick());
        assertTrue(order.isPermanent());
        assertFalse(order.isExpired(0L));
        assertFalse(order.isExpired(Long.MAX_VALUE / 2));
        assertEquals(0L, order.remainingTicks(999999L));
        assertEquals(0, order.remainingSeconds(999999L));
    }

    @Test
    void timed_expiryIsInclusive() {
        MoveOrderData order = timed(5000L, 1000L);
        assertFalse(order.isPermanent());
        assertFalse(order.isExpired(4999L));
        assertTrue(order.isExpired(5000L));
        assertTrue(order.isExpired(5001L));
    }

    @Test
    void remainingTicks_usesGameTimeDifference() {
        MoveOrderData order = timed(5000L, 1000L);
        assertEquals(4000L, order.remainingTicks(1000L));
        assertEquals(1L, order.remainingTicks(4999L));
        assertEquals(0L, order.remainingTicks(5000L));
        // 已经过期时不返回负数（调用方可能晚一个处理周期才看到）
        assertEquals(0L, order.remainingTicks(99999L));
    }

    /**
     * 跨重启换算：重启前 1000 tick 登记、5000 tick 到期；重启后 gameTime 仍是世界时间
     * （不会回到 0），所以"剩余 = 到期 gameTime - 现在 gameTime"直接成立。
     */
    @Test
    void remaining_afterRestart_keepsTheAbsoluteExpireTime() {
        MoveOrderData order = timed(5000L, 1000L);
        long afterRestart = 4200L;
        assertEquals(800L, order.remainingTicks(afterRestart));
        assertEquals(40, order.remainingSeconds(afterRestart));
    }

    @Test
    void remainingSeconds_roundsUp() {
        // 20 tick = 1 秒；多出 1 tick 也算 1 秒（向上取整），所以 21 tick 是 2 秒
        assertEquals(1, timed(1020L, 1000L).remainingSeconds(1000L));
        assertEquals(2, timed(1021L, 1000L).remainingSeconds(1000L));
        assertEquals(2, timed(1040L, 1000L).remainingSeconds(1000L));
        assertEquals(60, timed(1200L, 0L).remainingSeconds(0L));
    }

    @Test
    void withExpireAtGameTick_keepsEverythingElse() {
        FollowSuspension suspension = new FollowSuspension(true, true, 1L, 2L, 3L, 4.0F);
        MoveOrderData order = MoveOrderData.timed(MAID, OWNER, MoveAction.STAY, null,
                0, 0, 0, 0, 5000L, 1000L, suspension);
        MoveOrderData moved = order.withExpireAtGameTick(9000L);

        assertEquals(9000L, moved.expireAtGameTick());
        assertEquals(1000L, moved.createdGameTick());
        assertEquals(suspension, moved.followSuspension());
        assertEquals(MoveAction.STAY, moved.action());
    }

    @Test
    void withSuspension_replacesOnlyTheRecord() {
        MoveOrderData order = timed(5000L, 1000L);
        FollowSuspension suspension = new FollowSuspension(false, false, 0L, 0L, 0L, -1.0F);
        MoveOrderData suspended = order.withSuspension(suspension);
        assertSame(suspension, suspended.followSuspension());
        assertEquals(5000L, suspended.expireAtGameTick());
        assertEquals(MAID, suspended.maidId());
    }
}
