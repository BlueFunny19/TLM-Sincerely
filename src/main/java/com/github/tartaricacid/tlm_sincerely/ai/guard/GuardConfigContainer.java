package com.github.tartaricacid.tlm_sincerely.ai.guard;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.TaskConfigContainer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;

/**
 * 守护任务的配置界面容器。和主模组其他任务的配置页一样继承 {@link TaskConfigContainer}，
 * 玩家背包栏位、主人校验等都沿用它；只带女仆的实体 ID，名单本身存在女仆的任务数据里（会同步到客户端）。
 */
public class GuardConfigContainer extends TaskConfigContainer {
    public static final MenuType<GuardConfigContainer> TYPE = IForgeMenuType.create(
            (int windowId, Inventory inv, FriendlyByteBuf data) ->
                    new GuardConfigContainer(windowId, inv, data.readInt()));

    public GuardConfigContainer(int id, Inventory inventory, int entityId) {
        super(TYPE, id, inventory, entityId);
    }

    /** 主模组打开任务配置页时用：{@code NetworkHooks.openScreen(player, provider, buf -> buf.writeInt(maidId))}。 */
    public static MenuProvider createProvider(int entityId) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("container.tlm_sincerely.guard_config");
            }

            @Override
            public AbstractContainerMenu createMenu(int index, Inventory playerInventory, Player player) {
                return new GuardConfigContainer(index, playerInventory, entityId);
            }
        };
    }
}
