package com.github.tartaricacid.tlm_sincerely.ai.overtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ScheduleRelief} 的单元测试：建立指令时的放宽决策表、结束指令时的恢复决策表，
 * 以及"已有原日程记录"（替换指令 / 外部改动）这几种关键情形。
 *
 * <p>全部是纯逻辑，不依赖实体与世界。
 */
class ScheduleReliefTest {
    // ===== 建立指令：decide =====

    @Test
    void decide_normalCase_forcesAll() {
        // 没睡觉、日程不是 ALL、没有旧记录、配置允许 → 保存原日程并改成全天工作
        assertEquals(ScheduleRelief.Action.FORCE_ALL, ScheduleRelief.decide(false, false, false, true));
    }

    @Test
    void decide_sleeping_doesNothing() {
        assertEquals(ScheduleRelief.Action.UNCHANGED, ScheduleRelief.decide(true, false, false, true));
    }

    @Test
    void decide_alreadyAll_doesNothing() {
        // 玩家自己就把日程设成了全天工作：不改也不保存（结束时没有需要恢复的东西）
        assertEquals(ScheduleRelief.Action.UNCHANGED, ScheduleRelief.decide(false, true, false, true));
    }

    @Test
    void decide_configDisabled_doesNothing() {
        assertEquals(ScheduleRelief.Action.UNCHANGED, ScheduleRelief.decide(false, false, false, false));
    }

    @Test
    void decide_existingRecord_isReused() {
        // 旧指令已经放宽过：沿用旧记录，绝不能把已经改成 ALL 的日程当成原日程再存一次
        assertEquals(ScheduleRelief.Action.REUSE, ScheduleRelief.decide(false, true, true, true));
    }

    @Test
    void decide_existingRecordWinsOverSleeping() {
        // 放宽期间女仆睡着了又来一条新指令：日程仍是 ALL，旧记录必须留着，结束时才恢复得回去
        assertEquals(ScheduleRelief.Action.REUSE, ScheduleRelief.decide(true, true, true, true));
    }

    @Test
    void decide_existingRecordButScheduleChanged_forcesAgain() {
        // 记录还在、日程已经不是 ALL（玩家改过，调用方会先清记录）：按当前日程重新放宽
        assertEquals(ScheduleRelief.Action.FORCE_ALL, ScheduleRelief.decide(false, false, true, true));
    }

    @Test
    void decide_workTimeIgnoresConfigBecauseCallerPassesTrue() {
        // work_time 自己就是为了放宽日程：调用方对 work_time 传 configAllows=true
        assertEquals(ScheduleRelief.Action.FORCE_ALL, ScheduleRelief.decide(false, false, false, true));
    }

    // ===== 结束指令：decideRestore =====

    @Test
    void decideRestore_withoutSavedSchedule_doesNothing() {
        assertEquals(ScheduleRelief.RestoreAction.NOTHING,
                ScheduleRelief.decideRestore(null, false));
        assertEquals(ScheduleRelief.RestoreAction.NOTHING,
                ScheduleRelief.decideRestore(null, true));
    }

    @Test
    void decideRestore_stillAll_restores() {
        assertEquals(ScheduleRelief.RestoreAction.RESTORE,
                ScheduleRelief.decideRestore("DAY", true));
    }

    @Test
    void decideRestore_scheduleChangedExternally_doesNotRestore() {
        // 玩家在界面里或用 switch_schedule 工具改过日程：视为接管，不恢复
        assertEquals(ScheduleRelief.RestoreAction.EXTERNAL_CHANGE,
                ScheduleRelief.decideRestore("DAY", false));
    }
}
