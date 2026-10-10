package com.github.tartaricacid.tlm_sincerely.ai.guard;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskAttack;
import net.minecraft.world.MenuProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 守护任务：一个独立的女仆任务，跟随守护名单（{@link GuardData}）去保护别人。
 *
 * <p>战斗能力直接继承近战攻击任务（{@link TaskAttack}）：武器判定、攻击排除规则、受击反应都和它一致，
 * 所以名单里暂时没人可守时，她的表现就是一只普通的近战女仆。"守谁"由 {@link GuardTaskDriver} 决定并下发，
 * 这个类本身不带逻辑。
 */
public class GuardTask extends TaskAttack {
    public static final ResourceLocation UID = new ResourceLocation("tlm_sincerely", "guard");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        // 自己的图标物品还没注册好时（理论上不会发生）退回盾牌，免得图标位置是空的
        return GuardItems.GUARD_TASK_ICON.isPresent()
                ? new ItemStack(GuardItems.GUARD_TASK_ICON.get())
                : Items.SHIELD.getDefaultInstance();
    }

    /** 守护名单的配置界面（取代近战任务自带的"攻击对象"配置页）。 */
    @Override
    public MenuProvider getTaskConfigGuiProvider(EntityMaid maid) {
        return GuardConfigContainer.createProvider(maid.getId());
    }

    @Override
    public String getMaidActionSummary() {
        return "Guard: stay near the people on her guard list (highest priority first, each with its own"
                + " condition such as 'only when their health is low') and fight hostile mobs that threaten them.";
    }
}
