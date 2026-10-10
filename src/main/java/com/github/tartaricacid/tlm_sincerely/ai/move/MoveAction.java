package com.github.tartaricacid.tlm_sincerely.ai.move;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 移动类指令的种类，也是指令表与存档里记录的 action 标识。
 * <p>
 * 分属四个工具：{@code maid_move}（approach / retreat / goto / stay / go_home）、{@code maid_guard}（guard）、
 * {@code maid_work_time}（work_time）、{@code maid_cancel}（cancel，只用于存档里的旧数据与内部结束指令）。
 * 它们共用同一套指令登记、暂停跟随与恢复机制，所以放在同一个枚举里。
 * <p>
 * 机器标识（{@link #id()}）与参数名保持英文；{@link #defaultDistance()} 是各动作的默认距离（格），
 * 非距离类动作返回 0。此枚举为纯 Java，便于单元测试。
 */
public enum MoveAction {
    APPROACH("approach", 3.0D),
    RETREAT("retreat", 16.0D),
    GOTO("goto", 0.0D),
    GUARD("guard", 5.0D),
    /** 留在原地：不再跟随主人，也不走动（会把工作坐标临时清掉，结束后恢复）。 */
    STAY("stay", 0.0D),
    /** 回家：传送到自己的工作坐标并进入居家模式；永久性改变，不产生"指令"。 */
    GO_HOME("go_home", 0.0D),
    /** 暂时放宽工作时间：不移动，只把日程临时改成"全天工作"（可限时 / 永久）。 */
    WORK_TIME("work_time", 0.0D),
    CANCEL("cancel", 0.0D);

    private final String id;
    private final double defaultDistance;

    MoveAction(String id, double defaultDistance) {
        this.id = id;
        this.defaultDistance = defaultDistance;
    }

    public String id() {
        return id;
    }

    public double defaultDistance() {
        return defaultDistance;
    }

    /** 该动作是否需要目标实体（approach / retreat / guard）。goto 用坐标，其余不用目标。 */
    public boolean needsTarget() {
        return this == APPROACH || this == RETREAT || this == GUARD;
    }

    /** 该动作是否需要 x/y/z 坐标（只有 goto）。 */
    public boolean needsCoords() {
        return this == GOTO;
    }

    /** 该动作是否会产生一条"指令"（会登记、会到期/取消；go_home 是永久性改变，不产生指令）。 */
    public boolean isOrder() {
        return this != GO_HOME;
    }

    /** 该动作是否接受 duration_seconds / permanent 参数（go_home 不接受）。 */
    public boolean acceptsDuration() {
        return this != GO_HOME && this != CANCEL;
    }

    /** 该动作是否就是"只放宽工作时间"（{@code work_time}：自己不受 {@code MoveForceWorkSchedule} 影响）。 */
    public boolean isWorkTime() {
        return this == WORK_TIME;
    }

    /**
     * 该动作生效期间是否需要"临时放宽工作时间"（把日程临时改成全天工作，结束时恢复）。
     *
     * <p>approach / retreat / goto / guard / stay / work_time 需要；go_home（永久性改变、不产生指令）
     * 与 cancel（结束指令）不需要。是否真的放宽还要看 {@code MoveForceWorkSchedule} 与女仆是否在睡觉，
     * 见 {@link ScheduleRelief#decide}。
     */
    public boolean needsScheduleRelief() {
        return switch (this) {
            case APPROACH, RETREAT, GOTO, GUARD, STAY, WORK_TIME -> true;
            case GO_HOME, CANCEL -> false;
        };
    }

    /**
     * 该动作是否需要在生效期间"暂停跟随"（把女仆临时切到居家模式并清掉工作坐标）。
     *
     * <p>stay / goto / retreat 一律需要；approach / guard 只有在目标不是主人本人时才需要
     * （跟着主人的指令本来就该让跟随行为继续生效）。规则做成纯函数便于单测。
     *
     * @param targetIsOwner 指令目标是否就是女仆的主人
     */
    public boolean suspendsFollow(boolean targetIsOwner) {
        return switch (this) {
            case STAY, GOTO, RETREAT -> true;
            case APPROACH, GUARD -> !targetIsOwner;
            default -> false;
        };
    }

    /** 解析 action 标识（忽略大小写与首尾空白）；无法识别时返回 null，由调用方回 {@code unknownAction}。 */
    @Nullable
    public static MoveAction parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        for (MoveAction action : values()) {
            if (action.id.equals(value)) {
                return action;
            }
        }
        return null;
    }
}
