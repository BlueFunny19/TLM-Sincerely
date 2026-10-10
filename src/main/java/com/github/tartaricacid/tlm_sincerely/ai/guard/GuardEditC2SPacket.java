package com.github.tartaricacid.tlm_sincerely.ai.guard;

import com.github.tartaricacid.tlm_sincerely.priority.autowork.network.AutoWorkPermission;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * C2S：守护任务配置界面里的一次编辑。
 *
 * <p>所有操作都以<b>目标 UUID</b> 定位名单项而不是下标：界面上看到的名单可能比服务端旧（模型刚改过），
 * 用下标会改错人。服务端重新校验一切：发送者要能控制这只女仆；新增的目标必须是她的主人，
 * 或同一主人名下的另一只女仆；名单满了就拒绝。
 */
public final class GuardEditC2SPacket {
    public static final int INDEX = 14;
    private static final Logger LOGGER = LoggerFactory.getLogger(GuardEditC2SPacket.class);

    /** 编辑动作。 */
    public enum Op {
        /** 把目标加到名单最后（条件一直守，半径默认）。 */
        ADD,
        REMOVE,
        /** 优先级提高一位。 */
        MOVE_UP,
        /** 优先级降低一位。 */
        MOVE_DOWN,
        /** 修改条件与半径。 */
        SET,
        /** 取消守护任务的时限。 */
        MAKE_PERMANENT
    }

    private final UUID maidId;
    private final Op op;
    private final UUID targetId;
    private final GuardCondition.Kind kind;
    private final double threshold;
    private final double distance;

    public GuardEditC2SPacket(UUID maidId, Op op, UUID targetId, GuardCondition.Kind kind,
                              double threshold, double distance) {
        this.maidId = maidId;
        this.op = op;
        this.targetId = targetId;
        this.kind = kind;
        this.threshold = threshold;
        this.distance = distance;
    }

    public static GuardEditC2SPacket simple(UUID maidId, Op op, UUID targetId) {
        return new GuardEditC2SPacket(maidId, op, targetId, GuardCondition.Kind.ALWAYS, 0.0D, GuardEntry.DEFAULT_DISTANCE);
    }

    public static void encode(GuardEditC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.maidId);
        buf.writeEnum(msg.op);
        buf.writeUUID(msg.targetId);
        buf.writeEnum(msg.kind);
        buf.writeDouble(msg.threshold);
        buf.writeDouble(msg.distance);
    }

    public static GuardEditC2SPacket decode(FriendlyByteBuf buf) {
        return new GuardEditC2SPacket(buf.readUUID(), buf.readEnum(Op.class), buf.readUUID(),
                buf.readEnum(GuardCondition.Kind.class), buf.readDouble(), buf.readDouble());
    }

    public static void handle(GuardEditC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) {
                return;
            }
            EntityMaid maid = AutoWorkPermission.resolveMaid(sender, msg.maidId);
            if (maid == null || !AutoWorkPermission.canControlMaid(sender, maid)) {
                LOGGER.info("[guard] edit refused: player={} maid={} op={}",
                        sender.getName().getString(), msg.maidId, msg.op);
                return;
            }
            String error = msg.apply(sender, maid);
            if (error != null) {
                sender.displayClientMessage(Component.translatable(error), true);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /** 执行编辑；成功返回 null，失败返回要给玩家看的语言键。 */
    @Nullable
    private String apply(ServerPlayer sender, EntityMaid maid) {
        GuardData data = GuardTaskDriver.dataOf(maid);
        GuardRules rules = data.rules();
        GuardData next = data;
        switch (op) {
            case ADD -> {
                if (rules.contains(targetId)) {
                    return null;
                }
                if (rules.isFull()) {
                    return "gui.tlm_sincerely.guard.error.full";
                }
                String name = validTargetName(sender, maid);
                if (name == null) {
                    return "gui.tlm_sincerely.guard.error.bad_target";
                }
                next = data.withRules(rules.with(
                        new GuardEntry(targetId, name, GuardCondition.ALWAYS, GuardEntry.DEFAULT_DISTANCE),
                        GuardRules.Placement.LAST));
            }
            case REMOVE -> next = data.withRules(rules.without(targetId));
            case MOVE_UP -> next = data.withRules(rules.shift(targetId, -1));
            case MOVE_DOWN -> next = data.withRules(rules.shift(targetId, 1));
            case SET -> {
                int index = rules.indexOf(targetId);
                if (index < 0 || Double.isNaN(threshold) || Double.isNaN(distance)) {
                    return null;
                }
                GuardEntry entry = rules.entries().get(index);
                next = data.withRules(rules.with(
                        new GuardEntry(targetId, entry.name(), new GuardCondition(kind, threshold), distance),
                        GuardRules.Placement.KEEP));
            }
            case MAKE_PERMANENT -> next = data.withExpireAt(0L);
        }
        if (!next.equals(data)) {
            GuardTaskDriver.save(maid, next);
            LOGGER.info("[guard] edited from the config screen: maid={} player={} op={}",
                    maid.getUUID(), sender.getName().getString(), op);
            if (GuardTaskDriver.isGuarding(maid)) {
                GuardTaskDriver.reconcile(maid);
            }
        }
        return null;
    }

    /** 目标是主人（在线）或同一主人名下的另一只女仆时返回它的名字，否则 null。 */
    @Nullable
    private String validTargetName(ServerPlayer sender, EntityMaid maid) {
        UUID ownerId = maid.getOwnerUUID();
        if (ownerId == null) {
            return null;
        }
        if (targetId.equals(ownerId)) {
            ServerPlayer owner = sender.server.getPlayerList().getPlayer(ownerId);
            return owner == null ? null : owner.getName().getString();
        }
        EntityMaid other = AutoWorkPermission.resolveMaid(sender, targetId);
        if (other == null || other == maid || !ownerId.equals(other.getOwnerUUID())) {
            return null;
        }
        return other.getName().getString();
    }
}
