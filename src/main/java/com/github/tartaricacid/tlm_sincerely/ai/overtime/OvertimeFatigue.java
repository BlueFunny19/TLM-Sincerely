package com.github.tartaricacid.tlm_sincerely.ai.overtime;

/**
 * 一只女仆在"本该休息的时段里加班"的疲劳计时（纯逻辑，不依赖 Minecraft 类型，便于单元测试）。
 *
 * <p>规则：
 * <ul>
 *   <li>只在<b>本该休息</b>的时段（她原来的日程此刻是 REST）累积疲劳；工作时间 / 休闲时间的加班不累积；</li>
 *   <li>累积到 {@code 上限 + 额外时长} 就"累了"（{@link Event#BECAME_TIRED}），进入"开口要东西"的阶段
 *       （{@link Stage#ASKING}）；</li>
 *   <li>吃到点心（{@link #treat}）或决定硬撑（{@link #postpone}）会把上限往后推，回到 {@link Stage#FINE}；</li>
 *   <li>开口之后一直没要到（{@link #askTimedOut}）由调用方决定让她去休息；</li>
 *   <li>距离上一次加班过了 {@link #RECOVERY_TICKS} 就算歇够了（{@link #recovered}），疲劳清零——
 *       否则玩家只要隔一会儿再下一次指令就能无限加班。</li>
 * </ul>
 *
 * <p>所有时间都是服务端 tick，由调用方传入，本类不读时钟。
 */
public final class OvertimeFatigue {
    /** 距离上一次加班过去多久（tick）才算歇够：5 分钟。 */
    public static final long RECOVERY_TICKS = 6000L;

    /** 疲劳所处的阶段。 */
    public enum Stage {
        /** 还撑得住。 */
        FINE,
        /** 累了，正在向主人要东西，等着回应。 */
        ASKING
    }

    /** {@link #advance} 这一步发生了什么。 */
    public enum Event {
        NONE,
        /** 刚刚累了（从 FINE 变成 ASKING）。 */
        BECAME_TIRED
    }

    private long tiredTicks;
    private long extraTicks;
    private Stage stage = Stage.FINE;
    private long askingSinceTick;
    private long lastActiveTick = Long.MIN_VALUE;

    /**
     * 过了 {@code dtTicks} 的加班时间。
     *
     * @param nowTick        当前服务端 tick
     * @param dtTicks        距离上次调用过了多少 tick
     * @param originalIsRest 她原来的日程此刻是不是休息时段
     * @param capTicks       不吃点心能撑多久（tick）
     */
    public Event advance(long nowTick, long dtTicks, boolean originalIsRest, long capTicks) {
        lastActiveTick = nowTick;
        if (!originalIsRest || stage == Stage.ASKING) {
            return Event.NONE;
        }
        tiredTicks += Math.max(0L, dtTicks);
        if (tiredTicks >= capTicks + extraTicks) {
            stage = Stage.ASKING;
            askingSinceTick = nowTick;
            return Event.BECAME_TIRED;
        }
        return Event.NONE;
    }

    /** 吃到了点心：能多撑 {@code extraTicks} tick，回到还撑得住。 */
    public void treat(long extraTicks) {
        this.extraTicks += Math.max(0L, extraTicks);
        this.stage = Stage.FINE;
    }

    /** 不给点心、只是硬撑：把下一次"累了"推迟 {@code periodTicks}，回到还撑得住。 */
    public void postpone(long periodTicks) {
        treat(periodTicks);
    }

    /** 不再处于"要点心"的阶段（加班结束了、被玩家接管了、配置被关了）；累积的疲劳照旧。 */
    public void clearAsking() {
        stage = Stage.FINE;
    }

    public Stage stage() {
        return stage;
    }

    public boolean isAsking() {
        return stage == Stage.ASKING;
    }

    /** 开口要东西之后是否已经等了 {@code waitTicks} 还没有回应。 */
    public boolean askTimedOut(long nowTick, long waitTicks) {
        return stage == Stage.ASKING && nowTick - askingSinceTick >= waitTicks;
    }

    /** 距离上一次加班是否已经过了 {@link #RECOVERY_TICKS}（歇够了，该清零了）。 */
    public boolean recovered(long nowTick) {
        return lastActiveTick != Long.MIN_VALUE && nowTick - lastActiveTick >= RECOVERY_TICKS;
    }

    /** 记一下她此刻仍在加班（刚开始或沿用时调用），让"歇够了"的计时从现在算起。 */
    public void touch(long nowTick) {
        lastActiveTick = nowTick;
    }

    public long tiredTicks() {
        return tiredTicks;
    }

    public long extraTicks() {
        return extraTicks;
    }

    /** 疲劳清零。 */
    public void reset() {
        tiredTicks = 0L;
        extraTicks = 0L;
        stage = Stage.FINE;
        askingSinceTick = 0L;
        lastActiveTick = Long.MIN_VALUE;
    }
}
