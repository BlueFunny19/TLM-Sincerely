package com.github.tartaricacid.tlm_sincerely.ai.line;

import com.github.tartaricacid.tlm_sincerely.ai.line.LinePrompts.Situation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LinePrompts} 的单元测试：每种情景、每种语言都有各自的提示词，且都交代了"只输出这句话"。
 */
class LinePromptsTest {

    @Test
    void everySituationHasADistinctPromptInBothLanguages() {
        for (boolean zh : new boolean[]{true, false}) {
            Set<String> seen = new HashSet<>();
            for (Situation situation : Situation.values()) {
                String prompt = LinePrompts.of(situation, zh, 7);
                assertFalse(prompt.isBlank(), situation + " zh=" + zh);
                assertTrue(seen.add(prompt), "提示词不应重复: " + situation + " zh=" + zh);
            }
            assertEquals(Situation.values().length, seen.size());
        }
    }

    @Test
    void languagesDiffer() {
        for (Situation situation : Situation.values()) {
            assertFalse(LinePrompts.of(situation, true, 7).equals(LinePrompts.of(situation, false, 7)), situation.toString());
        }
    }

    @Test
    void everyPromptAsksForOnlyTheLineItself() {
        for (Situation situation : Situation.values()) {
            assertTrue(LinePrompts.of(situation, true, 7).contains("只输出这句话本身"), situation + " zh");
            assertTrue(LinePrompts.of(situation, false, 7).contains("Output only that line itself"), situation + " en");
        }
    }

    @Test
    void overtimeMinutesAreWrittenIntoTheSituationsThatMentionThem() {
        for (Situation situation : new Situation[]{Situation.TIRED_COMPLAIN, Situation.TIRED_ASK, Situation.GO_REST}) {
            assertTrue(LinePrompts.of(situation, true, 12).contains("12"), situation + " zh");
            assertTrue(LinePrompts.of(situation, false, 12).contains("12"), situation + " en");
        }
    }

    @Test
    void minutesAreAtLeastOne() {
        assertTrue(LinePrompts.of(Situation.TIRED_ASK, true, 0).contains(" 1 分钟"));
        assertTrue(LinePrompts.of(Situation.TIRED_ASK, false, -5).contains("about 1 minutes"));
    }

    @Test
    void askingDoesNotNameASpecificTreat() {
        assertTrue(LinePrompts.of(Situation.TIRED_ASK, true, 5).contains("不要说具体是哪种点心"));
        assertTrue(LinePrompts.of(Situation.TIRED_ASK, false, 5).contains("Do not name a specific treat"));
    }
}
