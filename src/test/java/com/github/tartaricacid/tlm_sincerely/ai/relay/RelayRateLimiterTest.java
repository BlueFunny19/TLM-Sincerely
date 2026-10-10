package com.github.tartaricacid.tlm_sincerely.ai.relay;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RelayRateLimiter} 的单元测试。时间由测试直接传入，不依赖真实时钟。
 */
class RelayRateLimiterTest {
    private static final long COOLDOWN = 30_000L;
    private static final int MAX_PER_MINUTE = 3;

    private final UUID owner = UUID.randomUUID();
    private final UUID maidA = UUID.randomUUID();
    private final UUID maidB = UUID.randomUUID();
    private final UUID maidC = UUID.randomUUID();

    @Test
    void firstRelay_isAllowed() {
        RelayRateLimiter limiter = new RelayRateLimiter();
        assertTrue(limiter.checkAndRecord(owner, maidA, maidB, 1_000L, COOLDOWN, MAX_PER_MINUTE).allowed());
    }

    @Test
    void samePair_isBlockedRegardlessOfDirection() {
        RelayRateLimiter limiter = new RelayRateLimiter();
        limiter.checkAndRecord(owner, maidA, maidB, 1_000L, COOLDOWN, MAX_PER_MINUTE);

        RelayRateLimiter.Decision reverse = limiter.checkAndRecord(owner, maidB, maidA, 2_000L, COOLDOWN, MAX_PER_MINUTE);
        assertFalse(reverse.allowed());
        assertEquals(RelayRateLimiter.Decision.Reason.COOLDOWN, reverse.reason());
        assertEquals(29_000L, reverse.retryAfterMillis());
    }

    @Test
    void samePair_isAllowedAgainAfterCooldown() {
        RelayRateLimiter limiter = new RelayRateLimiter();
        limiter.checkAndRecord(owner, maidA, maidB, 1_000L, COOLDOWN, MAX_PER_MINUTE);
        assertTrue(limiter.checkAndRecord(owner, maidA, maidB, 31_000L, COOLDOWN, MAX_PER_MINUTE).allowed());
    }

    @Test
    void differentPair_isNotBlockedByCooldown() {
        RelayRateLimiter limiter = new RelayRateLimiter();
        limiter.checkAndRecord(owner, maidA, maidB, 1_000L, COOLDOWN, MAX_PER_MINUTE);
        assertTrue(limiter.checkAndRecord(owner, maidA, maidC, 1_000L, COOLDOWN, MAX_PER_MINUTE).allowed());
    }

    @Test
    void zeroCooldown_disablesPairLimit() {
        RelayRateLimiter limiter = new RelayRateLimiter();
        assertTrue(limiter.checkAndRecord(owner, maidA, maidB, 1_000L, 0L, MAX_PER_MINUTE).allowed());
        assertTrue(limiter.checkAndRecord(owner, maidB, maidA, 1_000L, 0L, MAX_PER_MINUTE).allowed());
    }

    @Test
    void ownerLimit_blocksAfterCapAndSlides() {
        RelayRateLimiter limiter = new RelayRateLimiter();
        assertTrue(limiter.checkAndRecord(owner, maidA, maidB, 1_000L, 0L, MAX_PER_MINUTE).allowed());
        assertTrue(limiter.checkAndRecord(owner, maidA, maidB, 11_000L, 0L, MAX_PER_MINUTE).allowed());
        assertTrue(limiter.checkAndRecord(owner, maidA, maidB, 21_000L, 0L, MAX_PER_MINUTE).allowed());

        RelayRateLimiter.Decision blocked = limiter.checkAndRecord(owner, maidB, maidC, 31_000L, 0L, MAX_PER_MINUTE);
        assertFalse(blocked.allowed());
        assertEquals(RelayRateLimiter.Decision.Reason.OWNER_LIMIT, blocked.reason());
        assertEquals(30_000L, blocked.retryAfterMillis());

        // 最早一条在 61_000ms 滑出 60s 窗口
        assertTrue(limiter.checkAndRecord(owner, maidB, maidC, 61_000L, 0L, MAX_PER_MINUTE).allowed());
    }

    @Test
    void ownerLimit_isPerOwner() {
        RelayRateLimiter limiter = new RelayRateLimiter();
        UUID otherOwner = UUID.randomUUID();
        limiter.checkAndRecord(owner, maidA, maidB, 1_000L, 0L, 1);
        assertTrue(limiter.checkAndRecord(otherOwner, maidA, maidB, 1_000L, 0L, 1).allowed());
    }

    @Test
    void rejectedCall_doesNotConsumeOwnerQuota() {
        RelayRateLimiter limiter = new RelayRateLimiter();
        limiter.checkAndRecord(owner, maidA, maidB, 1_000L, COOLDOWN, MAX_PER_MINUTE);
        // 同一对被冷却拒绝两次后，换一对仍应有完整额度
        limiter.checkAndRecord(owner, maidA, maidB, 2_000L, COOLDOWN, MAX_PER_MINUTE);
        limiter.checkAndRecord(owner, maidA, maidB, 3_000L, COOLDOWN, MAX_PER_MINUTE);
        assertTrue(limiter.checkAndRecord(owner, maidA, maidC, 4_000L, COOLDOWN, MAX_PER_MINUTE).allowed());
    }

    @Test
    void nullIds_areTolerated_andRecordNothing() {
        RelayRateLimiter limiter = new RelayRateLimiter();
        assertTrue(limiter.checkAndRecord(null, null, null, 0L, COOLDOWN, MAX_PER_MINUTE).allowed());
        assertEquals(0, limiter.trackedPairCount());
    }

    @Test
    void clear_resetsState() {
        RelayRateLimiter limiter = new RelayRateLimiter();
        limiter.checkAndRecord(owner, maidA, maidB, 1_000L, COOLDOWN, MAX_PER_MINUTE);
        assertEquals(1, limiter.trackedPairCount());
        limiter.clear();
        assertEquals(0, limiter.trackedPairCount());
        assertTrue(limiter.checkAndRecord(owner, maidA, maidB, 1_000L, COOLDOWN, MAX_PER_MINUTE).allowed());
    }
}
