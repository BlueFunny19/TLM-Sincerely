package com.github.tartaricacid.tlm_sincerely.mixin;

import com.github.tartaricacid.tlm_sincerely.config.subconfig.AdventureConfig;
import com.github.tartaricacid.tlm_sincerely.schedule.AdventureSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidBedTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;

/**
 * 冒险日程的女仆也能睡原版的床。
 *
 * <p>主模组的 {@code MaidBedTask} 只在"女仆床"（POI 类型 {@code MAID_BED}）里找床，而且 {@code start} 里
 * 只对女仆床方块调用 {@code startSleeping}。带着女仆出去冒险的玩家通常只会铺原版的床，所以对冒险日程的女仆：
 * <ul>
 *   <li>{@code findBed}：主模组一张女仆床也没找到时，才在原版的床（POI 类型 {@code HOME}）里找最近的一张
 *       没人睡的床——也就是始终优先女仆床；</li>
 *   <li>{@code start}：目标是原版的床时，由这里让她 {@code startSleeping}（原版会把床标成"有人"，醒来时放开）。
 *       原方法随后照常清掉寻路记忆，对非女仆床的方块什么也不做。</li>
 * </ul>
 * 其它日程的女仆完全不受影响。{@code require = 0}：主模组改了内部结构，只是冒险女仆睡不了原版床。
 */
@Mixin(value = MaidBedTask.class, remap = false)
public abstract class AdventureBedTaskMixin {

    @Inject(method = "findBed", at = @At("RETURN"), cancellable = true, require = 0)
    private void tlmSincerely$findVanillaBed(ServerLevel level, EntityMaid maid, CallbackInfoReturnable<BlockPos> cir) {
        if (cir.getReturnValue() != null || !isAdventureWithVanillaBeds(maid)) {
            return;
        }
        BlockPos nearest = level.getPoiManager()
                .findAll(type -> type.is(PoiTypes.HOME), pos -> true, maid.getBrainSearchPos(),
                        (int) maid.getRestrictRadius(), PoiManager.Occupancy.ANY)
                .filter(pos -> isFreeVanillaBed(level.getBlockState(pos)))
                .min(Comparator.comparingDouble(pos -> pos.distSqr(maid.blockPosition())))
                .orElse(null);
        if (nearest != null) {
            cir.setReturnValue(nearest);
        }
    }

    @Inject(method = "start", at = @At("HEAD"), require = 0)
    private void tlmSincerely$sleepInVanillaBed(ServerLevel level, EntityMaid maid, long gameTime, CallbackInfo ci) {
        if (!isAdventureWithVanillaBeds(maid)) {
            return;
        }
        BlockPos target = maid.getBrain().getMemory(InitEntities.TARGET_POS.get())
                .map(PositionTracker::currentBlockPosition).orElse(null);
        if (target != null && isFreeVanillaBed(level.getBlockState(target))) {
            maid.startSleeping(target);
        }
    }

    private static boolean isAdventureWithVanillaBeds(EntityMaid maid) {
        return AdventureSchedule.is(maid.getSchedule()) && AdventureConfig.VANILLA_BEDS.get();
    }

    /** 原版的床（不是女仆床）、是床头、没人睡。 */
    private static boolean isFreeVanillaBed(@Nullable BlockState state) {
        return state != null
                && state.getBlock() instanceof BedBlock
                && !state.is(InitBlocks.MAID_BED.get())
                && state.getValue(BedBlock.PART) == BedPart.HEAD
                && !state.getValue(BedBlock.OCCUPIED);
    }
}
