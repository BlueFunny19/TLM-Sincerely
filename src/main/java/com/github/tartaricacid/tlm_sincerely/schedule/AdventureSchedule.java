package com.github.tartaricacid.tlm_sincerely.schedule;

import com.github.tartaricacid.tlm_sincerely.config.subconfig.AdventureConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "冒险"日程在运行时的入口：枚举值、原版 {@link Schedule} 时间表、此刻该做什么。
 *
 * <p>TLM 的 {@link MaidSchedule} 是个只有 {@code DAY / NIGHT / ALL} 的枚举，本 mod 用
 * {@code MaidScheduleEnumMixin} 在类初始化时给它追加第四个值 {@code ADVENTURE}。编译期这个值不存在，
 * 所以所有代码都通过 {@link #value()} / {@link #is} 访问它；混入没有生效时 {@link #value()} 返回
 * {@code null}，所有冒险相关的逻辑都自动不触发，不会报错。
 *
 * <p>枚举值是按名字存进女仆的存档的，女仆处于冒险日程时卸载本 mod，主模组读档会因为认不出这个名字而出错。
 * 这一点写在 README 的警告里。
 */
public final class AdventureSchedule {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdventureSchedule.class);
    /** 追加到 {@link MaidSchedule} 的枚举名。 */
    public static final String NAME = "ADVENTURE";

    private static boolean valueResolved;
    @Nullable
    private static MaidSchedule value;
    @Nullable
    private static AdventureWindow cachedWindow;
    @Nullable
    private static Schedule cachedSchedule;

    private AdventureSchedule() {
    }

    /** 追加进去的枚举值；混入没有生效（别的 mod 冲突、主模组改了类结构）时为 {@code null}。 */
    @Nullable
    public static synchronized MaidSchedule value() {
        if (!valueResolved) {
            valueResolved = true;
            try {
                value = MaidSchedule.valueOf(NAME);
                LOGGER.info("[adventure] MaidSchedule.{} is available (ordinal {} of {} values)",
                        NAME, value.ordinal(), MaidSchedule.values().length);
            } catch (IllegalArgumentException e) {
                value = null;
                LOGGER.warn("[adventure] MaidSchedule.{} does not exist: the enum mixin did not apply, "
                        + "the adventure schedule is unavailable", NAME);
            }
        }
        return value;
    }

    /** 这个日程是不是冒险日程。 */
    public static boolean is(@Nullable MaidSchedule schedule) {
        return schedule != null && schedule == value();
    }

    /**
     * 按当前配置的睡眠窗口生成的原版时间表（几乎全天 {@code WORK}，窗口里 {@code REST}，没有 {@code IDLE}）。
     * 配置没变就复用同一个对象；变了就重建，已经在用旧表的女仆在下次刷新大脑时换上新表。
     */
    public static synchronized Schedule timetable() {
        AdventureWindow window = AdventureConfig.window();
        if (cachedSchedule == null || !window.equals(cachedWindow)) {
            ScheduleBuilder builder = new ScheduleBuilder(new Schedule());
            for (AdventureWindow.Segment segment : window.segments()) {
                builder.changeActivityAt(segment.from(), segment.rest() ? Activity.REST : Activity.WORK);
            }
            cachedSchedule = builder.build();
            cachedWindow = window;
        }
        return cachedSchedule;
    }

    /** 某个世界时间点冒险日程下的活动（{@code REST} 或 {@code WORK}）。 */
    public static Activity activityAt(long dayTime) {
        return timetable().getActivityAt((int) Math.floorMod(dayTime, (long) AdventureWindow.DAY_LENGTH));
    }
}
