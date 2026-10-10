package com.github.tartaricacid.tlm_sincerely.mixin;

import com.github.tartaricacid.tlm_sincerely.schedule.AdventureSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code EntityMaid.getScheduleDetail()}（"这只女仆此刻该做什么"）对认不出的日程一律按白班算。
 * 冒险日程的女仆改按冒险时间表回答。主模组里凡是问"现在是不是工作时间"的地方（切换工作任务、工作坐标、
 * 给模型的当前活动上下文、女仆界面的提示）都经过这个方法。
 */
@Mixin(value = EntityMaid.class, remap = false)
public abstract class EntityMaidScheduleMixin {

    @Inject(method = "getScheduleDetail", at = @At("HEAD"), cancellable = true, require = 0)
    private void tlmSincerely$adventureScheduleDetail(CallbackInfoReturnable<Activity> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (AdventureSchedule.is(maid.getSchedule())) {
            cir.setReturnValue(AdventureSchedule.activityAt(maid.level().getDayTime()));
        }
    }
}
