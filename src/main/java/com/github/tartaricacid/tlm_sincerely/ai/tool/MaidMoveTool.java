package com.github.tartaricacid.tlm_sincerely.ai.tool;

import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.tlm_sincerely.ai.move.GoHome;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveAction;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveDescribe;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveMath;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveOrderPlacer;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveText;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.MoveConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.NumberParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.UUID;

/**
 * 移动指令工具 {@code maid_move}：让女仆在一段时间内走近 / 远离 / 前往某处 / 留在原地，或回家。
 *
 * <p>守护（{@code maid_guard}）、放宽工作时间（{@code maid_work_time}）、取消指令（{@code maid_cancel}）
 * 不属于"移动"，各自是独立的工具。工具只做参数校验、目标解析与指令登记
 * （见 {@link MoveOrderPlacer}）；真正的移动下发、暂停跟随与恢复、永久指令提醒都由
 * {@code MoveDirectiveManager} 在服务端 tick 上执行。
 *
 * <p>诊断日志：指令写入与被拒绝的每一种情况都记一条 INFO（"女仆答应了却没动"要靠日志定位）。
 */
public class MaidMoveTool extends MoveToolBase<MaidMoveTool.Result> {
    private static final String TOOL_ID = "maid_move";

    private static final Codec<Result> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("action", "").forGetter(Result::action),
            Codec.STRING.optionalFieldOf("target", "").forGetter(Result::target),
            Codec.DOUBLE.optionalFieldOf("x", Double.NaN).forGetter(Result::x),
            Codec.DOUBLE.optionalFieldOf("y", Double.NaN).forGetter(Result::y),
            Codec.DOUBLE.optionalFieldOf("z", Double.NaN).forGetter(Result::z),
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
        return MoveText.moveSummary(PromptLang.isChinese(PromptLang.of(maid)));
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        boolean zh = PromptLang.isChinese(PromptLang.of(maid));

        StringParameter action = StringParameter.create()
                .setDescription(MoveText.moveActionDesc(zh))
                .addEnumValues(MoveAction.APPROACH.id(), MoveAction.RETREAT.id(), MoveAction.GOTO.id(),
                        MoveAction.STAY.id(), MoveAction.GO_HOME.id())
                .setMinLength(1)
                .setMaxLength(16);
        root.addProperties("action", action);

        root.addProperties("target", StringParameter.create()
                .setDescription(MoveText.targetDesc(zh))
                .setMinLength(1)
                .setMaxLength(64), false);

        root.addProperties("x", NumberParameter.create()
                .setDescription(MoveText.coordDesc(zh, "x")), false);
        root.addProperties("y", NumberParameter.create()
                .setDescription(MoveText.coordDesc(zh, "y")), false);
        root.addProperties("z", NumberParameter.create()
                .setDescription(MoveText.coordDesc(zh, "z")), false);

        root.addProperties("distance", NumberParameter.create()
                .setDescription(MoveText.moveDistanceDesc(zh))
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

    // ===== 主线程逻辑 =====

    @Override
    protected String handle(Result result, Ctx ctx) {
        EntityMaid maid = ctx.maid();
        boolean zh = ctx.zh();

        String rawAction = safe(result.action()).trim();
        MoveAction action = MoveAction.parse(rawAction);
        if (action == null) {
            logRejected(ctx, "unknown-action", "action=" + rawAction);
            return MoveText.unknownAction(zh, rawAction);
        }
        if (action != MoveAction.APPROACH && action != MoveAction.RETREAT && action != MoveAction.GOTO
                && action != MoveAction.STAY && action != MoveAction.GO_HOME) {
            // guard / work_time / cancel 是别的工具：告诉模型该用哪个，而不是只回"未知"
            logRejected(ctx, "wrong-tool", "action=" + action.id());
            return MoveText.wrongTool(zh, action.id());
        }

        UUID ownerId = maid.getOwnerUUID();
        if (ownerId == null) {
            logRejected(ctx, "owner-unavailable", "action=" + action.id());
            return MoveText.ownerUnavailable(zh);
        }

        if (action == MoveAction.GO_HOME) {
            return goHome(result, ctx);
        }

        // goto 需要三个坐标；approach / retreat 需要目标实体；stay 两个都不需要
        double targetX = result.x();
        double targetY = result.y();
        double targetZ = result.z();
        UUID targetId = null;
        String targetDisplay = "";
        if (action.needsTarget()) {
            MoveTargetResolver.Resolution resolution = MoveTargetResolver.resolve(
                    ctx.level().getServer(), ctx.level(), maid, ownerId, result.target());
            if (resolution.error() != null) {
                logRejected(ctx, "target-resolution", "action=" + action.id() + " target=" + safe(result.target())
                        + " detail=" + resolution.error());
                return resolution.error();
            }
            targetId = resolution.targetId();
            targetDisplay = resolution.display();
        } else if (action.needsCoords()) {
            if (isMissing(targetX) || isMissing(targetY) || isMissing(targetZ)) {
                logRejected(ctx, "missing-coords", "action=" + action.id());
                return MoveText.missingCoords(zh);
            }
            targetDisplay = MoveDescribe.coordText(targetX, targetY, targetZ);
        }

        String[] immobile = immobileState(maid);
        if (immobile != null) {
            logRejected(ctx, "immobile", "action=" + action.id() + " state=" + immobile[1]);
            return MoveText.cannotMoveNow(zh, ctx.maidName(), immobile[0], immobile[1]);
        }

        boolean permanent = result.permanent();
        if (permanent && !MoveConfig.MOVE_ALLOW_PERMANENT.get()) {
            logRejected(ctx, "permanent-not-allowed", "action=" + action.id());
            return MoveText.permanentNotAllowed(zh);
        }

        double distance = MoveMath.clampDistance(result.distance(), action.defaultDistance());
        int seconds = MoveMath.resolveDurationSeconds(result.durationSeconds(),
                MoveConfig.MOVE_DEFAULT_DURATION_SECONDS.get(), MoveConfig.MOVE_MAX_DURATION_SECONDS.get());
        MoveOrderPlacer.Placement placement = MoveOrderPlacer.place(new MoveOrderPlacer.Request(
                maid, ownerId, action, targetId, targetX, targetY, targetZ, distance, permanent, seconds));
        if (placement.limitReached()) {
            return MoveText.limitReached(zh, placement.maxActive());
        }

        String message;
        if (permanent) {
            message = MoveText.startedPermanent(zh, ctx.maidName(), MoveText.orderVerb(zh, action.id(), targetDisplay))
                    + " " + MoveText.permanentNote(zh);
        } else if (action == MoveAction.STAY) {
            message = MoveText.stayStarted(zh, ctx.maidName(), placement.suspension().clearedWorkPos(), false, seconds);
        } else {
            message = MoveText.started(zh, action.id(), ctx.maidName(), targetDisplay, seconds);
        }
        if (maid.isHomeModeEnable() && action != MoveAction.STAY) {
            message = message + " " + MoveText.homeModeNote(zh);
        }
        String scheduleNote = scheduleNote(zh, placement.schedule());
        if (!scheduleNote.isEmpty()) {
            message = message + " " + scheduleNote;
        }
        return message;
    }

    /**
     * {@code go_home}：不接受 duration_seconds / permanent（回家本来就是永久性改变），
     * 有工作坐标就传送回工作坐标并打开居家模式，没有就返回 {@link MoveText#noWorkPos}。
     */
    private String goHome(Result result, Ctx ctx) {
        if (result.permanent() || result.durationSeconds() > 0) {
            logRejected(ctx, "unexpected-args", "action=go_home permanent=" + result.permanent()
                    + " durationSeconds=" + result.durationSeconds());
            return MoveText.goHomeIgnoredArgs(ctx.zh());
        }
        return GoHome.execute(ctx.level(), ctx.maid()).message();
    }

    private static boolean isMissing(double value) {
        return Double.isNaN(value) || Double.isInfinite(value);
    }

    /** {@code x/y/z} 与 {@code duration_seconds} 缺省时由 codec 填入哨兵值（NaN / -1）。 */
    public record Result(String action, String target, double x, double y, double z,
                         double distance, int durationSeconds, boolean permanent) {
    }
}
