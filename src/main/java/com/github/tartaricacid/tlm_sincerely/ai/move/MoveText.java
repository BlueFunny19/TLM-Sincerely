package com.github.tartaricacid.tlm_sincerely.ai.move;

import java.util.List;

/**
 * 移动指令工具（maid_move）里所有"给 LLM 看的文字"，中英各一套，由调用方按
 * {@code PromptLang.isChinese(PromptLang.of(maid))} 选择 {@code zh}。
 * <p>
 * 提示词集中在这里统一维护；工具逻辑只通过这些方法取文字，不要在别处另拼提示。
 * 动作名（approach / retreat / goto / guard / cancel）与参数名是机器标识，保持英文。
 */
public final class MoveText {
    private MoveText() {
    }

    // ===== 工具说明与参数说明 =====

    public static String summary(boolean zh) {
        return zh ? """
                指挥你自己（这只女仆）走近、远离、前往某处、守护某人、留在原地或回家。
                什么时候用：玩家要你靠近或远离某人、去某个地方、保护 / 守护某人、在原地待着不动、或者回家（回到你的工作坐标）时。
                什么时候不用：只是想让你一直跟着玩家，请用「切换跟随状态」工具；想让你去干活，请用「切换工作任务」工具；坐下或站起来请用「坐下」工具。
                action 取值：
                - approach：走近目标，保持在 distance 格以内。
                - retreat：远离目标，保持在 distance 格以外。
                - goto：前往坐标 x、y、z，到了以后在那里等着。
                - guard：守护目标，待在目标身边 distance 格以内，并攻击威胁目标的敌对生物。
                - stay：留在当前位置不动，也不再跟随主人（你原有的工作坐标会被暂时清掉，结束后恢复原样）。
                - go_home：立刻传送回你自己的工作坐标，并进入居家模式。这是永久性的改变，没有时限，也不能用 cancel 取消；想让你重新跟着主人，要用「切换跟随状态」工具。你没有设置工作坐标时无法使用。
                - work_time：不移动，只是把你的日程临时改成"全天工作"，这样夜里也能工作；之后再用「切换工作任务」工具让你去干活。
                - cancel：取消你当前的移动指令（stay 会恢复成原来的样子，被改过的日程也会恢复）。
                除 go_home 和 cancel 外，只要你不是正在睡觉，任何指令生效期间你的日程都会被临时改成全天工作，指令结束后恢复原来的日程。
                target 填 me（或"我"）表示你的主人，也可以填同一主人名下另一只女仆的名字；不能填你自己。
                指令默认持续 10 分钟，到了 duration_seconds 就自动结束；新指令会覆盖旧指令。
                如果玩家的意思是"一直这样""持续保持"（例如"一直守着澪""在这儿一直等我"），把 permanent 设为 true，指令就不会超时，直到玩家让你取消；这时你必须在回复里明确告诉玩家：你会一直这样做，想让你停下只要跟你说一声。
                如果路走不通，距离不太远时系统会直接把你传送到位；距离太远就会放弃指令。无论哪种情况，系统都会替你在聊天栏里说一句话告诉玩家，你不用再重复说明。
                守护时只有在你当前的任务是战斗类任务时才会主动攻击，否则只会守在目标身边。
                """.trim() : """
                Command yourself (this maid) to approach, retreat from, go to a place, guard someone, stay where you are, or go home.
                Use it when: the player asks you to come closer to / move away from someone, go somewhere, protect / guard someone, wait where you are, or go home (back to your work position).
                Do not use it when: the player only wants you to keep following them (use the follow-state tool), wants you to work (use the work-task tool), or wants you to sit or stand (use the sit tool).
                Values of 'action':
                - approach: move toward the target and stay within 'distance' blocks.
                - retreat: move away from the target and stay beyond 'distance' blocks.
                - goto: go to the coordinates x, y, z and wait there.
                - guard: stay within 'distance' blocks of the target and attack hostile mobs that threaten it.
                - stay: stay exactly where you are and stop following your owner (your work position is cleared temporarily and restored afterwards).
                - go_home: teleport back to your own work position right away and enter home mode. This is a permanent change with no time limit and cannot be undone with cancel; to make you follow your owner again the follow-state tool must be used. It does not work when you have no work position.
                - work_time: do not move, only switch your schedule to "work around the clock" temporarily so that you can work at night as well; afterwards use the work-task tool to start working.
                - cancel: cancel your current movement order (stay restores your previous state, and a changed schedule is restored too).
                Except for go_home and cancel, whenever you are not asleep your schedule is switched to "work around the clock" while any order is active, and your original schedule is restored when the order ends.
                'target' is 'me' for your owner, or the name of another maid of the same owner; never yourself.
                An order lasts 10 minutes by default and ends after 'duration_seconds'; a new order replaces the old one.
                If the player means "keep doing this" (for example "keep guarding Mio", "wait here for me"), set 'permanent' to true: the order never times out until the player tells you to cancel it. In that case you MUST tell the player in your reply that you will keep doing it and that they only need to tell you when to stop.
                If the path is blocked, you are teleported into place when the distance is not too large; when it is too far the order is dropped. Either way the system says a line in chat on your behalf, so you do not need to explain it again.
                While guarding you only attack actively if your current task is a combat task; otherwise you just stay beside the target.
                """.trim();
    }

    public static String actionDesc(boolean zh) {
        return zh ? "要执行的移动指令" : "The movement order to perform";
    }

    public static String targetDesc(boolean zh) {
        return zh
                ? "目标：'me'（或「我」）表示你的主人；也可以是同一主人名下另一只女仆的名字（先精确匹配，否则按子串匹配，多命中时取最近的）。approach / retreat / guard 用，缺省为 me。"
                : "Target: 'me' for your owner, or the name of another maid of the same owner (exact match first, otherwise substring, nearest wins). Used by approach / retreat / guard; defaults to 'me'.";
    }

    public static String coordDesc(boolean zh, String axis) {
        return zh ? "goto 的目标坐标 " + axis + "（方块坐标，可以是小数）。"
                : "Target coordinate " + axis + " for goto (block coordinates, decimals allowed).";
    }

    public static String distanceDesc(boolean zh) {
        return zh
                ? "距离（格，1~64）。approach 默认 3；retreat 默认 16；guard 默认 5（守护半径）。"
                : "Distance in blocks (1-64). Defaults: approach 3, retreat 16, guard 5 (guard radius).";
    }

    public static String durationDesc(boolean zh, int maxSeconds) {
        return zh
                ? "指令持续的秒数，默认 600（10 分钟），最多 " + maxSeconds + "。到时间自动结束。想让指令一直持续请改用 permanent。"
                : "How many seconds the order lasts. Default 600 (10 minutes), at most " + maxSeconds
                + ". It ends automatically. To make it last indefinitely use 'permanent' instead.";
    }

    public static String permanentDesc(boolean zh) {
        return zh
                ? "是否永久：true 表示指令没有时限，直到玩家让你取消。只有玩家明确表示要持续保持时才设为 true（例如\"一直跟着\"\"一直在这儿等我\"）；设为 true 时不要再传 duration_seconds。"
                : "Whether the order is permanent: true means no time limit until the player tells you to cancel. Only set it when the player clearly wants it kept up (for example \"keep guarding\", \"wait here for me\"); do not pass duration_seconds together with it.";
    }

    // ===== 工具返回给 LLM 的结果 =====

    /** 指令成功生效。{@code targetName} 对 goto 传坐标描述（如 "(100, 64, -20)"），对 cancel 不使用。 */
    public static String started(boolean zh, String action, String maidName, String targetName, int seconds) {
        String verbZh = switch (action) {
            case "approach" -> "走近" + targetName;
            case "retreat" -> "远离" + targetName;
            case "goto" -> "前往" + targetName;
            case "guard" -> "守护" + targetName;
            default -> action;
        };
        String verbEn = switch (action) {
            case "approach" -> "approach " + targetName;
            case "retreat" -> "move away from " + targetName;
            case "goto" -> "go to " + targetName;
            case "guard" -> "guard " + targetName;
            default -> action;
        };
        return zh ? "已让" + maidName + "开始" + verbZh + "，将持续约 " + seconds + " 秒。"
                : maidName + " will now " + verbEn + " for about " + seconds + " seconds.";
    }

    public static String cancelled(boolean zh, String maidName, boolean hadOrder) {
        if (hadOrder) {
            return zh ? "已取消" + maidName + "当前的移动指令。" : "Cancelled " + maidName + "'s current movement order.";
        }
        return zh ? maidName + "目前没有移动指令，不需要取消。" : maidName + " has no movement order, nothing to cancel.";
    }

    public static String disabled(boolean zh) {
        return zh ? "本服务器已禁用移动指令工具；什么都没有发生。" : "The movement tool is disabled on this server; nothing happened.";
    }

    public static String serverSideOnly(boolean zh) {
        return zh ? "移动指令只能在服务端执行。" : "Movement orders can only be given on the server side.";
    }

    public static String unknownAction(boolean zh, String action) {
        return (zh ? "未知的 action：" : "Unknown action: ") + action;
    }

    public static String targetNotFound(boolean zh, String targetName, List<String> available) {
        String list = available.isEmpty() ? (zh ? "（无）" : "(none)") : String.join(zh ? "、" : ", ", available);
        return zh ? "找不到目标 '" + targetName + "'。可以填 me，或同一主人名下其他女仆的名字，目前可选：" + list
                : "Target '" + targetName + "' not found. Use 'me' or the name of another maid of the same owner. Available: " + list;
    }

    public static String ownerUnavailable(boolean zh) {
        return zh ? "你的主人不在线，或者和你不在同一个维度，没法以主人为目标。"
                : "Your owner is offline or in a different dimension, so the owner cannot be the target.";
    }

    public static String targetOtherDimension(boolean zh, String targetName) {
        return zh ? targetName + " 和你不在同一个维度，没法以她为目标。"
                : targetName + " is in a different dimension and cannot be the target.";
    }

    public static String selfTarget(boolean zh) {
        return zh ? "目标不能是你自己。" : "The target cannot be yourself.";
    }

    public static String missingCoords(boolean zh) {
        return zh ? "goto 必须同时提供 x、y、z。" : "goto requires x, y and z.";
    }

    public static String cannotMoveNow(boolean zh, String maidName, String stateZh, String stateEn) {
        return zh ? maidName + "现在正" + stateZh + "，没法移动。请先让她站起来 / 醒来，再下达移动指令。"
                : maidName + " is currently " + stateEn + " and cannot move. Ask her to get up first, then give the order again.";
    }

    public static String limitReached(boolean zh, int max) {
        return zh ? "主人同时生效的移动指令已达上限（" + max + " 条）；请先取消一些再试。"
                : "Your owner already has the maximum number of active movement orders (" + max + "); cancel some first.";
    }

    public static String homeModeNote(boolean zh) {
        return zh ? "（注意：她处于 Home 模式，活动范围受限，可能走不到目标。）"
                : "(Note: she is in Home mode and her range is limited, so she may not be able to reach the target.)";
    }

    public static String guardNoCombatNote(boolean zh) {
        return zh ? "（注意：她当前的任务不是战斗类任务，所以只会守在目标身边，不会主动攻击；要战斗需要先切换到战斗类任务。）"
                : "(Note: her current task is not a combat task, so she will only stay beside the target and will not attack; switch to a combat task first if fighting is needed.)";
    }

    // ===== 女仆自己说出口的话（寻路失败的兜底提示，直接显示在聊天栏里，不经过 LLM） =====

    /** 走不过去、已经直接传送到位后，女仆说的一句话。 */
    public static String saidTeleported(boolean zh, String action) {
        return switch (action) {
            case "goto" -> zh ? "那边过不去，我直接传送到那儿了。" : "I couldn't walk there, so I teleported straight to that spot.";
            case "retreat" -> zh ? "这边不好走，我直接换到远一点的地方了。" : "The way is rough here, so I just moved straight to a spot farther away.";
            default -> zh ? "路被挡住了，我直接过来了。" : "The way was blocked, so I came straight over.";
        };
    }

    /** 离目标太远（超过传送兜底的最大距离），放弃指令时女仆说的一句话。 */
    public static String saidTooFar(boolean zh, int maxBlocks) {
        return zh ? "离得太远了（超过 " + maxBlocks + " 格），我走不过去，先不去了。"
                : "It is too far (over " + maxBlocks + " blocks) for me to get there, so I'll stay put.";
    }

    /** 找不到可以站的位置，放弃指令时女仆说的一句话。 */
    public static String saidNoSpot(boolean zh) {
        return zh ? "那边找不到能站的地方，我过不去。" : "I can't find anywhere to stand over there, so I can't get there.";
    }

    /** 传送兜底次数用完仍然到不了，放弃指令时女仆说的一句话。 */
    public static String saidGaveUp(boolean zh) {
        return zh ? "怎么都过不去，我放弃了。" : "I just can't get there, so I give up.";
    }

    // ===== 永久指令、留在原地、回家 =====

    /** 永久指令生效后追加在成功文字后面，提醒 LLM 必须告诉玩家。 */
    public static String permanentNote(boolean zh) {
        return zh ? "（这是一条没有时限的指令：请在回复里明确告诉玩家，你会一直这样做，想让你停下只要跟你说一声。）"
                : "(This order has no time limit: you MUST tell the player in your reply that you will keep doing it and that they only need to tell you when to stop.)";
    }

    public static String permanentNotAllowed(boolean zh) {
        return zh ? "本服务器不允许永久指令；请改用限时指令（设置 duration_seconds）。"
                : "Permanent orders are not allowed on this server; use a timed order (set duration_seconds) instead.";
    }

    /** 永久指令成功生效。{@code detail} 是动作描述（如"守护澪"）。 */
    public static String startedPermanent(boolean zh, String maidName, String detail) {
        return zh ? "已让" + maidName + "开始" + detail + "，没有时限，会一直这样做，直到玩家让她取消。"
                : maidName + " will now " + detail + " with no time limit, until the player tells her to cancel.";
    }

    /** stay 成功生效。 */
    public static String stayStarted(boolean zh, String maidName, boolean clearedWorkPos, boolean permanent, int seconds) {
        String duration = permanent ? (zh ? "没有时限，直到玩家让她取消" : "with no time limit, until the player tells her to cancel")
                : (zh ? "约 " + seconds + " 秒" : "for about " + seconds + " seconds");
        if (zh) {
            return "已让" + maidName + "留在原地不动，也不再跟随主人，持续" + duration + "。"
                    + (clearedWorkPos ? "她原来的工作坐标已暂时清除，结束后会恢复。" : "");
        }
        return maidName + " will now stay where she is and stop following her owner, " + duration + "."
                + (clearedWorkPos ? " Her work position was cleared temporarily and will be restored afterwards." : "");
    }

    /** 取消 / 到期时恢复了原状态（stay 以及需要暂停跟随的指令）。 */
    public static String restoredNote(boolean zh) {
        return zh ? "已恢复她原来的状态（居家模式与工作坐标）。" : "Her previous state (home mode and work position) has been restored.";
    }

    /** go_home 成功。 */
    public static String wentHome(boolean zh, String maidName, int x, int y, int z) {
        return zh ? "已让" + maidName + "传送回家（工作坐标 " + x + ", " + y + ", " + z + "）并开启居家模式。这是永久性的改变，她会一直留在那里，除非玩家用跟随等方式改变。"
                : maidName + " teleported home (work position " + x + ", " + y + ", " + z + ") and home mode is on. This is permanent; she stays there unless the player changes it, for example by making her follow.";
    }

    /** go_home 时没有工作坐标。 */
    public static String noWorkPos(boolean zh, String maidName) {
        return zh ? maidName + "没有设置工作坐标，没法回家。可以改用 stay 让她留在原地，或者请玩家先给她设置工作坐标。"
                : maidName + " has no work position, so she cannot go home. Use 'stay' to make her stay where she is, or ask the player to set a work position first.";
    }

    /** go_home 找不到可站的位置。 */
    public static String homeNoSpot(boolean zh, String maidName) {
        return zh ? "工作坐标附近找不到能站的地方，" + maidName + "回不去。" : "No standable spot near the work position, so " + maidName + " cannot get home.";
    }

    /** 回家后女仆说的一句话。 */
    public static String saidWentHome(boolean zh) {
        return zh ? "我回家啦，会一直待在这儿的。" : "I'm home now, and I'll stay here.";
    }

    /** 时限指令自然到期时女仆说的一句话。 */
    public static String saidExpired(boolean zh, String detail) {
        return zh ? "（" + detail + "的时间到了，我恢复自由行动啦。）" : "(Time's up for \"" + detail + "\"; I'm free to move again.)";
    }

    /** 永久指令的定期提醒，女仆自己说的一句话。 */
    public static String saidReminder(boolean zh, String detail) {
        return zh ? "（我还在" + detail + "呢，想让我停下，跟我说一声就行。）"
                : "(I'm still " + detail + "; just tell me when you want me to stop.)";
    }

    /** 动作描述：用在提醒、到期提示和上下文里。{@code target} 对 goto 传坐标描述，stay 不使用。 */
    public static String describe(boolean zh, String action, String target) {
        return switch (action) {
            case "approach" -> zh ? "走近" + target : "approaching " + target;
            case "retreat" -> zh ? "远离" + target : "keeping away from " + target;
            case "goto" -> zh ? "在" + target + "等着" : "waiting at " + target;
            case "guard" -> zh ? "守护" + target : "guarding " + target;
            case "stay" -> zh ? "留在原地" : "staying where I am";
            case "work_time" -> zh ? "全天候工作" : "working around the clock";
            default -> action;
        };
    }

    // ===== 给 LLM 看的当前指令上下文 =====

    /** 上下文栏目标题（构造时固定，中英并列）。 */
    public static String contextLabel() {
        return "当前的移动指令 (Current movement order)";
    }

    public static String contextNone(boolean zh) {
        return zh ? "没有" : "None";
    }

    public static String contextActive(boolean zh, String detail, boolean permanent, int remainingSeconds) {
        if (zh) {
            return "你正在" + detail + (permanent ? "，没有时限（永久），直到玩家让你取消。"
                    : "，还剩约 " + remainingSeconds + " 秒。")
                    + "玩家问起你在做什么、或者让你取消时，据此回答，需要取消时调用 maid_move 的 cancel。";
        }
        return "You are " + detail + (permanent ? " with no time limit until the player tells you to cancel."
                : ", about " + remainingSeconds + " seconds left.")
                + " Answer from this when the player asks what you are doing, and call maid_move with 'cancel' when asked to stop.";
    }

    // ===== 追加（本次任务新增的场景；上面的文字均未改动） =====

    /** {@code describe} / 提醒 / 上下文里"主人"这个 target 的说法。 */
    public static String ownerName(boolean zh) {
        return zh ? "主人" : "the owner";
    }

    /** go_home 不接受 duration_seconds / permanent 参数，模型却传了时的提示。 */
    public static String goHomeIgnoredArgs(boolean zh) {
        return zh ? "go_home 不接受 duration_seconds / permanent 参数：回家本来就是永久性改变。请只在明确要「留在某处 / 走近某人」时才传这两个参数。"
                : "go_home does not take duration_seconds / permanent: going home is already a permanent change. Only pass those for 'stay somewhere' or 'approach someone' orders.";
    }

    /** 指令被丢弃时目标女仆已经不在了（例如她死了），给模型的一句说明。 */
    public static String maidGone(boolean zh, String maidName) {
        return zh ? maidName + "已经不在了，这条移动指令没有生效，也没有改变她的状态。"
                : maidName + " is no longer there, so the movement order did not take effect and none of her state was changed.";
    }

    /** 指令登记时女仆突然找不到了（理论上不可达的防御分支）。 */
    public static String maidUnavailable(boolean zh, String maidName) {
        return zh ? "现在联系不上" + maidName + "，指令没有生效；请稍后再试。"
                : maidName + " cannot be reached right now, so the order did not take effect; please try again later.";
    }

    // ===== 日程放宽（临时改成全天工作） =====

    /** 指令生效、同时临时放宽了日程时，追加在成功文字后面。 */
    public static String scheduleForcedNote(boolean zh) {
        return zh ? "（她的日程已被临时改成全天工作，指令结束后会恢复原样。）"
                : "(Her schedule was temporarily switched to work around the clock and will be restored when the order ends.)";
    }

    /** 指令生效但她正在睡觉，所以没有改日程。 */
    public static String scheduleSleepingNote(boolean zh) {
        return zh ? "（她正在睡觉，所以没有改动她的日程。）" : "(She is asleep, so her schedule was left unchanged.)";
    }

    /** work_time 成功。 */
    public static String workTimeStarted(boolean zh, String maidName, boolean permanent, int seconds) {
        String duration = permanent ? (zh ? "没有时限，直到玩家让她取消" : "with no time limit, until the player tells her to cancel")
                : (zh ? "约 " + seconds + " 秒" : "for about " + seconds + " seconds");
        return zh ? "已临时把" + maidName + "的日程改成全天工作，持续" + duration + "。现在可以让她切换工作任务了。"
                : maidName + "'s schedule is now temporarily set to work around the clock, " + duration + ". You can switch her work task now.";
    }

    /** work_time 但她正在睡觉。 */
    public static String workTimeSleeping(boolean zh, String maidName) {
        return zh ? maidName + "正在睡觉，没有改动她的日程，现在不适合让她工作。"
                : maidName + " is asleep, so her schedule was left unchanged and it is not a good time to put her to work.";
    }

    /** work_time 但日程本来就是全天工作。 */
    public static String workTimeAlready(boolean zh, String maidName) {
        return zh ? maidName + "的日程本来就是全天工作，不需要改动。"
                : maidName + "'s schedule is already work-around-the-clock, nothing to change.";
    }
}
