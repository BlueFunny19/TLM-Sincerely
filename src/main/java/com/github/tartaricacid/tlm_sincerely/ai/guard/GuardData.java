package com.github.tartaricacid.tlm_sincerely.ai.guard;

/**
 * 守护任务存在女仆身上的全部状态（不可变，纯逻辑）。
 *
 * @param rules             守护名单
 * @param expireAtGameTick  守护任务到期的游戏刻；0 表示一直守下去（默认）
 * @param revertTaskUid     进入守护任务之前她做的任务（UID 字符串）；任务结束 / 取消时回到它，空串表示没有记录
 */
public record GuardData(GuardRules rules, long expireAtGameTick, String revertTaskUid) {
    public static final GuardData EMPTY = new GuardData(GuardRules.EMPTY, 0L, "");

    public GuardData {
        rules = rules == null ? GuardRules.EMPTY : rules;
        expireAtGameTick = Math.max(0L, expireAtGameTick);
        revertTaskUid = revertTaskUid == null ? "" : revertTaskUid;
    }

    public boolean isTemporary() {
        return expireAtGameTick > 0L;
    }

    public boolean isExpired(long nowGameTick) {
        return expireAtGameTick > 0L && nowGameTick >= expireAtGameTick;
    }

    public GuardData withRules(GuardRules newRules) {
        return new GuardData(newRules, expireAtGameTick, revertTaskUid);
    }

    public GuardData withExpireAt(long newExpireAtGameTick) {
        return new GuardData(rules, newExpireAtGameTick, revertTaskUid);
    }

    public GuardData withRevertTask(String newRevertTaskUid) {
        return new GuardData(rules, expireAtGameTick, newRevertTaskUid);
    }
}
