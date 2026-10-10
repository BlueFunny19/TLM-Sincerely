package com.github.tartaricacid.tlm_sincerely.ai.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一只女仆的守护名单（不可变，纯逻辑）：按优先级从高到低排好的 {@link GuardEntry}。
 * 同一个目标在名单里最多出现一次。
 *
 * <p>守谁由 {@link GuardDecider} 决定：从上往下找第一个"目标在场、条件成立"的项。
 */
public record GuardRules(List<GuardEntry> entries) {
    /** 名单最多几项（再多的守护规则没有意义，也避免有人往里塞一堆）。 */
    public static final int MAX_ENTRIES = 8;

    public static final GuardRules EMPTY = new GuardRules(List.of());

    /** 新增 / 更新一项时，它在名单里的位置。 */
    public enum Placement {
        /** 放到最前面（最高优先级）。 */
        FIRST,
        /** 放到最后面（最低优先级）。 */
        LAST,
        /** 目标已在名单里就原地更新，不动位置；不在则放到最后。 */
        KEEP
    }

    public GuardRules {
        List<GuardEntry> clean = new ArrayList<>(entries.size());
        for (GuardEntry entry : entries) {
            if (entry != null && clean.stream().noneMatch(e -> e.targetId().equals(entry.targetId()))
                    && clean.size() < MAX_ENTRIES) {
                clean.add(entry);
            }
        }
        entries = List.copyOf(clean);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean contains(UUID targetId) {
        return indexOf(targetId) >= 0;
    }

    public int indexOf(UUID targetId) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).targetId().equals(targetId)) {
                return i;
            }
        }
        return -1;
    }

    /** 新增或更新一项。名单已满且是新目标时返回原名单不变（调用方用 {@link #isFull()} 先判断并告知）。 */
    public GuardRules with(GuardEntry entry, Placement placement) {
        int existing = indexOf(entry.targetId());
        List<GuardEntry> next = new ArrayList<>(entries);
        if (existing >= 0) {
            next.remove(existing);
            switch (placement) {
                case FIRST -> next.add(0, entry);
                case LAST -> next.add(entry);
                case KEEP -> next.add(existing, entry);
            }
        } else {
            if (entries.size() >= MAX_ENTRIES) {
                return this;
            }
            if (placement == Placement.FIRST) {
                next.add(0, entry);
            } else {
                next.add(entry);
            }
        }
        return new GuardRules(next);
    }

    public GuardRules without(UUID targetId) {
        List<GuardEntry> next = new ArrayList<>(entries);
        next.removeIf(entry -> entry.targetId().equals(targetId));
        return new GuardRules(next);
    }

    public boolean isFull() {
        return entries.size() >= MAX_ENTRIES;
    }
}
