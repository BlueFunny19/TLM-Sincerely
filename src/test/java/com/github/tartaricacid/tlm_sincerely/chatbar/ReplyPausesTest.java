package com.github.tartaricacid.tlm_sincerely.chatbar;

import com.github.tartaricacid.tlm_sincerely.chatbar.ReplyPauses.Delivery;
import com.github.tartaricacid.tlm_sincerely.chatbar.ReplyPauses.Parsed;
import com.github.tartaricacid.tlm_sincerely.chatbar.ReplyPauses.Segment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReplyPauses} 的单元测试：标记的各种写法、切段、剥除，以及显示计划。
 */
class ReplyPausesTest {

    @Test
    void containsMarker_recognisesTheLooseSpellings() {
        for (String marker : new String[]{"<等待>", "< 等待 >", "<等待/>", "<等待 />", "<等待 2>", "<等待:1.5秒>",
                "<等待：3>", "<wait>", "<WAIT>", "<wait 2s>", "<Wait/>", "<wait=2 sec>"}) {
            assertTrue(ReplyPauses.containsMarker("你好" + marker + "再见"), marker);
        }
    }

    @Test
    void containsMarker_ignoresOrdinaryText() {
        assertFalse(ReplyPauses.containsMarker(null));
        assertFalse(ReplyPauses.containsMarker(""));
        assertFalse(ReplyPauses.containsMarker("等待一下吧"));
        assertFalse(ReplyPauses.containsMarker("我在 <b>等待</b> 你"));
        assertFalse(ReplyPauses.containsMarker("a < b and c > d"));
        assertFalse(ReplyPauses.containsMarker("<waiting>"));
    }

    @Test
    void parse_withoutMarkerKeepsTheTextAsOneSegment() {
        Parsed parsed = ReplyPauses.parse("主人，欢迎回来！\n今天辛苦了。", 1.5);
        assertFalse(parsed.hadMarkers());
        assertEquals(List.of(new Segment("主人，欢迎回来！\n今天辛苦了。", 0.0)), parsed.segments());
        assertEquals("主人，欢迎回来！\n今天辛苦了。", parsed.strippedText());
    }

    @Test
    void parse_splitsAtEachMarkerAndUsesTheDefaultPause() {
        Parsed parsed = ReplyPauses.parse("诶？<等待>这个是给我的吗……<等待>谢谢主人！", 1.5);
        assertTrue(parsed.hadMarkers());
        assertEquals(List.of(
                new Segment("诶？", 0.0),
                new Segment("这个是给我的吗……", 1.5),
                new Segment("谢谢主人！", 1.5)), parsed.segments());
        assertEquals("诶？\n这个是给我的吗……\n谢谢主人！", parsed.strippedText());
    }

    @Test
    void parse_aMarkerWithSecondsUsesThemClamped() {
        Parsed parsed = ReplyPauses.parse("a<等待 2>b<wait 0.01>c<等待:99秒>d", 1.5);
        assertEquals(2.0, parsed.segments().get(1).pauseSecondsBefore());
        assertEquals(ReplyPauses.MIN_PAUSE_SECONDS, parsed.segments().get(2).pauseSecondsBefore());
        assertEquals(ReplyPauses.MAX_PAUSE_SECONDS, parsed.segments().get(3).pauseSecondsBefore());
    }

    @Test
    void parse_leadingTrailingAndConsecutiveMarkers() {
        Parsed leading = ReplyPauses.parse("<等待>你好", 1.5);
        assertEquals(List.of(new Segment("你好", 0.0)), leading.segments(), "开头的停顿没有意义");

        Parsed trailing = ReplyPauses.parse("你好<等待>", 1.5);
        assertEquals(List.of(new Segment("你好", 0.0)), trailing.segments());
        assertEquals("你好", trailing.strippedText());

        Parsed doubled = ReplyPauses.parse("a<等待><等待>b", 1.5);
        assertEquals(List.of(new Segment("a", 0.0), new Segment("b", 3.0)), doubled.segments(),
                "连续的标记停顿相加，不产生空段");
    }

    @Test
    void parse_onlyMarkersYieldsNoSegments() {
        Parsed parsed = ReplyPauses.parse("<等待><等待>", 1.5);
        assertTrue(parsed.hadMarkers());
        assertTrue(parsed.segments().isEmpty());
        assertEquals("", parsed.strippedText());
    }

    @Test
    void parse_nullIsEmpty() {
        Parsed parsed = ReplyPauses.parse(null, 1.5);
        assertFalse(parsed.hadMarkers());
        assertTrue(parsed.segments().isEmpty());
    }

    @Test
    void strip_returnsTheSameStringWhenThereIsNoMarker() {
        String text = "  保持原样 \n 连空白也不动  ";
        assertSame(text, ReplyPauses.strip(text));
        assertEquals(null, ReplyPauses.strip(null));
    }

    @Test
    void strip_replacesMarkersWithLineBreaks() {
        assertEquals("好的\n我这就去", ReplyPauses.strip("好的<等待>我这就去"));
        assertEquals("好的", ReplyPauses.strip("<wait>好的<等待 2>"));
    }

    @Test
    void plan_firstSegmentIsImmediateAndLaterOnesAccumulate() {
        List<Segment> segments = List.of(new Segment("a", 0.0), new Segment("b", 1.5), new Segment("c", 2.0));
        assertEquals(List.of(
                new Delivery(0, "<小澪> a"),
                new Delivery(30, "<小澪> b"),
                new Delivery(70, "<小澪> c")), ReplyPauses.plan("<小澪> ", segments, true, 60.0));
    }

    @Test
    void plan_totalPauseIsCapped() {
        List<Segment> segments = List.of(new Segment("a", 0.0), new Segment("b", 8.0), new Segment("c", 8.0),
                new Segment("d", 8.0));
        List<Delivery> plan = ReplyPauses.plan("", segments, true, 10.0);
        assertEquals(List.of(0, 160, 200, 200), plan.stream().map(Delivery::delayTicks).toList());
    }

    @Test
    void plan_splitLinesGivesEachLineItsOwnMessageWithTheSameDelay() {
        List<Segment> segments = List.of(new Segment("第一句\n第二句", 0.0), new Segment("第三句", 1.0));
        assertEquals(List.of(
                new Delivery(0, "<小澪> 第一句"),
                new Delivery(0, "<小澪> 第二句"),
                new Delivery(20, "<小澪> 第三句")), ReplyPauses.plan("<小澪> ", segments, true, 60.0));
    }

    @Test
    void plan_withoutSplitKeepsOneMessagePerSegment() {
        List<Segment> segments = List.of(new Segment("第一句\n第二句", 0.0), new Segment("第三句", 1.0));
        assertEquals(List.of(
                new Delivery(0, "<小澪> 第一句\n第二句"),
                new Delivery(20, "<小澪> 第三句")), ReplyPauses.plan("<小澪> ", segments, false, 60.0));
    }

    @Test
    void plan_noSegmentsMeansNothingToShow() {
        assertTrue(ReplyPauses.plan("<小澪> ", List.of(), true, 60.0).isEmpty());
    }

    @Test
    void normalizeForMatch_collapsesWhitespace() {
        assertEquals("a b c", ReplyPauses.normalizeForMatch("  a \n b\t c  "));
        assertEquals("", ReplyPauses.normalizeForMatch(null));
    }

    @Test
    void strippedTextOfParseMatchesWhatTheChatBodyWillBe() {
        // 钩子把 strippedText 写回回复正文；聊天栏收到的正文要能和它对上（空白差异不影响匹配）
        Parsed parsed = ReplyPauses.parse("诶？<等待>真的吗<等待>太好了", 1.0);
        String body = ReplySplitter.bodyOf("<小澪> " + parsed.strippedText());
        assertEquals(ReplyPauses.normalizeForMatch(parsed.strippedText()), ReplyPauses.normalizeForMatch(body));
    }
}
