package com.github.tartaricacid.tlm_sincerely.ai.tool;

import com.github.tartaricacid.tlm_sincerely.ai.PromptLang;
import com.github.tartaricacid.tlm_sincerely.ai.move.MoveText;
import com.github.tartaricacid.tlm_sincerely.chatbar.MaidFinder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 解析 {@code approach} / {@code retreat} / {@code guard} 的目标：
 * <ul>
 *   <li>{@code me} / {@code 我} / 缺省 → 主人（必须在线且同维度）；</li>
 *   <li>否则按 {@link MaidFinder#matchByName} 的规则在同一主人名下另一只女仆里匹配
 *       （同维度、喊话范围 {@code TargetRange} 内）；</li>
 *   <li>本维度找不到但在其他维度存在同名女仆 → {@code targetOtherDimension}。</li>
 * </ul>
 */
final class MoveTargetResolver {
    /** 目标为"主人"时的写法（英文 + 中文）。 */
    private static final String TARGET_ME = "me";
    private static final String TARGET_ME_ZH = "我";

    private MoveTargetResolver() {
    }

    /** 解析结果：成功时给出 UUID 与展示名，失败时给出已本地化的说明文字。 */
    record Resolution(@Nullable UUID targetId, String display, @Nullable String error) {
        static Resolution ok(UUID targetId, String display) {
            return new Resolution(targetId, display, null);
        }

        static Resolution fail(String error) {
            return new Resolution(null, "", error);
        }
    }

    static Resolution resolve(MinecraftServer server, ServerLevel level, EntityMaid maid,
                              UUID ownerId, @Nullable String rawTarget) {
        boolean zh = PromptLang.isChinese(PromptLang.of(maid));
        String name = rawTarget == null ? "" : rawTarget.trim();
        if (name.isEmpty() || name.equalsIgnoreCase(TARGET_ME) || name.equals(TARGET_ME_ZH)) {
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
            if (owner == null || owner.level() != level) {
                return Resolution.fail(MoveText.ownerUnavailable(zh));
            }
            return Resolution.ok(ownerId, owner.getName().getString());
        }
        if (name.equalsIgnoreCase(maid.getName().getString())) {
            return Resolution.fail(MoveText.selfTarget(zh));
        }

        List<EntityMaid> candidates = MaidFinder.getOwnedMaidsOfSameOwner(maid, MaidFinder.targetRange(), maid);
        List<EntityMaid> matches = MaidFinder.matchByName(candidates, name, maid);
        EntityMaid target = MaidFinder.findNearest(level, maid, matches);
        if (target != null) {
            return Resolution.ok(target.getUUID(), target.getName().getString());
        }
        if (existsInOtherDimension(server, maid, ownerId, name)) {
            return Resolution.fail(MoveText.targetOtherDimension(zh, name));
        }
        List<String> available = new ArrayList<>(candidates.size());
        for (EntityMaid candidate : candidates) {
            available.add(candidate.getName().getString());
        }
        return Resolution.fail(MoveText.targetNotFound(zh, name, available));
    }

    /** 其他维度（不含本维度）里是否有同主人、同名匹配的女仆——只用于给出"跨维度"的准确提示。 */
    private static boolean existsInOtherDimension(MinecraftServer server, EntityMaid maid,
                                                  UUID ownerId, String name) {
        for (ServerLevel other : server.getAllLevels()) {
            if (other == maid.level()) {
                continue;
            }
            List<EntityMaid> candidates = new ArrayList<>();
            for (Entity entity : other.getAllEntities()) {
                if (entity instanceof EntityMaid candidate && candidate != maid && candidate.isAlive()
                        && ownerId.equals(candidate.getOwnerUUID())) {
                    candidates.add(candidate);
                }
            }
            if (!MaidFinder.matchByName(candidates, name, maid).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
