package com.github.tartaricacid.tlm_sincerely.chatbar;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把女仆的一条多段回复拆成多条聊天消息。
 *
 * <p>主 Mod 把 LLM 回复整段拼成 {@code <女仆名> 正文} 一条聊天消息发出，模型分段（换行）后在聊天栏里就是
 * 一大块带空行的文字，不像女仆连续说了几句话。这里按换行把正文拆开，每段都加上同样的 {@code <女仆名> } 前缀，
 * 让每段在聊天栏里各占一条。
 *
 * <p>本类不依赖任何 Minecraft 类型，便于纯 Java 单元测试。
 */
public final class ReplySplitter {
    /** 主 Mod 拼出的前缀形如 {@code <女仆名> }；名字里不会有 '>' 或换行。 */
    private static final Pattern PREFIX = Pattern.compile("^(<[^>\\n\\r]{1,64}> )");

    private ReplySplitter() {
    }

    /**
     * @param fullText 整条消息的纯文本（含 {@code <女仆名> } 前缀）
     * @return 拆开后的各条消息；不需要拆（只有一段、或无法识别）时返回只含原文的列表
     */
    public static List<String> split(String fullText) {
        if (fullText == null || fullText.isEmpty()) {
            return List.of(fullText == null ? "" : fullText);
        }
        String prefix = prefixOf(fullText);
        List<String> lines = splitBody(prefix, fullText.substring(prefix.length()));
        if (lines.size() <= 1) {
            return List.of(fullText);
        }
        return lines;
    }

    /** 整条消息开头的 {@code <女仆名> } 前缀；认不出时返回空串。 */
    public static String prefixOf(String fullText) {
        if (fullText == null) {
            return "";
        }
        Matcher matcher = PREFIX.matcher(fullText);
        return matcher.find() ? matcher.group(1) : "";
    }

    /** 去掉前缀之后的正文。 */
    public static String bodyOf(String fullText) {
        return fullText == null ? "" : fullText.substring(prefixOf(fullText).length());
    }

    /**
     * 把一段正文按换行拆开，每段前面加上 {@code prefix}；空段丢掉。正文没有可显示的内容时返回空列表。
     */
    public static List<String> splitBody(String prefix, String body) {
        List<String> lines = new ArrayList<>();
        if (body == null) {
            return lines;
        }
        for (String part : body.split("\\R+")) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                lines.add(prefix + trimmed);
            }
        }
        return lines;
    }
}
