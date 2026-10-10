package com.github.tartaricacid.tlm_sincerely.mixin;

import com.github.tartaricacid.tlm_sincerely.config.subconfig.AdventureConfig;
import com.github.tartaricacid.tlm_sincerely.schedule.AdventureSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 主模组给模型看的"当前日程"上下文（{@code MaidContexts.ScheduleModeContext#getValue}）只会为
 * {@code DAY / NIGHT / ALL} 返回文字，遇到别的值会直接抛 {@code IncompatibleClassChangeError}，
 * 冒险日程的女仆一说话就会出错。这里为冒险日程补上文字，并且把它的含义写给模型。
 *
 * <p>目标是主模组里包可见的内部类，所以用类名字符串指定；{@code require = 0}：主模组改了结构只是退回原样。
 */
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.ai.agent.context.prompts.MaidContexts$ScheduleModeContext",
        remap = false)
public abstract class ScheduleModeContextMixin {

    @Inject(method = "getValue", at = @At("HEAD"), cancellable = true, require = 0)
    private void tlmSincerely$adventureValue(EntityMaid maid, CallbackInfoReturnable<String> cir) {
        if (AdventureSchedule.is(maid.getSchedule())) {
            cir.setReturnValue(AdventureSchedule.NAME + " (works almost around the clock with no leisure time; "
                    + "only sleeps " + AdventureConfig.window().restRangeText() + " if there is a bed to sleep in)");
        }
    }
}
