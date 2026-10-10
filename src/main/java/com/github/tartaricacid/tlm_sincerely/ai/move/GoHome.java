package com.github.tartaricacid.tlm_sincerely.ai.move;

import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static com.github.tartaricacid.tlm_sincerely.ai.move.MoveLog.shortId;

/**
 * {@code maid_move} 的 {@code go_home} 动作：立刻传送回工作坐标并打开居家模式。
 * 这是永久性的改变，不产生指令、不写标签、不超时。
 */
public final class GoHome {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoHome.class);

    /** go_home 在工作坐标周围搜索落脚点的半径（格）——比 {@link MoveMath#standableOffsets()} 的 ±2 格更大。 */
    private static final int HOME_SEARCH_RADIUS = 4;

    private GoHome() {
    }

    /** {@code go_home} 的结果：给模型的文字 + 是否真的执行了。 */
    public record ActionResult(String message, boolean acted) {
    }

    /**
     * 回家：立刻传送到工作坐标附近并打开居家模式。
     *
     * <p>前提是女仆有工作坐标（见 {@link #hasWorkPos}）。
     * 找不到落脚点时返回 {@link MoveText#homeNoSpot}，且不改变任何状态（先结束暂停中的指令再找位置，
     * 这样拿到的才是真实的工作坐标；找不到时女仆状态已经被恢复成原样，指令也没了）。
     */
    public static ActionResult execute(ServerLevel level, EntityMaid maid) {
        UUID maidId = maid.getUUID();
        boolean zh = PromptLang.isChinese(PromptLang.of(maid));
        String maidName = maid.getName().getString();
        if (!hasWorkPos(maid)) {
            LOGGER.info("[maid_move] go_home rejected: no work position. maid={} name={}", shortId(maidId), maidName);
            return new ActionResult(MoveText.noWorkPos(zh, maidName), false);
        }
        BlockPos workPos = maid.getSchedulePos().getWorkPos();
        // 先结束暂停中的指令：这样读到的是真实工作坐标，状态也会被恢复成原样（不会停在"被改过"的样子）
        MoveDirectiveManager.removeOrder(maidId, maid, "go-home");
        BlockPos landing = findStandableNearHome(level, workPos);
        if (landing == null) {
            LOGGER.info("[maid_move] go_home rejected: no standable spot near work position. maid={} name={} workPos={}",
                    shortId(maidId), maidName, workPos);
            return new ActionResult(MoveText.homeNoSpot(zh, maidName), false);
        }
        MoveNavigation.teleportMaid(maid, landing);
        maid.setHomeModeEnable(true);
        MaidSpeech.say(maid, MoveText.saidWentHome(zh));
        LOGGER.info("[maid_move] go_home done: maid={} name={} workPos={} landing={} homeMode={} dimension={}",
                shortId(maidId), maidName, workPos, landing, maid.isHomeModeEnable(), level.dimension().location());
        return new ActionResult(MoveText.wentHome(zh, maidName, workPos.getX(), workPos.getY(), workPos.getZ()), true);
    }

    /**
     * 女仆有没有工作坐标。核实的判定方式（javap，mapped TLM 1.5.3）：
     * <ul>
     *   <li>{@code EntityMaid#hasRestriction()} 被覆写成 {@code isHomeModeEnable()}，<b>不能</b>用来判断
     *       "有没有工作坐标"；{@code getRestrictRadius()} 的默认值是
     *       {@code MaidConfig.MAID_NON_HOME_RANGE}（默认 8），也不是 -1 哨兵值；</li>
     *   <li>工作坐标真正的载体是女仆的日程坐标 {@code EntityMaid#getSchedulePos()}
     *       （{@code SchedulePos.isConfigured()} / {@code getWorkPos()}，持久化在 {@code MaidSchedulePos} 标签里）。
     *       TLM 的 {@code SchedulePos#setHomeModeEnable(maid,pos)} 只在 {@code !configured} 时写入
     *       work/idle/sleep 三个坐标，也就是玩家设定工作坐标的那一次。所以
     *       {@code isConfigured() && getWorkPos() != null} 就是"有工作坐标"。</li>
     * </ul>
     */
    public static boolean hasWorkPos(@Nullable EntityMaid maid) {
        if (maid == null) {
            return false;
        }
        var schedulePos = maid.getSchedulePos();
        return schedulePos != null && schedulePos.isConfigured() && schedulePos.getWorkPos() != null;
    }

    /**
     * 在工作坐标周围更大范围里找落脚点：先按每格一个的水平搜索点（近的优先）扫，
     * 每个点再交给既有的 {@link MoveNavigation#findStandable} 偏移表。
     */
    @Nullable
    private static BlockPos findStandableNearHome(ServerLevel level, BlockPos workPos) {
        MoveMath.Point center = new MoveMath.Point(
                workPos.getX() + 0.5D, workPos.getY(), workPos.getZ() + 0.5D);
        for (MoveMath.Point point : MoveMath.horizontalSearchPoints(center, HOME_SEARCH_RADIUS)) {
            BlockPos standable = MoveNavigation.findStandable(level, point);
            if (standable != null) {
                return standable;
            }
        }
        return null;
    }
}
