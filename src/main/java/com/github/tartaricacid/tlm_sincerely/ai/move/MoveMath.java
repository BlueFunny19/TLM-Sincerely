package com.github.tartaricacid.tlm_sincerely.ai.move;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code maid_move} 的移动几何与判定，纯 Java、不引用任何 Minecraft 类型，便于单元测试。
 * <p>
 * 这里的常量同时定义了指令的"到位/需要移动"口径，改数值前先看
 * {@code MoveDirectiveManager} 里对应的用法。
 */
public final class MoveMath {
    /** distance 参数的允许范围（格）。 */
    public static final double MIN_DISTANCE = 1.0D;
    public static final double MAX_DISTANCE = 64.0D;

    /** approach / guard 的距离宽容：超过 distance + 该值才重新下发移动，避免到位后原地抖动。 */
    public static final double APPROACH_SLACK = 1.0D;

    /** retreat 的目标点取 distance + 该值，避免刚好贴在阈值上来回走。 */
    public static final double RETREAT_EXTRA = 2.0D;

    /** goto 的到达判定半径（格）。 */
    public static final double ARRIVE_RADIUS = 2.0D;

    /** 水平方向长度小于该值视为"与目标重合"，无法判断远离方向。 */
    private static final double EPSILON = 1.0E-4D;

    /**
     * approach / guard 传送落点的环上采样角度（相对"女仆原来所在方向"的偏转，单位：度）。
     * 顺序固定：先正对女仆原来的方向，再左右依次偏转，最后是正对面——越靠前越符合"优先靠近女仆原来的方向"。
     */
    private static final double[] LANDING_RING_ANGLES = {0.0D, 45.0D, -45.0D, 90.0D, -90.0D, 135.0D, -135.0D, 180.0D};

    /**
     * 找可站立位置的采样偏移（dx, dy, dz），顺序固定：先原地，再水平一圈与两圈，最后上下层。
     * 顺序固定是为了让"找位置"的行为可复现、可测试。
     */
    private static final int[][] STANDABLE_OFFSETS = {
            {0, 0, 0},
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
            {2, 0, 0}, {-2, 0, 0}, {0, 0, 2}, {0, 0, -2},
            {2, 0, 2}, {2, 0, -2}, {-2, 0, 2}, {-2, 0, -2},
            {0, 1, 0}, {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
            {0, -1, 0}, {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1},
    };

    private MoveMath() {
    }

    /** 不依赖 Minecraft 的三维点。 */
    public record Point(double x, double y, double z) {
    }

    /** 采样偏移。 */
    public record Offset(int dx, int dy, int dz) {
    }

    /** 距离参数规整：NaN / 无穷 / 越界时回落到合法范围，非距离动作由调用方传 {@code defaultDistance()}。 */
    public static double clampDistance(double value, double fallback) {
        double base = (Double.isNaN(value) || Double.isInfinite(value)) ? fallback : value;
        if (Double.isNaN(base) || Double.isInfinite(base)) {
            base = MIN_DISTANCE;
        }
        return Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, base));
    }

    /**
     * 指令持续秒数规整：没传（≤ 0，codec 的缺省哨兵是 -1）用默认值，传了的不超过上限。
     * 默认值本身也不会超过上限。
     */
    public static int resolveDurationSeconds(int requested, int defaultSeconds, int maxSeconds) {
        int seconds = requested <= 0 ? defaultSeconds : requested;
        return Math.max(1, Math.min(seconds, maxSeconds));
    }

    /**
     * 一条指令的时效：{@code permanent} 为 true 表示没有时限（直到玩家取消），否则 {@code seconds} 秒后自动结束。
     * {@code coercedToTimed} 为 true 表示玩家并没有要求限时，只是服务器不允许无时限的指令，才被改成了限时。
     */
    public record Timing(boolean permanent, int seconds, boolean coercedToTimed) {
    }

    /**
     * 指令时效的决定：<b>默认一直生效</b>，只有玩家明确要求临时（模型传了 {@code temporary = true}，
     * 或者给了 {@code duration_seconds}）才限时；限时没给秒数时用默认值。服务器不允许无时限的指令
     * （{@code allowPermanent = false}）时，没要求限时的指令也按默认时长限时，并标记 {@code coercedToTimed}
     * 让调用方告诉模型。
     */
    public static Timing resolveTiming(boolean temporaryFlag, int requestedSeconds, boolean allowPermanent,
                                       int defaultSeconds, int maxSeconds) {
        boolean wantsTemporary = temporaryFlag || requestedSeconds > 0;
        if (!wantsTemporary && allowPermanent) {
            return new Timing(true, 0, false);
        }
        return new Timing(false, resolveDurationSeconds(requestedSeconds, defaultSeconds, maxSeconds),
                !wantsTemporary);
    }

    /** 距离是否已超过 distance + {@link #APPROACH_SLACK}（超过才需要重新下发靠近移动）。 */
    public static boolean needsApproach(double distanceSqr, double distance) {
        double limit = distance + APPROACH_SLACK;
        return distanceSqr > limit * limit;
    }

    /** 距离是否已小于 distance（小于才需要远离）。 */
    public static boolean needsRetreat(double distanceSqr, double distance) {
        return distanceSqr < distance * distance;
    }

    /** goto 是否已到达（距离小于 {@link #ARRIVE_RADIUS}）。 */
    public static boolean hasArrived(double distanceSqr) {
        return distanceSqr < ARRIVE_RADIUS * ARRIVE_RADIUS;
    }

    /**
     * 远离点计算：从 {@code target} 指向 {@code maid} 的水平方向延长 {@code desiredDistance} 格后的点。
     * <p>
     * y 取女仆自己的高度（保持在同一层走动）；两者水平位置重合时没有"远离方向"可言，返回 null，
     * 调用方应保持不动。
     */
    @Nullable
    public static Point retreatPoint(Point maid, Point target, double desiredDistance) {
        if (maid == null || target == null) {
            return null;
        }
        double dx = maid.x() - target.x();
        double dz = maid.z() - target.z();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < EPSILON) {
            return null;
        }
        double scale = desiredDistance / length;
        return new Point(target.x() + dx * scale, maid.y(), target.z() + dz * scale);
    }

    /**
     * 传送兜底的落点候选（按优先级排序，调用方逐个交给"找可站立位置"的逻辑试）。
     *
     * <p>approach / guard：{@code destination} 是目标实体位置，候选是目标周围 {@code distance} 格环上的
     * 8 个点，第一个正对女仆原来所在的方向（"优先靠近女仆原来的方向"），再左右依次偏转。
     * 环的半径先按 1~64 夹紧；目标与女仆水平重合时没有"原来的方向"，就从 +x 方向开始。
     *
     * <p>goto / retreat：候选只有 {@code destination} 本身（goto 坐标 / 算出来的后退点），
     * 附近的偏移交给 {@link #standableOffsets()} 的采样表处理。
     *
     * <p>纯函数，便于单测；返回新列表，调用方可安全遍历。
     */
    public static List<Point> landingCandidates(@Nullable Point maid, @Nullable Point destination,
                                                @Nullable MoveAction action, double distance) {
        List<Point> candidates = new ArrayList<>();
        if (destination == null) {
            return candidates;
        }
        if (action != MoveAction.APPROACH && action != MoveAction.GUARD) {
            candidates.add(destination);
            return candidates;
        }
        double radius = clampDistance(distance, MIN_DISTANCE);
        // 水平重合时 atan2(0, 0) 就是 0，从 +x 方向开始，不需要特判
        double baseAngle = maid == null
                ? 0.0D
                : Math.atan2(maid.z() - destination.z(), maid.x() - destination.x());
        for (double degreeOffset : LANDING_RING_ANGLES) {
            double angle = baseAngle + Math.toRadians(degreeOffset);
            candidates.add(new Point(
                    destination.x() + Math.cos(angle) * radius,
                    destination.y(),
                    destination.z() + Math.sin(angle) * radius));
        }
        return candidates;
    }

    /**
     * 以 {@code center} 为中心、在半径 {@code radius} 内的水平搜索点（每格一个，按"离中心近优先"排序）。
     *
     * <p>用途：{@code go_home} 要在工作坐标附近找落脚点，而 {@link #standableOffsets()} 只覆盖
     * "原地 ±2 格"；把工作坐标当作搜索中心，再用这张表逐个交给可站立判定，就能在更大的范围里找位置。
     * 纯函数，便于单测。
     */
    public static List<Point> horizontalSearchPoints(Point center, int radius) {
        List<Point> points = new ArrayList<>();
        if (center == null) {
            return points;
        }
        int r = Math.max(0, radius);
        for (int ring = 0; ring <= r; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    // 只取当前这一圈，避免重复（内圈已经在前面的迭代里加过）
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    points.add(new Point(center.x() + dx, center.y(), center.z() + dz));
                }
            }
        }
        return points;
    }

    /** 可站立位置的采样偏移列表（每次返回新列表，调用方可安全遍历）。 */
    public static List<Offset> standableOffsets() {
        List<Offset> result = new ArrayList<>(STANDABLE_OFFSETS.length);
        for (int[] offset : STANDABLE_OFFSETS) {
            result.add(new Offset(offset[0], offset[1], offset[2]));
        }
        return result;
    }
}
