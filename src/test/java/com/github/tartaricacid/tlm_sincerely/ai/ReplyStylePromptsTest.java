package com.github.tartaricacid.tlm_sincerely.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReplyStylePrompts} 的单元测试：开关组合与中英版本。
 */
class ReplyStylePromptsTest {

    @Test
    void bothSwitchesOffAddsNothing() {
        assertNull(ReplyStylePrompts.of(true, false, false));
        assertNull(ReplyStylePrompts.of(false, false, false));
    }

    @Test
    void splitOnlyDoesNotMentionTheMarker() {
        for (boolean zh : new boolean[]{true, false}) {
            String text = ReplyStylePrompts.of(zh, false, true);
            assertFalse(text.isBlank());
            assertFalse(text.contains("<等待>") || text.contains("<wait>"));
        }
    }

    @Test
    void pauseOnlyMentionsTheMarkerButNotSplitting() {
        assertTrue(ReplyStylePrompts.of(true, true, false).contains("<等待>"));
        assertTrue(ReplyStylePrompts.of(false, true, false).contains("<wait>"));
        assertFalse(ReplyStylePrompts.of(true, true, false).contains("回复分句"));
        assertFalse(ReplyStylePrompts.of(false, true, false).contains("Splitting Replies"));
    }

    @Test
    void bothOnCombinesTheTwoSections() {
        String zh = ReplyStylePrompts.of(true, true, true);
        assertTrue(zh.contains("回复分句") && zh.contains("回复停顿"));
        String en = ReplyStylePrompts.of(false, true, true);
        assertTrue(en.contains("Splitting Replies") && en.contains("Pauses in Replies"));
    }

    @Test
    void languagesDiffer() {
        assertNotEquals(ReplyStylePrompts.of(true, true, true), ReplyStylePrompts.of(false, true, true));
        assertNotEquals(ReplyStylePrompts.of(true, true, false), ReplyStylePrompts.of(true, false, true));
    }

    @Test
    void everyVariantEndsByCallingItselfInternal() {
        assertTrue(ReplyStylePrompts.of(true, true, false).endsWith("不要提到它们。"));
        assertTrue(ReplyStylePrompts.of(false, false, true).endsWith("replying to the player."));
    }
}
