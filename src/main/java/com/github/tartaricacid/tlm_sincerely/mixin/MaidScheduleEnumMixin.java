package com.github.tartaricacid.tlm_sincerely.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * 给主模组的日程枚举 {@link MaidSchedule}（原来只有 {@code DAY / NIGHT / ALL}）追加第四个值 {@code ADVENTURE}。
 *
 * <p>做法是枚举扩展的标准套路：在枚举类静态初始化里、{@code $VALUES} 数组刚赋值之后，用调用构造器的访问器
 * 造出一个新值，再把数组换成多一个元素的新数组。{@code values()} / {@code valueOf()} 都读 {@code $VALUES}
 * （前者直接克隆它，后者通过 {@code Class.getEnumConstants()}），所以追加之后各处都认得这个值；而枚举类
 * 之外那些按 {@code ordinal} 生成的 {@code switch} 映射表（{@code MaidBrain$1} 等）是在本类初始化之后才建的，
 * 长度也是对的，对没有列出的新值会走各自的 {@code default} 分支——这些分支另有混入处理
 * （见 {@link MaidBrainScheduleMixin} 及其同类）。
 *
 * <p>编译期代码引用不到 {@code ADVENTURE}，运行期统一经 {@code AdventureSchedule.value()} 取用；
 * 本混入没有生效时它返回 {@code null}，冒险相关的逻辑自动不触发。
 */
@Mixin(value = MaidSchedule.class, remap = false)
public abstract class MaidScheduleEnumMixin {
    @Shadow
    @Final
    @Mutable
    private static MaidSchedule[] $VALUES;

    @Invoker("<init>")
    private static MaidSchedule tlmSincerely$construct(String name, int ordinal) {
        throw new AssertionError("replaced by the mixin invoker");
    }

    @Inject(method = "<clinit>",
            at = @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, shift = At.Shift.AFTER,
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/ai/brain/MaidSchedule;$VALUES:"
                            + "[Lcom/github/tartaricacid/touhoulittlemaid/entity/ai/brain/MaidSchedule;"),
            require = 0)
    private static void tlmSincerely$addAdventure(CallbackInfo ci) {
        MaidSchedule[] original = $VALUES;
        MaidSchedule[] extended = Arrays.copyOf(original, original.length + 1);
        extended[original.length] = tlmSincerely$construct("ADVENTURE", original.length);
        $VALUES = extended;
    }
}
