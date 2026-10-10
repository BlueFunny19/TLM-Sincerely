package com.github.tartaricacid.tlm_sincerely.ai.guard;

import org.jetbrains.annotations.Nullable;

/**
 * 守护任务相关的全部文字（中英两套，按女仆的对话语言选择）：给模型看的工具说明与回执、女仆自己说出口的话。
 * 参数名与取值保持英文机器标识。
 */
public final class GuardText {
    private GuardText() {
    }

    // ===== 工具说明 =====

    public static String summary(boolean zh, int defaultSeconds) {
        int minutes = Math.max(1, defaultSeconds / 60);
        return zh ? """
                守护任务：你有一份"守护名单"，按优先级从高到低排列，每一项是一个要守的人、守护半径，以及一个条件（一直守，或者"对方血量低于多少才守"）。
                你会守名单里"排得最靠前、此刻在场、条件成立"的那一个；这个人血量恢复后条件不再成立，你就自动改守下一个。名单里暂时没人可守时，你就是一只普通的近战女仆。
                什么时候用：玩家要你保护、守护、护送某人，或调整守护的对象、优先级、条件时（主人，或同一主人名下的另一只女仆）。
                什么时候不用：只是走近某人请用 maid_move 的 approach；让你停止守护请用 maid_cancel。
                action：add（默认）把 target 加进名单或修改它的条件 / 半径 / 优先级；remove 把 target 从名单里去掉；clear 清空名单（同时结束守护任务）；list 只查看名单。
                target 填 me（或"我"）表示你的主人，也可以填同一主人名下另一只女仆的名字；不能填你自己。
                condition：always（默认，一直守）、hp_below（对方血量低于 threshold 点才守，2 点 = 1 颗心，玩家满血 20 点）、hp_percent_below（对方血量低于 threshold% 才守）。
                priority：highest 放到最优先、lowest 放到最后、keep（默认）不动位置（新加入的人放在最后）。例如"平时守 B，但 A 血量低于 20 点时优先守 A"：先 add B（always），再 add A（hp_below、threshold 20、priority highest）。
                修改名单会让你自动进入守护任务（原来的任务会被记住，守护结束或取消后回去）。
                守护默认一直持续，没有时限，直到玩家让你取消；只有玩家明确要求"守一会儿""守 N 分钟"时，才把 temporary 设为 true（默认限时 %d 分钟），或用 duration_seconds 给出时长，到时间守护自动结束。修改名单时不会改变已有的时限，除非同时给了 temporary。
                没有要求临时的守护，你必须在回复里明确告诉玩家：你会一直守着，想让你停下只要跟你说一声。
                只要你不是正在睡觉，守护期间你的日程都会被临时改成全天工作，结束后恢复原来的日程。
                如果路走不通，距离不太远时系统会直接把你传送到位；距离太远就会放弃。无论哪种情况，系统都会替你在聊天栏里说一句话，你不用再重复说明。
                """.formatted(minutes).trim() : """
                Guard task: you keep a "guard list", ordered from highest to lowest priority. Each entry is a person to guard, a guard radius and a condition (always guard, or "only while their health is below some value").
                You guard the first entry on the list that is present and whose condition holds; once that person has recovered the condition stops holding and you automatically move on to the next one. When nobody on the list needs guarding right now you are simply an ordinary melee maid.
                Use it when: the player asks you to protect, guard or escort someone (your owner, or another maid of the same owner), or to change who you guard, in what order, or under which condition.
                Do not use it when: you only need to walk up to someone (use maid_move with 'approach'), or to stop guarding (use maid_cancel).
                'action': add (default) puts 'target' on the list or edits its condition / radius / priority; remove takes 'target' off the list; clear empties the list (and ends the guard task); list only shows the list.
                'target' is 'me' for your owner, or the name of another maid of the same owner; never yourself.
                'condition': always (default), hp_below (only while the target's health is below 'threshold' points; 2 points = 1 heart, a player has 20 at full health), hp_percent_below (only while the target's health is below 'threshold' percent).
                'priority': highest puts the entry first, lowest puts it last, keep (default) leaves its position alone (a newly added target goes last). Example "normally guard B, but guard A first while A's health is below 20": add B (always), then add A (hp_below, threshold 20, priority highest).
                Editing the list moves you into the guard task automatically (your previous task is remembered and restored when guarding ends or is cancelled).
                Guarding lasts until the player cancels it, with no time limit, by default. Only when the player explicitly asks for it to be temporary ("guard for a while", "guard for 5 minutes") set 'temporary' to true (it then lasts %d minutes by default) or give the length with 'duration_seconds'; guarding ends by itself when the time is up. Editing the list keeps an existing time limit unless 'temporary' is given too.
                For guarding that was not asked to be temporary you MUST tell the player in your reply that you will keep guarding and that they only need to tell you when to stop.
                Whenever you are not asleep your schedule is switched to "work around the clock" while you guard, and your original schedule is restored afterwards.
                If the path is blocked, you are teleported into place when the distance is not too large; when it is too far the order is dropped. Either way the system says a line in chat on your behalf, so you do not need to explain it again.
                """.formatted(minutes).trim();
    }

    public static String actionDesc(boolean zh) {
        return zh
                ? "对守护名单做什么：add（默认）、remove、clear、list。"
                : "What to do with the guard list: add (default), remove, clear, list.";
    }

    public static String conditionDesc(boolean zh) {
        return zh
                ? "什么条件下才守这个人：always（默认，一直守）、hp_below（血量低于 threshold 点）、hp_percent_below（血量低于 threshold%）。缺省时保留已有条件，新加入的人默认 always。"
                : "When to guard this person: always (default), hp_below (health below 'threshold' points), hp_percent_below (health below 'threshold' percent). If omitted an existing entry keeps its condition and a new entry defaults to always.";
    }

    public static String thresholdDesc(boolean zh) {
        return zh
                ? "condition 为 hp_below 时是血量点数（2 点 = 1 颗心），为 hp_percent_below 时是百分比（1~99）。"
                : "For hp_below the health in points (2 points = 1 heart); for hp_percent_below the percentage (1-99).";
    }

    public static String priorityDesc(boolean zh) {
        return zh
                ? "这个人在名单里的位置：highest（最优先）、lowest（最后）、keep（默认，不动位置，新加入的放最后）。"
                : "Where this person goes on the list: highest, lowest, or keep (default; leaves the position alone, a new entry goes last).";
    }

    public static String distanceDesc(boolean zh) {
        return zh
                ? "守护半径（格，1~64），默认 5：待在这个人身边这么远以内。"
                : "Guard radius in blocks (1-64), default 5: how close to this person you stay.";
    }

    // ===== 条件与名单的描述 =====

    public static String conditionLabel(boolean zh, GuardCondition condition) {
        return switch (condition.kind()) {
            case ALWAYS -> zh ? "一直守" : "always";
            case HP_BELOW -> zh
                    ? "对方血量低于 %s 点才守".formatted(number(condition.threshold()))
                    : "only while their health is below %s points".formatted(number(condition.threshold()));
            case HP_PERCENT_BELOW -> zh
                    ? "对方血量低于 %s%% 才守".formatted(number(condition.threshold()))
                    : "only while their health is below %s%%".formatted(number(condition.threshold()));
        };
    }

    public static String entryLine(boolean zh, GuardEntry entry) {
        return zh
                ? "%s（%s，守护半径 %s 格）".formatted(entry.name(), conditionLabel(true, entry.condition()), number(entry.distance()))
                : "%s (%s, radius %s)".formatted(entry.name(), conditionLabel(false, entry.condition()), number(entry.distance()));
    }

    /** 名单的多行描述（按优先级编号）；空名单返回 {@link #emptyList}。 */
    public static String rulesBlock(boolean zh, GuardRules rules) {
        if (rules.isEmpty()) {
            return emptyList(zh);
        }
        StringBuilder out = new StringBuilder(zh ? "守护名单（越靠前越优先）：" : "Guard list (earlier = higher priority):");
        int index = 1;
        for (GuardEntry entry : rules.entries()) {
            out.append('\n').append(index++).append(". ").append(entryLine(zh, entry));
        }
        return out.toString();
    }

    public static String emptyList(boolean zh) {
        return zh ? "守护名单是空的。" : "The guard list is empty.";
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    // ===== 工具回执 =====

    public static String added(boolean zh, String maidName, GuardEntry entry, boolean existed, GuardRules rules) {
        String head = zh
                ? (existed ? "%s 已更新守护名单里的 %s。" : "%s 已把 %s 加进守护名单。").formatted(maidName, entry.name())
                : (existed ? "%s updated %s on the guard list." : "%s added %s to the guard list.").formatted(maidName, entry.name());
        return head + "\n" + rulesBlock(zh, rules);
    }

    public static String removed(boolean zh, String maidName, String targetName, GuardRules rules) {
        String head = zh
                ? "%s 已把 %s 从守护名单里去掉。".formatted(maidName, targetName)
                : "%s removed %s from the guard list.".formatted(maidName, targetName);
        return head + "\n" + rulesBlock(zh, rules);
    }

    public static String removeNotFound(boolean zh, String target, GuardRules rules) {
        String head = zh
                ? "守护名单里没有「%s」，没有做任何修改。".formatted(target)
                : "'%s' is not on the guard list; nothing was changed.".formatted(target);
        return head + "\n" + rulesBlock(zh, rules);
    }

    public static String cleared(boolean zh, String maidName, boolean leftTask) {
        return zh
                ? "%s 的守护名单已清空%s。".formatted(maidName, leftTask ? "，她结束了守护任务" : "")
                : "%s's guard list has been cleared%s.".formatted(maidName, leftTask ? " and she left the guard task" : "");
    }

    public static String listing(boolean zh, GuardRules rules, boolean guardingTask, @Nullable String currentTarget) {
        StringBuilder out = new StringBuilder(rulesBlock(zh, rules));
        out.append('\n').append(statusLine(zh, guardingTask, currentTarget));
        return out.toString();
    }

    public static String statusLine(boolean zh, boolean guardingTask, @Nullable String currentTarget) {
        if (!guardingTask) {
            return zh ? "她现在不在守护任务里。" : "She is not in the guard task right now.";
        }
        if (currentTarget == null) {
            return zh ? "名单里此刻没有需要守的人（不在场或条件不成立），她先作为普通近战女仆待命。"
                    : "Nobody on the list needs guarding right now (absent or condition not met); she waits as an ordinary melee maid.";
        }
        return zh ? "她现在正守着 %s。".formatted(currentTarget) : "She is guarding %s right now.".formatted(currentTarget);
    }

    public static String thresholdRequired(boolean zh) {
        return zh ? "condition 为 hp_below / hp_percent_below 时必须给出 threshold（血量点数或百分比）。没有做任何修改。"
                : "'threshold' (health points or percent) is required for hp_below / hp_percent_below. Nothing was changed.";
    }

    public static String unknownCondition(boolean zh, String value) {
        return zh ? "未知的 condition「%s」，可选：always、hp_below、hp_percent_below。没有做任何修改。".formatted(value)
                : "Unknown condition '%s'; use always, hp_below or hp_percent_below. Nothing was changed.".formatted(value);
    }

    public static String unknownAction(boolean zh, String value) {
        return zh ? "未知的 action「%s」，可选：add、remove、clear、list。没有做任何修改。".formatted(value)
                : "Unknown action '%s'; use add, remove, clear or list. Nothing was changed.".formatted(value);
    }

    public static String full(boolean zh, int max) {
        return zh ? "守护名单最多 %d 项，已经满了；请先去掉一项。".formatted(max)
                : "The guard list holds at most %d entries and is full; remove one first.".formatted(max);
    }

    public static String permanentNote(boolean zh) {
        return zh ? "守护会一直持续，没有时限；想让她停下，只要说一声（maid_cancel）。"
                : "Guarding continues with no time limit; to stop it, just tell her (maid_cancel).";
    }

    public static String temporaryNote(boolean zh, int seconds) {
        return zh ? "守护限时 %d 秒，时间到了会自动结束并回到原来的任务。".formatted(seconds)
                : "Guarding is limited to %d seconds and then ends by itself, returning to her previous task.".formatted(seconds);
    }

    public static String cancelledNote(boolean zh, String taskName) {
        return zh ? "她也离开了守护任务，回到了「%s」。".formatted(taskName)
                : "She also left the guard task and went back to '%s'.".formatted(taskName);
    }

    // ===== 女仆自己说出口的话 =====

    public static String expired(boolean zh) {
        return zh ? "守护的时间到啦，我先回去做原来的事了。" : "My guard time is up; I'm going back to what I was doing.";
    }

    // ===== 给模型的上下文 =====

    public static String contextLabel() {
        return "守护任务 / Guard task";
    }

    public static String contextNotGuarding(boolean zh) {
        return zh ? "当前没有在执行守护任务。玩家要你守护某人时，调用 maid_guard 工具。"
                : "Not in the guard task. When the player asks you to guard someone, call the maid_guard tool.";
    }

    public static String contextGuarding(boolean zh, GuardRules rules, @Nullable String currentTarget,
                                         boolean temporary, int remainingSeconds) {
        StringBuilder out = new StringBuilder(zh
                ? "你正在执行守护任务。玩家要调整守护对象 / 优先级 / 条件时调用 maid_guard，要你停下时调用 maid_cancel。\n"
                : "You are in the guard task. To change who / in what order / under which condition you guard call maid_guard; to stop call maid_cancel.\n");
        out.append(rulesBlock(zh, rules)).append('\n').append(statusLine(zh, true, currentTarget));
        if (temporary) {
            out.append('\n').append(zh ? "守护还剩约 %d 秒。".formatted(remainingSeconds)
                    : "About %d seconds of guarding remain.".formatted(remainingSeconds));
        } else {
            out.append('\n').append(zh ? "守护没有时限，会一直持续。" : "Guarding has no time limit.");
        }
        return out.toString();
    }
}
