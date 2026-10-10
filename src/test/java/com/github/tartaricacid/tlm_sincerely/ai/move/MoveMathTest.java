package com.github.tartaricacid.tlm_sincerely.ai.move;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MoveMath} 的单元测试：远离点计算、到位/需要移动判定与参数规整。
 * 全部为纯数学，不依赖 Minecraft。
 */
class MoveMathTest {
    private static final double EPSILON = 1.0E-6D;

    // ===== retreatPoint：远离点计算 =====

    @Test
    void retreatPoint_isOnTheTargetToMaidRayAtDesiredDistance() {
        // 女仆在目标正 +x 方向 3 格，要求落在距目标 5 格处 → (15, 64, 0)
        MoveMath.Point point = MoveMath.retreatPoint(
                new MoveMath.Point(13.0D, 64.0D, 0.0D),
                new MoveMath.Point(10.0D, 64.0D, 0.0D),
                5.0D);
        assertNotNull(point);
        assertEquals(15.0D, point.x(), EPSILON);
        assertEquals(64.0D, point.y(), EPSILON);
        assertEquals(0.0D, point.z(), EPSILON);
        assertEquals(5.0D, horizontalDistance(point, new MoveMath.Point(10.0D, 64.0D, 0.0D)), EPSILON);
    }

    @Test
    void retreatPoint_worksDiagonally() {
        // dx=3, dz=4 → 水平距离 5；要求 10 格 → 方向单位向量 (0.6, 0.8) 放大 10
        MoveMath.Point point = MoveMath.retreatPoint(
                new MoveMath.Point(13.0D, 64.0D, 4.0D),
                new MoveMath.Point(10.0D, 64.0D, 0.0D),
                10.0D);
        assertNotNull(point);
        assertEquals(16.0D, point.x(), EPSILON);
        assertEquals(8.0D, point.z(), EPSILON);
        assertEquals(10.0D, horizontalDistance(point, new MoveMath.Point(10.0D, 64.0D, 0.0D)), EPSILON);
    }

    @Test
    void retreatPoint_keepsMaidY() {
        MoveMath.Point point = MoveMath.retreatPoint(
                new MoveMath.Point(13.0D, 70.5D, 0.0D),
                new MoveMath.Point(10.0D, 64.0D, 0.0D),
                4.0D);
        assertNotNull(point);
        assertEquals(70.5D, point.y(), EPSILON);
        // 水平距离用 dx/dz 计算，与 y 无关
        assertEquals(4.0D, horizontalDistance(point, new MoveMath.Point(10.0D, 64.0D, 0.0D)), EPSILON);
    }

    @Test
    void retreatPoint_targetFurtherThanMaid_stillMovesAway() {
        // 目标在女仆 +x 方向（女仆比目标更远离原点），远离点应落在"继续往 +x"
        MoveMath.Point point = MoveMath.retreatPoint(
                new MoveMath.Point(12.0D, 64.0D, 0.0D),
                new MoveMath.Point(10.0D, 64.0D, 0.0D),
                2.0D);
        assertNotNull(point);
        assertEquals(12.0D, point.x(), EPSILON);
    }

    @Test
    void retreatPoint_horizontalOverlap_returnsNull() {
        // 只有 y 不同视为水平重合：判断不出远离方向
        assertNull(MoveMath.retreatPoint(
                new MoveMath.Point(10.0D, 70.0D, 0.0D),
                new MoveMath.Point(10.0D, 64.0D, 0.0D),
                5.0D));
        assertNull(MoveMath.retreatPoint(
                new MoveMath.Point(10.0D, 64.0D, 0.0D),
                new MoveMath.Point(10.0D, 64.0D, 0.0D),
                5.0D));
    }

    @Test
    void retreatPoint_nullInputs_returnNull() {
        assertNull(MoveMath.retreatPoint(null, new MoveMath.Point(0.0D, 0.0D, 0.0D), 5.0D));
        assertNull(MoveMath.retreatPoint(new MoveMath.Point(0.0D, 0.0D, 0.0D), null, 5.0D));
    }

    // ===== 判定 =====

    @Test
    void needsApproach_usesDistancePlusSlack() {
        // distance=3 → 阈值 4 格
        assertTrue(MoveMath.needsApproach(16.25D, 3.0D));   // 4.03 格
        assertFalse(MoveMath.needsApproach(16.0D, 3.0D));   // 正好 4 格
        assertFalse(MoveMath.needsApproach(1.0D, 3.0D));
    }

    @Test
    void needsRetreat_usesDistance() {
        assertTrue(MoveMath.needsRetreat(255.0D, 16.0D));   // 15.97 格
        assertFalse(MoveMath.needsRetreat(256.0D, 16.0D));  // 正好 16 格
        assertFalse(MoveMath.needsRetreat(400.0D, 16.0D));
    }

    @Test
    void hasArrived_usesArriveRadius() {
        assertTrue(MoveMath.hasArrived(3.9D));
        assertFalse(MoveMath.hasArrived(4.0D));
        assertFalse(MoveMath.hasArrived(100.0D));
    }

    // ===== 参数规整 =====

    @Test
    void clampDistance_keepsValueInRange() {
        assertEquals(3.0D, MoveMath.clampDistance(3.0D, 16.0D), EPSILON);
        assertEquals(1.0D, MoveMath.clampDistance(0.0D, 16.0D), EPSILON);
        assertEquals(1.0D, MoveMath.clampDistance(-5.0D, 16.0D), EPSILON);
        assertEquals(64.0D, MoveMath.clampDistance(200.0D, 16.0D), EPSILON);
    }

    @Test
    void clampDistance_nanUsesFallback() {
        assertEquals(16.0D, MoveMath.clampDistance(Double.NaN, 16.0D), EPSILON);
        assertEquals(5.0D, MoveMath.clampDistance(Double.NaN, 5.0D), EPSILON);
        assertEquals(3.0D, MoveMath.clampDistance(Double.POSITIVE_INFINITY, 3.0D), EPSILON);
    }

    @Test
    void clampDistance_nanFallbackStillClamped() {
        // 非距离动作传 0 作为缺省值：结果被夹到最小值，调用方不会拿到非法距离
        assertEquals(MoveMath.MIN_DISTANCE, MoveMath.clampDistance(Double.NaN, 0.0D), EPSILON);
    }

    // ===== 可站立点采样 =====

    @Test
    void standableOffsets_firstIsOrigin_andNoDuplicates() {
        List<MoveMath.Offset> offsets = MoveMath.standableOffsets();
        assertFalse(offsets.isEmpty());
        assertEquals(new MoveMath.Offset(0, 0, 0), offsets.get(0));
        assertEquals(offsets.size(), offsets.stream().distinct().count());
    }

    @Test
    void standableOffsets_returnsFreshList() {
        List<MoveMath.Offset> first = MoveMath.standableOffsets();
        first.clear();
        assertFalse(MoveMath.standableOffsets().isEmpty());
    }

    private static double horizontalDistance(MoveMath.Point a, MoveMath.Point b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
