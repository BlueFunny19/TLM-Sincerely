package com.github.tartaricacid.tlm_sincerely.chatbar;

import com.github.tartaricacid.tlm_sincerely.config.subconfig.ChatBarConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class MaidFinder {
    /** 玩家“喊话”能传多远（格），由配置 {@code chatbar.TargetRange} 决定。必须在配置加载后调用。 */
    public static double targetRange() {
        return ChatBarConfig.TARGET_RANGE.get();
    }

    public static List<EntityMaid> getOwnedMaids(ServerPlayer player) {
        return getOwnedMaids(player, targetRange());
    }

    /** 玩家名下、存活、与玩家直线距离不超过 {@code range} 格（同维度）的女仆。 */
    public static List<EntityMaid> getOwnedMaids(ServerPlayer player, double range) {
        double rangeSqr = range * range;
        return player.level().getEntitiesOfClass(EntityMaid.class,
                player.getBoundingBox().inflate(range),
                maid -> maid.isOwnedBy(player) && maid.isAlive() && maid.distanceToSqr(player) <= rangeSqr
        );
    }

    public static FindResult findByName(ServerPlayer player, String name) {
        List<EntityMaid> matches = matchByName(getOwnedMaids(player), name, null);
        if (matches.isEmpty()) {
            return new FindResult(null, 0);
        }
        return new FindResult(findNearest(player, matches), matches.size());
    }

    /**
     * 按名字列表逐个解析（沿用 {@link #findByName} 的匹配规则），可选追加“范围内全部女仆”。
     *
     * <p>解析结果保序去重；超过 {@code maxTargets} 时只保留距离玩家最近的前 N 只，
     * 并把 {@link MultiFindResult#truncated()} 置位，由调用方提示玩家。
     *
     * @param names      名字列表，可为空（此时只有 {@code selectAll} 会命中）
     * @param selectAll  是否把喊话范围（{@link #targetRange}）内属于该玩家的全部女仆加入结果（@全体）
     * @param maxTargets 结果数量上限，必须 ≥ 1
     * @param player     用于解析名字与计算距离的玩家
     */
    public static MultiFindResult findByNames(ServerPlayer player, List<String> names,
                                              boolean selectAll, int maxTargets) {
        List<EntityMaid> owned = getOwnedMaids(player);
        List<EntityMaid> resolved = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();

        if (names != null) {
            for (String name : names) {
                if (name == null || name.isEmpty()) {
                    continue;
                }
                List<EntityMaid> matches = matchByName(owned, name, null);
                if (matches.isEmpty()) {
                    missing.add(name);
                    continue;
                }
                EntityMaid nearest = findNearest(player, matches);
                if (nearest != null && seen.add(nearest.getUUID())) {
                    resolved.add(nearest);
                }
            }
        }

        if (selectAll) {
            for (EntityMaid maid : owned) {
                if (seen.add(maid.getUUID())) {
                    resolved.add(maid);
                }
            }
        }

        int max = Math.max(1, maxTargets);
        boolean truncated = resolved.size() > max;
        if (truncated) {
            List<EntityMaid> sorted = new ArrayList<>(resolved);
            sorted.sort(comparator(player));
            resolved = new ArrayList<>(sorted.subList(0, max));
        }
        return new MultiFindResult(resolved, missing, truncated);
    }

    /**
     * 在一批候选女仆里按名字匹配：先全等集合、为空则子串集合，最后取最近的一只。
     *
     * @param candidates 候选（应已是同一主人、存活、同维度的女仆）
     * @param name       目标名字
     * @param exclude    需要排除的女仆（通常是调用者自己），可为 null
     */
    public static List<EntityMaid> matchByName(List<EntityMaid> candidates, String name,
                                               @Nullable EntityMaid exclude) {
        if (candidates == null || candidates.isEmpty() || name == null) {
            return List.of();
        }
        String lowerName = name.toLowerCase().trim();
        if (lowerName.isEmpty()) {
            return List.of();
        }

        List<EntityMaid> exactMatches = new ArrayList<>();
        List<EntityMaid> fuzzyMatches = new ArrayList<>();
        for (EntityMaid maid : candidates) {
            if (maid == null || maid == exclude || !maid.isAlive()) {
                continue;
            }
            String maidName = maid.getName().getString().toLowerCase();
            if (maidName.equals(lowerName)) {
                exactMatches.add(maid);
            } else if (maidName.contains(lowerName)) {
                fuzzyMatches.add(maid);
            }
        }
        return exactMatches.isEmpty() ? fuzzyMatches : exactMatches;
    }

    /**
     * 收集与 {@code source} 同一主人、同一维度、存活且在给定范围内的女仆。
     *
     * <p>供不持有 {@link ServerPlayer} 的调用方（如 AI 工具）使用：按主人 UUID 过滤，
     * 而不是按在线玩家对象。
     *
     * @param source  参照女仆（决定查询中心与主人）
     * @param range   以 {@code source} 为中心的查询半径
     * @param exclude 需要排除的女仆（通常是 {@code source} 自己），可为 null
     */
    public static List<EntityMaid> getOwnedMaidsOfSameOwner(EntityMaid source, double range,
                                                            @Nullable EntityMaid exclude) {
        if (source == null || !(source.level() instanceof ServerLevel level)) {
            return List.of();
        }
        UUID ownerUuid = source.getOwnerUUID();
        if (ownerUuid == null) {
            return List.of();
        }
        double rangeSqr = range * range;
        return level.getEntitiesOfClass(EntityMaid.class,
                source.getBoundingBox().inflate(range),
                maid -> maid != source
                        && maid != exclude
                        && maid.isAlive()
                        && ownerUuid.equals(maid.getOwnerUUID())
                        && maid.distanceToSqr(source) <= rangeSqr);
    }

    @Nullable
    public static EntityMaid findByUuid(ServerPlayer player, String uuidString) {
        try {
            UUID uuid = UUID.fromString(uuidString);
            for (EntityMaid maid : getOwnedMaids(player)) {
                if (maid.getUUID().equals(uuid)) {
                    return maid;
                }
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        return null;
    }

    public static EntityMaid findNearest(ServerPlayer player) {
        return findNearest(player, getOwnedMaids(player));
    }

    public static EntityMaid findNearest(ServerPlayer player, List<EntityMaid> maids) {
        if (maids.isEmpty()) {
            return null;
        }

        return maids.stream()
                .min(comparator(player))
                .orElse(null);
    }

    private static Comparator<EntityMaid> comparator(ServerPlayer player) {
        return Comparator
                .comparing((EntityMaid m) -> m.isHomeModeEnable())
                .thenComparingDouble(m -> m.distanceToSqr(player));
    }

    /**
     * 以另一只女仆为参照取最近的一只（供 AI 工具使用：不持有 {@link ServerPlayer}）。
     * 优先非 Home 模式，再比距离，与 {@link #findNearest(ServerPlayer, List)} 规则一致。
     */
    @Nullable
    public static EntityMaid findNearest(Level level, EntityMaid reference, List<EntityMaid> maids) {
        if (reference == null || maids == null || maids.isEmpty()) {
            return null;
        }
        return maids.stream()
                .filter(maid -> maid != null && maid.isAlive() && maid.level() == level)
                .min(Comparator
                        .comparing((EntityMaid m) -> m.isHomeModeEnable())
                        .thenComparingDouble(reference::distanceToSqr))
                .orElse(null);
    }

    public record FindResult(@Nullable EntityMaid maid, int matchCount) {
        public boolean hasMaid() {
            return maid != null;
        }

        public boolean hasMultipleMatches() {
            return matchCount > 1;
        }
    }

    /**
     * @param maids     命中的女仆（保序、已去重；截断时按距离从近到远）
     * @param missing   没有找到对应女仆的名字（保序）
     * @param truncated 是否因为超过数量上限而只保留了最近的若干只
     */
    public record MultiFindResult(List<EntityMaid> maids, List<String> missing, boolean truncated) {
    }
}
