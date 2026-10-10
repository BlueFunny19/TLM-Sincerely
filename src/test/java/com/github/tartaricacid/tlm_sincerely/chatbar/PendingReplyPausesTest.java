package com.github.tartaricacid.tlm_sincerely.chatbar;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link PendingReplyPauses} 的单元测试：按女仆和正文对账、只能取一次、过期作废。
 */
class PendingReplyPausesTest {
    private static final List<ReplyPauses.Segment> SEGMENTS =
            List.of(new ReplyPauses.Segment("诶？", 0.0), new ReplyPauses.Segment("真的吗", 1.5));

    @AfterEach
    void cleanUp() {
        PendingReplyPauses.clear();
    }

    @Test
    void take_returnsTheEntryOnceWhenTheBodyMatches() {
        UUID maid = UUID.randomUUID();
        PendingReplyPauses.put(maid, "诶？\n真的吗", SEGMENTS, 1000L);
        PendingReplyPauses.Pending taken = PendingReplyPauses.take(maid, "诶？\n真的吗", 1500L);
        assertNotNull(taken);
        assertEquals(SEGMENTS, taken.segments());
        assertNull(PendingReplyPauses.take(maid, "诶？\n真的吗", 1500L), "取过就没有了");
    }

    @Test
    void take_ignoresWhitespaceDifferences() {
        UUID maid = UUID.randomUUID();
        PendingReplyPauses.put(maid, "诶？\n真的吗", SEGMENTS, 0L);
        assertNotNull(PendingReplyPauses.take(maid, "  诶？   真的吗 ", 10L));
    }

    @Test
    void take_leavesTheEntryWhenTheBodyDoesNotMatch() {
        UUID maid = UUID.randomUUID();
        PendingReplyPauses.put(maid, "诶？\n真的吗", SEGMENTS, 0L);
        assertNull(PendingReplyPauses.take(maid, "完全不同的一句话", 10L));
        assertNotNull(PendingReplyPauses.take(maid, "诶？\n真的吗", 20L), "不匹配不应把登记吃掉");
    }

    @Test
    void take_isPerMaid() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        PendingReplyPauses.put(a, "甲", SEGMENTS, 0L);
        assertNull(PendingReplyPauses.take(b, "甲", 10L));
        assertNotNull(PendingReplyPauses.take(a, "甲", 10L));
    }

    @Test
    void take_expiredEntriesAreDropped() {
        UUID maid = UUID.randomUUID();
        PendingReplyPauses.put(maid, "甲", SEGMENTS, 0L);
        assertNull(PendingReplyPauses.take(maid, "甲", PendingReplyPauses.TTL_MILLIS + 1));
        assertEquals(0, PendingReplyPauses.size());
    }

    @Test
    void put_replacesTheEarlierEntryOfTheSameMaid() {
        UUID maid = UUID.randomUUID();
        PendingReplyPauses.put(maid, "旧的", SEGMENTS, 0L);
        PendingReplyPauses.put(maid, "新的", SEGMENTS, 0L);
        assertNull(PendingReplyPauses.take(maid, "旧的", 10L));
        assertNotNull(PendingReplyPauses.take(maid, "新的", 10L));
    }
}
