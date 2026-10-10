package com.github.tartaricacid.tlm_sincerely.ai.move;

import com.github.tartaricacid.tlm_sincerely.ai.overtime.OvertimeHolder;
import com.github.tartaricacid.tlm_sincerely.ai.overtime.OvertimeManager;
import com.github.tartaricacid.tlm_sincerely.config.subconfig.MoveConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static com.github.tartaricacid.tlm_sincerely.ai.move.MoveLog.shortId;

/**
 * 登记一条指令的统一流程，供 {@code maid_move} / {@code maid_guard} / {@code maid_work_time} 共用：
 * 建指令 → 覆盖旧指令时处理暂停跟随 → 加入"加班"（临时改成全天工作）→ 占用名额（名额不够时撤销前面的副作用）。
 *
 * <p>必须在服务端主线程调用。登记成功后的"给模型看的文字"由各个工具自己拼。
 */
public final class MoveOrderPlacer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoveOrderPlacer.class);

    private MoveOrderPlacer() {
    }

    /**
     * 一次登记请求。{@code x / y / z} 只对 goto 有意义，{@code targetId} 只对 approach / retreat / guard 有意义，
     * 其余动作传 0 / null。{@code seconds} 只在 {@code permanent} 为 false 时使用。
     */
    public record Request(EntityMaid maid, UUID ownerId, MoveAction action, @Nullable UUID targetId,
                          double x, double y, double z, double distance, boolean permanent, int seconds) {
    }

    /**
     * 登记结果。{@code limitReached} 为 true 时指令没有写入，暂停跟随与加班的副作用也已撤销。
     */
    public record Placement(boolean limitReached, int maxActive,
                            MoveDirectiveManager.SuspensionChange suspension,
                            OvertimeManager.Acquisition overtime) {
    }

    public static Placement place(Request request) {
        EntityMaid maid = request.maid();
        UUID maidId = maid.getUUID();
        long nowGameTick = maid.level().getGameTime();
        MoveOrderData order = request.permanent()
                ? MoveOrderData.permanent(maidId, request.ownerId(), request.action(), request.targetId(),
                        request.x(), request.y(), request.z(), request.distance(), nowGameTick, null)
                : MoveOrderData.timed(maidId, request.ownerId(), request.action(), request.targetId(),
                        request.x(), request.y(), request.z(), request.distance(),
                        nowGameTick + request.seconds() * 20L, nowGameTick, null);

        // 覆盖旧指令：新指令不需要暂停而旧指令暂停过 → 先恢复；新指令需要暂停 → 建立（或沿用）暂停记录
        MoveDirectiveManager.SuspensionChange suspension = MoveDirectiveManager.onOrderReplaced(maid, order);
        if (suspension.record() != null) {
            // 把"暂停前的原状态"挂到指令上一起持久化，跨重启才恢复得回去
            order = order.withSuspension(suspension.record());
        }
        // 加班：需要的话让这条指令成为持有者（没在睡觉就临时改成全天工作；已经在加班就只是加入）。
        // 新指令不需要加班时，旧指令占着的这一份要放掉。
        boolean hadOrderHolder = OvertimeManager.holds(maidId, OvertimeHolder.ORDER);
        OvertimeManager.Acquisition overtime;
        if (request.action().needsScheduleRelief()) {
            // work_time 自己就是为了加班，不受 MoveForceWorkSchedule 影响；其余指令都要看配置
            boolean configAllows = request.action().isWorkTime() || MoveConfig.MOVE_FORCE_WORK_SCHEDULE.get();
            overtime = OvertimeManager.acquire(maid, OvertimeHolder.ORDER, configAllows);
        } else {
            OvertimeManager.release(maidId, maid, OvertimeHolder.ORDER, "replaced-by-order-without-relief");
            overtime = OvertimeManager.Acquisition.notStarted();
        }
        int maxActive = MoveConfig.MOVE_MAX_ACTIVE_PER_OWNER.get();
        if (MoveDirectiveManager.putOrder(maid, order, maxActive) == MoveOrders.PutResult.LIMIT_REACHED) {
            // 名额不够：不能留下"已经暂停过 / 已经加上班"的副作用（旧指令原本就占着的那份不动）
            MoveDirectiveManager.restoreSuspension(maidId, maid, "limit-reached");
            if (!hadOrderHolder) {
                OvertimeManager.release(maidId, maid, OvertimeHolder.ORDER, "limit-reached");
            }
            LOGGER.info("[maid_move] rejected: reason=limit-reached maid={} name={} action={} permanent={} max={}",
                    shortId(maidId), maid.getName().getString(), request.action().id(), request.permanent(), maxActive);
            return new Placement(true, maxActive, suspension, overtime);
        }
        LOGGER.info("[maid_move] order put: maid={} name={} action={} target={} distance={} permanent={} seconds={} followSuspended={} overtime={} originalSchedule={}",
                shortId(maidId), maid.getName().getString(), request.action().id(), shortId(request.targetId()),
                request.distance(), request.permanent(), request.permanent() ? -1 : request.seconds(),
                suspension.suspended(), overtime.action(), overtime.originalSchedule());
        return new Placement(false, maxActive, suspension, overtime);
    }
}
