package com.questnpc.network;

import com.questnpc.client.gui.NPCMenuScreen;
import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C-пакет: открывает меню NPC на клиенте.
 * Содержит текущие настройки NPC для отображения в GUI, включая наборы сделок и расписание.
 */
public class OpenNPCMenuPacket {

    private final int entityId;
    private final double speed;
    private final int delayMin;
    private final int delayMax;
    private final String modelType;
    private final boolean tradingEnabled;
    private final List<QuestNPCEntity.TradeSet> tradeSets;
    private final boolean scheduleEnabled;
    private final List<CompoundTag> schedule;

    public OpenNPCMenuPacket(int entityId, double speed, int delayMin, int delayMax, String modelType,
                             boolean tradingEnabled, List<QuestNPCEntity.TradeSet> tradeSets,
                             boolean scheduleEnabled, List<CompoundTag> schedule) {
        this.entityId = entityId;
        this.speed = speed;
        this.delayMin = delayMin;
        this.delayMax = delayMax;
        this.modelType = modelType != null ? modelType : "";
        this.tradingEnabled = tradingEnabled;
        this.tradeSets = tradeSets != null ? tradeSets : new ArrayList<>();
        this.scheduleEnabled = scheduleEnabled;
        this.schedule = schedule != null ? schedule : new ArrayList<>();
    }

    public static void encode(OpenNPCMenuPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeDouble(packet.speed);
        buf.writeVarInt(packet.delayMin);
        buf.writeVarInt(packet.delayMax);
        buf.writeUtf(packet.modelType, 256);
        buf.writeBoolean(packet.tradingEnabled);
        UpdateTradeOffersPacket.writeTradeSets(buf, packet.tradeSets);
        buf.writeBoolean(packet.scheduleEnabled);
        int scheduleSize = Math.min(packet.schedule.size(), QuestNPCEntity.MAX_SCHEDULE_ENTRIES);
        buf.writeVarInt(scheduleSize);
        for (int i = 0; i < scheduleSize; i++) {
            buf.writeNbt(packet.schedule.get(i));
        }
    }

    public static OpenNPCMenuPacket decode(FriendlyByteBuf buf) {
        int entityId   = buf.readVarInt();
        double speed   = buf.readDouble();
        int delayMin   = buf.readVarInt();
        int delayMax   = buf.readVarInt();
        String model   = buf.readUtf(256);
        boolean trading = buf.readBoolean();
        List<QuestNPCEntity.TradeSet> sets = UpdateTradeOffersPacket.readTradeSets(buf);
        boolean scheduleEnabled = buf.readBoolean();
        int scheduleSize = Math.min(buf.readVarInt(), QuestNPCEntity.MAX_SCHEDULE_ENTRIES);
        List<CompoundTag> schedule = new ArrayList<>(scheduleSize);
        for (int i = 0; i < scheduleSize; i++) {
            CompoundTag tag = buf.readNbt();
            if (tag != null) schedule.add(tag);
        }
        return new OpenNPCMenuPacket(entityId, speed, delayMin, delayMax, model, trading, sets,
                scheduleEnabled, schedule);
    }

    public static void handle(OpenNPCMenuPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            Entity entity = mc.level.getEntity(packet.entityId);
            if (entity instanceof QuestNPCEntity npc) {
                mc.setScreen(new NPCMenuScreen(npc, packet.speed, packet.delayMin, packet.delayMax,
                        packet.modelType, packet.tradingEnabled, packet.tradeSets,
                        packet.scheduleEnabled, packet.schedule));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
