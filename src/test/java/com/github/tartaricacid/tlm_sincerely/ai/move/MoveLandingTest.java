package com.github.tartaricacid.tlm_sincerely.ai.move;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MoveMath#landingCandidates} 的单元测试：传送兜底的落点环（approach / guard）
 * 与坐标落点（goto / retreat）。纯数学，不依赖 Minecraft。
 */
class MoveLandingTest {
    private static final double EPSILON = 1.0E-6D;

    private static final MoveMath.Point TARGET = new MoveMath.Point(10.0D, 64.0D, 0.0D);

    // ===== approach / guard：目标周围的环 =====

    @Test
    void approach_firstCandidateIsOnTheMaidsOriginalDirection() {
        // 女仆在目标 +x 方向 20 格；距离 5 → 第一个候选应落在 (15, 64, 0)
        List<MoveMath.Point> candidates = MoveMath.landingCandidates(
                new MoveMath.Point(30.0D, 70.0D, 0.0D), TARGET, MoveAction.APPROACH, 5.0D);
        assertEquals(8, candidates.size());
        MoveMath.Point first = candidates.get(0);
        assertEquals(15.0D, first.x(), EPSILON);
        assertEquals(64.0D, first.y(), EPSILON);
        assertEquals(0.0D, first.z(), EPSILON);
    }

    @Test
    void approach_allCandidatesAreOnTheRingAtDistance() {
        MoveMath.Point maid = new MoveMath.Point(30.0D, 70.0D, 0.0D);
        for (MoveMath.Point candidate : MoveMath.landingCandidates(maid, TARGET, MoveAction.APPROACH, 5.0D)) {
            assertEquals(5.0D, horizontalDistance(candidate, TARGET), EPSILON);
            assertEquals(TARGET.y(), candidate.y(), EPSILON);
        }
    }

    @Test
    void guard_usesTheSameRingAsApproach() {
        MoveMath.Point maid = new MoveMath.Point(0.0D, 64.0D, 10.0D);
        List<MoveMath.Point> guard = MoveMath.landingCandidates(maid, TARGET, MoveAction.GUARD, 5.0D);
        List<MoveMath.Point> approach = MoveMath.landingCandidates(maid, TARGET, MoveAction.APPROACH, 5.0D);
        assertEquals(approach, guard);
        // 女仆在目标 +z 方向：第一个候选应落在"目标指向女仆"的射线上
        double dirX = maid.x() - TARGET.x();
        double dirZ = maid.z() - TARGET.z();
        double norm = Math.hypot(dirX, dirZ);
        assertEquals(TARGET.x() + 5.0D * dirX / norm, guard.get(0).x(), EPSILON);
        assertEquals(TARGET.z() + 5.0D * dirZ / norm, guard.get(0).z(), EPSILON);
        assertEquals(5.0D, horizontalDistance(guard.get(0), TARGET), EPSILON);
    }

    @Test
    void ring_candidatesAreDistinctDirections() {
        List<MoveMath.Point> candidates = MoveMath.landingCandidates(
                new MoveMath.Point(30.0D, 64.0D, 0.0D), TARGET, MoveAction.GUARD, 5.0D);
        long distinct = candidates.stream()
                .map(point -> Math.round(point.x() * 1000) + ":" + Math.round(point.z() * 1000))
                .distinct()
                .count();
        assertEquals(candidates.size(), distinct);
    }

    @Test
    void ring_maidHorizontallyOnTarget_startsAtPositiveX() {
        List<MoveMath.Point> candidates = MoveMath.landingCandidates(
                new MoveMath.Point(10.0D, 70.0D, 0.0D), TARGET, MoveAction.APPROACH, 3.0D);
        assertEquals(13.0D, candidates.get(0).x(), EPSILON);
        assertEquals(0.0D, candidates.get(0).z(), EPSILON);
    }

    @Test
    void ring_nullMaid_startsWithPositiveX() {
        List<MoveMath.Point> candidates = MoveMath.landingCandidates(null, TARGET, MoveAction.APPROACH, 3.0D);
        assertEquals(8, candidates.size());
        assertEquals(13.0D, candidates.get(0).x(), EPSILON);
    }

    @Test
    void ring_distanceIsClampedToTheLegalRange() {
        // 0 与 200 都会被夹到 1 / 64
        List<MoveMath.Point> tooSmall = MoveMath.landingCandidates(
                new MoveMath.Point(30.0D, 64.0D, 0.0D), TARGET, MoveAction.APPROACH, 0.0D);
        assertEquals(1.0D, horizontalDistance(tooSmall.get(0), TARGET), EPSILON);
        List<MoveMath.Point> tooLarge = MoveMath.landingCandidates(
                new MoveMath.Point(30.0D, 64.0D, 0.0D), TARGET, MoveAction.GUARD, 200.0D);
        assertEquals(64.0D, horizontalDistance(tooLarge.get(0), TARGET), EPSILON);
    }

    // ===== goto / retreat：坐标落点 =====

    @Test
    void goto_usesTheDestinationItself() {
        List<MoveMath.Point> candidates = MoveMath.landingCandidates(
                new MoveMath.Point(30.0D, 64.0D, 0.0D), TARGET, MoveAction.GOTO, 0.0D);
        assertEquals(List.of(TARGET), candidates);
    }

    @Test
    void retreat_usesTheDestinationItself() {
        MoveMath.Point retreatPoint = new MoveMath.Point(20.0D, 65.0D, -3.0D);
        List<MoveMath.Point> candidates = MoveMath.landingCandidates(
                new MoveMath.Point(13.0D, 64.0D, 0.0D), retreatPoint, MoveAction.RETREAT, 16.0D);
        assertEquals(List.of(retreatPoint), candidates);
    }

    @Test
    void nullDestination_returnsEmptyList() {
        assertTrue(MoveMath.landingCandidates(TARGET, null, MoveAction.APPROACH, 3.0D).isEmpty());
        assertTrue(MoveMath.landingCandidates(TARGET, null, MoveAction.GOTO, 0.0D).isEmpty());
    }

    @Test
    void nullAction_isTreatedAsCoordinateDestination() {
        List<MoveMath.Point> candidates = MoveMath.landingCandidates(TARGET, TARGET, null, 3.0D);
        assertEquals(List.of(TARGET), candidates);
    }

    @Test
    void returnsFreshList() {
        List<MoveMath.Point> first = MoveMath.landingCandidates(TARGET, TARGET, MoveAction.APPROACH, 3.0D);
        first.clear();
        assertEquals(8, MoveMath.landingCandidates(TARGET, TARGET, MoveAction.APPROACH, 3.0D).size());
    }

    private static double horizontalDistance(MoveMath.Point a, MoveMath.Point b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
