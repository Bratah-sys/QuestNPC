package com.questnpc.network;

import com.questnpc.client.gui.NPCMenuScreen;
import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C-пакет: открывает меню NPC на клиенте.
 * Содержит текущие настройки NPC для отображения в GUI.
 */
public class OpenNPCMenuPacket {

    private final int entityId;
    private final double speed;
    private final int delayMin;
    private final int delayMax;

    public OpenNPCMenuPacket(int entityId, double speed, int delayMin, int delayMax) {
        this.entityId = entityId;
        this.speed = speed;
        this.delayMin = delayMin;
        this.delayMax = delayMax;
    }

    public static void encode(OpenNPCMenuPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeDouble(packet.speed);
        buf.writeVarInt(packet.delayMin);
        buf.writeVarInt(packet.delayMax);
    }

    public static OpenNPCMenuPacket decode(FriendlyByteBuf buf) {
        return new OpenNPCMenuPacket(
                buf.readVarInt(),
                buf.readDouble(),
                buf.readVarInt(),
                buf.readVarInt()
        );
    }

    public static void handle(OpenNPCMenuPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            Entity entity = mc.level.getEntity(packet.entityId);
            if (entity instanceof QuestNPCEntity npc) {
                mc.setScreen(new NPCMenuScreen(npc, packet.speed, packet.delayMin, packet.delayMax));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
