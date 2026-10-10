package com.github.tartaricacid.tlm_sincerely.ai.tool;

import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.MemoryConfig;
import com.github.tartaricacid.tlm_sincerely.memory.MaidMemory;
import com.github.tartaricacid.tlm_sincerely.memory.MaidMemoryManager;
import com.github.tartaricacid.tlm_sincerely.memory.MaidMemory.MemoryEntry;
import com.github.tartaricacid.tlm_sincerely.memory.MemoryMaintenanceManager;
import com.github.tartaricacid.tlm_sincerely.memory.MemoryChatTracker;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ArrayParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class MaidMemoryTool implements ITool<MaidMemoryTool.Result> {
    private static final String TOOL_ID = "tlm_memory";

    private static final Set<String> MAINTENANCE_ACTIONS = Set.of("search", "recall", "merge");

    private static final String TOOL_DESC_ZH = """
            管理女仆的长期记忆（键值对）。
            remember（记住）：保存关于玩家的重要信息（喜好、经历、约定）。
            recall（回忆）：读取某条记忆的完整内容。
            forget（遗忘）：玩家要求时，或这条记忆已经没用时，删除它。
            search（搜索）：按关键词查找记忆（匹配 key 或 value，最多返回 10 条）。
            merge（合并）：把 2~5 条相近的 archive 记忆合并成一条。
            """.trim();

    private static final String TOOL_DESC_EN = """
            Manage persistent key-value memories for the maid.
            Use 'remember' to store important facts about the player (preferences, history, promises).
            Use 'recall' to retrieve the full content of a specific memory.
            Use 'forget' to delete a memory when the player asks or when it's no longer relevant.
            Use 'search' to find memories by keyword (matches key or value, returns up to 10 results).
            Use 'merge' to combine 2-5 similar archive entries into one.
            """.trim();

    private static final String MAINTENANCE_MSG_ZH = "记忆整理期间只允许 search、recall、merge 操作。";
    private static final String MAINTENANCE_MSG_EN =
            "Only search/recall/merge are allowed during memory maintenance.";

    /** remember 成功结果的前缀（中/英），用来判断是否需要排整理。 */
    private static final String REMEMBERED_ZH = "已记住";
    private static final String REMEMBERED_EN = "Remembered";

    private static final Codec<Result> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("action").forGetter(Result::action),
            Codec.STRING.optionalFieldOf("key", "").forGetter(Result::key),
            Codec.STRING.optionalFieldOf("value", "").forGetter(Result::value),
            Codec.STRING.optionalFieldOf("importance", MemoryEntry.ARCHIVE).forGetter(Result::importance),
            Codec.STRING.optionalFieldOf("query", "").forGetter(Result::query),
            Codec.list(Codec.STRING).optionalFieldOf("keys", List.of()).forGetter(Result::keys)
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
                .setDescription(zh ? "要执行的记忆操作" : "The action to perform on memories")
                .addEnumValues("remember", "recall", "forget", "search", "merge");
        root.addProperties("action", action);

        StringParameter key = StringParameter.create()
                .setDescription(zh
                        ? "记忆的 key（记忆的唯一标识）。remember 时可以是新 key；recall/forget 时用已有的 key（见上下文里的记忆列表，或工具报错里的「可用的 key」）；merge 时是合并后的目标 key。"
                        : "The memory key (unique identifier for the memory). For 'remember' this may be a new key; for 'recall'/'forget' use an existing key (see preview or 'Available keys' in tool errors). For 'merge' this is the target key.");
        root.addProperties("key", key, false);

        StringParameter value = StringParameter.create()
                .setDescription(zh
                        ? "记忆的内容（value）。remember 和 merge 时必填。"
                        : "The memory value. Required for 'remember' and 'merge' actions.");
        root.addProperties("value", value, false);

        StringParameter importance = StringParameter.create()
                .setDescription(zh
                        ? "记忆的重要程度：core（始终完整显示）或 archive（只显示摘要）"
                        : "Memory importance: 'core' (always shown in full) or 'archive' (shown as preview)")
                .addEnumValues(MemoryEntry.CORE, MemoryEntry.ARCHIVE);
        root.addProperties("importance", importance, false);

        StringParameter query = StringParameter.create()
                .setDescription(zh
                        ? "search 操作的搜索关键词，返回匹配的记忆 key 和摘要（最多 10 条）。"
                        : "Search query for 'search' action. Returns matching memory keys and previews (up to 10).");
        root.addProperties("query", query, false);

        ArrayParameter keys = ArrayParameter.create()
                .setDescription(zh
                        ? "merge 操作要合并的来源 key（2~5 条已有的 archive 记忆）。"
                        : "Source keys to merge (2-5 existing archive entries) for 'merge' action.")
                .setItems(StringParameter.create())
                .setRange(2, 5)
                .setUniqueItems(true);
        root.addProperties("keys", keys, false);

        return root;
    }

    @Override
    public Codec<Result> codec() {
        return CODEC;
    }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        boolean zh = PromptLang.isChinese(PromptLang.of(maid));
        if (!MemoryConfig.ENABLED.get()) {
            return callback.addToolResult(zh ? "记忆系统当前未启用。" : "Memory system is currently disabled.", toolCallId);
        }

        boolean maintenanceCallback = MemoryChatTracker.isMaintenance(callback);
        if (maintenanceCallback && !MemoryMaintenanceManager.isMaintaining(maid.getUUID())) {
            return callback.addToolResult(zh
                    ? "记忆整理请求已过期，没有做任何修改。"
                    : "Memory maintenance request expired; no changes were applied.", toolCallId);
        }
        if (MemoryMaintenanceManager.isMaintaining(maid.getUUID())) {
            MemoryMaintenanceManager.recordToolActivity(maid.getUUID());
            if (!maintenanceCallback) {
                return callback.addToolResult(zh ? MAINTENANCE_MSG_ZH : MAINTENANCE_MSG_EN, toolCallId);
            }
        }

        MaidMemory memory = MaidMemoryManager.load(maid.getUUID());

        return switch (result.action()) {
            case "remember" -> {
                String msg = remember(memory, maid, result, zh);
                if (MemoryConfig.TIDY_ENABLED.get()
                        && (msg.startsWith(REMEMBERED_ZH) || msg.startsWith(REMEMBERED_EN))) {
                    MemoryMaintenanceManager.checkAndScheduleTidy(maid);
                }
                yield callback.addToolResult(msg, toolCallId);
            }
            case "recall" -> callback.addToolResult(recall(memory, maid, result, zh), toolCallId);
            case "forget" -> callback.addToolResult(forget(memory, maid, result, zh), toolCallId);
            case "search" -> callback.addToolResult(search(memory, result, zh), toolCallId);
            case "merge" -> callback.addToolResult(merge(memory, maid, result, zh), toolCallId);
            default -> callback.addToolResult(ITool.invalidParam("action",
                    List.of("remember", "recall", "forget", "search", "merge"),
                    (zh ? "未知的操作：" : "Unknown action: ") + result.action()), toolCallId);
        };
    }

    @Override
    public String invocationSummary(Result result) {
        return TOOL_ID + " { " + result.action() + " " + result.key() + " }";
    }

    private String remember(MaidMemory memory, EntityMaid maid, Result result, boolean zh) {
        if (result.key().isEmpty()) {
            return ITool.invalidParam("key", List.of(zh ? "<记忆的 key>" : "<memory key>"),
                    zh ? "remember 操作必须提供 key" : "key is required for 'remember' action");
        }
        if (result.value().isEmpty()) {
            return ITool.invalidParam("value", List.of(zh ? "<记忆的内容>" : "<memory value>"),
                    zh ? "remember 操作必须提供 value" : "value is required for 'remember' action");
        }

        int max = MemoryConfig.MAX_MEMORIES.get();
        String trimmedKey = result.key().trim();
        if (memory.size() >= max && !memory.getMemories().containsKey(trimmedKey)) {
            if (!MemoryConfig.AUTO_EVICT.get()) {
                return (zh ? "记忆已满（上限 %d 条）。请先用 forget 删除一些记忆。"
                        : "Memory limit reached (%d max). Delete some memories first with 'forget'.").formatted(max);
            }
            Optional<String> oldestArchive = memory.findOldestArchiveKey();
            if (oldestArchive.isEmpty()) {
                return (zh ? "记忆已满（上限 %d 条），而且全部是 core 记忆。请询问玩家要忘掉哪一条。"
                        : "Memory limit reached (%d max) and all entries are core. "
                        + "Ask the player which memory to forget.").formatted(max);
            }
            String evictedKey = oldestArchive.get();
            memory.forget(evictedKey);
            memory.set(result.key(), result.value(), result.importance(), getSource(maid));
            if (!MaidMemoryManager.save(maid.getUUID(), memory)) {
                return zh ? "淘汰旧记忆后保存失败，请重试。" : "Memory save failed after eviction. Please try again.";
            }
            return (zh ? "已记住 '%s' = \"%s\"（%s）。已淘汰最旧的 archive 记忆 '%s' 来腾出空间。"
                    : "Remembered '%s' = \"%s\" (%s). Evicted oldest archive '%s' to make room.").formatted(
                    result.key(), result.value(), result.importance(), evictedKey);
        }

        memory.set(result.key(), result.value(), result.importance(), getSource(maid));
        if (!MaidMemoryManager.save(maid.getUUID(), memory)) {
            return zh ? "保存记忆失败，请重试。" : "Memory save failed. Please try again.";
        }
        return (zh ? "已记住 '%s' = \"%s\"（%s）" : "Remembered '%s' = \"%s\" (%s)")
                .formatted(result.key(), result.value(), result.importance());
    }

    private String recall(MaidMemory memory, EntityMaid maid, Result result, boolean zh) {
        if (result.key().isEmpty()) {
            List<String> keys = memory.keys();
            return ITool.invalidParam("key", keys.isEmpty() ? List.of(zh ? "（没有任何记忆）" : "no memories stored") : keys,
                    zh ? "recall 操作必须提供 key" : "key is required for 'recall' action");
        }

        Optional<MemoryEntry> entry = memory.get(result.key());
        if (entry.isEmpty()) {
            return (zh ? "没有找到记忆 key '%s'。可用的 key：%s"
                    : "Memory key '%s' not found. Available keys: %s").formatted(
                    result.key(), memory.keys());
        }

        memory.touch(result.key());
        if (!MaidMemoryManager.save(maid.getUUID(), memory)) {
            return zh ? "更新记忆访问记录失败，请重试。" : "Memory touch save failed. Please try again.";
        }

        MemoryEntry mem = entry.get();
        String output = "[%s] %s: %s".formatted(mem.importance(), result.key(), mem.value());
        if (MemoryConfig.SHOW_SOURCE.get() && !mem.source().isEmpty()) {
            output += (zh ? "（来源：" : " (source: ") + mem.source() + (zh ? "）" : ")");
        }
        return output;
    }

    private String forget(MaidMemory memory, EntityMaid maid, Result result, boolean zh) {
        if (result.key().isEmpty()) {
            List<String> keys = memory.keys();
            return ITool.invalidParam("key", keys.isEmpty() ? List.of(zh ? "（没有任何记忆）" : "no memories stored") : keys,
                    zh ? "forget 操作必须提供 key" : "key is required for 'forget' action");
        }

        if (!memory.getMemories().containsKey(result.key())) {
            return (zh ? "没有找到记忆 key '%s'。可用的 key：%s"
                    : "Memory key '%s' not found. Available keys: %s").formatted(
                    result.key(), memory.keys());
        }

        memory.forget(result.key());
        if (!MaidMemoryManager.save(maid.getUUID(), memory)) {
            return zh ? "删除记忆失败，请重试。" : "Memory forget save failed. Please try again.";
        }
        return (zh ? "已忘掉记忆 '%s'" : "Forgot memory '%s'").formatted(result.key());
    }

    private String search(MaidMemory memory, Result result, boolean zh) {
        String query = result.query().trim();
        if (query.isEmpty()) {
            return ITool.invalidParam("query", List.of(zh ? "<搜索关键词>" : "<search query>"),
                    zh ? "search 操作必须提供 query" : "query is required for 'search' action");
        }

        List<Map.Entry<String, MemoryEntry>> matches = memory.search(query);
        if (matches.isEmpty()) {
            return (zh ? "没有匹配 '%s' 的记忆。" : "No memories match '%s'.").formatted(query);
        }

        int previewLength = MemoryConfig.CONTEXT_PREVIEW_LENGTH.get();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, MemoryEntry> entry : matches) {
            String value = entry.getValue().value();
            String preview = value.length() > previewLength
                    ? value.substring(0, previewLength) + "..."
                    : value;
            sb.append("  %s: \"%s\"\n".formatted(entry.getKey(), preview));
        }
        sb.append((zh ? "共 %d 条匹配" : "%d matches").formatted(matches.size()));
        return sb.toString();
    }

    private String merge(MaidMemory memory, EntityMaid maid, Result result, boolean zh) {
        List<String> keys = result.keys();
        if (keys == null || keys.size() < 2 || keys.size() > 5) {
            return ITool.invalidParam("keys", List.of(zh ? "2~5 个来源 key" : "2-5 source keys"),
                    zh ? "merge 需要 2~5 个来源 key" : "merge requires 2-5 source keys");
        }
        if (result.key().isEmpty()) {
            return ITool.invalidParam("key", List.of(zh ? "<目标 key>" : "<target key>"),
                    zh ? "merge 操作必须提供目标 key" : "target key is required for 'merge' action");
        }
        if (result.value().isEmpty()) {
            return ITool.invalidParam("value", List.of(zh ? "<合并后的内容>" : "<merged value>"),
                    zh ? "merge 操作必须提供合并后的 value" : "merged value is required for 'merge' action");
        }

        List<String> trimmedKeys = new ArrayList<>();
        for (String k : keys) {
            String tk = k.trim();
            if (!memory.getMemories().containsKey(tk)) {
                return (zh ? "找不到来源 key '%s'。" : "Source key '%s' not found.").formatted(tk);
            }
            MemoryEntry entry = memory.getMemories().get(tk);
            if (!MemoryEntry.ARCHIVE.equals(entry.importance())) {
                return zh ? "merge 只能合并已有的 archive 记忆，core 记忆受保护。"
                        : "merge can only combine existing archive memories; core entries are protected.";
            }
            if (trimmedKeys.contains(tk)) {
                return (zh ? "来源 key '%s' 重复。" : "Duplicate source key '%s'.").formatted(tk);
            }
            trimmedKeys.add(tk);
        }

        String targetKey = result.key().trim();
        MemoryEntry existingTarget = memory.getMemories().get(targetKey);
        if (existingTarget != null && MemoryEntry.CORE.equals(existingTarget.importance())) {
            return (zh ? "目标 key '%s' 是 core 记忆，不能被 merge 覆盖。"
                    : "Target key '%s' is a core entry and cannot be overwritten by merge.").formatted(targetKey);
        }

        for (String tk : trimmedKeys) {
            memory.forget(tk);
        }
        memory.set(targetKey, result.value(), MemoryEntry.ARCHIVE, getSource(maid));
        if (!MaidMemoryManager.save(maid.getUUID(), memory)) {
            return zh ? "合并后保存失败，请重试。" : "Memory merge save failed. Please try again.";
        }

        MemoryMaintenanceManager.logMerge(maid.getUUID(), trimmedKeys, targetKey);

        return (zh ? "已把 %d 条记忆合并为 '%s'" : "Merged %d entries into '%s'").formatted(trimmedKeys.size(), targetKey);
    }

    private String getSource(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        if (owner != null) {
            return owner.getName().getString();
        }
        return "";
    }

    public record Result(String action, String key, String value, String importance,
                         String query, List<String> keys) {
    }
}
