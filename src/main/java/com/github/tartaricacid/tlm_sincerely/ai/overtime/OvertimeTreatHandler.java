package com.github.tartaricacid.tlm_sincerely.ai.overtime;

import com.github.tartaricacid.tlm_sincerely.SincerelyExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 主人拿着点心右键一只正在要点心的女仆：由 {@link OvertimeManager#tryAcceptTreat} 判断收不收。
 * 收下就吃掉这次交互（不再打开女仆界面）；不收（女仆没在要点心、不是主人、不是点心）则什么都不做，
 * 交互照常交给女仆自己的处理。
 */
@Mod.EventBusSubscriber(modid = SincerelyExtension.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OvertimeTreatHandler {
    private OvertimeTreatHandler() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getTarget() instanceof EntityMaid maid) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (OvertimeManager.tryAcceptTreat(maid, player, event.getItemStack())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
}
