package com.github.tartaricacid.tlm_sincerely.ai.move;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MoveOrders} 的单元测试：单只女仆一条指令、覆盖、每个主人的数量上限、到期清理、
 * 以及永久指令（不受 removeExpired 影响，但同样占主人名额）。
 * 时间由测试直接给出 gameTime，不依赖真实时钟。
 */
class MoveOrdersTest {
    private static final int MAX_PER_OWNER = 3;

    private final UUID owner = UUID.randomUUID();
    private final UUID otherOwner = UUID.randomUUID();
    private final UUID maidA = UUID.randomUUID();
    private final UUID maidB = UUID.randomUUID();
    private final UUID maidC = UUID.randomUUID();
    private final UUID maidD = UUID.randomUUID();

    private static MoveOrderData order(UUID maid, UUID owner, long expireTick) {
        return MoveOrderData.timed(maid, owner, MoveAction.APPROACH, null, 0.0D, 0.0D, 0.0D, 3.0D,
                expireTick, 0L, null);
    }

    private static MoveOrderData permanentOrder(UUID maid, UUID owner) {
        return MoveOrderData.permanent(maid, owner, MoveAction.STAY, null, 0.0D, 0.0D, 0.0D, 0.0D, 0L, null);
    }

    @Test
    void firstOrder_isAccepted() {
        MoveOrders orders = new MoveOrders();
        assertEquals(MoveOrders.PutResult.OK, orders.put(order(maidA, owner, 100L), MAX_PER_OWNER));
        assertTrue(orders.isActive(maidA));
        assertEquals(1, orders.size());
        assertEquals(1, orders.countForOwner(owner));
    }

    @Test
    void oneOrderPerMaid_newOrderReplacesOld() {
        MoveOrders orders = new MoveOrders();
        orders.put(order(maidA, owner, 100L), MAX_PER_OWNER);
        MoveOrderData replacement = MoveOrderData.timed(
                maidA, owner, MoveAction.GUARD, maidB, 0.0D, 0.0D, 0.0D, 5.0D, 200L, 0L, null);
        assertEquals(MoveOrders.PutResult.OK, orders.put(replacement, MAX_PER_OWNER));

        assertEquals(1, orders.size());
        assertSame(replacement, orders.get(maidA));
        assertEquals(MoveAction.GUARD, orders.get(maidA).action());
        assertEquals(maidB, orders.get(maidA).targetId());
    }

    @Test
    void replacingOwnOrder_doesNotConsumeAnotherSlot() {
        MoveOrders orders = new MoveOrders();
        assertEquals(MoveOrders.PutResult.OK, orders.put(order(maidA, owner, 100L), 1));
        // 上限为 1，但覆盖自己不算新增
        assertEquals(MoveOrders.PutResult.OK, orders.put(order(maidA, owner, 200L), 1));
        assertEquals(1, orders.size());
    }

    @Test
    void ownerLimit_rejectsNewMaidAndKeepsExisting() {
        MoveOrders orders = new MoveOrders();
        orders.put(order(maidA, owner, 100L), MAX_PER_OWNER);
        orders.put(order(maidB, owner, 100L), MAX_PER_OWNER);
        orders.put(order(maidC, owner, 100L), MAX_PER_OWNER);
        assertEquals(MAX_PER_OWNER, orders.countForOwner(owner));

        assertEquals(MoveOrders.PutResult.LIMIT_REACHED, orders.put(order(maidD, owner, 100L), MAX_PER_OWNER));
        assertFalse(orders.isActive(maidD));
        assertEquals(MAX_PER_OWNER, orders.size());
        // 已有指令不受影响
        assertTrue(orders.isActive(maidA));
    }

    @Test
    void ownerLimit_isPerOwner() {
        MoveOrders orders = new MoveOrders();
        orders.put(order(maidA, owner, 100L), 1);
        assertEquals(MoveOrders.PutResult.LIMIT_REACHED, orders.put(order(maidB, owner, 100L), 1));
        assertEquals(MoveOrders.PutResult.OK, orders.put(order(maidB, otherOwner, 100L), 1));
    }

    @Test
    void ownerLimit_removingFreesASlot() {
        MoveOrders orders = new MoveOrders();
        orders.put(order(maidA, owner, 100L), 1);
        assertEquals(MoveOrders.PutResult.LIMIT_REACHED, orders.put(order(maidB, owner, 100L), 1));
        assertTrue(orders.remove(maidA));
        assertEquals(MoveOrders.PutResult.OK, orders.put(order(maidB, owner, 100L), 1));
    }

    @Test
    void ownerLimit_isClampedToAtLeastOne() {
        MoveOrders orders = new MoveOrders();
        orders.put(order(maidA, owner, 100L), 0);
        assertEquals(MoveOrders.PutResult.LIMIT_REACHED, orders.put(order(maidB, owner, 100L), 0));
    }

    @Test
    void removeExpired_removesOnlyDueOrders() {
        MoveOrders orders = new MoveOrders();
        orders.put(order(maidA, owner, 100L), MAX_PER_OWNER);
        orders.put(order(maidB, owner, 200L), MAX_PER_OWNER);

        assertEquals(List.of(), orders.removeExpired(99L));
        assertEquals(2, orders.size());

        // expireTick <= nowTick 即到期
        assertEquals(List.of(maidA), orders.removeExpired(100L));
        assertFalse(orders.isActive(maidA));
        assertTrue(orders.isActive(maidB));

        assertEquals(List.of(maidB), orders.removeExpired(1000L));
        assertEquals(0, orders.size());
    }

    @Test
    void removeExpired_neverRemovesPermanentOrders() {
        MoveOrders orders = new MoveOrders();
        orders.put(permanentOrder(maidA, owner), MAX_PER_OWNER);
        orders.put(order(maidB, owner, 100L), MAX_PER_OWNER);

        assertEquals(List.of(maidB), orders.removeExpired(100L));
        assertTrue(orders.isActive(maidA));
        assertEquals(MoveOrderData.PERMANENT, orders.get(maidA).expireAtGameTick());
        assertEquals(List.of(), orders.removeExpired(Long.MAX_VALUE / 2));
        assertTrue(orders.isActive(maidA));
    }

    @Test
    void permanentOrders_occupyTheOwnerSlot() {
        MoveOrders orders = new MoveOrders();
        orders.put(permanentOrder(maidA, owner), 1);
        assertEquals(1, orders.countForOwner(owner));
        assertEquals(MoveOrders.PutResult.LIMIT_REACHED, orders.put(order(maidB, owner, 100L), 1));
    }

    @Test
    void permanentAndTimed_shareTheSameCount() {
        MoveOrders orders = new MoveOrders();
        orders.put(permanentOrder(maidA, owner), 2);
        orders.put(order(maidB, owner, 100L), 2);
        assertEquals(2, orders.countForOwner(owner));
        assertEquals(MoveOrders.PutResult.LIMIT_REACHED, orders.put(order(maidC, owner, 100L), 2));
    }

    @Test
    void permanentOrder_canReplaceATimedOneForFree() {
        MoveOrders orders = new MoveOrders();
        orders.put(order(maidA, owner, 100L), 1);
        assertEquals(MoveOrders.PutResult.OK, orders.put(permanentOrder(maidA, owner), 1));
        assertTrue(orders.get(maidA).isPermanent());
    }

    // ===== work_time（不移动、只放宽日程的指令） =====

    private static MoveOrderData workTimeOrder(UUID maid, UUID owner, long expireTick) {
        return MoveOrderData.timed(maid, owner, MoveAction.WORK_TIME, null, 0.0D, 0.0D, 0.0D, 0.0D,
                expireTick, 0L, null);
    }

    @Test
    void workTime_occupiesASlotAndExpiresLikeAnyTimedOrder() {
        MoveOrders orders = new MoveOrders();
        orders.put(workTimeOrder(maidA, owner, 100L), 1);
        assertEquals(1, orders.countForOwner(owner));
        assertEquals(MoveOrders.PutResult.LIMIT_REACHED, orders.put(order(maidB, owner, 100L), 1));

        assertEquals(List.of(maidA), orders.removeExpired(100L));
        assertFalse(orders.isActive(maidA));
    }

    @Test
    void workTime_permanent_isNeverRemoved() {
        MoveOrders orders = new MoveOrders();
        MoveOrderData permanent = MoveOrderData.permanent(maidA, owner, MoveAction.WORK_TIME, null,
                0.0D, 0.0D, 0.0D, 0.0D, 0L, null);
        orders.put(permanent, 1);

        assertEquals(List.of(), orders.removeExpired(Long.MAX_VALUE / 2));
        assertTrue(orders.isActive(maidA));
        assertTrue(orders.get(maidA).isPermanent());
        assertEquals(MoveOrders.PutResult.LIMIT_REACHED, orders.put(order(maidB, owner, 100L), 1));
    }

    @Test
    void workTime_canReplaceAMovementOrderForFreeAndBeReplaced() {
        MoveOrders orders = new MoveOrders();
        orders.put(order(maidA, owner, 100L), 1);
        assertEquals(MoveOrders.PutResult.OK, orders.put(workTimeOrder(maidA, owner, 200L), 1));
        assertEquals(MoveAction.WORK_TIME, orders.get(maidA).action());
        assertEquals(1, orders.size());

        // 反向：work_time 被移动指令覆盖，也不占额外名额
        MoveOrderData back = MoveOrderData.timed(maidA, owner, MoveAction.APPROACH, null,
                0.0D, 0.0D, 0.0D, 3.0D, 300L, 0L, null);
        assertEquals(MoveOrders.PutResult.OK, orders.put(back, 1));
        assertEquals(MoveAction.APPROACH, orders.get(maidA).action());
        assertEquals(1, orders.countForOwner(owner));
    }

    @Test
    void remove_unknownOrNull_isNoOp() {
        MoveOrders orders = new MoveOrders();
        orders.put(order(maidA, owner, 100L), MAX_PER_OWNER);
        assertFalse(orders.remove(maidB));
        assertFalse(orders.remove(null));
        assertEquals(1, orders.size());
    }

    @Test
    void getAndIsActive_tolerateNull() {
        MoveOrders orders = new MoveOrders();
        assertNull(orders.get(null));
        assertFalse(orders.isActive(null));
        assertEquals(0, orders.countForOwner(null));
    }

    @Test
    void put_nullOrder_isRejected() {
        MoveOrders orders = new MoveOrders();
        assertEquals(MoveOrders.PutResult.LIMIT_REACHED, orders.put(null, MAX_PER_OWNER));
        assertEquals(0, orders.size());
    }

    @Test
    void maidIds_isACopy() {
        MoveOrders orders = new MoveOrders();
        orders.put(order(maidA, owner, 100L), MAX_PER_OWNER);
        List<UUID> ids = orders.maidIds();
        ids.clear();
        assertEquals(1, orders.size());
    }

    @Test
    void clear_resetsEverything() {
        MoveOrders orders = new MoveOrders();
        orders.put(order(maidA, owner, 100L), MAX_PER_OWNER);
        orders.put(permanentOrder(maidB, owner), MAX_PER_OWNER);
        orders.clear();
        assertEquals(0, orders.size());
        assertFalse(orders.isActive(maidA));
        assertFalse(orders.isActive(maidB));
        assertEquals(0, orders.countForOwner(owner));
    }
}
