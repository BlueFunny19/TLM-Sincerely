package com.github.tartaricacid.tlm_sincerely.chatbar;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AtTargetParser} 的单元测试。本类不依赖任何 Minecraft 类型。
 */
class AtTargetParserTest {
    private static final List<String> KEYWORDS = List.of("全体", "all");

    @Test
    void singleTarget_matchesLegacyBehavior() {
        AtTargetParser.Result result = AtTargetParser.parse("@Alice hello world", "@", KEYWORDS);
        assertTrue(result.valid());
        assertEquals(List.of("Alice"), result.names());
        assertEquals("hello world", result.message());
        assertFalse(result.all());
        assertTrue(result.hasNamedTargets());
    }

    @Test
    void multipleTargets_keepsOrder() {
        AtTargetParser.Result result = AtTargetParser.parse("@Alice @Bob hi there", "@", KEYWORDS);
        assertTrue(result.valid());
        assertEquals(List.of("Alice", "Bob"), result.names());
        assertEquals("hi there", result.message());
    }

    @Test
    void duplicateTargets_areDeduplicatedKeepingFirstSpelling() {
        AtTargetParser.Result result = AtTargetParser.parse("@Alice @alice @Alice hey", "@", KEYWORDS);
        assertTrue(result.valid());
        assertEquals(List.of("Alice", "alice"), result.names());
    }

    @Test
    void allKeyword_isDetectedIgnoringCase() {
        AtTargetParser.Result lower = AtTargetParser.parse("@all hello", "@", KEYWORDS);
        assertTrue(lower.valid());
        assertTrue(lower.all());
        assertTrue(lower.names().isEmpty());

        AtTargetParser.Result mixed = AtTargetParser.parse("@All hello", "@", KEYWORDS);
        assertTrue(mixed.valid());
        assertTrue(mixed.all());

        AtTargetParser.Result chinese = AtTargetParser.parse("@全体 hello", "@", KEYWORDS);
        assertTrue(chinese.valid());
        assertTrue(chinese.all());
    }

    @Test
    void allKeyword_canBeMixedWithNamedTargets() {
        AtTargetParser.Result result = AtTargetParser.parse("@全体 @Alice 集合啦", "@", KEYWORDS);
        assertTrue(result.valid());
        assertTrue(result.all());
        assertEquals(List.of("Alice"), result.names());
        assertEquals("集合啦", result.message());
    }

    @Test
    void missingBody_isInvalid() {
        assertFalse(AtTargetParser.parse("@Alice", "@", KEYWORDS).valid());
        assertFalse(AtTargetParser.parse("@Alice   ", "@", KEYWORDS).valid());
        assertFalse(AtTargetParser.parse("@all", "@", KEYWORDS).valid());
    }

    @Test
    void noPrefix_isInvalid() {
        assertFalse(AtTargetParser.parse("hello @Alice", "@", KEYWORDS).valid());
        assertFalse(AtTargetParser.parse("", "@", KEYWORDS).valid());
        assertFalse(AtTargetParser.parse(null, "@", KEYWORDS).valid());
        assertFalse(AtTargetParser.parse("@Alice hi", "", KEYWORDS).valid());
        assertFalse(AtTargetParser.parse("@Alice hi", null, KEYWORDS).valid());
    }

    @Test
    void nonPrefixWord_startsBody() {
        AtTargetParser.Result result = AtTargetParser.parse("@Alice hello @Bob", "@", KEYWORDS);
        assertTrue(result.valid());
        assertEquals(List.of("Alice"), result.names());
        assertEquals("hello @Bob", result.message());
    }

    @Test
    void customPrefix_isSupported() {
        AtTargetParser.Result result = AtTargetParser.parse("#Alice #Bob 你好", "#", KEYWORDS);
        assertTrue(result.valid());
        assertEquals(List.of("Alice", "Bob"), result.names());
        assertEquals("你好", result.message());

        assertFalse(AtTargetParser.parse("@Alice hi", "#", KEYWORDS).valid());
    }

    @Test
    void barePrefix_isInvalid_soLegacyFallbackKeepsWorking() {
        // 旧正则 "^@(.+?)\s+(.+)$" 不匹配 "@ 内容"，解析器必须同样判为无效
        assertFalse(AtTargetParser.parse("@ 内容", "@", KEYWORDS).valid());
        assertFalse(AtTargetParser.parse("@Alice @ 内容", "@", KEYWORDS).valid());
    }

    @Test
    void nullKeywords_areTolerated() {
        AtTargetParser.Result result = AtTargetParser.parse("@全体 hello", "@", null);
        assertTrue(result.valid());
        assertFalse(result.all());
        assertEquals(List.of("全体"), result.names());
    }

    @Test
    void whitespaceInsideBody_isNormalized() {
        AtTargetParser.Result result = AtTargetParser.parse("@Alice 你好   世界", "@", KEYWORDS);
        assertEquals("你好 世界", result.message());
    }
}
