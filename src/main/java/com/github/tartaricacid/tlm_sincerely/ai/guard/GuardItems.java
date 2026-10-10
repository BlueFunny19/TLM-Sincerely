package com.github.tartaricacid.tlm_sincerely.ai.guard;

import com.github.tartaricacid.tlm_sincerely.SincerelyExtension;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 守护任务的图标物品。女仆任务的图标在主模组里是一个 {@code ItemStack}，要用自己画的图标就得有一个带贴图的物品。
 * 它只用来当图标：不进任何创造模式标签页，也没有任何功能。
 */
public final class GuardItems {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SincerelyExtension.MOD_ID);

    public static final RegistryObject<Item> GUARD_TASK_ICON =
            ITEMS.register("guard_task_icon", () -> new Item(new Item.Properties().stacksTo(1)));

    private GuardItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
