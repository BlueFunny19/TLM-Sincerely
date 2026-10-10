package com.github.tartaricacid.tlm_sincerely.ai.tool;

import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveAction;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveMath;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveOrderPlacer;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveText;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.MoveConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.NumberParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.UUID;

/**
 * 守护工具 {@code maid_guard}：让女仆在一段时间内待在某人身边，并攻击威胁他的敌对生物。
 *
 * <p>守护本质上是"跟着一个目标并战斗"，不属于单纯的移动，所以和 {@code maid_move} 分开。
 * 它和移动指令共用同一套指令登记 / 暂停跟随 / 卡住兜底机制（一只女仆同一时间只有一条指令，新指令覆盖旧指令）。
 */
public class MaidGuardTool extends MoveToolBase<MaidGuardTool.Result> {
    private static final String TOOL_ID = "maid_guard";

    private static final Codec<Result> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("target", "").forGetter(Result::target),
            Codec.DOUBLE.optionalFieldOf("distance", Double.NaN).forGetter(Result::distance),
            Codec.INT.optionalFieldOf("duration_seconds", -1).forGetter(Result::durationSeconds),
            Codec.BOOL.optionalFieldOf("permanent", false).forGetter(Result::permanent)
    ).apply(instance, Result::new));

    @Override
    public String id() {
        return TOOL_ID;
    }

    @Override
    public String summary(EntityMaid maid) {
        return MoveText.guardSummary(PromptLang.isChinese(PromptLang.of(maid)));
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        boolean zh = PromptLang.isChinese(PromptLang.of(maid));

        root.addProperties("target", StringParameter.create()
                .setDescription(MoveText.targetDesc(zh))
                .setMinLength(1)
                .setMaxLength(64), false);

        root.addProperties("distance", NumberParameter.create()
                .setDescription(MoveText.guardDistanceDesc(zh))
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
        return "%s { %s }".formatted(TOOL_ID, safe(result.target()));
    }

    @Override
    protected String handle(Result result, Ctx ctx) {
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

        boolean permanent = result.permanent();
        if (permanent && !MoveConfig.MOVE_ALLOW_PERMANENT.get()) {
            logRejected(ctx, "permanent-not-allowed", "");
            return MoveText.permanentNotAllowed(zh);
        }

        double distance = MoveMath.clampDistance(result.distance(), MoveAction.GUARD.defaultDistance());
        int seconds = MoveMath.resolveDurationSeconds(result.durationSeconds(),
                MoveConfig.MOVE_DEFAULT_DURATION_SECONDS.get(), MoveConfig.MOVE_MAX_DURATION_SECONDS.get());
        MoveOrderPlacer.Placement placement = MoveOrderPlacer.place(new MoveOrderPlacer.Request(
                maid, ownerId, MoveAction.GUARD, resolution.targetId(), 0.0D, 0.0D, 0.0D, distance, permanent, seconds));
        if (placement.limitReached()) {
            return MoveText.limitReached(zh, placement.maxActive());
        }

        String targetDisplay = resolution.display();
        String message;
        if (permanent) {
            message = MoveText.startedPermanent(zh, ctx.maidName(),
                    MoveText.orderVerb(zh, MoveAction.GUARD.id(), targetDisplay)) + " " + MoveText.permanentNote(zh);
        } else {
            message = MoveText.started(zh, MoveAction.GUARD.id(), ctx.maidName(), targetDisplay, seconds);
        }
        if (maid.isHomeModeEnable()) {
            message = message + " " + MoveText.homeModeNote(zh);
        }
        if (!(maid.getTask() instanceof IAttackTask)) {
            message = message + " " + MoveText.guardNoCombatNote(zh);
        }
        String scheduleNote = scheduleNote(zh, ctx.maidName(), placement.overtime());
        if (!scheduleNote.isEmpty()) {
            message = message + " " + scheduleNote;
        }
        return message;
    }

    public record Result(String target, double distance, int durationSeconds, boolean permanent) {
    }
}
