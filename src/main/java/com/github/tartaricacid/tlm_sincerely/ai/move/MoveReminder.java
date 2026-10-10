package com.github.tartaricacid.tlm_sincerely.ai.move;

/**
 * 永久指令的"定期提醒"计时（纯逻辑，tick 由调用方给出，便于单元测试）。
 *
 * <p>规则：永久指令每隔 {@code MovePermanentReminderMinutes} 分钟让女仆自己说一次
 * {@code MoveText.saidReminder}；间隔为 0（或负数）表示关闭提醒。计时基准是指令登记时的那一刻
 * （{@link #arm}），因此跨服务器重启、重新登记的永久指令的提醒计时会从重启后重新开始。
 *
 * <p>一帧的处理周期是 {@link MoveDirectiveManager#TICK_INTERVAL}（10 tick），所以提醒的实际触发
 * 时刻会比标称间隔晚最多一个处理周期；这是有意为之（不必为此提高处理频率）。
 */
public final class MoveReminder {
    /** 一分钟对应的 tick 数。 */
    public static final int TICKS_PER_MINUTE = 1200;

    private long nextReminderTick;
    private boolean armed;

    /** 把间隔（分钟）换算成 tick；0 或负数返回 0，表示不提醒。 */
    public static int intervalTicks(int minutes) {
        return minutes <= 0 ? 0 : minutes * TICKS_PER_MINUTE;
    }

    /** 以当前 tick 为基准开始计时（指令登记 / 跨重启重新登记时调用）。 */
    public void arm(long nowTick, int intervalTicks) {
        armed = true;
        nextReminderTick = nowTick + Math.max(0, intervalTicks);
    }

    /** 停止计时（指令结束、被取消、被替换时调用）。 */
    public void clear() {
        armed = false;
        nextReminderTick = 0L;
    }

    /**
     * 本周期是否该提醒；是则顺手把下一次提醒时间推到 {@code nowTick + intervalTicks}。
     * 未计时、间隔为 0 或还没到时间都返回 false。
     */
    public boolean due(long nowTick, int intervalTicks) {
        if (!armed || intervalTicks <= 0 || nowTick < nextReminderTick) {
            return false;
        }
        nextReminderTick = nowTick + intervalTicks;
        return true;
    }
}
