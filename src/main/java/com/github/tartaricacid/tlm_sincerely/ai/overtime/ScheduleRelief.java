package com.github.tartaricacid.tlm_sincerely.ai.overtime;

import org.jetbrains.annotations.Nullable;

/**
 * "加班"（把女仆日程临时改成 TLM 的 {@code MaidSchedule.ALL}）的纯逻辑判定。
 *
 * <p>为什么需要：夜里主模组的日程行为不是"工作时间"，移动指令会被日程拦下、
 * 切换工作任务也会回答"不在工作时间"。所以玩家要女仆干活（移动 / 守护 / 切换工作任务）时，
 * 只要她不是在睡觉，就把日程临时改成全天工作，活干完（或指令结束）时再恢复。
 *
 * <p>这套判定与暂停跟随（{@code FollowSuspension}）一样是"状态守卫"：保存原状态 → 改动 →
 * 恢复 → 持久化 → 外部改动检测 → 重复触发时不重复保存。它不引用任何 Minecraft / TLM 类型
 * （原日程只以枚举 {@code name()} 字符串出现），因此可以在 JUnit 里直接跑。
 *
 * <p><b>开始加班时的规则</b>（{@link #decide}）：
 * <ol>
 *   <li>这只女仆已经在加班（有"原日程"记录）且当前日程仍是 ALL → {@link Action#REUSE} 沿用旧记录，
 *       <b>绝不能</b>把已经被改成 ALL 的日程当成原日程再存一次（否则结束后永远恢复不回去）；</li>
 *   <li>正在睡觉 → {@link Action#UNCHANGED}：不改、也不保存；</li>
 *   <li>当前日程本来就是 ALL → {@link Action#UNCHANGED}：不改、不保存（结束时也没有需要恢复的东西）；</li>
 *   <li>配置不允许（例如 {@code MoveForceWorkSchedule} 关闭）→ {@link Action#UNCHANGED}；</li>
 *   <li>其余情况 → {@link Action#FORCE_ALL}：保存原日程并改成全天工作。</li>
 * </ol>
 *
 * <p><b>结束加班时的规则</b>（{@link #decideRestore}）：保存过原日程、而当前日程已经不是 ALL，
 * 说明玩家在加班期间自己改了日程（界面 / 主模组的 {@code switch_schedule} 工具）——
 * 视为玩家接管，不恢复，直接丢弃记录。
 */
public final class ScheduleRelief {
    /** 建立指令时对日程该做什么。 */
    public enum Action {
        /** 不改日程，也不保存原日程（正在睡觉 / 本来就是全天工作 / 配置关闭）。 */
        UNCHANGED,
        /** 保存原日程，并把日程改成 TLM 的"全天工作"。 */
        FORCE_ALL,
        /** 沿用已有的"原日程"记录（旧指令已经放宽过）。 */
        REUSE
    }

    /** 结束指令时对日程该做什么。 */
    public enum RestoreAction {
        /** 没有保存过原日程，什么都不用做。 */
        NOTHING,
        /** 把保存的原日程写回去。 */
        RESTORE,
        /** 放宽期间日程被改成了别的值：不恢复，直接丢弃记录。 */
        EXTERNAL_CHANGE
    }

    private ScheduleRelief() {
    }

    /**
     * 建立 / 替换指令时该对日程做什么。
     *
     * @param sleeping       女仆是否正在睡觉（只认"正在睡觉"这一个条件，坐着照样放宽）
     * @param currentlyAll   当前日程是否已经是"全天工作"（{@code MaidSchedule.ALL}）
     * @param hasSavedRelief 内存里是否已经有这只女仆的"原日程"记录
     * @param configAllows   配置是否允许放宽（{@code MoveForceWorkSchedule}；{@code work_time} 恒为 true）
     */
    public static Action decide(boolean sleeping, boolean currentlyAll,
                                boolean hasSavedRelief, boolean configAllows) {
        if (hasSavedRelief && currentlyAll) {
            return Action.REUSE;
        }
        if (sleeping) {
            return Action.UNCHANGED;
        }
        if (currentlyAll) {
            return Action.UNCHANGED;
        }
        if (!configAllows) {
            return Action.UNCHANGED;
        }
        return Action.FORCE_ALL;
    }

    /**
     * 结束指令时该不该把原日程写回去。
     *
     * @param savedOriginal 保存的原日程（{@code MaidSchedule#name()}）；没保存过时传 null
     * @param currentlyAll  当前日程是否仍然是"全天工作"
     */
    public static RestoreAction decideRestore(@Nullable String savedOriginal, boolean currentlyAll) {
        if (savedOriginal == null) {
            return RestoreAction.NOTHING;
        }
        return currentlyAll ? RestoreAction.RESTORE : RestoreAction.EXTERNAL_CHANGE;
    }
}
