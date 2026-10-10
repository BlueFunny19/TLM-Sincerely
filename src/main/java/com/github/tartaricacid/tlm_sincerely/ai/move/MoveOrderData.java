package com.github.tartaricacid.tlm_sincerely.ai.move;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 一条移动指令的完整数据（不依赖任何 Minecraft 类型，便于单元测试）。
 *
 * <p>{@link MoveOrders} 用它做内存指令表；持久化由
 * {@link MoveOrderNbt} 适配到女仆实体的 {@code getPersistentData()} 子复合标签，
 * 这样"跨服务器重启恢复"与"内存管理"共用同一套字段，不会两边写歪。
 *
 * <p>时间口径：{@link #expireAtGameTick} 与 {@link #createdGameTick} 都用
 * {@code ServerLevel#getGameTime()}（存档持久化的世界时间）。服务端 tick 计数
 * （{@code MinecraftServer#getTickCount()}）与它同源且每 tick 同步，重启后不会回到 0，
 * 因此 {@link #remainingTicks(long)} 直接用 gameTime 差值即可，重启后依然有效。
 *
 * @param maidId          执行指令的女仆
 * @param ownerId         女仆的主人（用于"每个主人同时生效指令数"上限）
 * @param action          动作
 * @param targetId        目标实体 UUID；{@code null} 表示坐标指令（goto）或不需要目标
 * @param x               目标坐标 / goto 坐标
 * @param y               目标坐标 / goto 坐标
 * @param z               目标坐标 / goto 坐标
 * @param distance        距离参数（格），非距离类动作不使用
 * @param expireAtGameTick 到期 gameTime（含）；{@link #PERMANENT}（-1）表示永久，不会到期
 * @param createdGameTick 写入指令时的 gameTime（永久指令的提醒计时基准）
 * @param followSuspension 暂停跟随期间保存的"原状态"；{@code null} 表示本条指令不需要暂停跟随
 * @param originalSchedule 临时放宽日程期间保存的原日程（{@code MaidSchedule#name()}）；
 *                         {@code null} 表示本条指令没有放宽过日程（含读取旧记录的情形）
 */
public record MoveOrderData(UUID maidId, UUID ownerId, MoveAction action, @Nullable UUID targetId,
                            double x, double y, double z, double distance,
                            long expireAtGameTick, long createdGameTick,
                            @Nullable FollowSuspension followSuspension,
                            @Nullable String originalSchedule) {
    /** {@link #expireAtGameTick} 的"永久"取值。 */
    public static final long PERMANENT = -1L;

    /** 时限指令。 */
    public static MoveOrderData timed(UUID maidId, UUID ownerId, MoveAction action, @Nullable UUID targetId,
                                      double x, double y, double z, double distance,
                                      long expireAtGameTick, long createdGameTick,
                                      @Nullable FollowSuspension followSuspension) {
        return timed(maidId, ownerId, action, targetId, x, y, z, distance,
                expireAtGameTick, createdGameTick, followSuspension, null);
    }

    /** 时限指令（带"临时放宽日程"保存的原日程）。 */
    public static MoveOrderData timed(UUID maidId, UUID ownerId, MoveAction action, @Nullable UUID targetId,
                                      double x, double y, double z, double distance,
                                      long expireAtGameTick, long createdGameTick,
                                      @Nullable FollowSuspension followSuspension,
                                      @Nullable String originalSchedule) {
        return new MoveOrderData(maidId, ownerId, action, targetId, x, y, z, distance,
                expireAtGameTick, createdGameTick, followSuspension, originalSchedule);
    }

    /** 永久指令（不会到期，{@code createdGameTick} 用作提醒计时基准）。 */
    public static MoveOrderData permanent(UUID maidId, UUID ownerId, MoveAction action, @Nullable UUID targetId,
                                          double x, double y, double z, double distance, long createdGameTick,
                                          @Nullable FollowSuspension followSuspension) {
        return permanent(maidId, ownerId, action, targetId, x, y, z, distance, createdGameTick,
                followSuspension, null);
    }

    /** 永久指令（带"临时放宽日程"保存的原日程）。 */
    public static MoveOrderData permanent(UUID maidId, UUID ownerId, MoveAction action, @Nullable UUID targetId,
                                          double x, double y, double z, double distance, long createdGameTick,
                                          @Nullable FollowSuspension followSuspension,
                                          @Nullable String originalSchedule) {
        return new MoveOrderData(maidId, ownerId, action, targetId, x, y, z, distance,
                PERMANENT, createdGameTick, followSuspension, originalSchedule);
    }

    public boolean isPermanent() {
        return expireAtGameTick < 0L;
    }

    public boolean isExpired(long nowGameTick) {
        return !isPermanent() && expireAtGameTick <= nowGameTick;
    }

    /**
     * 距离到期还有多少 tick；永久指令或已经到期的指令返回 0。
     * {@code nowGameTick} 由调用方给出（{@code ServerLevel#getGameTime()}）。
     */
    public long remainingTicks(long nowGameTick) {
        if (isPermanent()) {
            return 0L;
        }
        return Math.max(0L, expireAtGameTick - nowGameTick);
    }

    /** 距离到期还有多少秒（向上取整）；永久指令返回 0。 */
    public int remainingSeconds(long nowGameTick) {
        long ticks = remainingTicks(nowGameTick);
        return ticks <= 0L ? 0 : (int) ((ticks + 19L) / 20L);
    }

    /** 换一个暂停跟随记录（例如重新登记时把持久化的记录装回来）。 */
    public MoveOrderData withSuspension(@Nullable FollowSuspension suspension) {
        return new MoveOrderData(maidId, ownerId, action, targetId, x, y, z, distance,
                expireAtGameTick, createdGameTick, suspension, originalSchedule);
    }

    /** 换一个"临时放宽日程"保存的原日程（登记时挂上，或外部改动后清掉）。 */
    public MoveOrderData withOriginalSchedule(@Nullable String schedule) {
        return new MoveOrderData(maidId, ownerId, action, targetId, x, y, z, distance,
                expireAtGameTick, createdGameTick, followSuspension, schedule);
    }

    /** 重新登记（跨重启恢复）时改写到期的 gameTime。 */
    public MoveOrderData withExpireAtGameTick(long newExpireAtGameTick) {
        return new MoveOrderData(maidId, ownerId, action, targetId, x, y, z, distance,
                newExpireAtGameTick, createdGameTick, followSuspension, originalSchedule);
    }
}
