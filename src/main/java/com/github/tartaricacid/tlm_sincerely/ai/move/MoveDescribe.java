package com.github.tartaricacid.tlm_sincerely.ai.move;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 指令的"人类可读描述"拼装：给女仆的定期提醒、到期提示与 LLM 上下文栏目共用，
 * 保证三处说法一致（文字本身仍然全部取自 {@link MoveText#describe}，这里只决定 target 该填什么）。
 *
 * <p>target 的取法（与需求一致）：
 * <ul>
 *   <li>目标是主人 → {@code MoveText.ownerName(zh)}（zh 为"主人"，en 为 "the owner"）；</li>
 *   <li>目标是另一只女仆 → 她的名字；</li>
 *   <li>goto → 坐标描述 {@code (x, y, z)}；</li>
 *   <li>stay → 不使用 target（{@link MoveText#describe} 会忽略它）。</li>
 * </ul>
 * 纯函数，便于单测。
 */
public final class MoveDescribe {
    private MoveDescribe() {
    }

    /** goto 的坐标描述，如 {@code (100.0, 64.0, -20.0)}。 */
    public static String coordText(double x, double y, double z) {
        return String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", x, y, z);
    }

    /**
     * 指令描述。
     *
     * @param order       指令
     * @param targetName  目标实体的显示名；目标是主人时调用方直接传 {@link MoveText#ownerName(boolean)}，
     *                    解析不到目标时传 null
     * @param zh          是否中文
     */
    public static String describe(MoveOrderData order, @Nullable String targetName, boolean zh) {
        if (order == null) {
            return "";
        }
        String target = switch (order.action()) {
            case GOTO -> coordText(order.x(), order.y(), order.z());
            case STAY, WORK_TIME -> "";
            default -> targetName == null ? MoveText.ownerName(zh) : targetName;
        };
        return MoveText.describe(zh, order.action().id(), target);
    }
}
