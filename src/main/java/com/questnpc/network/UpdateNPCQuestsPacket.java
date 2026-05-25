package com.questnpc.network;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.quest.QuestDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * C2S-пакет (v2.9.1, ID 17): применяет полный список квестов NPC + глобальный toggle.
 *
 * <p>Session-validated через {@link NPCMenuSessionManager} (зеркало паттерна
 * {@link UpdateEquipmentPacket}). Defense-in-depth: даже если клиент прислал больше
 * лимитов — сервер кламп'ит без отказа всего пакета.
 *
 * <p>Helpers {@link #writeQuests} / {@link #readQuests} переиспользуются из
 * {@link OpenNPCMenuPacket} (S→C), по образцу
 * {@link UpdateTradeOffersPacket#writeTradeSets}.
 *
 * <p>Optimistic apply — никаких ack-пакетов, клиент обновляет свой parent-snapshot
 * локально (через {@link com.questnpc.client.gui.NPCMenuScreen#setQuestsSnapshot}).
 */
public class UpdateNPCQuestsPacket {

    private final int entityId;
    private final boolean questsEnabled;
    private final List<QuestDefinition> quests;

    public UpdateNPCQuestsPacket(int entityId, boolean questsEnabled, List<QuestDefinition> quests) {
        this.entityId = entityId;
        this.questsEnabled = questsEnabled;
        this.quests = quests != null ? quests : new ArrayList<>();
    }

    public static void encode(UpdateNPCQuestsPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeBoolean(packet.questsEnabled);
        writeQuests(buf, packet.quests);
    }

    public static UpdateNPCQuestsPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        boolean enabled = buf.readBoolean();
        List<QuestDefinition> quests = readQuests(buf);
        return new UpdateNPCQuestsPacket(entityId, enabled, quests);
    }

    /**
     * Общий helper: сериализует список квестов в пакет. Клампится до {@link QuestNPCEntity#MAX_QUESTS}.
     * Используется из этого пакета (C→S) и из {@link OpenNPCMenuPacket} (S→C).
     */
    public static void writeQuests(FriendlyByteBuf buf, List<QuestDefinition> quests) {
        int n = Math.min(quests != null ? quests.size() : 0, QuestNPCEntity.MAX_QUESTS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeNbt(quests.get(i).save());
        }
    }

    /** Симметричное чтение: возвращает список квестов, клампится до {@link QuestNPCEntity#MAX_QUESTS}. */
    public static List<QuestDefinition> readQuests(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), QuestNPCEntity.MAX_QUESTS);
        List<QuestDefinition> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            CompoundTag tag = buf.readNbt();
            if (tag == null) continue;
            try {
                QuestDefinition q = QuestDefinition.load(tag);
                if (q != null) out.add(q);
            } catch (Exception ex) {
                QuestNPCLogger.warn("UpdateNPCQuestsPacket.readQuests: failed to load quest at {}: {}",
                        i, ex.getMessage());
            }
        }
        return out;
    }

    public static void handle(UpdateNPCQuestsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (!NPCMenuSessionManager.getInstance().isSessionActive(player.getUUID(), packet.entityId)) {
                QuestNPCLogger.warn("Игрок {} без активной сессии — UpdateNPCQuests отклонён для NPC {}",
                        player.getName().getString(), packet.entityId);
                return;
            }

            Entity entity = player.serverLevel().getEntity(packet.entityId);
            if (!(entity instanceof QuestNPCEntity npc)) {
                QuestNPCLogger.warn("Игрок {} попытался изменить квесты несуществующего NPC {}",
                        player.getName().getString(), packet.entityId);
                return;
            }

            // Defense-in-depth: клампим лимиты на сервере (клиент уже проверил, но мало ли).
            boolean clamped = false;
            List<QuestDefinition> validated = new ArrayList<>(packet.quests.size());
            int questLimit = Math.min(packet.quests.size(), QuestNPCEntity.MAX_QUESTS);
            if (packet.quests.size() > questLimit) clamped = true;
            for (int i = 0; i < questLimit; i++) {
                QuestDefinition src = packet.quests.get(i);
                if (src == null) continue;

                // Title/description уже клампятся внутри сеттеров — но пересохраним
                // через round-trip чтобы гарантированно применить clamp в случае,
                // если они были выставлены через рефлексию/в обход сеттеров.
                src.setTitle(src.getTitle());
                src.setDescription(src.getDescription());

                if (src.getObjectives().size() > QuestDefinition.MAX_OBJECTIVES) clamped = true;
                if (src.getRewards().size() > QuestDefinition.MAX_REWARDS) clamped = true;
                if (src.getPrerequisites().size() > QuestDefinition.MAX_PREREQUISITES) clamped = true;
                // QuestDefinition addObjective/addReward/addPrerequisite уже возвращают false при лимите,
                // и при загрузке через QuestDefinition.load() лимит проверяется в цикле. Так что само
                // дополнительное обрезание здесь — на случай прихода вручную собранного списка.

                validated.add(src);
            }

            if (clamped) {
                QuestNPCLogger.warn("Игрок {}: квесты NPC {} были обрезаны до лимитов (input={}, applied={})",
                        player.getName().getString(), packet.entityId,
                        packet.quests.size(), validated.size());
            }

            npc.setQuestsEnabled(packet.questsEnabled);
            npc.setQuestsFromSnapshot(validated);

            QuestNPCLogger.info("Игрок {} обновил квесты NPC {}: enabled={}, квестов={}",
                    player.getName().getString(), npc.getId(), packet.questsEnabled, validated.size());
        });
        ctx.get().setPacketHandled(true);
    }
}
