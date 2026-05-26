package com.questnpc.network;

import com.questnpc.client.gui.NPCMenuScreen;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.quest.QuestDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C-пакет: открывает меню NPC на клиенте.
 * Содержит текущие настройки NPC для отображения в GUI, включая наборы сделок, расписание и квесты.
 *
 * <p>v2.9.1: добавлена секция Quests (questsEnabled + List&lt;QuestDefinition&gt;) в конце payload
 * по аналогии с Equipment-секцией v2.8.0. Сериализация делегирована
 * {@link UpdateNPCQuestsPacket#writeQuests} / {@link UpdateNPCQuestsPacket#readQuests}.
 */
public class OpenNPCMenuPacket {

    private final int entityId;
    private final double speed;
    private final int delayMin;
    private final int delayMax;
    private final String modelType;
    private final boolean tradingEnabled;
    private final List<QuestNPCEntity.TradeSet> tradeSets;
    private final List<String> lockedTradeSetNames; // v2.9.5
    private final boolean scheduleEnabled;
    private final List<CompoundTag> schedule;
    private final ItemStack[] equipment; // v2.8.0: снимок экипировки длиной EQUIPMENT_SLOTS
    private final boolean questsEnabled; // v2.9.1
    private final List<QuestDefinition> quests; // v2.9.1

    public OpenNPCMenuPacket(int entityId, double speed, int delayMin, int delayMax, String modelType,
                             boolean tradingEnabled, List<QuestNPCEntity.TradeSet> tradeSets,
                             List<String> lockedTradeSetNames,
                             boolean scheduleEnabled, List<CompoundTag> schedule,
                             ItemStack[] equipment,
                             boolean questsEnabled, List<QuestDefinition> quests) {
        this.entityId = entityId;
        this.speed = speed;
        this.delayMin = delayMin;
        this.delayMax = delayMax;
        this.modelType = modelType != null ? modelType : "";
        this.tradingEnabled = tradingEnabled;
        this.tradeSets = tradeSets != null ? tradeSets : new ArrayList<>();
        this.lockedTradeSetNames = lockedTradeSetNames != null ? lockedTradeSetNames : new ArrayList<>();
        this.scheduleEnabled = scheduleEnabled;
        this.schedule = schedule != null ? schedule : new ArrayList<>();
        this.equipment = (equipment != null && equipment.length == QuestNPCEntity.EQUIPMENT_SLOTS)
                ? equipment
                : new ItemStack[]{ ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };
        this.questsEnabled = questsEnabled;
        this.quests = quests != null ? quests : new ArrayList<>();
    }

    public static void encode(OpenNPCMenuPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeDouble(packet.speed);
        buf.writeVarInt(packet.delayMin);
        buf.writeVarInt(packet.delayMax);
        buf.writeUtf(packet.modelType, 256);
        buf.writeBoolean(packet.tradingEnabled);
        UpdateTradeOffersPacket.writeTradeSets(buf, packet.tradeSets);
        UpdateTradeOffersPacket.writeLockedNames(buf, packet.lockedTradeSetNames);
        buf.writeBoolean(packet.scheduleEnabled);
        int scheduleSize = Math.min(packet.schedule.size(), QuestNPCEntity.MAX_SCHEDULE_ENTRIES);
        buf.writeVarInt(scheduleSize);
        for (int i = 0; i < scheduleSize; i++) {
            buf.writeNbt(packet.schedule.get(i));
        }
        // v2.8.0: экипировка (4 ItemStack)
        for (int i = 0; i < QuestNPCEntity.EQUIPMENT_SLOTS; i++) {
            buf.writeItem(packet.equipment[i]);
        }
        // v2.9.1: квесты (questsEnabled + список QuestDefinition)
        buf.writeBoolean(packet.questsEnabled);
        UpdateNPCQuestsPacket.writeQuests(buf, packet.quests);
    }

    public static OpenNPCMenuPacket decode(FriendlyByteBuf buf) {
        int entityId   = buf.readVarInt();
        double speed   = buf.readDouble();
        int delayMin   = buf.readVarInt();
        int delayMax   = buf.readVarInt();
        String model   = buf.readUtf(256);
        boolean trading = buf.readBoolean();
        List<QuestNPCEntity.TradeSet> sets = UpdateTradeOffersPacket.readTradeSets(buf);
        List<String> lockedNames = UpdateTradeOffersPacket.readLockedNames(buf);
        boolean scheduleEnabled = buf.readBoolean();
        int scheduleSize = Math.min(buf.readVarInt(), QuestNPCEntity.MAX_SCHEDULE_ENTRIES);
        List<CompoundTag> schedule = new ArrayList<>(scheduleSize);
        for (int i = 0; i < scheduleSize; i++) {
            CompoundTag tag = buf.readNbt();
            if (tag != null) schedule.add(tag);
        }
        ItemStack[] equipment = new ItemStack[QuestNPCEntity.EQUIPMENT_SLOTS];
        for (int i = 0; i < QuestNPCEntity.EQUIPMENT_SLOTS; i++) {
            equipment[i] = buf.readItem();
        }
        boolean questsEnabled = buf.readBoolean();
        List<QuestDefinition> quests = UpdateNPCQuestsPacket.readQuests(buf);
        return new OpenNPCMenuPacket(entityId, speed, delayMin, delayMax, model, trading, sets,
                lockedNames, scheduleEnabled, schedule, equipment, questsEnabled, quests);
    }

    public static void handle(OpenNPCMenuPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            Entity entity = mc.level.getEntity(packet.entityId);
            if (entity instanceof QuestNPCEntity npc) {
                mc.setScreen(new NPCMenuScreen(npc, packet.speed, packet.delayMin, packet.delayMax,
                        packet.modelType, packet.tradingEnabled, packet.tradeSets,
                        packet.lockedTradeSetNames,
                        packet.scheduleEnabled, packet.schedule, packet.equipment,
                        packet.questsEnabled, packet.quests));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
