package com.github.tartaricacid.tlm_sincerely.mixin;

import com.github.tartaricacid.tlm_sincerely.SincerelyExtension;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.AdventureConfig;
import com.github.tartaricacid.tlm_sincerely.schedule.AdventureSchedule;
import com.github.tartaricacid.tlm_sincerely.schedule.AdventureWindow;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.ScheduleButton;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 女仆界面里的日程按钮（点一下切换到下一个日程）只会画三种图标、写三种提示，对追加的冒险日程：
 * 图标会取到贴图上不相干的区域、提示会显示成白班的时间表。这里为冒险日程换成自己的图标与提示。
 *
 * <p>按钮循环切换的顺序来自 {@code MaidSchedule.values()}，冒险日程排在"全天"之后、回到"白班"之前，不需要处理。
 * 仅客户端。
 */
@Mixin(value = ScheduleButton.class, remap = true)
public abstract class ScheduleButtonMixin {
    private static final ResourceLocation TLM_SINCERELY$ADVENTURE_ICON =
            new ResourceLocation(SincerelyExtension.MOD_ID, "textures/gui/schedule_adventure.png");
    private static final int TLM_SINCERELY$ICON_WIDTH = 61;
    private static final int TLM_SINCERELY$ICON_HEIGHT = 13;

    @Shadow(remap = false)
    private MaidSchedule mode;

    @Shadow(remap = false)
    @Final
    private EntityMaid maid;

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void tlmSincerely$renderAdventureIcon(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                                  CallbackInfo ci) {
        if (!AdventureSchedule.is(mode)) {
            return;
        }
        AbstractWidget self = (AbstractWidget) (Object) this;
        RenderSystem.enableDepthTest();
        graphics.blit(TLM_SINCERELY$ADVENTURE_ICON, self.getX(), self.getY(), 0.0F, 0.0F,
                self.getWidth(), self.getHeight(), TLM_SINCERELY$ICON_WIDTH, TLM_SINCERELY$ICON_HEIGHT);
        ci.cancel();
    }

    @Inject(method = "getTooltips", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void tlmSincerely$adventureTooltips(CallbackInfoReturnable<List<Component>> cir) {
        if (!AdventureSchedule.is(mode)) {
            return;
        }
        long dayTime = maid.level().getDayTime();
        int tickOfDay = (int) Math.floorMod(dayTime, (long) AdventureWindow.DAY_LENGTH);
        AdventureWindow window = AdventureConfig.window();
        boolean restNow = window.isRest(tickOfDay);

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.tlm_sincerely.schedule.adventure")
                .withStyle(style -> style.withUnderlined(true))
                .append(Component.literal(" " + AdventureWindow.formatTick(tickOfDay)).withStyle(style -> style
                        .withUnderlined(false).withColor(0xAAAAAA))));
        for (AdventureWindow.Segment segment : window.segments()) {
            boolean current = segment.rest() == restNow
                    && tickOfDay >= segment.from() && tickOfDay < segment.to();
            Activity activity = segment.rest() ? Activity.REST : Activity.WORK;
            Component name = Component.translatable(activity == Activity.REST
                    ? "gui.tlm_sincerely.schedule.adventure.rest" : "gui.tlm_sincerely.schedule.adventure.work");
            lines.add(Component.literal(AdventureWindow.formatTick(segment.from()) + "-"
                            + AdventureWindow.formatTick(segment.to()) + " ")
                    .append(name)
                    .withStyle(style -> style.withColor(current ? 0x55FF55 : 0x555555)));
        }
        lines.add(Component.translatable("tooltips.touhou_little_maid.schedule.desc"));
        cir.setReturnValue(lines);
    }
}
