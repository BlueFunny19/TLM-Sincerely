package com.github.tartaricacid.tlm_sincerely.ai.guard;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 守护任务状态的持久化：通过主模组的 {@code TaskDataRegister} 存在女仆身上。
 *
 * <p>NBT：{@code expireAt}（long）、{@code revert}（string）、{@code entries}（列表，每项
 * {@code target}（UUID）/ {@code name} / {@code kind} / {@code threshold} / {@code distance}）。
 * 读取时遇到坏数据一律跳过那一项，其余照常可用。
 */
public final class GuardTaskDataKeys {
    public static final ResourceLocation GUARD_KEY_ID = new ResourceLocation("tlm_sincerely", "guard_state");

    private static final String TAG_EXPIRE_AT = "expireAt";
    private static final String TAG_REVERT = "revert";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_TARGET = "target";
    private static final String TAG_NAME = "name";
    private static final String TAG_KIND = "kind";
    private static final String TAG_THRESHOLD = "threshold";
    private static final String TAG_DISTANCE = "distance";

    public static final TaskDataKey<GuardData> GUARD_KEY = new TaskDataKey<>() {
        @Override
        public ResourceLocation getKey() {
            return GUARD_KEY_ID;
        }

        @Override
        public CompoundTag writeSaveData(GuardData data) {
            return write(data);
        }

        @Override
        public GuardData readSaveData(CompoundTag tag) {
            return read(tag);
        }
    };

    private GuardTaskDataKeys() {
    }

    static CompoundTag write(GuardData data) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(TAG_EXPIRE_AT, data.expireAtGameTick());
        tag.putString(TAG_REVERT, data.revertTaskUid());
        ListTag entries = new ListTag();
        for (GuardEntry entry : data.rules().entries()) {
            CompoundTag item = new CompoundTag();
            item.putUUID(TAG_TARGET, entry.targetId());
            item.putString(TAG_NAME, entry.name());
            item.putString(TAG_KIND, entry.condition().kind().name());
            item.putDouble(TAG_THRESHOLD, entry.condition().threshold());
            item.putDouble(TAG_DISTANCE, entry.distance());
            entries.add(item);
        }
        tag.put(TAG_ENTRIES, entries);
        return tag;
    }

    static GuardData read(CompoundTag tag) {
        long expireAt = tag.contains(TAG_EXPIRE_AT) ? tag.getLong(TAG_EXPIRE_AT) : 0L;
        String revert = tag.contains(TAG_REVERT, Tag.TAG_STRING) ? tag.getString(TAG_REVERT) : "";
        List<GuardEntry> entries = new ArrayList<>();
        if (tag.contains(TAG_ENTRIES, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag item = list.getCompound(i);
                if (!item.hasUUID(TAG_TARGET)) {
                    continue;
                }
                GuardCondition.Kind kind;
                try {
                    kind = GuardCondition.Kind.valueOf(item.getString(TAG_KIND));
                } catch (IllegalArgumentException e) {
                    kind = GuardCondition.Kind.ALWAYS;
                }
                entries.add(new GuardEntry(item.getUUID(TAG_TARGET), item.getString(TAG_NAME),
                        new GuardCondition(kind, item.getDouble(TAG_THRESHOLD)),
                        item.contains(TAG_DISTANCE) ? item.getDouble(TAG_DISTANCE) : GuardEntry.DEFAULT_DISTANCE));
            }
        }
        return new GuardData(new GuardRules(entries), expireAt, revert);
    }
}
