package com.github.tartaricacid.tlm_sincerely.ai.move;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MoveOrderNbt} 的单元测试：指令与"暂停前原状态"的 NBT 往返，以及各种畸形数据的容错。
 * <p>
 * {@link CompoundTag} 是 vanilla 类，但纯数据操作不依赖客户端/服务端运行时，可以在 JUnit 里直接跑。
 */
class MoveOrderNbtTest {
    private static final UUID MAID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OWNER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID TARGET = UUID.fromString("99999999-8888-7777-6666-555555555555");

    private static MoveOrderData sample(boolean permanent, FollowSuspension suspension) {
        return permanent
                ? MoveOrderData.permanent(MAID, OWNER, MoveAction.GUARD, TARGET, 0, 0, 0, 5.0D, 1200L, suspension)
                : MoveOrderData.timed(MAID, OWNER, MoveAction.GOTO, null, 10.5D, 64.0D, -20.25D,
                        3.0D, 9999L, 1200L, suspension);
    }

    @Test
    void roundTrip_timedOrder_withoutSuspension() {
        CompoundTag data = new CompoundTag();
        MoveOrderData original = sample(false, null);
        MoveOrderNbt.write(data, original);

        MoveOrderData read = MoveOrderNbt.read(data);
        assertNotNull(read);
        assertEquals(original, read);
    }

    @Test
    void roundTrip_permanentOrder_withSuspension() {
        FollowSuspension suspension = new FollowSuspension(true, true, 100L, 64L, -200L, 12.5F);
        CompoundTag data = new CompoundTag();
        MoveOrderData original = sample(true, suspension);
        MoveOrderNbt.write(data, original);

        MoveOrderData read = MoveOrderNbt.read(data);
        assertNotNull(read);
        assertEquals(original, read);
        assertEquals(MoveOrderData.PERMANENT, read.expireAtGameTick());
        assertTrue(read.isPermanent());
        assertNotNull(read.followSuspension());
        assertEquals(suspension, read.followSuspension());
        assertEquals(12.5F, read.followSuspension().radius());
    }

    @Test
    void roundTrip_suspensionWithoutWorkPos() {
        FollowSuspension suspension = new FollowSuspension(false, false, 0L, 0L, 0L, -1.0F);
        CompoundTag data = new CompoundTag();
        MoveOrderNbt.write(data, sample(true, suspension));

        MoveOrderData read = MoveOrderNbt.read(data);
        assertNotNull(read);
        assertEquals(suspension, read.followSuspension());
        assertEquals(false, read.followSuspension().hadRestriction());
    }

    @Test
    void write_null_clearsTheTag() {
        CompoundTag data = new CompoundTag();
        MoveOrderNbt.write(data, sample(false, null));
        assertTrue(data.contains(MoveOrderNbt.ROOT_TAG));

        MoveOrderNbt.write(data, null);
        assertNull(MoveOrderNbt.read(data));
    }

    @Test
    void read_emptyOrNullTag_returnsNull() {
        assertNull(MoveOrderNbt.read(null));
        assertNull(MoveOrderNbt.read(new CompoundTag()));
    }

    @Test
    void read_unknownAction_returnsNull() {
        CompoundTag data = new CompoundTag();
        MoveOrderNbt.write(data, sample(false, null));
        data.getCompound(MoveOrderNbt.ROOT_TAG).putString("action", "teleport");

        assertNull(MoveOrderNbt.read(data));
    }

    @Test
    void read_missingUuid_returnsNull() {
        CompoundTag data = new CompoundTag();
        MoveOrderNbt.write(data, sample(false, null));
        data.getCompound(MoveOrderNbt.ROOT_TAG).remove("maid");

        assertNull(MoveOrderNbt.read(data));
    }

    @Test
    void read_malformedUuid_returnsNull() {
        CompoundTag data = new CompoundTag();
        MoveOrderNbt.write(data, sample(false, null));
        data.getCompound(MoveOrderNbt.ROOT_TAG).putString("owner", "not-a-uuid");

        assertNull(MoveOrderNbt.read(data));
    }

    @Test
    void rewrite_replacesTheOldOrder() {
        CompoundTag data = new CompoundTag();
        MoveOrderNbt.write(data, sample(false, null));
        MoveOrderData permanent = sample(true, new FollowSuspension(false, true, 1L, 2L, 3L, 4.0F));
        MoveOrderNbt.write(data, permanent);

        MoveOrderData read = MoveOrderNbt.read(data);
        assertEquals(permanent, read);
    }

    @Test
    void clear_removesTheTag() {
        CompoundTag data = new CompoundTag();
        MoveOrderNbt.write(data, sample(false, null));
        MoveOrderNbt.clear(data);
        assertNull(MoveOrderNbt.read(data));
    }

    @Test
    void read_suspensionMissingCenter_stillReadsWithZeros() {
        CompoundTag data = new CompoundTag();
        MoveOrderNbt.write(data, sample(true, new FollowSuspension(false, true, 7L, 8L, 9L, 3.0F)));
        data.getCompound(MoveOrderNbt.ROOT_TAG).getCompound("followSuspension").remove("center");

        MoveOrderData read = MoveOrderNbt.read(data);
        assertNotNull(read);
        assertNotNull(read.followSuspension());
        assertEquals(0L, read.followSuspension().centerX());
        assertEquals(3.0F, read.followSuspension().radius());
    }

    // ===== 临时放宽日程保存的原日程（本次新增字段） =====

    @Test
    void roundTrip_orderWithOriginalSchedule() {
        CompoundTag data = new CompoundTag();
        MoveOrderData original = MoveOrderData.timed(MAID, OWNER, MoveAction.WORK_TIME, null,
                0, 0, 0, 0, 9999L, 1200L, null, "DAY");
        MoveOrderNbt.write(data, original);

        MoveOrderData read = MoveOrderNbt.read(data);
        assertNotNull(read);
        assertEquals(original, read);
        assertEquals("DAY", read.originalSchedule());
        assertEquals(MoveAction.WORK_TIME, read.action());
    }

    @Test
    void roundTrip_orderWithoutOriginalSchedule_readsNull() {
        CompoundTag data = new CompoundTag();
        MoveOrderNbt.write(data, sample(false, null));

        MoveOrderData read = MoveOrderNbt.read(data);
        assertNotNull(read);
        assertNull(read.originalSchedule());
        assertFalse(data.getCompound(MoveOrderNbt.ROOT_TAG).contains("originalSchedule"));
    }

    /** 旧记录（本次改动之前写下的标签）没有 originalSchedule 键，必须读成"没有放宽过"。 */
    @Test
    void read_legacyRecordWithoutOriginalScheduleField_isCompatible() {
        CompoundTag data = new CompoundTag();
        MoveOrderData withSchedule = MoveOrderData.timed(MAID, OWNER, MoveAction.GOTO, null,
                1, 2, 3, 0, 9999L, 1200L, null, "NIGHT");
        MoveOrderNbt.write(data, withSchedule);
        // 模拟旧版本写下的标签：手动把新键删掉
        data.getCompound(MoveOrderNbt.ROOT_TAG).remove("originalSchedule");

        MoveOrderData read = MoveOrderNbt.read(data);
        assertNotNull(read);
        assertNull(read.originalSchedule());
        assertEquals(MoveAction.GOTO, read.action());
        assertEquals(9999L, read.expireAtGameTick());
    }

    @Test
    void read_emptyOriginalScheduleString_readsNull() {
        CompoundTag data = new CompoundTag();
        MoveOrderNbt.write(data, sample(false, null));
        data.getCompound(MoveOrderNbt.ROOT_TAG).putString("originalSchedule", "");

        MoveOrderData read = MoveOrderNbt.read(data);
        assertNotNull(read);
        assertNull(read.originalSchedule());
    }
}
