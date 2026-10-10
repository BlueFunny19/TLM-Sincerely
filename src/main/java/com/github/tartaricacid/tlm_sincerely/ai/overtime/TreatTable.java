package com.github.tartaricacid.tlm_sincerely.ai.overtime;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 加班时女仆愿意吃的"点心"表：物品 id → 吃一个能多撑多少秒（纯逻辑，不依赖 Minecraft 类型）。
 *
 * <p>配置里每条写成 {@code 命名空间:物品=秒数}，例如 {@code minecraft:cake=300}。
 * 格式不对、秒数不是正整数的条目直接忽略（配置是玩家手写的，不能因为一条写错就整个失效）；
 * 同一个物品写了多次时以最后一条为准；秒数最多按 {@link #MAX_SECONDS} 算。
 */
public final class TreatTable {
    /** 单个点心最多能多撑多久（秒）。 */
    public static final int MAX_SECONDS = 3600;

    private static final TreatTable EMPTY = new TreatTable(Collections.emptyMap());

    private final Map<String, Integer> seconds;

    private TreatTable(Map<String, Integer> seconds) {
        this.seconds = seconds;
    }

    public static TreatTable empty() {
        return EMPTY;
    }

    /** 解析配置里的条目；{@code null} 或空集合得到空表。 */
    public static TreatTable parse(@Nullable Collection<? extends String> entries) {
        if (entries == null || entries.isEmpty()) {
            return EMPTY;
        }
        Map<String, Integer> parsed = new LinkedHashMap<>();
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            int eq = entry.lastIndexOf('=');
            if (eq <= 0 || eq == entry.length() - 1) {
                continue;
            }
            String id = entry.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            if (!looksLikeItemId(id)) {
                continue;
            }
            int value;
            try {
                value = Integer.parseInt(entry.substring(eq + 1).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (value <= 0) {
                continue;
            }
            parsed.put(id, Math.min(value, MAX_SECONDS));
        }
        return parsed.isEmpty() ? EMPTY : new TreatTable(Collections.unmodifiableMap(parsed));
    }

    /** 吃一个这个物品能多撑多少秒；不是点心返回 0。 */
    public int secondsFor(@Nullable String itemId) {
        if (itemId == null) {
            return 0;
        }
        return seconds.getOrDefault(itemId.toLowerCase(Locale.ROOT), 0);
    }

    public boolean isEmpty() {
        return seconds.isEmpty();
    }

    /** 所有点心的物品 id（保持配置里的顺序）。 */
    public Set<String> itemIds() {
        return seconds.keySet();
    }

    /** 粗略检查是不是 {@code 命名空间:路径}（只含小写字母、数字、下划线、连字符、点、斜杠）。 */
    private static boolean looksLikeItemId(String id) {
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1 || id.indexOf(':', colon + 1) >= 0) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == '/' || c == ':';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
