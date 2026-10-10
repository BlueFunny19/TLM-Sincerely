package com.github.tartaricacid.tlm_sincerely.ai.move;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MoveAction#suspendsFollow} 与 {@link MoveDescribe} 的单元测试：
 * "哪些指令需要暂停跟随"的规则，以及提醒 / 上下文里动作描述的拼装。
 */
class MoveSuspensionRuleTest {
    @Test
    void suspendsFollow_stayGotoRetreatAlways() {
        assertTrue(MoveAction.STAY.suspendsFollow(false));
        assertTrue(MoveAction.STAY.suspendsFollow(true));
        assertTrue(MoveAction.GOTO.suspendsFollow(false));
        assertTrue(MoveAction.GOTO.suspendsFollow(true));
        assertTrue(MoveAction.RETREAT.suspendsFollow(false));
        assertTrue(MoveAction.RETREAT.suspendsFollow(true));
    }

    @Test
    void suspendsFollow_approachAndGuardOnlyWhenTargetIsNotTheOwner() {
        assertFalse(MoveAction.APPROACH.suspendsFollow(true));
        assertTrue(MoveAction.APPROACH.suspendsFollow(false));
        assertFalse(MoveAction.GUARD.suspendsFollow(true));
        assertTrue(MoveAction.GUARD.suspendsFollow(false));
    }

    @Test
    void suspendsFollow_isFalseForNonOrders() {
        assertFalse(MoveAction.CANCEL.suspendsFollow(true));
        assertFalse(MoveAction.GO_HOME.suspendsFollow(true));
    }

    @Test
    void isOrder_and_acceptsDuration_matchTheToolContract() {
        assertTrue(MoveAction.STAY.isOrder());
        assertFalse(MoveAction.GO_HOME.isOrder());
        assertTrue(MoveAction.GO_HOME.acceptsDuration() == false);
        assertTrue(MoveAction.CANCEL.acceptsDuration() == false);
        assertTrue(MoveAction.STAY.acceptsDuration());
        assertTrue(MoveAction.GOTO.acceptsDuration());
    }

    @Test
    void needsCoords_onlyForGoto() {
        assertTrue(MoveAction.GOTO.needsCoords());
        assertFalse(MoveAction.STAY.needsCoords());
        assertFalse(MoveAction.GO_HOME.needsCoords());
        assertFalse(MoveAction.CANCEL.needsCoords());
    }

    @Test
    void describe_usesOwnerNameForOwnerTarget() {
        MoveOrderData order = MoveOrderData.timed(UUID.randomUUID(), UUID.randomUUID(),
                MoveAction.GUARD, UUID.randomUUID(), 0, 0, 0, 5.0D, 100L, 0L, null);
        assertEquals("守护主人", MoveDescribe.describe(order, MoveText.ownerName(true), true));
        assertEquals("guarding the owner", MoveDescribe.describe(order, MoveText.ownerName(false), false));
    }

    @Test
    void describe_usesMaidNameForMaidTarget() {
        MoveOrderData order = MoveOrderData.timed(UUID.randomUUID(), UUID.randomUUID(),
                MoveAction.APPROACH, UUID.randomUUID(), 0, 0, 0, 3.0D, 100L, 0L, null);
        assertEquals("走近澪", MoveDescribe.describe(order, "澪", true));
        assertEquals("approaching Mio", MoveDescribe.describe(order, "Mio", false));
    }

    @Test
    void describe_gotoUsesCoordinates() {
        MoveOrderData order = MoveOrderData.timed(UUID.randomUUID(), UUID.randomUUID(),
                MoveAction.GOTO, null, 100.0D, 64.0D, -20.0D, 0.0D, 100L, 0L, null);
        assertEquals("在(100.0, 64.0, -20.0)等着", MoveDescribe.describe(order, null, true));
        assertEquals("waiting at (100.0, 64.0, -20.0)", MoveDescribe.describe(order, null, false));
    }

    @Test
    void describe_stayIgnoresTarget() {
        MoveOrderData order = MoveOrderData.timed(UUID.randomUUID(), UUID.randomUUID(),
                MoveAction.STAY, null, 0, 0, 0, 0, 100L, 0L, null);
        assertEquals("留在原地", MoveDescribe.describe(order, "whatever", true));
        assertEquals("staying where I am", MoveDescribe.describe(order, null, false));
    }

    @Test
    void describe_unresolvedTargetFallsBackToOwnerWording() {
        MoveOrderData order = MoveOrderData.timed(UUID.randomUUID(), UUID.randomUUID(),
                MoveAction.RETREAT, UUID.randomUUID(), 0, 0, 0, 16.0D, 100L, 0L, null);
        assertEquals("远离主人", MoveDescribe.describe(order, null, true));
    }

    @Test
    void coordText_isStable() {
        assertEquals("(1.0, 2.5, -3.0)", MoveDescribe.coordText(1.0D, 2.5D, -3.0D));
    }

    @Test
    void horizontalSearchPoints_startsAtCenterAndGrowsByRing() {
        List<MoveMath.Point> points = MoveMath.horizontalSearchPoints(new MoveMath.Point(10.0D, 64.0D, 20.0D), 2);
        // ring 0: 1 个；ring 1: 8 个；ring 2: 16 个 → 25 个
        assertEquals(25, points.size());
        assertEquals(new MoveMath.Point(10.0D, 64.0D, 20.0D), points.get(0));
        assertEquals(64.0D, points.get(1).y());
        assertTrue(points.contains(new MoveMath.Point(12.0D, 64.0D, 22.0D)));
        assertTrue(points.contains(new MoveMath.Point(8.0D, 64.0D, 18.0D)));
    }

    @Test
    void horizontalSearchPoints_radiusZeroIsJustTheCenter() {
        List<MoveMath.Point> points = MoveMath.horizontalSearchPoints(new MoveMath.Point(1.0D, 2.0D, 3.0D), 0);
        assertEquals(1, points.size());
        assertEquals(new MoveMath.Point(1.0D, 2.0D, 3.0D), points.get(0));
    }

    @Test
    void horizontalSearchPoints_negativeRadiusIsClamped() {
        assertEquals(1, MoveMath.horizontalSearchPoints(new MoveMath.Point(0, 0, 0), -3).size());
        assertEquals(0, MoveMath.horizontalSearchPoints(null, 4).size());
    }
}
