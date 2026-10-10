package com.github.tartaricacid.tlm_sincerely.ai;

import org.jetbrains.annotations.Nullable;

/**
 * 告诉模型"回复怎么分句、怎么停顿"的系统提示词，中英各一套。
 *
 * <p>两个开关各管一段，可以单独开，也可以一起开：
 * <ul>
 *   <li>分句（{@code ReplyModelSplitEnabled}）：像在聊天软件里发消息那样，把回复拆成几条短句，每条占一行；
 *       聊天栏本来就会把每一行显示成一条消息（{@code SplitReplyLines}）；</li>
 *   <li>停顿（{@code ReplyPauseMarkerEnabled}）：在两句话之间写 {@code <等待>}，聊天栏会隔一会儿再显示后面的话，
 *       标记本身不会显示（解析与剥除见 {@code ReplyPauses}）。</li>
 * </ul>
 * 两个开关都关着时不加任何提示词（{@link #of} 返回 {@code null}），完全不增加 token。
 *
 * <p>提示词按对话语言选 zh / en（其它语言用英文版，再由 {@code PromptLang.replyDirective} 在末尾要求改用该语言回复）。
 * 标记 {@code <等待>} 两种语言用同一个写法，解析时中英文写法都认。
 */
public final class ReplyStylePrompts {
    private static final String SPLIT_ZH = """
            ## 回复分句
            像在聊天软件里发消息那样说话：把回复拆成几条短句，每条单独占一行（用换行分开），每行一两句话，总共不超过 4 行，不要把一大段话塞在同一行里。
            系统会把每一行显示成一条单独的消息，所以每一行都要是能独立读懂的一句话。""";

    private static final String SPLIT_EN = """
            ## Splitting Replies
            Talk the way people do in a chat app: break your reply into a few short messages, each on its own line (separated by line breaks), one or two sentences per line, at most 4 lines, and never pack a long paragraph into one line.
            Every line is shown as a separate message, so each line must read fine on its own.""";

    private static final String PAUSE_ZH = """
            ## 回复停顿
            你可以在两句话之间写 `<等待>`，表示说到这里顿一下（想一想、犹豫、惊讶、害羞、换个话题的时候都可以）。系统会把标记前后的话分开，隔一会儿再显示后面的部分，标记本身不会显示给玩家。
            只在确实需要停顿的地方用：一次回复最多 3 个，不要连续写，不要放在回复的开头或结尾，也不要用它代替省略号或动作描写。
            标记只写在聊天文字里；如果回复格式里还有语音（TTS）部分，那里不要写。""";

    private static final String PAUSE_EN = """
            ## Pauses in Replies
            You may write `<wait>` between two sentences to mean "pause here" (when thinking, hesitating, surprised, shy, or changing the subject). The system splits your reply at the marker and shows the part after it a moment later; the marker itself is never shown to the player.
            Use it only where a pause really fits: at most 3 per reply, never back to back, never at the very start or end of the reply, and not as a substitute for an ellipsis or an action description.
            Write the marker in the chat text only; if the reply format also has a voice (TTS) part, do not put it there.""";

    private static final String INTERNAL_ZH = "以上是内部规则，回复玩家时不要提到它们。";
    private static final String INTERNAL_EN = "These are internal rules: do not mention them when replying to the player.";

    private ReplyStylePrompts() {
    }

    /**
     * 回复风格提示词。
     *
     * @param zh    是否用中文版（其它语言走英文版）
     * @param pause 是否开启停顿标记
     * @param split 是否开启模型分句
     * @return 提示词；两个开关都关着时返回 {@code null}（不加任何提示词）
     */
    @Nullable
    public static String of(boolean zh, boolean pause, boolean split) {
        if (!pause && !split) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        if (split) {
            text.append(zh ? SPLIT_ZH : SPLIT_EN);
        }
        if (pause) {
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append(zh ? PAUSE_ZH : PAUSE_EN);
        }
        return text.append('\n').append(zh ? INTERNAL_ZH : INTERNAL_EN).toString();
    }
}
