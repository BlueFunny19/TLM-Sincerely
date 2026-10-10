package com.github.tartaricacid.tlm_sincerely.mixin;

import com.github.tartaricacid.tlm_sincerely.schedule.AdventureSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 主模组的 {@code MaidBrain.registerSchedule} 只认识三个日程，认不出的值一律当成白班。冒险日程的女仆
 * 在这里换上本 mod 自己的时间表（几乎全天工作、只在睡眠窗口里休息）。
 *
 * <p>{@code require = 0}：目标是主模组的内部方法，改名后只是冒险女仆被当成白班，不应连累游戏启动。
 */
@Mixin(value = MaidBrain.class, remap = false)
public abstract class MaidBrainScheduleMixin {

    @Inject(method = "registerSchedule", at = @At("HEAD"), cancellable = true, require = 0)
    private static void tlmSincerely$registerAdventureSchedule(Brain<EntityMaid> brain, EntityMaid maid,
                                                               CallbackInfo ci) {
        if (AdventureSchedule.is(maid.getSchedule())) {
            brain.setSchedule(AdventureSchedule.timetable());
            ci.cancel();
        }
    }
}
