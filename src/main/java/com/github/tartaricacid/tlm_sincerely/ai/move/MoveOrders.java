package com.github.tartaricacid.tlm_sincerely.ai.move;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 移动类指令（maid_move / maid_guard / maid_work_time）的指令表：每只女仆（UUID）最多一条当前指令，新的覆盖旧的。
 * <p>
 * 纯 Java 内存态，不引用任何 Minecraft 类型，也不持有世界/实体引用，便于单元测试；
 * 实体解析、移动下发与清理时机由 {@link MoveDirectiveManager} 负责。
 * 到期清理由调用方给出 gameTime，因此这里对"时间"没有依赖。
 */
public final class MoveOrders {
    /** {@link #put} 的结果。 */
    public enum PutResult {
        OK,
        /** 同一主人同时生效的指令数已达上限（覆盖本人已有的指令不算新增）。 */
        LIMIT_REACHED
    }

    private final Map<UUID, MoveOrderData> byMaid = new LinkedHashMap<>();

    /**
     * 写入一条指令：同一只女仆已有指令时直接覆盖（不占额外名额）；
     * 否则检查该主人的同时生效数量上限，超限返回 {@link PutResult#LIMIT_REACHED} 且不写入。
     *
     * <p>永久指令同样占用主人"同时生效指令数"的名额（{@link #countForOwner} 一视同仁地计数）。
     */
    public PutResult put(MoveOrderData order, int maxPerOwner) {
        if (order == null || order.maidId() == null) {
            return PutResult.LIMIT_REACHED;
        }
        if (!byMaid.containsKey(order.maidId())) {
            int max = Math.max(1, maxPerOwner);
            if (countForOwner(order.ownerId()) >= max) {
                return PutResult.LIMIT_REACHED;
            }
        }
        byMaid.put(order.maidId(), order);
        return PutResult.OK;
    }

    /** @return 是否真的移除了指令（用于 cancel 的"本来就没有指令"文案） */
    public boolean remove(@Nullable UUID maidId) {
        return maidId != null && byMaid.remove(maidId) != null;
    }

    @Nullable
    public MoveOrderData get(@Nullable UUID maidId) {
        return maidId == null ? null : byMaid.get(maidId);
    }

    public boolean isActive(@Nullable UUID maidId) {
        return maidId != null && byMaid.containsKey(maidId);
    }

    /** 该主人当前生效的指令数（覆盖写入不会重复计数；永久与限时一并计数）。 */
    public int countForOwner(@Nullable UUID ownerId) {
        if (ownerId == null) {
            return 0;
        }
        int count = 0;
        for (MoveOrderData order : byMaid.values()) {
            if (ownerId.equals(order.ownerId())) {
                count++;
            }
        }
        return count;
    }

    public int size() {
        return byMaid.size();
    }

    /** 当前所有指令的女仆 UUID（副本，可在遍历中安全地 {@link #remove}）。 */
    public List<UUID> maidIds() {
        return new ArrayList<>(byMaid.keySet());
    }

    /**
     * 移除所有已到期的"限时"指令，返回被移除的女仆 UUID。
     * 永久指令（{@code expireAtGameTick < 0}）永远不会到期，不在此列。
     */
    public List<UUID> removeExpired(long nowGameTick) {
        List<UUID> removed = new ArrayList<>();
        var iterator = byMaid.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, MoveOrderData> entry = iterator.next();
            if (entry.getValue().isExpired(nowGameTick)) {
                iterator.remove();
                removed.add(entry.getKey());
            }
        }
        return removed;
    }

    public void clear() {
        byMaid.clear();
    }
}
