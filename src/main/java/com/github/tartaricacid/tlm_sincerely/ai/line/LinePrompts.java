package com.github.tartaricacid.tlm_sincerely.ai.line;

/**
 * 请模型按人设说一句"此刻该说的话"的提示词，中英各一套。
 *
 * <p>消息前缀里已经有她的人设与对话历史（见 {@link MaidLines}），这里只交代"眼下发生了什么、要说哪一类话"。
 * 话说出口时不会写进聊天历史，所以每条提示词都要求：只输出这句话本身、不加引号和动作描写、不提到这条提示。
 * 不写具体的点心名字——点心名字由另一条可本地化的系统消息告诉主人，女仆的话只表达"想吃点心"的意思。
 */
public final class LinePrompts {
    /** 一句话的字数上限（中文按字，英文按字符，给模型的说法是"不超过 30 个字 / 25 words"，这里再兜一道）。 */
    public static final int MAX_CHARS = 80;

    /** 要说的是哪一类话。 */
    public enum Situation {
        /** 累了，但不会向主人要东西：抱怨一句，继续干。 */
        TIRED_COMPLAIN,
        /** 累了，向主人要点心。 */
        TIRED_ASK,
        /** 累了，自己背包里有点心，吃了。 */
        ATE_OWN,
        /** 主人递来了点心。 */
        THANKS,
        /** 没要到点心，撑不住了，要去睡觉。 */
        GO_REST
    }

    private LinePrompts() {
    }

    /**
     * @param overtimeMinutes 她已经在休息时段里连续加班了大约多少分钟（只用来让她知道自己熬了多久）
     */
    public static String of(Situation situation, boolean zh, int overtimeMinutes) {
        int minutes = Math.max(1, overtimeMinutes);
        return zh ? zhOf(situation, minutes) : enOf(situation, minutes);
    }

    private static String zhOf(Situation situation, int minutes) {
        String tail = "\n只输出这句话本身，不要加引号、动作描写或任何解释，也不要提到这条提示。";
        return switch (situation) {
            case TIRED_COMPLAIN -> "现在是深夜，本该是你睡觉的时间，可玩家一直让你干活，你已经连续熬了大约 " + minutes
                    + " 分钟，又困又累。请按你的人设，对玩家抱怨一句（不超过 30 个字）：可以是打哈欠、发牢骚、撒娇、赌气，"
                    + "也可以是体贴地说自己有点累了，由你的性格决定。你嘴上这么说，手上还是会继续干。" + tail;
            case TIRED_ASK -> "现在是深夜，本该是你睡觉的时间，可玩家一直让你干活，你已经连续熬了大约 " + minutes
                    + " 分钟，又困又累，快撑不住了，想吃点点心提提神。请按你的人设，对玩家说一句话（不超过 30 个字），"
                    + "向他要点心：撒娇、可怜兮兮、嘴硬、理直气壮都可以，由你的性格决定。不要说具体是哪种点心。" + tail;
            case ATE_OWN -> "你又困又累，正好自己的背包里有点心，你吃了一块，精神好了不少，打算再坚持一会儿。"
                    + "请按你的人设，说一句话（不超过 30 个字）：可以是心满意足、偷偷吃了有点心虚，或者给自己打气。" + tail;
            case THANKS -> "你又困又累，玩家刚刚递给你一块点心，你吃了，又有精神了，打算再坚持一会儿。"
                    + "请按你的人设，对玩家说一句话（不超过 30 个字）：道谢，并表示还能再干一阵，语气由你的性格决定。" + tail;
            case GO_REST -> "你已经连续熬了大约 " + minutes + " 分钟，实在撑不住了，而玩家没有给你点心（或者不在你身边），"
                    + "你决定放下手头的活去睡觉了。请按你的人设，说一句话（不超过 30 个字）：可以是道晚安、嘟囔几句、"
                    + "赌气，或者不好意思地告别，由你的性格决定。" + tail;
        };
    }

    private static String enOf(Situation situation, int minutes) {
        String tail = "\nOutput only that line itself, without quotation marks, action descriptions or any explanation, "
                + "and do not mention this note.";
        return switch (situation) {
            case TIRED_COMPLAIN -> "It is the middle of the night, when you should be asleep, but the player keeps having you work, "
                    + "and you have been up for about " + minutes + " minutes, tired and sleepy. Based on your persona, grumble "
                    + "to the player in one line (no more than 25 words): a yawn, a complaint, some sulking or coaxing, or a "
                    + "considerate remark that you are a bit tired; your personality decides. You say it, but you keep working anyway."
                    + tail;
            case TIRED_ASK -> "It is the middle of the night, when you should be asleep, but the player keeps having you work, "
                    + "and you have been up for about " + minutes + " minutes, tired and sleepy and nearly at your limit; "
                    + "a sweet treat would perk you up. Based on your persona, ask the player for a treat in one line "
                    + "(no more than 25 words): coaxing, pitiful, stubborn or shameless are all fine; your personality decides. "
                    + "Do not name a specific treat." + tail;
            case ATE_OWN -> "You were tired and sleepy, and luckily you had a treat in your own backpack. You ate one and feel much "
                    + "better, and you will keep going a little longer. Based on your persona, say one line (no more than 25 words): "
                    + "content, a little guilty about sneaking it, or just cheering yourself on." + tail;
            case THANKS -> "You were tired and sleepy, and the player has just handed you a treat. You ate it and have energy "
                    + "again, and you will keep going a little longer. Based on your persona, say one line to the player "
                    + "(no more than 25 words): thank them and say you can work a while longer; your personality decides the tone."
                    + tail;
            case GO_REST -> "You have been up for about " + minutes + " minutes and cannot go on, and the player gave you no "
                    + "treat (or is not near you), so you decide to drop your work and go to sleep. Based on your persona, say "
                    + "one line (no more than 25 words): a good-night, a mumble, some sulking, or a sheepish goodbye; your "
                    + "personality decides." + tail;
        };
    }
}
