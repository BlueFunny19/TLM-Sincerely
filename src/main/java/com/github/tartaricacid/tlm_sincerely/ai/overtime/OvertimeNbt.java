package com.github.tartaricacid.tlm_sincerely.ai.overtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * {@link OvertimeRecord} 与 NBT 之间的适配：把加班状态存进女仆实体的 {@code getPersistentData()}
 * 子复合标签 {@value #ROOT_TAG}，用于跨服务器重启恢复（重启后日程还停在"全天工作"，必须知道原日程是什么）。
 *
 * <p>读不出来（缺字段 / 没有认得出的持有者）时返回 {@code null}，调用方按"拿不准就不动日程、只清标签"处理——
 * 宁可让女仆多保持一会儿全天工作，也不要把日程改成错的值。
 */
public final class OvertimeNbt {
    /** 女仆 {@code getPersistentData()} 里存放加班状态的子标签名。 */
    public static final String ROOT_TAG = "tlm_sincerely:overtime";

    private static final String ORIGINAL = "original";
    private static final String HOLDERS = "holders";
    private static final String TASK_DEADLINE = "taskDeadlineGameTime";

    private OvertimeNbt() {
    }

    /** 写入（覆盖旧值）。{@code record} 为 null 时清除标签。 */
    public static void write(@Nullable CompoundTag persistentData, @Nullable OvertimeRecord record) {
        if (persistentData == null) {
            return;
        }
        if (record == null) {
            persistentData.remove(ROOT_TAG);
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString(ORIGINAL, record.originalSchedule());
        ListTag holders = new ListTag();
        for (OvertimeHolder holder : record.holders()) {
            holders.add(StringTag.valueOf(holder.id()));
        }
        tag.put(HOLDERS, holders);
        tag.putLong(TASK_DEADLINE, record.taskDeadlineGameTick());
        persistentData.put(ROOT_TAG, tag);
    }

    /** 读出加班状态；没有标签、原日程为空或没有认得出的持有者时返回 {@code null}。 */
    @Nullable
    public static OvertimeRecord read(@Nullable CompoundTag persistentData) {
        if (persistentData == null || !persistentData.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag tag = persistentData.getCompound(ROOT_TAG);
        String original = tag.getString(ORIGINAL);
        if (original.isEmpty()) {
            return null;
        }
        Set<OvertimeHolder> holders = EnumSet.noneOf(OvertimeHolder.class);
        ListTag list = tag.getList(HOLDERS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            OvertimeHolder holder = OvertimeHolder.parse(list.getString(i));
            if (holder != null) {
                holders.add(holder);
            }
        }
        if (holders.isEmpty()) {
            return null;
        }
        long deadline = tag.contains(TASK_DEADLINE, Tag.TAG_LONG)
                ? tag.getLong(TASK_DEADLINE) : OvertimeRecord.NO_DEADLINE;
        return new OvertimeRecord(original, holders, deadline);
    }

    /** 清除加班标签。 */
    public static void clear(@Nullable CompoundTag persistentData) {
        if (persistentData != null) {
            persistentData.remove(ROOT_TAG);
        }
    }
}
