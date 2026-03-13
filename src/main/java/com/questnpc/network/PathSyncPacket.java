package com.questnpc.network;

import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C-пакет: синхронизирует узлы навигационного пути NPC с клиентом.
 * Отправляется при смене маршрута, не каждый тик.
 */
public class PathSyncPacket {

    private final int entityId;
    private final List<Vec3> nodes;

    public PathSyncPacket(int entityId, List<Vec3> nodes) {
        this.entityId = entityId;
        this.nodes = nodes;
    }

    public static void encode(PathSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeVarInt(packet.nodes.size());
        for (Vec3 node : packet.nodes) {
            buf.writeDouble(node.x);
            buf.writeDouble(node.y);
            buf.writeDouble(node.z);
        }
    }

    public static PathSyncPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        int count = buf.readVarInt();
        List<Vec3> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            nodes.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
        return new PathSyncPacket(entityId, nodes);
    }

    public static void handle(PathSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            Entity entity = mc.level.getEntity(packet.entityId);
            if (entity instanceof QuestNPCEntity npc) {
                npc.setClientPathNodes(packet.nodes);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
