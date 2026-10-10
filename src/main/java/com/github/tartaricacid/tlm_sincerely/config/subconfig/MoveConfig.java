package com.github.tartaricacid.tlm_sincerely.config.subconfig;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 女仆移动类指令（{@code maid_move} / {@code maid_guard} / {@code maid_work_time} / {@code maid_cancel} 四个工具）配置。
 *
 * <p>指令本身是服务端内存态，但"指令 + 暂停跟随前的原状态"会写进女仆实体的持久化标签，
 * 因此服务器重启后能重新登记或精确恢复（见 {@code ai.move.MoveDirectiveManager}）。
 * 关闭开关后该工具不会出现在模型工具列表中。
 */
public final class MoveConfig {
    public static ForgeConfigSpec.BooleanValue MOVE_ENABLED;
    public static ForgeConfigSpec.IntValue MOVE_DEFAULT_DURATION_SECONDS;
    public static ForgeConfigSpec.IntValue MOVE_MAX_DURATION_SECONDS;
    public static ForgeConfigSpec.IntValue MOVE_MAX_ACTIVE_PER_OWNER;
    public static ForgeConfigSpec.BooleanValue MOVE_ALLOW_PERMANENT;
    public static ForgeConfigSpec.IntValue MOVE_PERMANENT_REMINDER_MINUTES;
    public static ForgeConfigSpec.BooleanValue MOVE_TELEPORT_FALLBACK;
    public static ForgeConfigSpec.IntValue MOVE_TELEPORT_MAX_DISTANCE;
    public static ForgeConfigSpec.BooleanValue MOVE_FORCE_WORK_SCHEDULE;

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("move");

        builder.comment("Expose the maid_move, maid_guard, maid_work_time and maid_cancel AI tools so a maid can approach, retreat, go to a place, guard someone, stay where she is, go home or work around the clock, and cancel it again",
                "Timed orders live in server memory, but the order, the state saved before suspending follow and the schedule saved before forcing work time are written to the maid entity so they survive a server restart");
        MOVE_ENABLED = builder.define("MoveEnabled", true);

        builder.comment("duration_seconds used when the model does not pass one, in seconds (default 600 = 10 minutes)");
        MOVE_DEFAULT_DURATION_SECONDS = builder.defineInRange("MoveDefaultDurationSeconds", 600, 10, 86400);

        builder.comment("Upper bound of duration_seconds for a single movement order, in seconds",
                "Requests longer than this are clamped; note the default changed from 300 to 3600");
        MOVE_MAX_DURATION_SECONDS = builder.defineInRange("MoveMaxDurationSeconds", 3600, 10, 86400);

        builder.comment("Maximum movement orders active at the same time for all maids of one owner",
                "Permanent orders occupy a slot of this budget as well");
        MOVE_MAX_ACTIVE_PER_OWNER = builder.defineInRange("MoveMaxActivePerOwner", 8, 1, 64);

        builder.comment("Allow permanent orders (permanent=true, no time limit until the player cancels)",
                "When disabled a request with permanent=true is refused with an explanation for the model");
        MOVE_ALLOW_PERMANENT = builder.define("MoveAllowPermanent", true);

        builder.comment("How often a permanent order makes the maid repeat a reminder, in minutes",
                "0 disables reminders; a re-registered permanent order (after a server restart) restarts this timer from zero");
        MOVE_PERMANENT_REMINDER_MINUTES = builder.defineInRange("MovePermanentReminderMinutes", 10, 0, 120);

        builder.comment("Teleport the maid to the destination when she cannot walk there (path blocked / stuck).",
                "When disabled, stuck orders keep retrying to walk and only write an INFO log, with no teleport and no line in chat");
        MOVE_TELEPORT_FALLBACK = builder.define("MoveTeleportFallback", true);

        builder.comment("Maximum maid-to-destination distance (blocks) at which the teleport fallback is still allowed.",
                "Farther than this the order is cancelled and the maid says a line in chat instead of teleporting");
        MOVE_TELEPORT_MAX_DISTANCE = builder.defineInRange("MoveTeleportMaxDistance", 64, 8, 256);

        builder.comment("While a movement order is active, temporarily switch the maid's schedule to ALL (work around the clock) when she is not asleep, and restore the original schedule when the order ends",
                "This lets a maid without a work position move and accept a work task at night; the work_time action is not affected by this switch",
                "Changing the schedule yourself (in the maid GUI or with the switch_schedule tool) while it is forced stops the restore and is treated as the player taking over");
        MOVE_FORCE_WORK_SCHEDULE = builder.define("MoveForceWorkSchedule", true);

        builder.pop();
    }
}
