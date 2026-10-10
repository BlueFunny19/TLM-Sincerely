package com.github.tartaricacid.tlm_sincerely.ai.move;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FollowSuspension} 的单元测试：恢复决策（精确恢复 / 检测到外部改动就不恢复）与记录相等性。
 */
class FollowSuspensionTest {
    @Test
    void decideRestore_restoresWhenHomeModeUnchanged_off() {
        FollowSuspension suspension = new FollowSuspension(false, false, 0L, 0L, 0L, -1.0F);
        assertEquals(FollowSuspension.RestoreAction.RESTORE, suspension.decideRestore(false));
    }

    @Test
    void decideRestore_restoresWhenHomeModeUnchanged_on() {
        // 女仆原本就是"居家 + 工作坐标"：必须精确恢复成原样，而不是一律关掉居家模式
        FollowSuspension suspension = new FollowSuspension(true, true, 10L, 64L, 20L, 16.0F);
        assertEquals(FollowSuspension.RestoreAction.RESTORE, suspension.decideRestore(true));
    }

    @Test
    void decideRestore_externalChangeWhenHomeModeFlippedOn() {
        FollowSuspension suspension = new FollowSuspension(false, true, 10L, 64L, 20L, 16.0F);
        assertEquals(FollowSuspension.RestoreAction.EXTERNAL_CHANGE, suspension.decideRestore(true));
    }

    @Test
    void decideRestore_externalChangeWhenHomeModeFlippedOff() {
        FollowSuspension suspension = new FollowSuspension(true, false, 0L, 0L, 0L, -1.0F);
        assertEquals(FollowSuspension.RestoreAction.EXTERNAL_CHANGE, suspension.decideRestore(false));
    }

    @Test
    void equalsAndHashCode_coverAllFields() {
        FollowSuspension base = new FollowSuspension(true, true, 1L, 2L, 3L, 4.0F);
        assertEquals(base, new FollowSuspension(true, true, 1L, 2L, 3L, 4.0F));
        assertEquals(base.hashCode(), new FollowSuspension(true, true, 1L, 2L, 3L, 4.0F).hashCode());
        assertNotEquals(base, new FollowSuspension(false, true, 1L, 2L, 3L, 4.0F));
        assertNotEquals(base, new FollowSuspension(true, false, 1L, 2L, 3L, 4.0F));
        assertNotEquals(base, new FollowSuspension(true, true, 9L, 2L, 3L, 4.0F));
        assertNotEquals(base, new FollowSuspension(true, true, 1L, 2L, 3L, 5.0F));
        assertNotEquals(base, null);
    }

    @Test
    void describe_isReadable() {
        String text = new FollowSuspension(true, true, 1L, 2L, 3L, 4.0F).describe();
        assertTrue(text.contains("homeMode=true"));
        assertTrue(text.contains("center=(1, 2, 3)"));
        assertTrue(text.contains("radius=4.0"));
        assertTrue(new FollowSuspension(false, false, 0L, 0L, 0L, -1.0F).describe().contains("restriction=false"));
    }
}
