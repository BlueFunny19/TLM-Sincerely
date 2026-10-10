package com.github.tartaricacid.tlm_sincerely.ai.tool;

import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveText;
import com.github.tartaricacid.tlm_sincerely.ai.overtime.OvertimeManager;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.MoveConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.BoolParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ChatCompletion;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.github.tartaricacid.tlm_sincerely.ai.move.MoveLog.shortId;

/**
 * 移动类指令工具（{@code maid_move} / {@code maid_guard} / {@code maid_work_time} / {@code maid_cancel}）的公共骨架。
 *
 * <p>共同点：都由 {@link MoveConfig#MOVE_ENABLED} 总开关控制是否出现在模型的工具列表里；
 * 真正的逻辑都必须在服务端主线程执行（实体查询与指令登记的要求）；
 * 失败与拒绝都要记一条 INFO 日志（"女仆答应了却没动"要靠日志定位）。
 * 子类只需要实现 {@link #handle}：拿到已校验的上下文，返回给模型看的文字。
 *
 * <p>所有给模型看的文字都取自 {@link MoveText}（中英两套，按对话语言选择），
 * 参数名与动作标识保持英文机器标识。日志统一带 {@code [maid_move]} 前缀。
 *
 * @param <R> 工具参数的解码结果
 */
public abstract class MoveToolBase<R> implements ITool<R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoveToolBase.class);

    /** 一次工具调用的执行上下文：已确认是服务端的女仆。 */
    protected record Ctx(EntityMaid maid, ServerLevel level, boolean zh, UUID maidId, String maidName) {
    }

    /** 在服务端主线程处理一次调用，返回给模型的文字。 */
    protected abstract String handle(R result, Ctx ctx);

    /** 只在配置开启时把工具暴露给模型；关闭后该工具不会出现在模型工具列表中。 */
    @Override
    public boolean trigger(EntityMaid maid, ChatCompletion chatCompletion) {
        return MoveConfig.MOVE_ENABLED.get();
    }

    /** Safety net only: TLM 1.5.3 调用 {@link #onCallAsync}，同步路径没有服务端线程保证，直接拒绝。 */
    @Override
    public final LLMCallback onCall(String toolCallId, R result, LLMCallback callback) {
        boolean zh = PromptLang.isChinese(PromptLang.of(callback.getMaid()));
        LOGGER.info("[maid_move] rejected: tool={} reason=not-server-thread maid={}", id(),
                shortId(callback.getMaid() == null ? null : callback.getMaid().getUUID()));
        return callback.addToolResult(MoveText.serverSideOnly(zh), toolCallId);
    }

    @Override
    public final CompletableFuture<LLMCallback> onCallAsync(String toolCallId, R result,
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

    private CompletableFuture<LLMCallback> dispatch(String toolCallId, R result, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        if (maid == null) {
            LOGGER.info("[maid_move] rejected: tool={} reason=no-maid", id());
            return completed(callback.addToolResult(MoveText.serverSideOnly(false), toolCallId));
        }
        boolean zh = PromptLang.isChinese(PromptLang.of(maid));
        UUID maidId = maid.getUUID();
        String maidName = maid.getName().getString();
        if (!(maid.level() instanceof ServerLevel level)) {
            LOGGER.info("[maid_move] rejected: tool={} reason=not-server-level maid={} name={}",
                    id(), shortId(maidId), maidName);
            return completed(callback.addToolResult(MoveText.serverSideOnly(zh), toolCallId));
        }
        if (!MoveConfig.MOVE_ENABLED.get()) {
            LOGGER.info("[maid_move] rejected: tool={} reason=disabled maid={} name={}", id(), shortId(maidId), maidName);
            return completed(callback.addToolResult(MoveText.disabled(zh), toolCallId));
        }
        String message = handle(result, new Ctx(maid, level, zh, maidId, maidName));
        return completed(callback.addToolResult(message, toolCallId));
    }

    /** 记一条拒绝日志。{@code detail} 可以为空串。 */
    protected final void logRejected(Ctx ctx, String reason, String detail) {
        LOGGER.info("[maid_move] rejected: tool={} reason={} maid={} name={}{}", id(), reason,
                shortId(ctx.maidId()), ctx.maidName(), detail.isEmpty() ? "" : " " + detail);
    }

    /** 坐着 / 睡觉 / 骑乘 / 被拴住时不能移动，返回 {@code {中文状态, 英文状态}}；可移动时返回 null。 */
    @Nullable
    protected static String[] immobileState(EntityMaid maid) {
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

    /** 加上 {@code duration_seconds} 与 {@code permanent} 两个可选参数（限时 / 永久的指令都用）。 */
    protected static void addDurationParameters(ObjectParameter root, boolean zh) {
        int maxDuration = MoveConfig.MOVE_MAX_DURATION_SECONDS.get();
        root.addProperties("duration_seconds", IntegerParameter.create()
                .setDescription(MoveText.durationDesc(zh, maxDuration))
                .setMinimum(1)
                .setMaximum(maxDuration), false);
        root.addProperties("permanent", BoolParameter.create()
                .setDescription(MoveText.permanentDesc(zh)), false);
    }

    /**
     * 日程放宽的说明文字：真的放宽过（含沿用旧记录）→ {@link MoveText#scheduleForcedNote}；
     * 因为正在睡觉而没动日程 → {@link MoveText#scheduleSleepingNote}；本来就是全天工作 / 配置关闭 → 不追加。
     */
    protected static String scheduleNote(boolean zh, OvertimeManager.Acquisition change) {
        return switch (change.action()) {
            case FORCE_ALL, REUSE -> MoveText.scheduleForcedNote(zh);
            case UNCHANGED -> change.sleeping() ? MoveText.scheduleSleepingNote(zh) : "";
        };
    }

    protected static String safe(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static CompletableFuture<LLMCallback> completed(LLMCallback callback) {
        return CompletableFuture.completedFuture(callback);
    }
}
