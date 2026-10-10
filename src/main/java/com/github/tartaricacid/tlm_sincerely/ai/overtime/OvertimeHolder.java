package com.github.tartaricacid.tlm_sincerely.ai.overtime;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 谁让这只女仆在加班。只要还有任何一个持有者，加班就继续；最后一个持有者释放时才恢复原日程。
 */
public enum OvertimeHolder {
    /** 一条生效中的移动类指令（走近 / 远离 / 前往 / 留在原地 / 守护 / 放宽工作时间）。指令结束时释放。 */
    ORDER("order"),
    /** 玩家（通过模型）在非工作时间让她切换了工作任务。任务换成待命、或一段时间没有新指令时释放。 */
    WORK_TASK("work_task");

    private final String id;

    OvertimeHolder(String id) {
        this.id = id;
    }

    /** 写进存档的标识。 */
    public String id() {
        return id;
    }

    /** 解析存档里的标识；认不出时返回 {@code null}。 */
    @Nullable
    public static OvertimeHolder parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        for (OvertimeHolder holder : values()) {
            if (holder.id.equals(value)) {
                return holder;
            }
        }
        return null;
    }
}
