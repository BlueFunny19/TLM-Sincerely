package com.github.tartaricacid.tlm_sincerely.chatbar;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ReplySplitter} 的单元测试。
 */
class ReplySplitterTest {

    @Test
    void split_paragraphsBecomeSeparateLinesWithSamePrefix() {
        String text = "<澪> 我很好呀！就是站在这儿看樱花看入迷了。\n\n主人你看，花瓣都落我耳朵上了。";
        assertEquals(List.of(
                "<澪> 我很好呀！就是站在这儿看樱花看入迷了。",
                "<澪> 主人你看，花瓣都落我耳朵上了。"), ReplySplitter.split(text));
    }

    @Test
    void split_singleParagraphIsUnchanged() {
        String text = "<澪> 我很好呀！就是站在这儿看樱花看入迷了。";
        assertEquals(List.of(text), ReplySplitter.split(text));
    }

    @Test
    void split_blankLinesAndWhitespaceAreDropped() {
        String text = "<颜> 第一句\n \n\n  第二句  \r\n第三句\n";
        assertEquals(List.of("<颜> 第一句", "<颜> 第二句", "<颜> 第三句"), ReplySplitter.split(text));
    }

    @Test
    void split_onlyTrailingNewlineKeepsOriginal() {
        String text = "<颜> 只有一句\n";
        assertEquals(List.of(text), ReplySplitter.split(text));
    }

    @Test
    void split_withoutPrefixStillSplitsBody() {
        assertEquals(List.of("甲", "乙"), ReplySplitter.split("甲\n乙"));
    }

    @Test
    void split_nullAndEmpty() {
        assertEquals(List.of(""), ReplySplitter.split(null));
        assertEquals(List.of(""), ReplySplitter.split(""));
    }
}
