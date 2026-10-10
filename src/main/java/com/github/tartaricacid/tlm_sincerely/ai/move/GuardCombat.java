package com.github.tartaricacid.tlm_sincerely.ai.move;

import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 守护指令里"把威胁目标的敌对生物设为攻击目标"的那一部分（无状态）。
 *
 * <p>只有女仆当前的任务是战斗类任务（{@link IAttackTask}）时才会主动攻击，不擅自换任务；
 * 守护任务（{@code GuardTask}）本身就是战斗类任务，所以按守护名单守护的女仆总是会出手。
 */
public final class GuardCombat {
    /** 守护时搜索敌对生物的最小间隔（tick）。 */
    public static final int SCAN_INTERVAL = 20;

    /** 守护时以被守护者为中心搜索敌对生物的范围（格）。 */
    private static final double SCAN_RANGE = 8.0D;

    private GuardCombat() {
    }

    /**
     * 守护时把最近的敌对生物设为攻击目标。
     * <p>当前任务不是 {@link IAttackTask} 时不擅自换任务，直接放弃。
     */
    public static void tryAttack(ServerLevel level, EntityMaid maid, @Nullable LivingEntity guarded) {
        if (guarded == null) {
            return;
        }
        IMaidTask task = maid.getTask();
        if (!(task instanceof IAttackTask attackTask)) {
            return;
        }
        LivingEntity hostile = findNearestHostile(level, maid, guarded, attackTask);
        if (hostile == null) {
            return;
        }
        // 与主模组 SwitchWorkTaskTool#attackResult 同构：先 setTarget，再按攻击排除规则回滚或写 Brain 记忆
        LivingEntity previous = maid.getTarget();
        maid.setTarget(hostile);
        if (!attackTask.canAttack(maid, hostile)) {
            maid.setTarget(previous);
            return;
        }
        maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, hostile);
    }

    /**
     * 在被守护者周围找最近的敌对生物：{@link Enemy}、存活、不是玩家/女仆、不是有主人的生物，
     * 并且必须能通过当前攻击任务的 {@link IAttackTask#canAttack} 排除规则。
     */
    @Nullable
    private static LivingEntity findNearestHostile(ServerLevel level, EntityMaid maid,
                                                   LivingEntity guarded, IAttackTask attackTask) {
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
                guarded.getBoundingBox().inflate(SCAN_RANGE),
                entity -> isHostile(maid, guarded, entity));
        LivingEntity nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            if (!attackTask.canAttack(maid, candidate)) {
                continue;
            }
            double distanceSqr = candidate.distanceToSqr(guarded);
            if (distanceSqr < nearestSqr) {
                nearestSqr = distanceSqr;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private static boolean isHostile(EntityMaid maid, LivingEntity guarded, LivingEntity entity) {
        if (entity == maid || entity == guarded || !entity.isAlive()) {
            return false;
        }
        if (!(entity instanceof Enemy)) {
            return false;
        }
        if (entity instanceof Player || entity instanceof EntityMaid) {
            return false;
        }
        // 被驯服/有主人的生物不打（狼、猫、马、其他模组的宠物等）
        return !(entity instanceof OwnableEntity ownable) || ownable.getOwnerUUID() == null;
    }
}
