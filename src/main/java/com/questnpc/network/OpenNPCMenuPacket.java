package com.questnpc.network;

import com.questnpc.client.gui.NPCMenuScreen;
import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C-пакет: открывает меню NPC на клиенте.
 * Содержит текущие настройки NPC для отображения в GUI.
 */
public class OpenNPCMenuPacket {

    private static final int MAX_OFFERS = 10;

    private final int entityId;
    private final double speed;
    private final int delayMin;
    private final int delayMax;
    private final String modelType;
    private final boolean tradingEnabled;
    private final ListTag tradeOffers;

    public OpenNPCMenuPacket(int entityId, double speed, int delayMin, int delayMax, String modelType,
                             boolean tradingEnabled, ListTag tradeOffers) {
        this.entityId = entityId;
        this.speed = speed;
        this.delayMin = delayMin;
        this.delayMax = delayMax;
        this.modelType = modelType != null ? modelType : "";
        this.tradingEnabled = tradingEnabled;
        this.tradeOffers = tradeOffers != null ? tradeOffers : new ListTag();
    }

    public static void encode(OpenNPCMenuPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeDouble(packet.speed);
        buf.writeVarInt(packet.delayMin);
        buf.writeVarInt(packet.delayMax);
        buf.writeUtf(packet.modelType, 256);
        buf.writeBoolean(packet.tradingEnabled);
        int size = Math.min(packet.tradeOffers.size(), MAX_OFFERS);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            buf.writeNbt(packet.tradeOffers.getCompound(i));
        }
    }

    public static OpenNPCMenuPacket decode(FriendlyByteBuf buf) {
        int entityId   = buf.readVarInt();
        double speed   = buf.readDouble();
        int delayMin   = buf.readVarInt();
        int delayMax   = buf.readVarInt();
        String model   = buf.readUtf(256);
        boolean trading = buf.readBoolean();
        ListTag offers = new ListTag();
        int size = Math.min(buf.readVarInt(), MAX_OFFERS);
        for (int i = 0; i < size; i++) {
            CompoundTag tag = buf.readNbt();
            if (tag != null) offers.add(tag);
        }
        return new OpenNPCMenuPacket(entityId, speed, delayMin, delayMax, model, trading, offers);
    }

    public static void handle(OpenNPCMenuPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            Entity entity = mc.level.getEntity(packet.entityId);
            if (entity instanceof QuestNPCEntity npc) {
                mc.setScreen(new NPCMenuScreen(npc, packet.speed, packet.delayMin, packet.delayMax,
                        packet.modelType, packet.tradingEnabled, packet.tradeOffers));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
