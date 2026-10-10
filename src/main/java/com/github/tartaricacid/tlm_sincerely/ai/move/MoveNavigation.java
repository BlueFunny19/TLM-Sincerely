package com.github.tartaricacid.tlm_sincerely.ai.move;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 移动指令用到的"走路 / 找落脚点 / 传送"小工具（无状态，只读写传入的女仆与世界）。
 *
 * <p><b>移动实现</b>：用 Brain 的 {@code WALK_TARGET} 记忆下发（实体目标走
 * {@link BehaviorUtils#setWalkAndLookTargetMemories}，坐标目标直接写 {@link WalkTarget}），
 * 与主模组自己的走动行为一致——TLM 的 {@code MaidFollowOwnerTask} / {@code MaidWalkToLivingEntityTask}
 * 都是这么做的，且 {@code MoveToTargetSink} 注册在女仆 Brain 的 CORE activity 上，任何时候都会消费该记忆。
 * 步速倍率取 {@code 0.5F}，与 {@code MaidFollowOwnerTask} 的构造参数一致。
 */
public final class MoveNavigation {
    /** 移动速度倍率，与 TLM {@code MaidFollowOwnerTask} 的构造参数一致。 */
    private static final float WALK_SPEED = 0.5F;

    /** 坐标移动的到位半径（格），与 {@link MoveMath#ARRIVE_RADIUS} 同口径。 */
    public static final int POINT_CLOSE_ENOUGH = 1;

    private MoveNavigation() {
    }

    /** 以 {@link BehaviorUtils} 走主模组的惯用路径：同时设置 WALK_TARGET 与 LOOK_TARGET。 */
    public static void walkToEntity(EntityMaid maid, @Nullable LivingEntity target, double closeEnough) {
        if (target == null) {
            return;
        }
        BehaviorUtils.setWalkAndLookTargetMemories(maid, target, WALK_SPEED, toCloseEnough(closeEnough));
    }

    public static void walkToPoint(EntityMaid maid, @Nullable Vec3 point, int closeEnough) {
        if (point == null) {
            return;
        }
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(point, WALK_SPEED, Math.max(1, closeEnough)));
    }

    private static int toCloseEnough(double distance) {
        return Math.max(1, (int) Math.round(distance));
    }

    /**
     * 女仆 Brain 里是否有原版寻路失败标记（{@code MoveToTargetSink#tryComputePath} 在
     * {@code path.canReach()} 为 false 且还没记过时写入，可走通时擦除）。
     * {@code Brain#hasMemoryValue} 对未注册的记忆类型安全返回 false，这里不额外兜异常。
     */
    public static boolean hasCantReachMemory(EntityMaid maid) {
        return maid.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    /**
     * 把女仆直接挪到指定落脚点，并照抄主模组传送后的收尾动作。
     *
     * <p>javap 证据（mapped TLM 1.5.3）：
     * {@code EntityMaid.maybeTeleportTo(LivingEntity,int,int,int)} 是主模组"主人太远时把女仆传送过去"的
     * 落地实现（公开入口是 {@code EntityMaid#teleportToOwner(LivingEntity)}：循环 10 次取 ±3 格随机偏移后
     * 调用它）。它通过私有的 {@code canTeleportTo(BlockPos)} 校验后依次执行：
     * {@code moveTo(x + 0.5, y, z + 0.5, getYRot(), getXRot())} →
     * {@code getNavigation().stop()} →
     * {@code eraseMemory(WALK_TARGET / LOOK_TARGET / ATTACK_TARGET / PATH)}。
     * {@code MaidFollowOwnerTask} 在 {@code teleportToOwner} 之后还会调用
     * {@code getNavigationManager().resetNavigation()}（切回地面导航、清掉游泳状态）。
     * 这里照抄同一套收尾动作，只是落点由我们自己按 {@link MoveMath#landingCandidates} 算。
     *
     * <p>没直接调 {@code teleportToOwner} 的原因：它要求目标是 {@link LivingEntity} 且落点只能在目标
     * ±3 格的随机位置（还强制与目标水平距离 ≥2 格），goto / retreat / go_home 的坐标目标用不上，
     * approach / guard 也没法"优先女仆原来的方向"。所以复用它内部的实现方式而不是它本身。
     */
    public static void teleportMaid(EntityMaid maid, BlockPos pos) {
        maid.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, maid.getYRot(), maid.getXRot());
        maid.getNavigation().stop();
        Brain<EntityMaid> brain = maid.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        brain.eraseMemory(MemoryModuleType.PATH);
        maid.getNavigationManager().resetNavigation();
    }

    /** 在 {@link MoveMath#standableOffsets()} 给出的偏移里找第一个可站立（下方有支撑、脚部与头部无碰撞）的位置。 */
    @Nullable
    public static BlockPos findStandable(ServerLevel level, MoveMath.Point point) {
        BlockPos base = BlockPos.containing(point.x(), point.y(), point.z());
        for (MoveMath.Offset offset : MoveMath.standableOffsets()) {
            BlockPos candidate = base.offset(offset.dx(), offset.dy(), offset.dz());
            if (isStandable(level, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (!belowState.isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    @Nullable
    public static MoveMath.Point toPoint(@Nullable Vec3 vec) {
        return vec == null ? null : new MoveMath.Point(vec.x(), vec.y(), vec.z());
    }
}
