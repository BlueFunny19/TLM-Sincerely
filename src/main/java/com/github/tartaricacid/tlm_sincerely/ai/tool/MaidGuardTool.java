package com.github.tartaricacid.tlm_sincerely.ai.tool;

import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardCondition;
import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardData;
import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardEntry;
import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardRules;
import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardTaskDriver;
import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardText;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveMath;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveText;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.MoveConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.NumberParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * 守护工具 {@code maid_guard}：编辑女仆的"守护名单"，并让她进入守护任务（{@link com.github.tartaricacid.tlm_sincerely.ai.guard.GuardTask}）。
 *
 * <p>名单的每一项是 目标 + 条件（一直守 / 对方血量低于多少才守）+ 守护半径，名单顺序就是优先级；
 * 守谁由 {@link GuardTaskDriver} 随时对账决定。这个工具只负责改名单、切任务、设时限，
 * 然后立刻对账一次让变化马上生效。
 */
public class MaidGuardTool extends MoveToolBase<MaidGuardTool.Result> {
    private static final String TOOL_ID = "maid_guard";

    private static final String ACTION_ADD = "add";
    private static final String ACTION_REMOVE = "remove";
    private static final String ACTION_CLEAR = "clear";
    private static final String ACTION_LIST = "list";

    private static final String CONDITION_ALWAYS = "always";
    private static final String CONDITION_HP_BELOW = "hp_below";
    private static final String CONDITION_HP_PERCENT_BELOW = "hp_percent_below";

    private static final String PRIORITY_HIGHEST = "highest";
    private static final String PRIORITY_LOWEST = "lowest";
    private static final String PRIORITY_KEEP = "keep";

    private static final Codec<Result> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("action", ACTION_ADD).forGetter(Result::action),
            Codec.STRING.optionalFieldOf("target", "").forGetter(Result::target),
            Codec.STRING.optionalFieldOf("condition", "").forGetter(Result::condition),
            Codec.DOUBLE.optionalFieldOf("threshold", Double.NaN).forGetter(Result::threshold),
            Codec.STRING.optionalFieldOf("priority", PRIORITY_KEEP).forGetter(Result::priority),
            Codec.DOUBLE.optionalFieldOf("distance", Double.NaN).forGetter(Result::distance),
            Codec.INT.optionalFieldOf("duration_seconds", -1).forGetter(Result::durationSeconds),
            Codec.BOOL.optionalFieldOf("temporary", false).forGetter(Result::temporary)
    ).apply(instance, Result::new));

    @Override
    public String id() {
        return TOOL_ID;
    }

    @Override
    public String summary(EntityMaid maid) {
        return GuardText.summary(PromptLang.isChinese(PromptLang.of(maid)), MoveConfig.MOVE_DEFAULT_DURATION_SECONDS.get());
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        boolean zh = PromptLang.isChinese(PromptLang.of(maid));

        root.addProperties("action", StringParameter.create()
                .setDescription(GuardText.actionDesc(zh))
                .addEnumValues(ACTION_ADD, ACTION_REMOVE, ACTION_CLEAR, ACTION_LIST)
                .setMaxLength(16), false);

        root.addProperties("target", StringParameter.create()
                .setDescription(MoveText.targetDesc(zh))
                .setMinLength(1)
                .setMaxLength(64), false);

        root.addProperties("condition", StringParameter.create()
                .setDescription(GuardText.conditionDesc(zh))
                .addEnumValues(CONDITION_ALWAYS, CONDITION_HP_BELOW, CONDITION_HP_PERCENT_BELOW)
                .setMaxLength(24), false);

        root.addProperties("threshold", NumberParameter.create()
                .setDescription(GuardText.thresholdDesc(zh))
                .setMinimum(1)
                .setMaximum(1000), false);

        root.addProperties("priority", StringParameter.create()
                .setDescription(GuardText.priorityDesc(zh))
                .addEnumValues(PRIORITY_HIGHEST, PRIORITY_LOWEST, PRIORITY_KEEP)
                .setMaxLength(16), false);

        root.addProperties("distance", NumberParameter.create()
                .setDescription(GuardText.distanceDesc(zh))
                .setMinimum((int) MoveMath.MIN_DISTANCE)
                .setMaximum((int) MoveMath.MAX_DISTANCE), false);

        addDurationParameters(root, zh);
        return root;
    }

    @Override
    public Codec<Result> codec() {
        return CODEC;
    }

    @Override
    public String invocationSummary(Result result) {
        return "%s { %s %s }".formatted(TOOL_ID, safe(result.action()), safe(result.target()));
    }

    @Override
    protected String handle(Result result, Ctx ctx) {
        String action = result.action() == null || result.action().isBlank()
                ? ACTION_ADD : result.action().trim().toLowerCase(Locale.ROOT);
        return switch (action) {
            case ACTION_ADD -> handleAdd(result, ctx);
            case ACTION_REMOVE -> handleRemove(result, ctx);
            case ACTION_CLEAR -> handleClear(ctx);
            case ACTION_LIST -> handleList(ctx);
            default -> {
                logRejected(ctx, "unknown-action", "action=" + safe(result.action()));
                yield GuardText.unknownAction(ctx.zh(), safe(result.action()));
            }
        };
    }

    private String handleList(Ctx ctx) {
        EntityMaid maid = ctx.maid();
        GuardData data = GuardTaskDriver.dataOf(maid);
        boolean guarding = GuardTaskDriver.isGuarding(maid);
        String current = null;
        if (guarding) {
            GuardTaskDriver.Outcome outcome = GuardTaskDriver.reconcile(maid);
            current = outcome.chosen() == null ? null : outcome.chosen().name();
        }
        return GuardText.listing(ctx.zh(), data.rules(), guarding, current);
    }

    private String handleClear(Ctx ctx) {
        EntityMaid maid = ctx.maid();
        boolean left = GuardTaskDriver.leave(maid, "list-cleared");
        GuardTaskDriver.save(maid, GuardTaskDriver.dataOf(maid).withRules(GuardRules.EMPTY));
        return GuardText.cleared(ctx.zh(), ctx.maidName(), left);
    }

    private String handleRemove(Result result, Ctx ctx) {
        EntityMaid maid = ctx.maid();
        boolean zh = ctx.zh();
        GuardData data = GuardTaskDriver.dataOf(maid);
        GuardEntry entry = findOnList(data.rules(), maid.getOwnerUUID(), safe(result.target()));
        if (entry == null) {
            logRejected(ctx, "not-on-list", "target=" + safe(result.target()));
            return GuardText.removeNotFound(zh, safe(result.target()), data.rules());
        }
        GuardRules rules = data.rules().without(entry.targetId());
        GuardTaskDriver.save(maid, data.withRules(rules));
        String message = GuardText.removed(zh, ctx.maidName(), entry.name(), rules);
        if (GuardTaskDriver.isGuarding(maid)) {
            GuardTaskDriver.Outcome outcome = GuardTaskDriver.reconcile(maid);
            message = message + "\n" + GuardText.statusLine(zh, true, outcome.chosen() == null ? null : outcome.chosen().name());
        }
        return message;
    }

    private String handleAdd(Result result, Ctx ctx) {
        EntityMaid maid = ctx.maid();
        boolean zh = ctx.zh();

        UUID ownerId = maid.getOwnerUUID();
        if (ownerId == null) {
            logRejected(ctx, "owner-unavailable", "");
            return MoveText.ownerUnavailable(zh);
        }
        MoveTargetResolver.Resolution resolution = MoveTargetResolver.resolve(
                ctx.level().getServer(), ctx.level(), maid, ownerId, result.target());
        if (resolution.error() != null) {
            logRejected(ctx, "target-resolution", "target=" + safe(result.target()) + " detail=" + resolution.error());
            return resolution.error();
        }
        String[] immobile = immobileState(maid);
        if (immobile != null) {
            logRejected(ctx, "immobile", "state=" + immobile[1]);
            return MoveText.cannotMoveNow(zh, ctx.maidName(), immobile[0], immobile[1]);
        }

        GuardData data = GuardTaskDriver.dataOf(maid);
        UUID targetId = resolution.targetId();
        int existingIndex = data.rules().indexOf(targetId);
        boolean existed = existingIndex >= 0;
        GuardEntry previous = existed ? data.rules().entries().get(existingIndex) : null;
        if (!existed && data.rules().isFull()) {
            logRejected(ctx, "list-full", "");
            return GuardText.full(zh, GuardRules.MAX_ENTRIES);
        }

        GuardCondition condition = previous == null ? GuardCondition.ALWAYS : previous.condition();
        String conditionName = safe(result.condition()).trim().toLowerCase(Locale.ROOT);
        if (!conditionName.isEmpty()) {
            boolean hasThreshold = !Double.isNaN(result.threshold()) && result.threshold() > 0.0D;
            switch (conditionName) {
                case CONDITION_ALWAYS -> condition = GuardCondition.ALWAYS;
                case CONDITION_HP_BELOW, CONDITION_HP_PERCENT_BELOW -> {
                    if (!hasThreshold) {
                        logRejected(ctx, "threshold-missing", "condition=" + conditionName);
                        return GuardText.thresholdRequired(zh);
                    }
                    condition = conditionName.equals(CONDITION_HP_BELOW)
                            ? GuardCondition.hpBelow(result.threshold())
                            : GuardCondition.hpPercentBelow(result.threshold());
                }
                default -> {
                    logRejected(ctx, "unknown-condition", "condition=" + conditionName);
                    return GuardText.unknownCondition(zh, conditionName);
                }
            }
        }
        double distance = Double.isNaN(result.distance())
                ? (previous == null ? GuardEntry.DEFAULT_DISTANCE : previous.distance())
                : result.distance();
        GuardEntry entry = new GuardEntry(targetId, resolution.display(), condition, distance);
        GuardRules.Placement placement = switch (safe(result.priority()).trim().toLowerCase(Locale.ROOT)) {
            case PRIORITY_HIGHEST -> GuardRules.Placement.FIRST;
            case PRIORITY_LOWEST -> GuardRules.Placement.LAST;
            default -> GuardRules.Placement.KEEP;
        };
        GuardRules rules = data.rules().with(entry, placement);

        // 时限：已经在守护任务里、又没提时限就沿用；其余按配置决定（默认一直守）
        long expireAt = data.expireAtGameTick();
        MoveMath.Timing timing = null;
        boolean wantsTime = result.temporary() || result.durationSeconds() > 0;
        if (wantsTime || !GuardTaskDriver.isGuarding(maid)) {
            timing = timingOf(result.temporary(), result.durationSeconds());
            expireAt = timing.permanent() ? 0L : ctx.level().getGameTime() + timing.seconds() * 20L;
        }

        GuardTaskDriver.enter(maid, data.withRules(rules).withExpireAt(expireAt));
        GuardTaskDriver.dropForeignOrder(maid, rules);
        GuardTaskDriver.Outcome outcome = GuardTaskDriver.reconcile(maid);

        StringBuilder message = new StringBuilder(GuardText.added(zh, ctx.maidName(), entry, existed, rules));
        message.append('\n').append(GuardText.statusLine(zh, true, outcome.chosen() == null ? null : outcome.chosen().name()));
        if (outcome.placement() != null && outcome.placement().limitReached()) {
            message.append(' ').append(MoveText.limitReached(zh, outcome.placement().maxActive()));
        }
        message.append(' ').append(expireAt == 0L
                ? GuardText.permanentNote(zh)
                : GuardText.temporaryNote(zh, (int) Math.max(1L, (expireAt - ctx.level().getGameTime()) / 20L)));
        if (maid.isHomeModeEnable()) {
            message.append(' ').append(MoveText.homeModeNote(zh));
        }
        if (timing != null && timing.coercedToTimed()) {
            message.append(' ').append(MoveText.coercedToTimedNote(zh, timing.seconds()));
        }
        if (outcome.placement() != null) {
            String scheduleNote = scheduleNote(zh, ctx.maidName(), outcome.placement().overtime());
            if (!scheduleNote.isEmpty()) {
                message.append(' ').append(scheduleNote);
            }
        }
        return message.toString();
    }

    /** 在名单里找 target：me / 我 → 主人；否则先精确（忽略大小写）再子串匹配名字。 */
    @Nullable
    private static GuardEntry findOnList(GuardRules rules, @Nullable UUID ownerId, String rawTarget) {
        String name = rawTarget.trim();
        if (name.isEmpty() || name.equalsIgnoreCase("me") || name.equals("我")) {
            return ownerId == null || !rules.contains(ownerId) ? null : rules.entries().get(rules.indexOf(ownerId));
        }
        for (GuardEntry entry : rules.entries()) {
            if (entry.name().equalsIgnoreCase(name)) {
                return entry;
            }
        }
        for (GuardEntry entry : rules.entries()) {
            if (entry.name().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) {
                return entry;
            }
        }
        return null;
    }

    public record Result(String action, String target, String condition, double threshold, String priority,
                         double distance, int durationSeconds, boolean temporary) {
    }
}
