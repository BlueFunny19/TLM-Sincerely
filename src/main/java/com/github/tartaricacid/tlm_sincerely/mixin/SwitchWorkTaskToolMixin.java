package com.github.tartaricacid.tlm_sincerely.mixin;

import com.github.tartaricacid.tlm_sincerely.ai.overtime.OvertimeManager;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.implement.SwitchWorkTaskTool;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 玩家（通过模型）让女仆在非工作时间切换工作任务时，让她"加班"而不是回答"现在不是工作时间"。
 *
 * <p>主模组 {@code SwitchWorkTaskTool#onCall}（读字节码得到的流程）是：先 {@code setTask}，再
 * {@code onFunctionCallSwitch}，然后调用一次 {@code maid.getScheduleDetail()}，只要结果不是
 * {@code Activity.WORK} 就直接返回"已切换到某任务，但当前日程是 X，不在工作时间"，后面设置攻击目标之类的
 * 逻辑都不会执行。
 *
 * <p>这里重定向的就是那一次 {@code getScheduleDetail()} 调用：{@link OvertimeManager#onWorkTaskSwitched}
 * 决定要不要让她加班（临时把日程改成全天工作）；要的话就返回 {@code Activity.WORK}，主模组按正常的工作时间
 * 继续走完剩下的逻辑。睡觉中 / 配置关闭 / 玩家刚改过日程等情况下原样返回真实活动，行为和以前一样。
 * 切到待命任务时则顺手结束这只女仆的"切换任务加班"。
 *
 * <p>{@code require = 0}：本 mod 的混入配置默认要求每个注入都必须命中，而这里的目标是主模组的内部方法，
 * 主模组将来改写这段代码时不应连累整个游戏启动失败。注入失败时只是回到以前的行为（回答"不在工作时间"）。
 */
@Mixin(value = SwitchWorkTaskTool.class, remap = false)
public abstract class SwitchWorkTaskToolMixin {

    @Redirect(method = "onCall(Ljava/lang/String;"
            + "Lcom/github/tartaricacid/touhoulittlemaid/ai/agent/tool/implement/SwitchWorkTaskTool$Result;"
            + "Lcom/github/tartaricacid/touhoulittlemaid/ai/manager/entity/LLMCallback;)"
            + "Lcom/github/tartaricacid/touhoulittlemaid/ai/manager/entity/LLMCallback;",
            at = @At(value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;"
                            + "getScheduleDetail()Lnet/minecraft/world/entity/schedule/Activity;"),
            remap = false, require = 0)
    private Activity tlmSincerely$overtimeOnWorkTaskSwitch(EntityMaid maid) {
        Activity real = maid.getScheduleDetail();
        try {
            return OvertimeManager.onWorkTaskSwitched(maid, real == Activity.WORK) ? Activity.WORK : real;
        } catch (RuntimeException e) {
            // 绝不向上抛：这里位于主模组的工具调用里，异常会让整次工具调用失败
            org.slf4j.LoggerFactory.getLogger(SwitchWorkTaskToolMixin.class)
                    .warn("[overtime] failed to handle a work task switch, falling back to the real activity", e);
            return real;
        }
    }
}
