package com.github.tartaricacid.tlm_sincerely;

import com.github.tartaricacid.tlm_sincerely.ai.context.MaidAutoWorkContext;
import com.github.tartaricacid.tlm_sincerely.ai.context.MaidMemoryContext;
import com.github.tartaricacid.tlm_sincerely.ai.context.MaidMoveContext;
import com.github.tartaricacid.tlm_sincerely.ai.tool.AutoWorkTool;
import com.github.tartaricacid.tlm_sincerely.ai.tool.MaidCommandTool;
import com.github.tartaricacid.tlm_sincerely.ai.tool.MaidMemoryTool;
import com.github.tartaricacid.tlm_sincerely.ai.tool.MaidMoveTool;
import com.github.tartaricacid.tlm_sincerely.ai.tool.TellMaidTool;
import com.github.tartaricacid.tlm_sincerely.priority.autowork.AutoWorkTaskDataKeys;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.GameContextRegister;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ToolRegister;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;

@LittleMaidExtension
public class SincerelyExtension implements ILittleMaid {
    public static final String MOD_ID = "tlm_sincerely";

    @Override
    public void registerAITool(ToolRegister register) {
        register.register(new AutoWorkTool());
        register.register(new MaidMemoryTool());
        register.register(new MaidCommandTool());
        // tell_maid：可用性由 RelayConfig.RelayEnabled 在 ITool#trigger 中判定
        register.register(new TellMaidTool());
        // maid_move：可用性由 MoveConfig.MoveEnabled 在 ITool#trigger 中判定
        register.register(new MaidMoveTool());
    }

    @Override
    public void registerTaskData(TaskDataRegister register) {
        register.register(AutoWorkTaskDataKeys.STATE_KEY);
    }

    @Override
    public void registerAIMaidContext(GameContextRegister register) {
        register.registerCategory("tlm_sincerely_memory",
                "女仆的长期记忆：关于玩家的键值事实、过往事件、偏好",
                true);
        register.registerContext("tlm_sincerely_memory", new MaidMemoryContext());
        register.registerCategory("tlm_sincerely_auto_work",
                "当前自动工作开关状态与配置的任务优先级",
                true);
        register.registerContext("tlm_sincerely_auto_work", new MaidAutoWorkContext());
        register.registerCategory("tlm_sincerely_move",
                "女仆当前生效的移动指令（maid_move）：动作描述、是否永久、剩余时间",
                true);
        register.registerContext("tlm_sincerely_move", new MaidMoveContext());
    }
}
