package com.github.tartaricacid.tlm_sincerely.ai.tool;

import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveAction;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveDescribe;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveDirectiveManager;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveMath;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveOrderData;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveOrders;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveText;
import com.github.tartaricacid.tlm_sincerely.chatbar.MaidFinder;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.MoveConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.BoolParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.NumberParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ChatCompletion;
import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 移动指令工具 {@code maid_move}：让女仆在一段时间内走近 / 远离 / 前往某处 / 守护某人 /
 * 留在原地 / 回家。
 *
 * <p>工具只做参数校验、目标解析与指令登记（见 {@link MoveDirectiveManager}）；
 * 真正的移动下发、攻击目标设置、暂停跟随与恢复、永久指令提醒都由管理器在服务端 tick 上执行。
 *
 * <p>所有给模型看的文字都取自 {@link MoveText}（中英两套，按对话语言选择），
 * 参数名与动作标识保持英文机器标识。
 *
 * <p>诊断日志：指令写入与被拒绝的每一种情况都记一条 INFO（"女仆答应了却没动"要靠日志定位），
 * 暂停 / 恢复 / 外部改动 / 跨重启 / 卡住的日志在 {@link MoveDirectiveManager} 里。
 */
public class MaidMoveTool implements ITool<MaidMoveTool.Result> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MaidMoveTool.class);

    private static final String TOOL_ID = "maid_move";

    /** 目标为"主人"时的写法（英文 + 中文）。 */
    private static final String TARGET_ME = "me";
    private static final String TARGET_ME_ZH = "我";

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
        return MoveText.summary(PromptLang.isChinese(PromptLang.of(maid)));
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        boolean zh = PromptLang.isChinese(PromptLang.of(maid));

        StringParameter action = StringParameter.create()
                .setDescription(MoveText.actionDesc(zh))
                .addEnumValues(MoveAction.APPROACH.id(), MoveAction.RETREAT.id(), MoveAction.GOTO.id(),
                        MoveAction.GUARD.id(), MoveAction.STAY.id(), MoveAction.GO_HOME.id(),
                        MoveAction.WORK_TIME.id(), MoveAction.CANCEL.id())
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
                .setDescription(MoveText.distanceDesc(zh))
                .setMinimum((int) MoveMath.MIN_DISTANCE)
                .setMaximum((int) MoveMath.MAX_DISTANCE), false);

        int maxDuration = MoveConfig.MOVE_MAX_DURATION_SECONDS.get();
        root.addProperties("duration_seconds", IntegerParameter.create()
                .setDescription(MoveText.durationDesc(zh, maxDuration))
                .setMinimum(1)
                .setMaximum(maxDuration), false);

        root.addProperties("permanent", BoolParameter.create()
                .setDescription(MoveText.permanentDesc(zh)), false);
        return root;
    }

    @Override
    public Codec<Result> codec() {
        return CODEC;
    }

    /** 只在配置开启时把工具暴露给模型；关闭后该工具不会出现在模型工具列表中。 */
    @Override
    public boolean trigger(EntityMaid maid, ChatCompletion chatCompletion) {
        return MoveConfig.MOVE_ENABLED.get();
    }

    /** Safety net only: TLM 1.5.3 调用 {@link #onCallAsync}，同步路径没有服务端线程保证，直接拒绝。 */
    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        boolean zh = PromptLang.isChinese(PromptLang.of(callback.getMaid()));
        LOGGER.info("[maid_move] rejected: reason=not-server-thread maid={}",
                shortId(callback.getMaid() == null ? null : callback.getMaid().getUUID()));
        return callback.addToolResult(MoveText.serverSideOnly(zh), toolCallId);
    }

    @Override
    public CompletableFuture<LLMCallback> onCallAsync(String toolCallId, Result result,
                                                      LLMCallback callback, LLMClient client) {
        if (callback.isOnServerThread()) {
            return dispatch(toolCallId, result, callback);
        }
        // 防御性：LLM 回调可能落在异步线程，而实体查询与指令登记都要求服务端主线程
        EntityMaid maid = callback.getMaid();
        if (maid == null || !(maid.level() instanceof ServerLevel)) {
            return CompletableFuture.completedFuture(callback);
        }
        CompletableFuture<LLMCallback> scheduled = new CompletableFuture<>();
        callback.runOnServerThread(() -> {
            try {
                dispatch(toolCallId, result, callback).whenComplete((completed, throwable) -> {
                    if (throwable != null) {
                        scheduled.completeExceptionally(throwable);
                    } else {
                        scheduled.complete(completed);
                    }
                });
            } catch (Throwable throwable) {
                scheduled.completeExceptionally(throwable);
            }
        });
        return scheduled;
    }

    @Override
    public String invocationSummary(Result result) {
        return "%s { %s %s }".formatted(TOOL_ID, safe(result.action()), safe(result.target()));
    }

    // ===== 主线程逻辑 =====

    private CompletableFuture<LLMCallback> dispatch(String toolCallId, Result result, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        if (maid == null) {
            LOGGER.info("[maid_move] rejected: reason=no-maid action={} target={}",
                    safe(result.action()), safe(result.target()));
            return completed(callback.addToolResult(MoveText.serverSideOnly(false), toolCallId));
        }
        boolean zh = PromptLang.isChinese(PromptLang.of(maid));
        UUID maidId = maid.getUUID();
        String maidName = maid.getName().getString();
        if (!(maid.level() instanceof ServerLevel level)) {
            LOGGER.info("[maid_move] rejected: reason=not-server-level maid={} name={}",
                    shortId(maidId), maidName);
            return completed(callback.addToolResult(MoveText.serverSideOnly(zh), toolCallId));
        }
        if (!MoveConfig.MOVE_ENABLED.get()) {
            LOGGER.info("[maid_move] rejected: reason=disabled maid={} name={}", shortId(maidId), maidName);
            return completed(callback.addToolResult(MoveText.disabled(zh), toolCallId));
        }

        String rawAction = safe(result.action()).trim();
        MoveAction action = MoveAction.parse(rawAction);
        if (action == null) {
            LOGGER.info("[maid_move] rejected: reason=unknown-action maid={} name={} action={}",
                    shortId(maidId), maidName, rawAction);
            return completed(callback.addToolResult(MoveText.unknownAction(zh, rawAction), toolCallId));
        }

        if (action == MoveAction.CANCEL) {
            MoveDirectiveManager.CancelResult cancel = MoveDirectiveManager.cancelAndRestore(maidId, maid);
            LOGGER.info("[maid_move] cancel requested: maid={} name={} hadOrder={} suspensionRestored={} scheduleRestored={}",
                    shortId(maidId), maidName, cancel.hadOrder(), cancel.suspensionRestored(), cancel.scheduleRestored());
            String message = MoveText.cancelled(zh, maidName, cancel.hadOrder());
            if (cancel.hadOrder() && (cancel.suspensionRestored() || cancel.scheduleRestored())) {
                message = message + " " + MoveText.restoredNote(zh);
            }
            return completed(callback.addToolResult(message, toolCallId));
        }

        MinecraftServer server = level.getServer();
        UUID ownerId = maid.getOwnerUUID();
        if (ownerId == null) {
            LOGGER.info("[maid_move] rejected: reason=owner-unavailable maid={} name={} action={}",
                    shortId(maidId), maidName, action.id());
            return completed(callback.addToolResult(MoveText.ownerUnavailable(zh), toolCallId));
        }

        if (action == MoveAction.GO_HOME) {
            return goHome(toolCallId, result, callback, level, maid, zh, maidName);
        }

        // work_time：不移动，只把日程临时改成全天工作；睡觉 / 本来就是全天工作时拒绝且不改变任何状态
        if (action == MoveAction.WORK_TIME) {
            return workTime(toolCallId, result, callback, level, maid, ownerId, zh, maidName);
        }

        // goto 需要三个坐标；approach / retreat / guard 需要目标实体；stay 两个都不需要
        double targetX = result.x();
        double targetY = result.y();
        double targetZ = result.z();
        UUID targetId = null;
        String targetDisplay = "";
        if (action.needsTarget()) {
            TargetResolution resolution = resolveTarget(server, level, maid, ownerId, result.target());
            if (resolution.error() != null) {
                LOGGER.info("[maid_move] rejected: reason=target-resolution maid={} name={} action={} target={} detail={}",
                        shortId(maidId), maidName, action.id(), safe(result.target()), resolution.error());
                return completed(callback.addToolResult(resolution.error(), toolCallId));
            }
            targetId = resolution.targetId();
            targetDisplay = resolution.display();
        } else if (action.needsCoords()) {
            if (isMissing(targetX) || isMissing(targetY) || isMissing(targetZ)) {
                LOGGER.info("[maid_move] rejected: reason=missing-coords maid={} name={} action={}",
                        shortId(maidId), maidName, action.id());
                return completed(callback.addToolResult(MoveText.missingCoords(zh), toolCallId));
            }
            targetDisplay = MoveDescribe.coordText(targetX, targetY, targetZ);
        }

        String[] immobile = immobileState(maid);
        if (immobile != null) {
            LOGGER.info("[maid_move] rejected: reason=immobile maid={} name={} action={} state={}",
                    shortId(maidId), maidName, action.id(), immobile[1]);
            return completed(callback.addToolResult(
                    MoveText.cannotMoveNow(zh, maidName, immobile[0], immobile[1]), toolCallId));
        }

        boolean permanent = result.permanent();
        if (permanent && !MoveConfig.MOVE_ALLOW_PERMANENT.get()) {
            LOGGER.info("[maid_move] rejected: reason=permanent-not-allowed maid={} name={} action={}",
                    shortId(maidId), maidName, action.id());
            return completed(callback.addToolResult(MoveText.permanentNotAllowed(zh), toolCallId));
        }

        double distance = MoveMath.clampDistance(result.distance(), action.defaultDistance());
        int maxDuration = MoveConfig.MOVE_MAX_DURATION_SECONDS.get();
        int seconds = result.durationSeconds() <= 0
                ? MoveConfig.MOVE_DEFAULT_DURATION_SECONDS.get()
                : Math.min(result.durationSeconds(), maxDuration);
        long nowGameTick = level.getGameTime();
        MoveOrderData order = permanent
                ? MoveOrderData.permanent(maidId, ownerId, action, targetId, targetX, targetY, targetZ,
                        distance, nowGameTick, null)
                : MoveOrderData.timed(maidId, ownerId, action, targetId, targetX, targetY, targetZ,
                        distance, nowGameTick + seconds * 20L, nowGameTick, null);

        // 覆盖旧指令：新指令不需要暂停而旧指令暂停过 → 先恢复；新指令需要暂停 → 建立（或沿用）暂停记录
        MoveDirectiveManager.SuspensionChange change = MoveDirectiveManager.onOrderReplaced(maid, order);
        if (change.record() != null) {
            // 把"暂停前的原状态"挂到指令上一起持久化，跨重启才恢复得回去
            order = order.withSuspension(change.record());
        }
        // 临时放宽日程：没在睡觉就改成全天工作（已有原日程记录时沿用，不重复保存）
        MoveDirectiveManager.ScheduleChange scheduleChange = MoveDirectiveManager.onScheduleRelief(maid, order);
        if (scheduleChange.originalSchedule() != null) {
            order = order.withOriginalSchedule(scheduleChange.originalSchedule());
        }
        int maxActive = MoveConfig.MOVE_MAX_ACTIVE_PER_OWNER.get();
        if (MoveDirectiveManager.putOrder(maid, order, maxActive) == MoveOrders.PutResult.LIMIT_REACHED) {
            // 名额不够：不能留下"已经暂停过 / 已经放宽过日程"的副作用
            MoveDirectiveManager.restoreSuspension(maidId, maid, "limit-reached");
            MoveDirectiveManager.restoreScheduleRelief(maidId, maid, "limit-reached");
            LOGGER.info("[maid_move] rejected: reason=limit-reached maid={} name={} action={} permanent={} max={}",
                    shortId(maidId), maidName, action.id(), permanent, maxActive);
            return completed(callback.addToolResult(MoveText.limitReached(zh, maxActive), toolCallId));
        }

        LOGGER.info("[maid_move] order put: maid={} name={} action={} target={} distance={} permanent={} seconds={} followSuspended={} scheduleRelief={}",
                shortId(maidId), maidName, action.id(), targetDisplay, distance, permanent,
                permanent ? -1 : seconds, change.suspended(), scheduleChange.action());

        String message;
        if (permanent) {
            message = MoveText.startedPermanent(zh, maidName, detail(action, targetDisplay))
                    + " " + MoveText.permanentNote(zh);
        } else if (action == MoveAction.STAY) {
            message = MoveText.stayStarted(zh, maidName, change.clearedWorkPos(), false, seconds);
        } else {
            message = MoveText.started(zh, action.id(), maidName, targetDisplay, seconds);
        }
        if (maid.isHomeModeEnable() && action != MoveAction.STAY) {
            message = message + " " + MoveText.homeModeNote(zh);
        }
        if (action == MoveAction.GUARD && !(maid.getTask() instanceof IAttackTask)) {
            message = message + " " + MoveText.guardNoCombatNote(zh);
        }
        String scheduleNote = scheduleNote(zh, scheduleChange);
        if (!scheduleNote.isEmpty()) {
            message = message + " " + scheduleNote;
        }
        return completed(callback.addToolResult(message, toolCallId));
    }

    /**
     * 日程放宽的说明文字：真的放宽过（含沿用旧记录）→ {@link MoveText#scheduleForcedNote}；
     * 因为正在睡觉而没动日程 → {@link MoveText#scheduleSleepingNote}；本来就是全天工作 / 配置关闭 → 不追加。
     */
    private static String scheduleNote(boolean zh, MoveDirectiveManager.ScheduleChange change) {
        return switch (change.action()) {
            case FORCE_ALL, REUSE -> MoveText.scheduleForcedNote(zh);
            case UNCHANGED -> change.sleeping() ? MoveText.scheduleSleepingNote(zh) : "";
        };
    }

    /** {@code describe} / 成功文字里的动作细节（"守护澪"、"走近主人"、"在(x, y, z)等着"）。 */
    private static String detail(MoveAction action, String targetDisplay) {
        return switch (action) {
            case APPROACH -> "走近" + targetDisplay;
            case RETREAT -> "远离" + targetDisplay;
            case GOTO -> "前往" + targetDisplay;
            case GUARD -> "守护" + targetDisplay;
            case STAY -> "留在原地";
            default -> action.id();
        };
    }

    /**
     * {@code go_home}：不接受 duration_seconds / permanent（回家本来就是永久性改变），
     * 有工作坐标就传送回工作坐标并打开居家模式，没有就返回 {@link MoveText#noWorkPos}。
     */
    private CompletableFuture<LLMCallback> goHome(String toolCallId, Result result, LLMCallback callback,
                                                  ServerLevel level, EntityMaid maid, boolean zh, String maidName) {
        if (result.permanent() || result.durationSeconds() > 0) {
            LOGGER.info("[maid_move] go_home rejected: reason=unexpected-args maid={} name={} permanent={} durationSeconds={}",
                    shortId(maid.getUUID()), maidName, result.permanent(), result.durationSeconds());
            return completed(callback.addToolResult(MoveText.goHomeIgnoredArgs(zh), toolCallId));
        }
        MoveDirectiveManager.ActionResult actionResult = MoveDirectiveManager.goHome(level, maid);
        return completed(callback.addToolResult(actionResult.message(), toolCallId));
    }

    /**
     * {@code work_time}：不移动，只把女仆的日程临时改成"全天工作"（TLM 的 {@code MaidSchedule.ALL}），
     * 让她夜里也能被切换工作任务、也能执行移动；限时 / 永久都支持，与其他指令一样占名额。
     *
     * <p>不做目标 / 坐标解析，也不走"坐着 / 睡觉 / 骑乘不能移动"的检查（这条指令本来就不移动）。
     * 正在睡觉时返回 {@link MoveText#workTimeSleeping} 且不创建指令；日程本来就是全天工作时返回
     * {@link MoveText#workTimeAlready} 且不创建指令——这两种情况都不会改变女仆的任何状态。
     */
    private CompletableFuture<LLMCallback> workTime(String toolCallId, Result result, LLMCallback callback,
                                                    ServerLevel level, EntityMaid maid, UUID ownerId,
                                                    boolean zh, String maidName) {
        UUID maidId = maid.getUUID();
        if (maid.isSleeping()) {
            LOGGER.info("[maid_move] work_time rejected: reason=sleeping maid={} name={}", shortId(maidId), maidName);
            return completed(callback.addToolResult(MoveText.workTimeSleeping(zh, maidName), toolCallId));
        }
        if (MoveDirectiveManager.isScheduleAll(maid)) {
            LOGGER.info("[maid_move] work_time rejected: reason=schedule-already-all maid={} name={}",
                    shortId(maidId), maidName);
            return completed(callback.addToolResult(MoveText.workTimeAlready(zh, maidName), toolCallId));
        }
        boolean permanent = result.permanent();
        if (permanent && !MoveConfig.MOVE_ALLOW_PERMANENT.get()) {
            LOGGER.info("[maid_move] rejected: reason=permanent-not-allowed maid={} name={} action={}",
                    shortId(maidId), maidName, MoveAction.WORK_TIME.id());
            return completed(callback.addToolResult(MoveText.permanentNotAllowed(zh), toolCallId));
        }
        int maxDuration = MoveConfig.MOVE_MAX_DURATION_SECONDS.get();
        int seconds = result.durationSeconds() <= 0
                ? MoveConfig.MOVE_DEFAULT_DURATION_SECONDS.get()
                : Math.min(result.durationSeconds(), maxDuration);
        long nowGameTick = level.getGameTime();
        MoveOrderData order = permanent
                ? MoveOrderData.permanent(maidId, ownerId, MoveAction.WORK_TIME, null,
                        0.0D, 0.0D, 0.0D, 0.0D, nowGameTick, null)
                : MoveOrderData.timed(maidId, ownerId, MoveAction.WORK_TIME, null,
                        0.0D, 0.0D, 0.0D, 0.0D, nowGameTick + seconds * 20L, nowGameTick, null);

        // work_time 不暂停跟随（不碰居家模式与工作坐标），只放宽日程
        MoveDirectiveManager.SuspensionChange change = MoveDirectiveManager.onOrderReplaced(maid, order);
        if (change.record() != null) {
            order = order.withSuspension(change.record());
        }
        MoveDirectiveManager.ScheduleChange scheduleChange = MoveDirectiveManager.onScheduleRelief(maid, order);
        if (scheduleChange.originalSchedule() != null) {
            order = order.withOriginalSchedule(scheduleChange.originalSchedule());
        }
        int maxActive = MoveConfig.MOVE_MAX_ACTIVE_PER_OWNER.get();
        if (MoveDirectiveManager.putOrder(maid, order, maxActive) == MoveOrders.PutResult.LIMIT_REACHED) {
            MoveDirectiveManager.restoreSuspension(maidId, maid, "limit-reached");
            MoveDirectiveManager.restoreScheduleRelief(maidId, maid, "limit-reached");
            LOGGER.info("[maid_move] rejected: reason=limit-reached maid={} name={} action={} permanent={} max={}",
                    shortId(maidId), maidName, MoveAction.WORK_TIME.id(), permanent, maxActive);
            return completed(callback.addToolResult(MoveText.limitReached(zh, maxActive), toolCallId));
        }
        LOGGER.info("[maid_move] order put: maid={} name={} action={} permanent={} seconds={} scheduleRelief={} originalSchedule={}",
                shortId(maidId), maidName, MoveAction.WORK_TIME.id(), permanent, permanent ? -1 : seconds,
                scheduleChange.action(), scheduleChange.originalSchedule());
        return completed(callback.addToolResult(
                MoveText.workTimeStarted(zh, maidName, permanent, seconds), toolCallId));
    }

    /** 目标解析结果：成功时给出 UUID 与展示名，失败时给出已本地化的说明文字。 */
    private record TargetResolution(@Nullable UUID targetId, String display, @Nullable String error) {
        static TargetResolution ok(UUID targetId, String display) {
            return new TargetResolution(targetId, display, null);
        }

        static TargetResolution fail(String error) {
            return new TargetResolution(null, "", error);
        }
    }

    /**
     * 解析 approach / retreat / guard 的目标：
     * <ul>
     *   <li>{@code me} / {@code 我} / 缺省 → 主人（必须在线且同维度）；</li>
     *   <li>否则按 {@link MaidFinder#matchByName} 的规则在同一主人名下另一只女仆里匹配（同维度、64 格内）；</li>
     *   <li>本维度找不到但在其他维度存在同名女仆 → {@code targetOtherDimension}。</li>
     * </ul>
     */
    private static TargetResolution resolveTarget(MinecraftServer server, ServerLevel level, EntityMaid maid,
                                                  UUID ownerId, String rawTarget) {
        boolean zh = PromptLang.isChinese(PromptLang.of(maid));
        String name = safe(rawTarget).trim();
        if (name.isEmpty() || name.equalsIgnoreCase(TARGET_ME) || name.equals(TARGET_ME_ZH)) {
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
            if (owner == null || owner.level() != level) {
                return TargetResolution.fail(MoveText.ownerUnavailable(zh));
            }
            return TargetResolution.ok(ownerId, owner.getName().getString());
        }
        if (name.equalsIgnoreCase(maid.getName().getString())) {
            return TargetResolution.fail(MoveText.selfTarget(zh));
        }

        List<EntityMaid> candidates = MaidFinder.getOwnedMaidsOfSameOwner(maid, MaidFinder.DEFAULT_RANGE, maid);
        List<EntityMaid> matches = MaidFinder.matchByName(candidates, name, maid);
        EntityMaid target = MaidFinder.findNearest(level, maid, matches);
        if (target != null) {
            return TargetResolution.ok(target.getUUID(), target.getName().getString());
        }
        if (existsInOtherDimension(server, maid, ownerId, name)) {
            return TargetResolution.fail(MoveText.targetOtherDimension(zh, name));
        }
        List<String> available = new ArrayList<>(candidates.size());
        for (EntityMaid candidate : candidates) {
            available.add(candidate.getName().getString());
        }
        return TargetResolution.fail(MoveText.targetNotFound(zh, name, available));
    }

    /** 其他维度（不含本维度）里是否有同主人、同名匹配的女仆——只用于给出"跨维度"的准确提示。 */
    private static boolean existsInOtherDimension(MinecraftServer server, EntityMaid maid,
                                                  UUID ownerId, String name) {
        for (ServerLevel other : server.getAllLevels()) {
            if (other == maid.level()) {
                continue;
            }
            List<EntityMaid> candidates = new ArrayList<>();
            for (Entity entity : other.getAllEntities()) {
                if (entity instanceof EntityMaid candidate && candidate != maid && candidate.isAlive()
                        && ownerId.equals(candidate.getOwnerUUID())) {
                    candidates.add(candidate);
                }
            }
            if (!MaidFinder.matchByName(candidates, name, maid).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 坐着 / 睡觉 / 骑乘 / 被拴住时不能移动，返回 {@code {中文状态, 英文状态}}；可移动时返回 null。 */
    @Nullable
    private static String[] immobileState(EntityMaid maid) {
        if (maid.isMaidInSittingPose()) {
            return new String[]{"坐着", "sitting"};
        }
        if (maid.isSleeping()) {
            return new String[]{"睡觉", "sleeping"};
        }
        if (maid.isPassenger()) {
            return new String[]{"骑乘", "riding"};
        }
        if (maid.isLeashed()) {
            return new String[]{"被拴住", "leashed"};
        }
        return null;
    }

    private static boolean isMissing(double value) {
        return Double.isNaN(value) || Double.isInfinite(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /** 日志里的短 UUID（前 8 位），完整 UUID 太长看不出差别。 */
    private static String shortId(@Nullable UUID id) {
        return id == null ? "null" : id.toString().substring(0, 8);
    }

    private static CompletableFuture<LLMCallback> completed(LLMCallback callback) {
        return CompletableFuture.completedFuture(callback);
    }

    /** {@code x/y/z} 与 {@code duration_seconds} 缺省时由 codec 填入哨兵值（NaN / -1）。 */
    public record Result(String action, String target, double x, double y, double z,
                         double distance, int durationSeconds, boolean permanent) {
    }
}
