package com.github.tartaricacid.tlm_sincerely.ai.overtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OvertimeNbt} 的单元测试：往返、缺字段容错、未知持有者忽略。
 */
class OvertimeNbtTest {

    private static OvertimeRecord record(String original, long deadline, OvertimeHolder... holders) {
        EnumSet<OvertimeHolder> set = EnumSet.noneOf(OvertimeHolder.class);
        for (OvertimeHolder holder : holders) {
            set.add(holder);
        }
        return new OvertimeRecord(original, set, deadline);
    }

    @Test
    void roundTrip_keepsEveryField() {
        CompoundTag data = new CompoundTag();
        OvertimeNbt.write(data, record("NIGHT", 12345L, OvertimeHolder.ORDER, OvertimeHolder.WORK_TASK));
        OvertimeRecord read = OvertimeNbt.read(data);
        assertNotNull(read);
        assertEquals("NIGHT", read.originalSchedule());
        assertTrue(read.holds(OvertimeHolder.ORDER));
        assertTrue(read.holds(OvertimeHolder.WORK_TASK));
        assertEquals(12345L, read.taskDeadlineGameTick());
    }

    @Test
    void roundTrip_singleHolderWithoutDeadline() {
        CompoundTag data = new CompoundTag();
        OvertimeNbt.write(data, record("DAY", OvertimeRecord.NO_DEADLINE, OvertimeHolder.ORDER));
        OvertimeRecord read = OvertimeNbt.read(data);
        assertNotNull(read);
        assertTrue(read.holds(OvertimeHolder.ORDER));
        assertFalse(read.holds(OvertimeHolder.WORK_TASK));
        assertEquals(OvertimeRecord.NO_DEADLINE, read.taskDeadlineGameTick());
    }

    @Test
    void write_null_clearsTheTag() {
        CompoundTag data = new CompoundTag();
        OvertimeNbt.write(data, record("DAY", -1L, OvertimeHolder.ORDER));
        assertTrue(data.contains(OvertimeNbt.ROOT_TAG));
        OvertimeNbt.write(data, null);
        assertFalse(data.contains(OvertimeNbt.ROOT_TAG));
        assertNull(OvertimeNbt.read(data));
    }

    @Test
    void clear_removesTheTag() {
        CompoundTag data = new CompoundTag();
        OvertimeNbt.write(data, record("DAY", -1L, OvertimeHolder.WORK_TASK));
        OvertimeNbt.clear(data);
        assertFalse(data.contains(OvertimeNbt.ROOT_TAG));
    }

    @Test
    void read_noTag_returnsNull() {
        assertNull(OvertimeNbt.read(new CompoundTag()));
        assertNull(OvertimeNbt.read(null));
    }

    @Test
    void read_emptyOriginal_returnsNull() {
        CompoundTag data = new CompoundTag();
        OvertimeNbt.write(data, record("DAY", -1L, OvertimeHolder.ORDER));
        data.getCompound(OvertimeNbt.ROOT_TAG).putString("original", "");
        assertNull(OvertimeNbt.read(data));
    }

    @Test
    void read_unknownHoldersAreIgnored_butKnownOnesSurvive() {
        CompoundTag data = new CompoundTag();
        OvertimeNbt.write(data, record("DAY", -1L, OvertimeHolder.ORDER));
        ListTag holders = new ListTag();
        holders.add(StringTag.valueOf("from_the_future"));
        holders.add(StringTag.valueOf("work_task"));
        data.getCompound(OvertimeNbt.ROOT_TAG).put("holders", holders);
        OvertimeRecord read = OvertimeNbt.read(data);
        assertNotNull(read);
        assertEquals(EnumSet.of(OvertimeHolder.WORK_TASK), read.holders());
    }

    @Test
    void read_onlyUnknownHolders_returnsNull() {
        CompoundTag data = new CompoundTag();
        OvertimeNbt.write(data, record("DAY", -1L, OvertimeHolder.ORDER));
        ListTag holders = new ListTag();
        holders.add(StringTag.valueOf("from_the_future"));
        data.getCompound(OvertimeNbt.ROOT_TAG).put("holders", holders);
        assertNull(OvertimeNbt.read(data));
    }

    @Test
    void read_missingDeadlineKey_meansNoDeadline() {
        CompoundTag data = new CompoundTag();
        OvertimeNbt.write(data, record("DAY", 99L, OvertimeHolder.WORK_TASK));
        data.getCompound(OvertimeNbt.ROOT_TAG).remove("taskDeadlineGameTime");
        OvertimeRecord read = OvertimeNbt.read(data);
        assertNotNull(read);
        assertEquals(OvertimeRecord.NO_DEADLINE, read.taskDeadlineGameTick());
    }

    @Test
    void read_wrongTagType_returnsNull() {
        CompoundTag data = new CompoundTag();
        data.putString(OvertimeNbt.ROOT_TAG, "not a compound");
        assertNull(OvertimeNbt.read(data));
        assertFalse(data.contains(OvertimeNbt.ROOT_TAG, Tag.TAG_COMPOUND));
    }

    @Test
    void parseHolder_isCaseInsensitiveAndTolerant() {
        assertEquals(OvertimeHolder.ORDER, OvertimeHolder.parse(" ORDER "));
        assertEquals(OvertimeHolder.WORK_TASK, OvertimeHolder.parse("work_task"));
        assertNull(OvertimeHolder.parse("nope"));
        assertNull(OvertimeHolder.parse(null));
    }
}
