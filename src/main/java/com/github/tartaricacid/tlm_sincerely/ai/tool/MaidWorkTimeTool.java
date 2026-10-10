package com.github.tartaricacid.tlm_sincerely.ai.tool;

import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveAction;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveMath;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveOrderPlacer;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveText;
import com.github.tartaricacid.tlm_sincerely.ai.overtime.OvertimeManager;
import com.github.tartaricacid.tlm_sincerely.ai.overtime.OvertimeText;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.MoveConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.UUID;

/**
 * 放宽工作时间工具 {@code maid_work_time}：不移动，只把女仆的日程临时改成"全天工作"
 * （TLM 的 {@code MaidSchedule.ALL}），让她夜里也能被切换工作任务、也能执行移动；
 * 限时 / 永久都支持，和其他指令一样占名额。
 *
 * <p>不做目标 / 坐标解析，也不走"坐着 / 睡觉 / 骑乘不能移动"的检查（这条指令本来就不移动）。
 * 正在睡觉时返回 {@link MoveText#workTimeSleeping} 且不创建指令；日程本来就是全天工作时返回
 * {@link MoveText#workTimeAlready} 且不创建指令——这两种情况都不会改变女仆的任何状态。
 */
public class MaidWorkTimeTool extends MoveToolBase<MaidWorkTimeTool.Result> {
    private static final String TOOL_ID = "maid_work_time";

    private static final Codec<Result> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("duration_seconds", -1).forGetter(Result::durationSeconds),
            Codec.BOOL.optionalFieldOf("permanent", false).forGetter(Result::permanent)
    ).apply(instance, Result::new));

    @Override
    public String id() {
        return TOOL_ID;
    }

    @Override
    public String summary(EntityMaid maid) {
        return MoveText.workTimeSummary(PromptLang.isChinese(PromptLang.of(maid)));
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        addDurationParameters(root, PromptLang.isChinese(PromptLang.of(maid)));
        return root;
    }

    @Override
    public Codec<Result> codec() {
        return CODEC;
    }

    @Override
    public String invocationSummary(Result result) {
        return TOOL_ID;
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
        if (maid.isSleeping()) {
            logRejected(ctx, "sleeping", "");
            return MoveText.workTimeSleeping(zh, ctx.maidName());
        }
        if (OvertimeManager.isScheduleAll(maid)) {
            logRejected(ctx, "schedule-already-all", "");
            return MoveText.workTimeAlready(zh, ctx.maidName());
        }
        if (OvertimeManager.isTiredLockedOut(maid)) {
            logRejected(ctx, "tired-resting", "");
            return OvertimeText.tiredNote(zh, ctx.maidName());
        }
        boolean permanent = result.permanent();
        if (permanent && !MoveConfig.MOVE_ALLOW_PERMANENT.get()) {
            logRejected(ctx, "permanent-not-allowed", "");
            return MoveText.permanentNotAllowed(zh);
        }
        int seconds = MoveMath.resolveDurationSeconds(result.durationSeconds(),
                MoveConfig.MOVE_DEFAULT_DURATION_SECONDS.get(), MoveConfig.MOVE_MAX_DURATION_SECONDS.get());
        // work_time 不暂停跟随（不碰居家模式与工作坐标），只放宽日程
        MoveOrderPlacer.Placement placement = MoveOrderPlacer.place(new MoveOrderPlacer.Request(
                maid, ownerId, MoveAction.WORK_TIME, null, 0.0D, 0.0D, 0.0D, 0.0D, permanent, seconds));
        if (placement.limitReached()) {
            return MoveText.limitReached(zh, placement.maxActive());
        }
        return MoveText.workTimeStarted(zh, ctx.maidName(), permanent, seconds);
    }

    public record Result(int durationSeconds, boolean permanent) {
    }
}
