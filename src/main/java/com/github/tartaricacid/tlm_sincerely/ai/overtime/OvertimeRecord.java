package com.github.tartaricacid.tlm_sincerely.ai.overtime;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 一只女仆"正在加班"需要保存的全部事实（不依赖任何 Minecraft 类型，便于单元测试）。
 * 内存里的运行状态由 {@code OvertimeManager} 持有，存档用 {@link OvertimeNbt} 读写这个记录。
 *
 * @param originalSchedule     开始加班前的日程（{@code MaidSchedule#name()}），结束时写回
 * @param holders              当前的持有者；至少一个
 * @param taskDeadlineGameTick {@link OvertimeHolder#WORK_TASK} 的到期 gameTime；{@link #NO_DEADLINE} 表示没有
 */
public record OvertimeRecord(String originalSchedule, Set<OvertimeHolder> holders, long taskDeadlineGameTick) {
    /** {@link #taskDeadlineGameTick} 的"没有期限"取值。 */
    public static final long NO_DEADLINE = -1L;

    public OvertimeRecord {
        holders = Collections.unmodifiableSet(copyOf(holders));
    }

    private static EnumSet<OvertimeHolder> copyOf(Collection<OvertimeHolder> source) {
        EnumSet<OvertimeHolder> set = EnumSet.noneOf(OvertimeHolder.class);
        set.addAll(source);
        return set;
    }

    public boolean holds(OvertimeHolder holder) {
        return holders.contains(holder);
    }
}
