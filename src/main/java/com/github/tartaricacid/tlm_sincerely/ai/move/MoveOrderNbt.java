package com.github.tartaricacid.tlm_sincerely.ai.move;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * {@link MoveOrderData}（含 {@link FollowSuspension}）与 NBT 之间的适配：把指令存进女仆实体的
 * {@code getPersistentData()} 子复合标签 {@value #ROOT_TAG}，用于跨服务器重启恢复。
 *
 * <p>纯数据结构（{@link MoveOrderData} / {@link FollowSuspension}）不引用 MC 类型，可以单测；
 * 这里只做字段搬运与容错读。任何读不出来的字段都回落到"丢弃 + 恢复原状态"的安全侧：
 * {@link #read(CompoundTag)} 校验不通过时返回 {@code null}，调用方必须按
 * "能恢复就恢复、拿不准就恢复原状态并丢弃指令"处理，绝不能让女仆停留在被改过的状态里。
 */
public final class MoveOrderNbt {
    /** 女仆 {@code getPersistentData()} 里存放移动指令的子标签名。 */
    public static final String ROOT_TAG = "tlm_sincerely:move_order";

    private static final String MAID = "maid";
    private static final String OWNER = "owner";
    private static final String ACTION = "action";
    private static final String TARGET = "target";
    private static final String X = "x";
    private static final String Y = "y";
    private static final String Z = "z";
    private static final String DISTANCE = "distance";
    private static final String EXPIRE = "expireAtGameTime";
    private static final String CREATED = "createdGameTime";
    private static final String RECOVERY = "recovery";
    private static final String SUSPENSION = "followSuspension";
    private static final String SUSPENSION_HOME_MODE = "homeMode";
    private static final String SUSPENSION_HAS_RESTRICTION = "hadRestriction";
    private static final String SUSPENSION_CENTER = "center";
    private static final String SUSPENSION_RADIUS = "radius";
    /** 临时放宽日程期间保存的原日程（{@code MaidSchedule#name()}）。旧记录里没有这个键。 */
    private static final String ORIGINAL_SCHEDULE = "originalSchedule";

    private MoveOrderNbt() {
    }

    /** 把指令写进女仆实体的持久化标签（覆盖旧值）。{@code order} 为 null 时清除标签。 */
    public static void write(CompoundTag persistentData, @Nullable MoveOrderData order) {
        if (persistentData == null) {
            return;
        }
        if (order == null) {
            persistentData.remove(ROOT_TAG);
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString(MAID, order.maidId().toString());
        tag.putString(OWNER, order.ownerId().toString());
        tag.putString(ACTION, order.action().id());
        if (order.targetId() != null) {
            tag.putString(TARGET, order.targetId().toString());
        }
        tag.putDouble(X, order.x());
        tag.putDouble(Y, order.y());
        tag.putDouble(Z, order.z());
        tag.putDouble(DISTANCE, order.distance());
        tag.putLong(EXPIRE, order.expireAtGameTick());
        tag.putLong(CREATED, order.createdGameTick());
        tag.putBoolean(RECOVERY, false);
        if (order.followSuspension() != null) {
            tag.put(SUSPENSION, writeSuspension(order.followSuspension()));
        }
        if (order.originalSchedule() != null) {
            tag.putString(ORIGINAL_SCHEDULE, order.originalSchedule());
        }
        persistentData.put(ROOT_TAG, tag);
    }

    /**
     * 取出"需要跨重启恢复"的指令；没有标签、字段缺失、动作无法识别或 UUID 非法时返回 {@code null}
     * （调用方按"丢弃并倾向恢复原状态"处理）。
     *
     * <p>兼容性：{@code originalSchedule} 是本次新增的键，旧记录里没有它，读到旧记录时
     * {@link MoveOrderData#originalSchedule()} 为 {@code null}（等于"没有放宽过日程"）。
     */
    @Nullable
    public static MoveOrderData read(@Nullable CompoundTag persistentData) {
        if (persistentData == null || !persistentData.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag tag = persistentData.getCompound(ROOT_TAG);
        MoveAction action = MoveAction.parse(tag.getString(ACTION));
        if (action == null) {
            return null;
        }
        UUID maidId = readUuid(tag, MAID);
        UUID ownerId = readUuid(tag, OWNER);
        if (maidId == null || ownerId == null) {
            return null;
        }
        return new MoveOrderData(maidId, ownerId, action, readUuid(tag, TARGET),
                tag.getDouble(X), tag.getDouble(Y), tag.getDouble(Z), tag.getDouble(DISTANCE),
                tag.getLong(EXPIRE), tag.getLong(CREATED), readSuspension(tag), readOriginalSchedule(tag));
    }

    /** 清除女仆身上的指令标签。 */
    public static void clear(CompoundTag persistentData) {
        if (persistentData != null) {
            persistentData.remove(ROOT_TAG);
        }
    }

    private static CompoundTag writeSuspension(FollowSuspension suspension) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(SUSPENSION_HOME_MODE, suspension.homeMode());
        tag.putBoolean(SUSPENSION_HAS_RESTRICTION, suspension.hadRestriction());
        CompoundTag center = new CompoundTag();
        center.putLong("x", suspension.centerX());
        center.putLong("y", suspension.centerY());
        center.putLong("z", suspension.centerZ());
        tag.put(SUSPENSION_CENTER, center);
        tag.putFloat(SUSPENSION_RADIUS, suspension.radius());
        return tag;
    }

    @Nullable
    private static FollowSuspension readSuspension(CompoundTag tag) {
        if (!tag.contains(SUSPENSION, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag suspension = tag.getCompound(SUSPENSION);
        CompoundTag center = suspension.getCompound(SUSPENSION_CENTER);
        return new FollowSuspension(
                suspension.getBoolean(SUSPENSION_HOME_MODE),
                suspension.getBoolean(SUSPENSION_HAS_RESTRICTION),
                center.getLong("x"), center.getLong("y"), center.getLong("z"),
                suspension.getFloat(SUSPENSION_RADIUS));
    }

    /** 读取"临时放宽日程"保存的原日程；没有该键（旧记录）或为空串时返回 null。 */
    @Nullable
    private static String readOriginalSchedule(CompoundTag tag) {
        if (!tag.contains(ORIGINAL_SCHEDULE, Tag.TAG_STRING)) {
            return null;
        }
        String value = tag.getString(ORIGINAL_SCHEDULE);
        return value.isEmpty() ? null : value;
    }

    @Nullable
    private static UUID readUuid(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            return null;
        }
        try {
            return UUID.fromString(tag.getString(key));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
