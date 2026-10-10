package com.github.tartaricacid.tlm_sincerely.config.subconfig;

import com.github.tartaricacid.tlm_sincerely.ai.overtime.TreatTable;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 女仆"加班"配置。
 *
 * <p>加班 = 玩家要女仆干活、而她本来不在工作时间时，临时把她的日程改成全天工作，活干完再恢复。
 * 移动类指令（{@code maid_move} 等）期间的加班由 {@link MoveConfig#MOVE_FORCE_WORK_SCHEDULE} 控制；
 * 这里管的是"玩家让她切换工作任务"这一种来源、加班的收尾，以及<b>疲劳软上限</b>：
 * 在本该睡觉的时段里加班太久，女仆会累，向主人要点心（吃了就能再撑一会儿），要不到就去休息。
 */
public final class OvertimeConfig {
    /** 点心表的默认值：一个蛋糕多撑 300 秒。 */
    public static final List<String> DEFAULT_TREATS = List.of("minecraft:cake=300");

    public static ForgeConfigSpec.BooleanValue TASK_SWITCH_ENABLED;
    public static ForgeConfigSpec.IntValue TASK_HOLD_SECONDS;
    public static ForgeConfigSpec.BooleanValue SOFT_CAP_ENABLED;
    public static ForgeConfigSpec.IntValue SOFT_CAP_SECONDS;
    public static ForgeConfigSpec.BooleanValue COMPLAIN_ENABLED;
    public static ForgeConfigSpec.BooleanValue TREAT_ENABLED;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> TREATS;
    public static ForgeConfigSpec.BooleanValue EAT_OWN_TREATS;
    public static ForgeConfigSpec.IntValue TREAT_WAIT_SECONDS;

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

        builder.comment("Soft cap on overtime: a maid who works overtime during the time she should be asleep gets tired after OvertimeSoftCapSeconds",
                "Overtime in her working or leisure time does not count; she recovers fully after about 5 real minutes without overtime",
                "What happens when she is tired is decided by OvertimeTreatEnabled and OvertimeComplainEnabled below");
        SOFT_CAP_ENABLED = builder.define("OvertimeSoftCapEnabled", true);

        builder.comment("How many seconds of overtime in her rest time a maid can take before she gets tired (real seconds, 20 ticks each)");
        SOFT_CAP_SECONDS = builder.defineInRange("OvertimeSoftCapSeconds", 180, 30, 3600);

        builder.comment("When the soft cap is reached and treats are disabled, the maid complains with a line and keeps working",
                "Turn this off to silence the complaint; with treats enabled the request for a treat is always spoken");
        COMPLAIN_ENABLED = builder.define("OvertimeComplainEnabled", true);

        builder.comment("When the soft cap is reached the maid asks her owner for a treat (see OvertimeTreats) and walks over to the owner",
                "A treat lets her keep going longer; if she gets none within OvertimeTreatWaitSeconds (or the owner is offline / too far away)",
                "she stops her work and goes to rest, and cannot be put on overtime again until her rest time is over",
                "When disabled she only complains (OvertimeComplainEnabled) and keeps working");
        TREAT_ENABLED = builder.define("OvertimeTreatEnabled", true);

        builder.comment("Treats a tired maid accepts, as 'namespace:item=seconds': eating or being given one item lets her work that many more seconds",
                "Hand the item to her with a right click while she is asking, or put it in her backpack",
                "Entries that are not in this format are ignored");
        // 与 ChatBarConfig 一致：默认值用可变 List，避免 Forge 在缺失键时对不可变集合做写入
        TREATS = builder.defineList("OvertimeTreats", new ArrayList<>(DEFAULT_TREATS), value -> value instanceof String);

        builder.comment("Whether a tired maid eats a treat from her own backpack right away instead of asking for one");
        EAT_OWN_TREATS = builder.define("OvertimeEatOwnTreats", true);

        builder.comment("How long a tired maid waits for a treat before she gives up and goes to rest, in seconds");
        TREAT_WAIT_SECONDS = builder.defineInRange("OvertimeTreatWaitSeconds", 90, 10, 600);

        builder.pop();
    }

    /** 当前配置的点心表（每次现解析，调用不频繁）。 */
    public static TreatTable treatTable() {
        return TreatTable.parse(TREATS.get());
    }
}
