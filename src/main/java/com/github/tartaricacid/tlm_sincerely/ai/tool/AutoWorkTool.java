package com.github.tartaricacid.tlm_sincerely.ai.tool;

import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.tlm_sincerely.priority.autowork.AutoWorkState;
import com.github.tartaricacid.tlm_sincerely.priority.autowork.AutoWorkStateService;
import com.github.tartaricacid.tlm_sincerely.priority.autowork.compat.AutoWorkCompatService;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.PriorityConfig;
import com.github.tartaricacid.tlm_sincerely.priority.TaskAutoSwitchHandler;
import com.github.tartaricacid.tlm_sincerely.priority.autowork.network.AutoWorkServerHandler;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.BoolParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-authoritative AI tool for the auto work switch.
 *
 * <p>The preset library lives on each player's client and is invisible to the
 * server, so this tool can only inspect and edit the bound snapshot of the
 * maid it is called on. Preset selection / library management must happen in
 * the GUI; the preset actions below are intentionally rejected with that
 * explanation instead of silently doing something different.
 */
public class AutoWorkTool implements ITool<AutoWorkTool.Result> {
    private static final String TOOL_ID = "auto_work";

    private static final String TOOL_DESC_ZH = """
            管理【这只】女仆的自动工作开关状态。
            预设库保存在玩家的客户端里，服务端看不到，所以本工具不能创建、重命名、删除或选择预设；预设要由玩家在女仆界面里选择。
            用 'query' 查看这只女仆是否开启了自动工作、绑定了什么任务顺序。
            用 'set_auto_enabled'（enabled=true/false）为这只女仆开启或关闭自动切换。
            用 'add_task' / 'remove_task' / 'move_task' 编辑【这只】女仆当前绑定的任务顺序；'move_task' 的位置从 1 开始数。
            编辑绑定顺序由服务端直接生效，不会改动玩家客户端里的预设库。
            """.trim();

    private static final String TOOL_DESC_EN = """
            Manage the auto work switch state of THIS maid.
            The preset library is stored on the player's client and is not visible to the server, so this tool cannot create, rename, delete or select presets; players pick presets in the maid GUI.
            Use 'query' to see whether this maid has auto work enabled and which task order it is bound to.
            Use 'set_auto_enabled' with enabled=true/false to turn automatic switching on or off for this maid.
            Use 'add_task' / 'remove_task' / 'move_task' to edit THIS maid's currently bound task order; 'move_task' uses a 1-based position.
            Editing the bound order is server-authoritative and immediate; it does not change the player's client library.
            """.trim();

    private static final List<String> ACTIONS = List.of(
            "query",
            "set_auto_enabled",
            "select_preset",
            "create_preset",
            "rename_preset",
            "delete_preset",
            "add_task",
            "remove_task",
            "move_task"
    );

    private static final String LIBRARY_ON_CLIENT_MSG_ZH =
            "预设库保存在玩家的客户端里，服务端无法读取或编辑。请让玩家在女仆界面的自动工作页里选择预设；"
                    + "本工具只能通过 add_task / remove_task / move_task 编辑当前绑定在这只女仆身上的任务顺序。";

    private static final String DEPRECATED_NUMERIC_MSG_ZH =
            "'set' 操作（1-10 的数字优先级）已不再支持。请用 'add_task' / 'remove_task' / 'move_task' "
                    + "编辑这只女仆绑定的任务顺序，或调用 'query' 查看当前的自动工作状态。";

    private static final String LIBRARY_ON_CLIENT_MSG =
            "The preset library is stored on the player's client and cannot be read or edited from the server. "
                    + "Ask the player to select a preset in the maid GUI (auto work tab); this tool can only edit "
                    + "the task order currently bound to this maid via add_task / remove_task / move_task.";

    private static final String DEPRECATED_NUMERIC_MSG =
            "The 'set' action with a 1-10 numeric priority is no longer supported. "
                    + "Use 'add_task' / 'remove_task' / 'move_task' to edit this maid's bound task order, "
                    + "or call 'query' to inspect the current auto work state.";

    private static final Codec<Result> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("action").forGetter(Result::action),
            Codec.BOOL.optionalFieldOf("enabled", false).forGetter(Result::enabled),
            Codec.STRING.optionalFieldOf("preset_id", "").forGetter(Result::presetId),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Result::name),
            Codec.STRING.optionalFieldOf("task_id", "").forGetter(Result::taskId),
            Codec.INT.optionalFieldOf("position", 0).forGetter(Result::position)
    ).apply(instance, Result::new));

    @Override
    public String id() {
        return TOOL_ID;
    }

    @Override
    public String summary(EntityMaid maid) {
        return PromptLang.pick(maid, TOOL_DESC_ZH, TOOL_DESC_EN);
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        boolean zh = PromptLang.isChinese(PromptLang.of(maid));
        StringParameter action = StringParameter.create()
                .setDescription(zh ? "要执行的操作" : "The action to perform")
                .addEnumValues(ACTIONS.toArray(new String[0]));
        root.addProperties("action", action);

        BoolParameter enabled = BoolParameter.create();
        enabled.setDescription(zh ? "用于 'set_auto_enabled'：true 开启，false 关闭。"
                : "For 'set_auto_enabled': true to enable, false to disable.");
        root.addProperties("enabled", enabled, false);

        StringParameter presetId = StringParameter.create()
                .setDescription(zh
                        ? "旧版的预设 UUID 参数。预设库操作（select/create/rename/delete_preset）会被服务端拒绝，因为预设库在玩家客户端里；请让玩家改用女仆界面。"
                        : "Legacy preset UUID parameter. Preset library actions (select/create/rename/delete_preset) are refused on the server because the library lives on the player's client; use the maid GUI instead.");
        root.addProperties("preset_id", presetId, false);

        StringParameter name = StringParameter.create()
                .setDescription(zh ? "未使用；预设库的管理在玩家的界面里完成。"
                        : "Unused; preset library management happens in the player's GUI.")
                .setMinLength(1)
                .setMaxLength(64);
        root.addProperties("name", name, false);

        // task_id is static (IMaidTask registration is process-wide) so
        // enumerating here is safe and helps the LLM avoid typos.
        StringParameter taskId = StringParameter.create()
                .setDescription(zh
                        ? "任务的 UID（例如 'touhou_little_maid:attack'）。'add_task' / 'remove_task' / 'move_task' 必填。"
                        : "Task UID (e.g. 'touhou_little_maid:attack'). Required for 'add_task' / 'remove_task' / 'move_task'.");
        for (IMaidTask task : TaskManager.getTaskIndex()) {
            taskId.addEnumValues(task.getUid().toString());
        }
        root.addProperties("task_id", taskId, false);

        IntegerParameter position = IntegerParameter.create()
                .setDescription(zh
                        ? "'move_task' 的目标位置，从 1 开始数。位置 1 表示这个任务成为绑定顺序里优先级最高的；超出 [1, 列表长度] 的值会被截到范围内。"
                        : "1-based target position for 'move_task'. Position 1 means the task becomes the highest priority in the bound order. Values outside [1, list size] are clamped.");
        root.addProperties("position", position, false);

        return root;
    }

    @Override
    public Codec<Result> codec() {
        return CODEC;
    }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        MinecraftServer server = maid == null ? null : maid.level().getServer();

        if (maid == null) {
            return callback.addToolResult("No maid context available for this tool call.", toolCallId);
        }
        boolean zh = PromptLang.isChinese(PromptLang.of(maid));
        if (server == null) {
            return callback.addToolResult(zh
                    ? "客户端上没有自动工作服务；本工具必须在带有 MinecraftServer 的服务端线程里运行。"
                    : "Auto work services are not available on the client; "
                    + "this tool must run on the server thread with a live MinecraftServer.", toolCallId);
        }
        AutoWorkStateService stateService = AutoWorkStateService.getOrNull(server);
        if (stateService == null) {
            return callback.addToolResult(zh ? "自动工作服务还没有绑定到这个服务器。"
                    : "Auto work services are not bound to this server yet.", toolCallId);
        }
        AutoWorkCompatService compatService = AutoWorkCompatService.getOrNull(server);

        String action = result.action();
        // Explicit deprecation: do not silently fall back to the new
        // semantics for the legacy actions. Force the LLM to update.
        if ("set".equals(action)) {
            return callback.addToolResult(zh ? DEPRECATED_NUMERIC_MSG_ZH : DEPRECATED_NUMERIC_MSG, toolCallId);
        }
        if ("select_preset".equals(action) || "create_preset".equals(action)
                || "rename_preset".equals(action) || "delete_preset".equals(action)) {
            return callback.addToolResult(zh ? LIBRARY_ON_CLIENT_MSG_ZH : LIBRARY_ON_CLIENT_MSG, toolCallId);
        }

        return switch (action) {
            case "query" -> callback.addToolResult(query(stateService, compatService, maid, zh), toolCallId);
            case "set_auto_enabled" -> callback.addToolResult(
                    setAutoEnabled(server, stateService, maid, result, zh), toolCallId);
            case "add_task" -> callback.addToolResult(
                    addTask(server, stateService, maid, result, zh), toolCallId);
            case "remove_task" -> callback.addToolResult(
                    removeTask(server, stateService, maid, result, zh), toolCallId);
            case "move_task" -> callback.addToolResult(
                    moveTask(server, stateService, maid, result, zh), toolCallId);
            default -> callback.addToolResult(ITool.invalidParam("action", ACTIONS,
                    (zh ? "未知的操作：" : "Unknown action: ") + action), toolCallId);
        };
    }

    @Override
    public String invocationSummary(Result result) {
        return "%s { %s }".formatted(TOOL_ID, result.action());
    }

    // -----------------------------------------------------------------
    // Action implementations
    // -----------------------------------------------------------------

    private String query(AutoWorkStateService stateService,
                         AutoWorkCompatService compatService,
                         EntityMaid maid, boolean zh) {
        AutoWorkState state = stateService.getState(maid);
        StringBuilder sb = new StringBuilder();
        if (zh) {
            sb.append("这只女仆的自动工作状态：\n");
            sb.append("  全局自动调度是否开启：").append(PriorityConfig.ENABLED.get()).append('\n');
            sb.append("  这只女仆是否开启：").append(state.enabled()).append('\n');
            sb.append("  绑定的预设：").append(state.presetName().isEmpty() ? "<无>" : state.presetName())
                    .append("（preset_id=").append(state.presetId()).append("，baked=")
                    .append(state.snapshotBaked()).append("）\n");
            sb.append("  绑定的任务顺序：\n");
            appendOrder(sb, state.order(), compatService, true);
            sb.append("\n注意：预设库本身保存在玩家的客户端里，请让玩家在女仆界面里选择或编辑预设。");
            return sb.toString();
        }
        sb.append("Auto work state for this maid:\n");
        sb.append("  global scheduling enabled: ").append(PriorityConfig.ENABLED.get()).append('\n');
        sb.append("  enabled: ").append(state.enabled()).append('\n');
        sb.append("  bound preset: ").append(state.presetName().isEmpty() ? "<none>" : state.presetName())
                .append(" (preset_id=").append(state.presetId()).append(", baked=")
                .append(state.snapshotBaked()).append(")\n");
        sb.append("  bound task order:\n");
        appendOrder(sb, state.order(), compatService, false);
        sb.append("\nNote: the preset library itself is stored on the player's client; ")
                .append("use the maid GUI to choose or edit presets.");
        return sb.toString();
    }

    private String setAutoEnabled(MinecraftServer server,
                                  AutoWorkStateService stateService,
                                  EntityMaid maid,
                                  Result result, boolean zh) {
        boolean desired = result.enabled();
        if (desired) {
            AutoWorkState bound = stateService.ensureBoundSnapshot(maid);
            stateService.setEnabled(maid, true);
            TaskAutoSwitchHandler.requestImmediateEvaluation(maid, "AGENT_ENABLE", true);
            AutoWorkServerHandler.broadcastSnapshots(server);
            String name = bound.presetName().isEmpty() ? bound.presetId().toString() : bound.presetName();
            return (zh ? "已为这只女仆开启自动工作，使用它绑定的快照 '%s'（%d 个任务）。%s"
                    : "Auto work enabled for this maid using its bound snapshot '%s' (%d tasks).%s").formatted(
                    name, bound.order().size(), PriorityConfig.ENABLED.get() ? ""
                            : (zh ? "全局自动调度目前处于暂停状态。" : " Global automatic scheduling is currently paused."));
        }
        stateService.setEnabled(maid, false);
        AutoWorkServerHandler.broadcastSnapshots(server);
        return zh ? "已为这只女仆关闭自动工作。" : "Auto work disabled for this maid.";
    }

    private String addTask(MinecraftServer server, AutoWorkStateService stateService,
                           EntityMaid maid, Result result, boolean zh) {
        ResourceLocation taskId = parseTaskId(result.taskId());
        if (taskId == null) {
            return ITool.invalidParam("task_id", taskIdValues(),
                    zh ? "add_task 必须提供 task_id" : "task_id is required for 'add_task'");
        }
        if (!stateService.addSnapshotTask(maid, taskId)) {
            AutoWorkState state = stateService.getState(maid);
            if (state.order().contains(taskId)) {
                return (zh ? "任务 '%s' 已经在这只女仆的绑定顺序里了。"
                        : "Task '%s' is already present in this maid's bound order.").formatted(taskId);
            }
            return (zh ? "任务 '%s' 无法加入这只女仆的绑定顺序。"
                    : "Task '%s' cannot be added to this maid's bound order.").formatted(taskId);
        }
        TaskAutoSwitchHandler.requestImmediateEvaluation(maid, "AGENT_ADD_BOUND_TASK", true);
        AutoWorkServerHandler.broadcastSnapshots(server);
        return (zh ? "已把任务 '%s' 追加到这只女仆的绑定顺序末尾。"
                : "Appended task '%s' to this maid's bound order.").formatted(taskId);
    }

    private String removeTask(MinecraftServer server, AutoWorkStateService stateService,
                              EntityMaid maid, Result result, boolean zh) {
        ResourceLocation taskId = parseTaskId(result.taskId());
        if (taskId == null) {
            return ITool.invalidParam("task_id", taskIdValues(),
                    zh ? "remove_task 必须提供 task_id" : "task_id is required for 'remove_task'");
        }
        if (!stateService.removeSnapshotTask(maid, taskId)) {
            return (zh ? "任务 '%s' 不在这只女仆的绑定顺序里。"
                    : "Task '%s' is not in this maid's bound order.").formatted(taskId);
        }
        TaskAutoSwitchHandler.requestImmediateEvaluation(maid, "AGENT_REMOVE_BOUND_TASK", true);
        AutoWorkServerHandler.broadcastSnapshots(server);
        return (zh ? "已从这只女仆的绑定顺序里移除任务 '%s'。"
                : "Removed task '%s' from this maid's bound order.").formatted(taskId);
    }

    private String moveTask(MinecraftServer server, AutoWorkStateService stateService,
                            EntityMaid maid, Result result, boolean zh) {
        ResourceLocation taskId = parseTaskId(result.taskId());
        if (taskId == null) {
            return ITool.invalidParam("task_id", taskIdValues(),
                    zh ? "move_task 必须提供 task_id" : "task_id is required for 'move_task'");
        }
        AutoWorkState state = stateService.getState(maid);
        if (!state.order().contains(taskId)) {
            return (zh ? "任务 '%s' 不在这只女仆的绑定顺序里；请先用 'add_task' 加入。"
                    : "Task '%s' is not in this maid's bound order; use 'add_task' first.").formatted(taskId);
        }
        int orderSize = state.order().size();
        if (result.position() < 1) {
            return ITool.invalidParam("position", List.of("1..%d".formatted(orderSize)),
                    zh ? "position 必须大于等于 1（从 1 开始数）" : "position must be 1 or greater (1-based)");
        }
        int targetIndex = result.position() - 1;
        int clamped = Math.max(0, Math.min(targetIndex, orderSize - 1));
        boolean moved = stateService.moveSnapshotTask(maid, taskId, clamped);
        if (!moved) {
            return (zh ? "任务 '%s' 已经在这只女仆绑定顺序的第 %d 位了。"
                    : "Task '%s' is already at position %d in this maid's bound order.")
                    .formatted(taskId, clamped + 1);
        }
        TaskAutoSwitchHandler.requestImmediateEvaluation(maid, "AGENT_MOVE_BOUND_TASK", true);
        AutoWorkServerHandler.broadcastSnapshots(server);
        return (zh ? "已把任务 '%s' 移到这只女仆绑定顺序的第 %d 位。"
                : "Moved task '%s' to position %d in this maid's bound order.").formatted(taskId, clamped + 1);
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static void appendOrder(StringBuilder sb, List<ResourceLocation> order,
                                    AutoWorkCompatService compatService, boolean zh) {
        if (order.isEmpty()) {
            sb.append(zh ? "    （空）\n" : "    (empty)\n");
            return;
        }
        for (int i = 0; i < order.size(); i++) {
            ResourceLocation taskId = order.get(i);
            String taskName = TaskManager.findTask(taskId)
                    .map(task -> task.getName().getString())
                    .orElse(zh ? "<未注册>" : "<unregistered>");
            sb.append("    ").append(i + 1).append(". ").append(taskId)
                    .append(" [").append(taskName).append("]");
            AutoWorkCompatService.ReportEntry compatEntry = compatService == null
                    ? null : compatService.getEntry(taskId);
            if (compatEntry != null) {
                sb.append(" [detector=").append(compatEntry.level())
                        .append(", reason=").append(compatEntry.reason()).append("]");
            }
            sb.append('\n');
        }
    }

    private static List<String> taskIdValues() {
        List<String> out = new ArrayList<>();
        for (IMaidTask task : TaskManager.getTaskIndex()) {
            out.add(task.getUid().toString());
        }
        return out;
    }

    private static ResourceLocation parseTaskId(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return new ResourceLocation(raw.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    public record Result(String action,
                         boolean enabled,
                         String presetId,
                         String name,
                         String taskId,
                         int position) {
    }
}
