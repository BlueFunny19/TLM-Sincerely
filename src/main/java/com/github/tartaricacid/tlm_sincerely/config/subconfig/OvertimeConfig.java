package com.github.tartaricacid.tlm_sincerely.config.subconfig;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 女仆"加班"配置。
 *
 * <p>加班 = 玩家要女仆干活、而她本来不在工作时间时，临时把她的日程改成全天工作，活干完再恢复。
 * 移动类指令（{@code maid_move} 等）期间的加班由 {@link MoveConfig#MOVE_FORCE_WORK_SCHEDULE} 控制；
 * 这里管的是"玩家让她切换工作任务"这一种来源，以及加班的收尾。
 */
public final class OvertimeConfig {
    public static ForgeConfigSpec.BooleanValue TASK_SWITCH_ENABLED;
    public static ForgeConfigSpec.IntValue TASK_HOLD_SECONDS;

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("overtime");

        builder.comment("When the player asks a maid to switch to a work task (through the model) while it is not her working time,",
                "switch her schedule to work-around-the-clock temporarily instead of answering 'not in work time'",
                "Changing the schedule yourself (in the maid GUI or with the switch_schedule tool) while she works overtime stops the restore and is treated as the player taking over");
        TASK_SWITCH_ENABLED = builder.define("OvertimeTaskSwitchEnabled", true);

        builder.comment("How long overtime started by a work-task switch lasts without a new instruction, in seconds",
                "Every new task switch restarts this timer; when it runs out (or the maid is switched to the idle task) her original schedule is restored",
                "There is no reliable way to tell that every kind of task has run out of work, so this timer is the stand-in for 'the work is done'");
        TASK_HOLD_SECONDS = builder.defineInRange("OvertimeTaskHoldSeconds", 300, 30, 3600);

        builder.pop();
    }
}
