package com.github.tartaricacid.tlm_sincerely.ai.move;

import org.jetbrains.annotations.Nullable;

/**
 * 暂停跟随（"状态守卫"）期间保存下来的女仆原状态，以及"结束指令时要不要恢复"的纯决策。
 *
 * <p>为什么需要：TLM 的女仆只有在 <b>居家模式</b>（{@code EntityMaid#isHomeModeEnable()}）为真时才
 * 不执行 {@code MaidFollowOwnerTask}；跟随模式下该任务注册在 Brain 的 CORE，会每 tick 覆盖
 * {@code WALK_TARGET}，还可能在距离超过阈值时直接把女仆传送回主人——和 goto / retreat / stay
 * 这类"不要跟着主人"的指令直接打架。因此这些指令要在生效期间把女仆临时切到居家模式。
 *
 * <p>javap 证据（mapped TLM 1.5.3 + mapped Forge 1.20.1）：
 * <ul>
 *   <li>{@code MaidFollowOwnerTask#maidStateConditions} = {@code !maid.isHomeModeEnable() && maid.canBrainMoving()}；</li>
 *   <li>{@code EntityMaid#hasRestriction()} 被覆写成 {@code return isHomeModeEnable();}（不是看半径）；</li>
 *   <li>{@code EntityMaid#getRestrictCenter()/getRestrictRadius()} 读的是同步数据 RESTRICT_CENTER /
 *       RESTRICT_RADIUS，默认值分别是 {@code BlockPos.ZERO} 与 {@code MaidConfig.MAID_NON_HOME_RANGE}（默认 8）；</li>
 *   <li>{@code EntityMaid#m_21446_(BlockPos,int)}（即 {@code restrictTo}）只写同步数据；
 *       {@code EntityMaid} 覆写了 {@code m_21534_/m_21535_/m_21536_/m_21444_/m_21446_} 这一整组
 *       （{@code getRestrictCenter/getRestrictRadius/hasRestriction/isWithinRestriction/restrictTo}），
 *       全部读写同步数据，而不是 {@code Mob} 的 {@code restrictCenter/restrictRadius} 字段；
 *       因此恢复时一律显式写回保存下来的中心与半径，不依赖基类字段的副作用。</li>
 * </ul>
 *
 * <p>恢复语义是<b>精确恢复</b>：把居家模式写回保存时的值，并且把工作坐标（限制中心 + 半径）写回保存时的值。
 * 不能一律关掉居家模式——女仆原本就是"居家 + 工作坐标"时那样会破坏玩家设置。
 */
public final class FollowSuspension {
    /** 结束指令时如何处理暂停记录。 */
    public enum RestoreAction {
        /** 把保存的原状态写回女仆（指令正常结束）。 */
        RESTORE,
        /** 外部（玩家界面 / 跟随工具）已经改过居家模式：不恢复，直接丢弃记录。 */
        EXTERNAL_CHANGE
    }

    private final boolean homeMode;
    private final boolean hadRestriction;
    private final long centerX;
    private final long centerY;
    private final long centerZ;
    private final float radius;

    /**
     * @param homeMode      暂停前是否处于居家模式
     * @param hadRestriction 暂停前女仆是否有工作坐标（限制中心 + 半径）
     * @param centerX       限制中心 x（{@code hadRestriction} 为 false 时无意义）
     * @param centerY       限制中心 y
     * @param centerZ       限制中心 z
     * @param radius        限制半径（格）
     */
    public FollowSuspension(boolean homeMode, boolean hadRestriction,
                            long centerX, long centerY, long centerZ, float radius) {
        this.homeMode = homeMode;
        this.hadRestriction = hadRestriction;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.radius = radius;
    }

    /** 暂停前是否处于居家模式。 */
    public boolean homeMode() {
        return homeMode;
    }

    /** 暂停前是否有工作坐标。 */
    public boolean hadRestriction() {
        return hadRestriction;
    }

    public long centerX() {
        return centerX;
    }

    public long centerY() {
        return centerY;
    }

    public long centerZ() {
        return centerZ;
    }

    public float radius() {
        return radius;
    }

    /**
     * 结束指令时该不该恢复。
     *
     * <p>规则：暂停期间如果女仆的居家模式被别处改成了与保存值不同的值，视为玩家接管
     * （例如玩家在女仆界面里切换了居家模式，或用跟随工具让她重新跟随），此时<b>不恢复</b>，
     * 只丢弃记录，绝不能把玩家刚改的设置覆盖回去。
     *
     * @param currentHomeMode 现在的 {@code maid.isHomeModeEnable()}
     */
    public RestoreAction decideRestore(boolean currentHomeMode) {
        return currentHomeMode == homeMode ? RestoreAction.RESTORE : RestoreAction.EXTERNAL_CHANGE;
    }

    /** 恢复动作的日志文本（中文注释风格的项目里，日志保持英文便于检索）。 */
    public String describe() {
        return "homeMode=" + homeMode + " restriction=" + hadRestriction
                + (hadRestriction ? " center=(" + centerX + ", " + centerY + ", " + centerZ + ") radius=" + radius : "");
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FollowSuspension suspension)) {
            return false;
        }
        return homeMode == suspension.homeMode
                && hadRestriction == suspension.hadRestriction
                && centerX == suspension.centerX
                && centerY == suspension.centerY
                && centerZ == suspension.centerZ
                && Float.compare(radius, suspension.radius) == 0;
    }

    @Override
    public int hashCode() {
        int result = Boolean.hashCode(homeMode);
        result = 31 * result + Boolean.hashCode(hadRestriction);
        result = 31 * result + Long.hashCode(centerX);
        result = 31 * result + Long.hashCode(centerY);
        result = 31 * result + Long.hashCode(centerZ);
        result = 31 * result + Float.hashCode(radius);
        return result;
    }

    @Override
    public String toString() {
        return "FollowSuspension[" + describe() + "]";
    }
}
