package com.github.tartaricacid.tlm_sincerely.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 按 UUID 在所有已加载的维度里找女仆（服务端主线程调用）。
 */
public final class MaidLookup {
    private MaidLookup() {
    }

    /** 找不到（未加载 / 已死亡 / 已移除）时返回 {@code null}。 */
    @Nullable
    public static EntityMaid find(MinecraftServer server, UUID maidId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(maidId);
            if (entity instanceof EntityMaid maid) {
                return maid;
            }
        }
        return null;
    }
}
