package com.github.tartaricacid.tlm_sincerely.chatbar;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 回复里的"停顿"标记 {@code <等待>}：解析、剥除，以及把一条带停顿的回复排成"几点几刻显示哪几条"的计划。
 * 纯逻辑，不依赖 Minecraft 类型，便于单元测试。
 *
 * <p>模型被告知可以在两句话之间写 {@code <等待>}，表示说到这里顿一下。标记本身<b>永远不该</b>出现在
 * 气泡、语音、聊天栏或历史里，所以 {@link #strip} 是一道恒开的兜底（即使停顿功能关着、模型自己乱写也会被剥掉）；
 * 停顿功能开着时，{@link #parse} 还会把标记的位置记下来，聊天栏据此把标记之后的话晚一点显示。
 *
 * <p>标记的写法宽松一些，免得模型多写个空格或带上秒数就漏网：{@code <等待>}、{@code < 等待 >}、{@code <等待/>}、
 * {@code <等待 2>}、{@code <等待:1.5秒>}，英文 {@code <wait>}、{@code <wait 2s>}（不分大小写）。带秒数时按它停，
 * 夹在 {@link #MIN_PAUSE_SECONDS} 与 {@link #MAX_PAUSE_SECONDS} 之间；不带就用调用方给的默认值。
 */
public final class ReplyPauses {
    /** 单个标记的停顿下限（秒）。 */
    public static final double MIN_PAUSE_SECONDS = 0.3D;
    /** 单个标记的停顿上限（秒）。 */
    public static final double MAX_PAUSE_SECONDS = 10.0D;

    private static final Pattern MARKER = Pattern.compile(
            "<\\s*(?:等待|wait)(?:\\s*[:：=]?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:秒|s|sec|seconds)?)?\\s*/?\\s*>",
            Pattern.CASE_INSENSITIVE);

    private ReplyPauses() {
    }

    /**
     * 一段连续显示的话。{@code pauseSecondsBefore} 是它和上一段之间的停顿（第一段恒为 0）。
     */
    public record Segment(String text, double pauseSecondsBefore) {
    }

    /**
     * 解析结果。{@code segments} 里没有空段；没有任何标记时只有一段（整段正文）。
     * {@code strippedText} 是把各段用换行连起来的纯文本（标记已经去掉），原样替换回复里的正文即可。
     */
    public record Parsed(boolean hadMarkers, List<Segment> segments, String strippedText) {
    }

    /** 一次显示：{@code delayTicks} tick 之后显示 {@code text}（一条聊天消息，已带名字前缀）。 */
    public record Delivery(int delayTicks, String text) {
    }

    /** 文本里有没有停顿标记。 */
    public static boolean containsMarker(String text) {
        return text != null && MARKER.matcher(text).find();
    }

    /**
     * 剥掉全部标记；标记所在的位置换成换行（前后是两句话，分开比粘在一起自然），首尾空白去掉。
     * 没有标记时原样返回（不动空白），保证"功能关着、模型也没写标记"时文本一个字都不变。
     */
    public static String strip(String text) {
        if (!containsMarker(text)) {
            return text;
        }
        return parse(text, 0.0D).strippedText();
    }

    /**
     * 按标记把正文切成若干段。
     *
     * @param text                 回复的聊天正文
     * @param defaultPauseSeconds  不带秒数的标记停多久
     */
    public static Parsed parse(String text, double defaultPauseSeconds) {
        if (text == null) {
            return new Parsed(false, List.of(), "");
        }
        Matcher matcher = MARKER.matcher(text);
        List<Segment> segments = new ArrayList<>();
        int cursor = 0;
        double pendingPause = 0.0D;
        boolean found = false;
        while (matcher.find()) {
            found = true;
            pendingPause = addSegment(segments, text.substring(cursor, matcher.start()), pendingPause);
            pendingPause += pauseOf(matcher.group(1), defaultPauseSeconds);
            cursor = matcher.end();
        }
        addSegment(segments, text.substring(cursor), pendingPause);
        if (!found) {
            String body = text.strip();
            return new Parsed(false, body.isEmpty() ? List.of() : List.of(new Segment(body, 0.0D)), text);
        }
        StringBuilder joined = new StringBuilder();
        for (Segment segment : segments) {
            if (joined.length() > 0) {
                joined.append('\n');
            }
            joined.append(segment.text());
        }
        return new Parsed(true, segments, joined.toString());
    }

    /** 往列表里加一段（空白段丢掉，它前面的停顿并入下一段）；返回还没有用掉的停顿。 */
    private static double addSegment(List<Segment> segments, String raw, double pendingPause) {
        String body = raw.strip();
        if (body.isEmpty()) {
            return pendingPause;
        }
        // 第一段前面的停顿没有意义（还没有话可以"顿"）
        segments.add(new Segment(body, segments.isEmpty() ? 0.0D : pendingPause));
        return 0.0D;
    }

    private static double pauseOf(String seconds, double defaultSeconds) {
        double value = defaultSeconds;
        if (seconds != null) {
            try {
                value = Double.parseDouble(seconds);
            } catch (NumberFormatException ignored) {
                // 正则保证是数字，仍留一道保险
            }
        }
        return Math.max(MIN_PAUSE_SECONDS, Math.min(MAX_PAUSE_SECONDS, value));
    }

    /**
     * 把一条带停顿的回复排成显示计划：第一段马上显示，之后每一段在累计停顿之后显示，
     * 累计停顿不超过 {@code maxTotalSeconds}（超过的部分不再往后拖，免得一条回复把聊天栏占上半分钟）。
     *
     * @param prefix          每条消息前面的 {@code <女仆名> }
     * @param splitLines      每段里的换行是否再拆成多条消息（同 {@code SplitReplyLines}）
     * @param maxTotalSeconds 一条回复里累计停顿的上限（秒）
     * @return 按显示先后排好的计划；{@code segments} 为空时返回空列表
     */
    public static List<Delivery> plan(String prefix, List<Segment> segments, boolean splitLines,
                                      double maxTotalSeconds) {
        List<Delivery> deliveries = new ArrayList<>();
        double elapsed = 0.0D;
        for (Segment segment : segments) {
            elapsed = Math.min(Math.max(0.0D, maxTotalSeconds), elapsed + segment.pauseSecondsBefore());
            int delayTicks = (int) Math.round(elapsed * 20.0D);
            if (splitLines) {
                for (String line : ReplySplitter.splitBody(prefix, segment.text())) {
                    deliveries.add(new Delivery(delayTicks, line));
                }
            } else {
                deliveries.add(new Delivery(delayTicks, prefix + segment.text()));
            }
        }
        return deliveries;
    }

    /** 比较"回复正文"用：把所有空白压成单个空格并去掉首尾。 */
    public static String normalizeForMatch(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ");
    }
}
