package com.github.tartaricacid.tlm_sincerely.ai.line;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link LineText#normalize} 的单元测试：引号、换行、标记、截断。
 */
class LineTextTest {

    @Test
    void stripsPairedQuotesOfAllKinds() {
        assertEquals("好困……", LineText.normalize("“好困……”", 40));
        assertEquals("好困……", LineText.normalize("「好困……」", 40));
        assertEquals("sleepy...", LineText.normalize("\"sleepy...\"", 40));
        assertEquals("sleepy...", LineText.normalize("'sleepy...'", 40));
    }

    @Test
    void keepsUnpairedQuotes() {
        assertEquals("说了句“好困", LineText.normalize("说了句“好困", 40));
        assertEquals("好困”", LineText.normalize("好困”", 40));
    }

    @Test
    void collapsesLineBreaksAndSpaces() {
        assertEquals("好困 真的好困", LineText.normalize("好困\n\n真的好困", 40));
        assertEquals("a b", LineText.normalize("  a   b  ", 40));
    }

    @Test
    void removesPauseMarkers() {
        assertEquals("好困 真的好困", LineText.normalize("好困<等待>真的好困", 40));
        assertEquals("zzz", LineText.normalize("<wait 2s>zzz", 40));
    }

    @Test
    void truncatesAndRetrimsQuotes() {
        assertEquals("12345", LineText.normalize("1234567890", 5));
        assertEquals("好困", LineText.normalize("“好困”拖得很长很长", 4));
    }

    @Test
    void blankInputGivesEmptyString() {
        assertEquals("", LineText.normalize(null, 10));
        assertEquals("", LineText.normalize("   ", 10));
        assertEquals("", LineText.normalize("<等待>", 10));
    }
}
