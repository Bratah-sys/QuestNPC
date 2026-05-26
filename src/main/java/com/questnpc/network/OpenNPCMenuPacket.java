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
 * Содержит текущие настройки NPC для отображения в GUI, включая наборы сделок, расписание, квесты и диалоги.
 */
public class OpenNPCMenuPacket {

    private final int entityId;
    private final double speed;
    private final int delayMin;
    private final int delayMax;
    private final String modelType;
    private final boolean tradingEnabled;
    private final List<QuestNPCEntity.TradeSet> tradeSets;
    private final List<String> lockedTradeSetNames;
    private final boolean scheduleEnabled;
    private final List<CompoundTag> schedule;
    private final ItemStack[] equipment;
    private final boolean questsEnabled;
    private final List<QuestDefinition> quests;

    // === ПОЛЯ ДЛЯ СИСТЕМЫ ДИАЛОГОВ ===
    private final boolean dialoguesEnabled;
    private final String startNodeId;
    private final List<CompoundTag> dialogues;

    public OpenNPCMenuPacket(int entityId, double speed, int delayMin, int delayMax, String modelType,
                             boolean tradingEnabled, List<QuestNPCEntity.TradeSet> tradeSets,
                             List<String> lockedTradeSetNames, boolean scheduleEnabled,
                             List<CompoundTag> schedule, ItemStack[] equipment,
                             boolean questsEnabled, List<QuestDefinition> quests,
                             boolean dialoguesEnabled, String startNodeId, List<CompoundTag> dialogues) {
        this.entityId = entityId;
        this.speed = speed;
        this.delayMin = delayMin;
        this.delayMax = delayMax;
        this.modelType = modelType;
        this.tradingEnabled = tradingEnabled;
        this.tradeSets = tradeSets;
        this.lockedTradeSetNames = lockedTradeSetNames;
        this.scheduleEnabled = scheduleEnabled;
        this.schedule = schedule;
        this.equipment = equipment;
        this.questsEnabled = questsEnabled;
        this.quests = quests;

        // Диалоги
        this.dialoguesEnabled = dialoguesEnabled;
        this.startNodeId = startNodeId;
        this.dialogues = dialogues;
    }

    public static void encode(OpenNPCMenuPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.entityId);
        buf.writeDouble(packet.speed);
        buf.writeInt(packet.delayMin);
        buf.writeInt(packet.delayMax);
        buf.writeUtf(packet.modelType != null ? packet.modelType : "");
        buf.writeBoolean(packet.tradingEnabled);

        // ИСПРАВЛЕНО: Сначала buf, затем список сделок
        UpdateTradeOffersPacket.writeTradeSets(buf, packet.tradeSets);

        buf.writeInt(packet.lockedTradeSetNames.size());
        for (String name : packet.lockedTradeSetNames) {
            buf.writeUtf(name);
        }

        buf.writeBoolean(packet.scheduleEnabled);
        buf.writeInt(packet.schedule.size());
        for (CompoundTag tag : packet.schedule) {
            buf.writeNbt(tag);
        }

        for (int i = 0; i < QuestNPCEntity.EQUIPMENT_SLOTS; i++) {
            buf.writeItem(packet.equipment[i]);
        }

        buf.writeBoolean(packet.questsEnabled);
        // ИСПРАВЛЕНО: Сначала buf, затем список квестов
        UpdateNPCQuestsPacket.writeQuests(buf, packet.quests);

        // Пишем данные диалогов в самый конец буфера
        buf.writeBoolean(packet.dialoguesEnabled);
        buf.writeUtf(packet.startNodeId != null ? packet.startNodeId : "start");
        buf.writeInt(packet.dialogues.size());
        for (CompoundTag tag : packet.dialogues) {
            buf.writeNbt(tag);
        }
    }

    public static OpenNPCMenuPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        double speed = buf.readDouble();
        int delayMin = buf.readInt();
        int delayMax = buf.readInt();
        String model = buf.readUtf();
        boolean trading = buf.readBoolean();

        List<QuestNPCEntity.TradeSet> sets = UpdateTradeOffersPacket.readTradeSets(buf);

        int lockedSize = buf.readInt();
        List<String> lockedNames = new ArrayList<>(lockedSize);
        for (int i = 0; i < lockedSize; i++) {
            lockedNames.add(buf.readUtf());
        }

        boolean scheduleEnabled = buf.readBoolean();
        int scheduleSize = buf.readInt();
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

        // Читаем данные диалогов из буфера
        boolean dialoguesEnabled = buf.readBoolean();
        String startNodeId = buf.readUtf();
        int dialogueSize = buf.readInt();
        List<CompoundTag> dialogues = new ArrayList<>(dialogueSize);
        for (int i = 0; i < dialogueSize; i++) {
            CompoundTag tag = buf.readNbt();
            if (tag != null) dialogues.add(tag);
        }

        return new OpenNPCMenuPacket(entityId, speed, delayMin, delayMax, model, trading, sets,
                lockedNames, scheduleEnabled, schedule, equipment, questsEnabled, quests,
                dialoguesEnabled, startNodeId, dialogues);
    }

    public static void handle(OpenNPCMenuPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            Entity entity = mc.level.getEntity(packet.entityId);
            if (entity instanceof QuestNPCEntity npc) {
                // Передаем параметры диалогов в конструктор экрана
                mc.setScreen(new NPCMenuScreen(npc, packet.speed, packet.delayMin, packet.delayMax,
                        packet.modelType, packet.tradingEnabled, packet.tradeSets,
                        packet.lockedTradeSetNames,
                        packet.scheduleEnabled, packet.schedule, packet.equipment,
                        packet.questsEnabled, packet.quests,
                        packet.dialoguesEnabled, packet.startNodeId, packet.dialogues));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}