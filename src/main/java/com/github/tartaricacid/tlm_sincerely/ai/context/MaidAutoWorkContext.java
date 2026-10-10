package com.github.tartaricacid.tlm_sincerely.ai.context;

import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.PriorityConfig;
import com.github.tartaricacid.tlm_sincerely.priority.autowork.AutoWorkState;
import com.github.tartaricacid.tlm_sincerely.priority.autowork.AutoWorkStateService;
import com.github.tartaricacid.tlm_sincerely.priority.autowork.compat.AutoWorkCompatService;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.AbstractMaidContext;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.List;

/** Appends a prose snapshot of the maid's current auto-work state to AI context. */
public final class MaidAutoWorkContext extends AbstractMaidContext {
    /** 栏目标题由主 Mod 在构造时固定、不能按女仆切换语言，所以中英并列。 */
    public MaidAutoWorkContext() {
        super("tlm_sincerely_auto_work_status",
                "当前自动工作开关状态与任务绑定顺序 (Current auto work switch status and bound task order)");
    }

    @Override
    public String getValue(EntityMaid maid) {
        boolean zh = PromptLang.isChinese(PromptLang.of(maid));
        MinecraftServer server = maid == null ? null : maid.level().getServer();
        AutoWorkStateService stateService = AutoWorkStateService.getOrNull(server);
        if (stateService == null) {
            return zh ? "自动工作状态暂时不可用，因为服务端的相关服务还没有就绪。"
                    : "Auto work status is unavailable because its server services are not ready.";
        }

        AutoWorkState state = stateService.getState(maid);
        AutoWorkCompatService compatService = AutoWorkCompatService.getOrNull(server);
        String globalScheduling;
        if (zh) {
            globalScheduling = PriorityConfig.ENABLED.get()
                    ? "全局自动调度已开启。"
                    : "全局自动调度已暂停，在重新开启之前不会发生任何自动切换任务。";
        } else {
            globalScheduling = PriorityConfig.ENABLED.get()
                    ? "Global automatic scheduling is active. "
                    : "Global automatic scheduling is paused, so no automatic task switch will occur until it is enabled. ";
        }
        if (!state.enabled()) {
            return globalScheduling + (zh ? "这只女仆没有开启自动工作。" : "Auto work is disabled for this maid.");
        }

        StringBuilder out = new StringBuilder(globalScheduling)
                .append(zh ? "这只女仆已开启自动工作。" : "Auto work is enabled. ");
        if (state.presetName().isEmpty()) {
            out.append(zh ? "还没有绑定预设。" : "No preset is bound yet. ");
        } else if (zh) {
            out.append("绑定的预设是「").append(state.presetName())
                    .append("」（preset_id=").append(state.presetId()).append("）。");
        } else {
            out.append("The bound preset is '").append(state.presetName())
                    .append("' (preset_id=").append(state.presetId()).append("). ");
        }
        out.append(zh ? "绑定的任务顺序：" : "Bound task order: ");
        appendOrder(out, state.order(), compatService, zh);
        return out.toString();
    }

    private static void appendOrder(StringBuilder out, List<ResourceLocation> order,
                                    AutoWorkCompatService compatService, boolean zh) {
        if (order.isEmpty()) {
            out.append(zh ? "无。" : "none.");
            return;
        }
        for (int index = 0; index < order.size(); index++) {
            if (index > 0) {
                out.append("; ");
            }
            ResourceLocation taskId = order.get(index);
            String name = TaskManager.findTask(taskId)
                    .map(task -> task.getName().getString())
                    .orElse(zh ? "未注册的任务" : "unregistered task");
            out.append(index + 1).append(") ").append(name).append(" [").append(taskId).append("]");
            AutoWorkCompatService.ReportEntry entry = compatService == null ? null : compatService.getEntry(taskId);
            if (entry != null) {
                out.append(" {detector=").append(entry.level()).append(", reason=")
                        .append(entry.reason()).append('}');
            }
        }
        out.append('.');
    }
}
