package com.github.tartaricacid.tlm_sincerely.ai.tool;

import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveDirectiveManager;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveText;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.tartaricacid.tlm_sincerely.ai.move.MoveLog.shortId;

/**
 * 取消指令工具 {@code maid_cancel}：取消这只女仆当前由 {@code maid_move} / {@code maid_guard} /
 * {@code maid_work_time} 下达的指令，并把因它而临时改动的状态（居家模式与工作坐标、日程）恢复原样。
 *
 * <p>只有一个可选的 {@code reason} 参数，仅用于日志：主 Mod 的所有工具都至少有一个参数，
 * 没有先例证明"完全没有参数"的工具能被各家模型接口接受，所以不冒这个险。
 */
public class MaidCancelTool extends MoveToolBase<MaidCancelTool.Result> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MaidCancelTool.class);

    private static final String TOOL_ID = "maid_cancel";

    private static final Codec<Result> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("reason", "").forGetter(Result::reason)
    ).apply(instance, Result::new));

    @Override
    public String id() {
        return TOOL_ID;
    }

    @Override
    public String summary(EntityMaid maid) {
        return MoveText.cancelSummary(PromptLang.isChinese(PromptLang.of(maid)));
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties("reason", StringParameter.create()
                .setDescription(MoveText.cancelReasonDesc(PromptLang.isChinese(PromptLang.of(maid))))
                .setMaxLength(100), false);
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
        boolean zh = ctx.zh();
        MoveDirectiveManager.CancelResult cancel = MoveDirectiveManager.cancelAndRestore(ctx.maidId(), ctx.maid());
        LOGGER.info("[maid_move] cancel requested: maid={} name={} hadOrder={} suspensionRestored={} scheduleRestored={} reason={}",
                shortId(ctx.maidId()), ctx.maidName(), cancel.hadOrder(), cancel.suspensionRestored(),
                cancel.scheduleRestored(), safe(result.reason()));
        String message = MoveText.cancelled(zh, ctx.maidName(), cancel.hadOrder());
        if (cancel.hadOrder() && (cancel.suspensionRestored() || cancel.scheduleRestored())) {
            message = message + " " + MoveText.restoredNote(zh);
        }
        return message;
    }

    public record Result(String reason) {
    }
}
