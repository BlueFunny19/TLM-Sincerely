package com.github.tartaricacid.tlm_sincerely.ai.move;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * 移动指令相关日志的小工具（管理器、各个工具共用）。
 * <p>
 * 移动指令这一整套（{@code maid_move} / {@code maid_guard} / {@code maid_work_time} / {@code maid_cancel}）
 * 的日志都带 {@code [maid_move]} 前缀，排查时搜这个前缀就能看到指令写入、被拒绝原因、卡住、传送、放弃。
 */
public final class MoveLog {
    private MoveLog() {
    }

    /** 日志里的短 UUID（前 8 位），完整 UUID 太长看不出差别。 */
    public static String shortId(@Nullable UUID id) {
        return id == null ? "null" : id.toString().substring(0, 8);
    }

    public static String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    public static String fmtVec(@Nullable Vec3 vec) {
        return vec == null ? "null"
                : String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", vec.x(), vec.y(), vec.z());
    }
}
