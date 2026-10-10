package com.github.tartaricacid.tlm_sincerely.ai.line;

import com.github.tartaricacid.tlm_sincerely.chatbar.ReplyPauses;

/**
 * 模型生成的"女仆说的一句话"的规整（纯逻辑，便于单元测试）：
 * 去掉停顿标记、压掉换行、去掉首尾成对的引号、截断到上限。规整后为空串表示这次没有可用的话。
 */
public final class LineText {
    private LineText() {
    }

    public static String normalize(String raw, int maxChars) {
        if (raw == null) {
            return "";
        }
        // 这条回复不走主模组的完成流程，主模组开头的"剥停顿标记"钩子碰不到它，这里自己剥一遍
        String text = ReplyPauses.strip(raw);
        text = text.replace('\r', ' ').replace('\n', ' ').strip().replaceAll(" {2,}", " ");
        text = trimQuotes(text);
        if (text.length() > maxChars) {
            text = trimQuotes(text.substring(0, maxChars).strip());
        }
        return text;
    }

    /** 去掉首尾成对的引号（中英引号、直角引号）；两侧不成对时不动，免得误删正文里的符号。 */
    private static String trimQuotes(String text) {
        String result = text;
        boolean changed = true;
        while (changed && result.length() >= 2) {
            changed = false;
            char first = result.charAt(0);
            char last = result.charAt(result.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')
                    || (first == '“' && last == '”') || (first == '‘' && last == '’')
                    || (first == '「' && last == '」') || (first == '『' && last == '』')) {
                result = result.substring(1, result.length() - 1).strip();
                changed = true;
            }
        }
        return result;
    }
}
