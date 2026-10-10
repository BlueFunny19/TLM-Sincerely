package com.github.tartaricacid.tlm_sincerely.ai.overtime;

/**
 * 加班疲劳相关的所有文字，中英各一套，由调用方按 {@code PromptLang.isChinese(PromptLang.of(maid))} 选择 {@code zh}：
 * <ul>
 *   <li>女仆自己说出口的话（头顶气泡 + 聊天栏，不经过 LLM）：抱怨、要点心、吃到点心、去休息；</li>
 *   <li>给模型看的一句说明（工具结果里追加）。</li>
 * </ul>
 * 台词有几个变体，调用方传一个种子（例如女仆 id 加上当前时间）轮换着说，免得每次都是同一句。
 * 台词里不写点心的名字：女仆的话只能是纯文字，点心的名字会另外用可本地化的消息发给主人。
 */
public final class OvertimeText {
    private OvertimeText() {
    }

    private static String pick(long seed, String... variants) {
        return variants[(int) Math.floorMod(seed, (long) variants.length)];
    }

    /** 累了，但不会向主人要东西（点心功能关闭），只是抱怨一句、继续干。 */
    public static String complain(boolean zh, long seed) {
        return zh
                ? pick(seed,
                "（打了个哈欠）都这个点了，我还在干活呢……",
                "有点困了……能让我歇一会儿吗？",
                "再这样熬下去，我要撑不住啦。")
                : pick(seed,
                "(yawns) It's the middle of the night and I'm still working...",
                "I'm getting sleepy... could I rest for a bit?",
                "If this keeps up, I won't be able to hold on much longer.");
    }

    /** 累了，向主人要点心。 */
    public static String ask(boolean zh, long seed) {
        return zh
                ? pick(seed,
                "好困……能给我一点吃的点心吗？吃了我就能再坚持一会儿。",
                "撑不住了……有点心的话，给我一点吧？")
                : pick(seed,
                "I'm so sleepy... could you give me a little treat? Then I can keep going a bit longer.",
                "I can hardly keep my eyes open... do you have a treat for me?");
    }

    /** 自己背包里有点心，直接吃了。 */
    public static String ateOwn(boolean zh, long seed) {
        return zh
                ? pick(seed,
                "吃了点心，精神多了，再坚持一会儿！",
                "偷偷吃了块点心……好了，继续干活！")
                : pick(seed,
                "I had a treat and feel much better. I can keep going a little longer!",
                "I sneaked a treat... all right, back to work!");
    }

    /** 主人递了点心。 */
    public static String thanks(boolean zh, long seed) {
        return zh
                ? pick(seed,
                "谢谢！吃了点心又有劲了，再坚持一会儿。",
                "太好了，有点心吃！我还能再干一阵。")
                : pick(seed,
                "Thank you! That gave me new energy; I'll keep going a little longer.",
                "Wonderful, a treat! I can work a while longer.");
    }

    /** 没要到点心（或主人不在），撑不住了，去休息。 */
    public static String goRest(boolean zh, long seed) {
        return zh
                ? pick(seed,
                "撑不住了……我先去休息啦，晚安。",
                "不行了，眼睛都睁不开了，我去睡一会儿。")
                : pick(seed,
                "I can't keep going... I'm going to rest now. Good night.",
                "That's it, I can't keep my eyes open. I'm going to sleep for a while.");
    }

    /** 给模型看的：她刚被安排去休息，这段休息时间里不能再加班。 */
    public static String tiredNote(boolean zh, String maidName) {
        return zh ? maidName + "已经累坏了，刚刚去休息，这段休息时间里不能再让她加班。"
                : maidName + " is exhausted and has just gone to rest; she cannot work overtime again during this rest period.";
    }
}
