package com.github.tartaricacid.tlm_sincerely.mixin;

import com.github.tartaricacid.tlm_sincerely.config.subconfig.AdventureConfig;
import com.github.tartaricacid.tlm_sincerely.schedule.AdventureSchedule;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.implement.SwitchScheduleTool;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

/**
 * 主模组的 {@code switch_schedule} 工具会自动把枚举里所有的值（包括追加的 {@code ADVENTURE}）列给模型，
 * 并且 {@code setSchedule} 也能直接用，但有两处不认识它：
 * <ul>
 *   <li>工具说明里没有解释 {@code ADVENTURE} 是什么——这里在说明末尾补一句；</li>
 *   <li>聊天里显示的"调用了工具"摘要对没列出的值一律显示成"切换到全天"——这里改成冒险。</li>
 * </ul>
 * {@code require = 0}：主模组改了结构只是退回原样（模型仍能切换，只是说明和摘要不准）。
 */
@Mixin(value = SwitchScheduleTool.class, remap = false)
public abstract class SwitchScheduleToolMixin {

    @Inject(method = "summary", at = @At("RETURN"), cancellable = true, require = 0)
    private void tlmSincerely$explainAdventure(EntityMaid maid, CallbackInfoReturnable<String> cir) {
        if (AdventureSchedule.value() == null) {
            return;
        }
        cir.setReturnValue(cir.getReturnValue() + " ADVENTURE: the maid works almost around the clock with no leisure time"
                + " and only sleeps " + AdventureConfig.window().restRangeText()
                + " (she can use ordinary beds); meant for long expeditions.");
    }

    @Inject(method = "invocationSummaryComponent", at = @At("HEAD"), cancellable = true, require = 0)
    private void tlmSincerely$adventureSummary(String schedule, CallbackInfoReturnable<Component> cir) {
        if (schedule != null && AdventureSchedule.NAME.equals(schedule.toUpperCase(Locale.ROOT))) {
            cir.setReturnValue(Component.translatable("ai.tlm_sincerely.chat.tool_call.switch_schedule.adventure")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
