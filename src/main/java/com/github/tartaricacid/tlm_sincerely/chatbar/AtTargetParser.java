package com.github.tartaricacid.tlm_sincerely.chatbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 聊天栏 {@code @名字} 前缀解析器（多目标版）。
 *
 * <p>本类刻意不依赖任何 Minecraft 类型，便于用纯 Java 单元测试覆盖规则。
 *
 * <p>规则：从文本开头起，连续的“以前缀字符开头的词”都是目标；遇到第一个不以前缀字符
 * 开头的词，其后全部内容都是正文；正文为空则整体无效（沿用旧单目标没有正文时的行为）。
 * 目标名字保序去重；命中“全体”关键词（忽略大小写）时置 {@link Result#all()}。
 *
 * <p>与旧正则 {@code ^<prefix>(.+?)\s+(.+)$} 的兼容性：
 * <ul>
 *   <li>只有一个 {@code @名字 内容} 时，{@code names} 为单元素、{@code message} 与旧解析一致；
 *   <li>名字里含空格仍然不支持（规则本就停在第一个空白）；
 *   <li>前缀后紧跟空白（例如 {@code @ 内容}）返回无效结果，由调用方回退到旧逻辑，避免行为漂移。
 * </ul>
 */
public final class AtTargetParser {
    private AtTargetParser() {
    }

    /**
     * @param text        原始文本
     * @param prefix      前缀字符（例如 {@code "@"}）；为空时直接返回无效结果
     * @param allKeywords “全体”关键词集合，比较时忽略大小写；可为 null
     */
    public static Result parse(String text, String prefix, Iterable<? extends String> allKeywords) {
        if (text == null || prefix == null || prefix.isEmpty()) {
            return Result.invalid();
        }

        List<String> words = tokenize(text);
        if (words.isEmpty()) {
            return Result.invalid();
        }

        Set<String> names = new LinkedHashSet<>();
        boolean all = false;
        boolean anyTarget = false;
        int index = 0;
        while (index < words.size() && words.get(index).startsWith(prefix)) {
            String name = words.get(index).substring(prefix.length());
            if (name.isEmpty()) {
                // 只有裸前缀（"@ 正文"）：按“没有目标”处理，交给旧逻辑兜底
                return Result.invalid();
            }
            anyTarget = true;
            if (matchesKeyword(name, allKeywords)) {
                all = true;
            } else {
                names.add(name);
            }
            index++;
        }
        if (!anyTarget) {
            return Result.invalid();
        }

        String message = joinFrom(words, index).trim();
        if (message.isEmpty()) {
            return Result.invalid();
        }
        return new Result(new ArrayList<>(names), all, true, message);
    }

    /** 按空白切词；仅按字符切分，不依赖 Minecraft 的 StringUtil。 */
    private static List<String> tokenize(String text) {
        List<String> words = new ArrayList<>();
        int i = 0;
        int length = text.length();
        while (i < length) {
            while (i < length && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i >= length) {
                break;
            }
            int start = i;
            while (i < length && !Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            words.add(text.substring(start, i));
        }
        return words;
    }

    private static String joinFrom(List<String> words, int from) {
        if (from >= words.size()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < words.size(); i++) {
            if (i > from) {
                builder.append(' ');
            }
            builder.append(words.get(i));
        }
        return builder.toString();
    }

    private static boolean matchesKeyword(String name, Iterable<? extends String> allKeywords) {
        if (allKeywords == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String keyword : allKeywords) {
            if (keyword != null && !keyword.isEmpty() && lower.equals(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param names    目标名字，保序去重（不含“全体”关键词）
     * @param all      是否命中“全体”关键词
     * @param valid    是否有目标且有正文；false 时调用方应回退到旧单目标逻辑
     * @param message  正文
     */
    public record Result(List<String> names, boolean all, boolean valid, String message) {
        private static final Result INVALID = new Result(Collections.emptyList(), false, false, "");

        public static Result invalid() {
            return INVALID;
        }

        public boolean hasNamedTargets() {
            return !names.isEmpty();
        }
    }
}
