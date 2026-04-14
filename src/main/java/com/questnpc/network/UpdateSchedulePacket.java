package com.questnpc.network;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.schedule.ScheduleEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * C2S-пакет: обновляет расписание NPC на сервере.
 * Передаёт глобальный флаг включения и до {@link QuestNPCEntity#MAX_SCHEDULE_ENTRIES} записей.
 */
public class UpdateSchedulePacket {

    private final int entityId;
    private final boolean enabled;
    private final List<CompoundTag> entries;

    public UpdateSchedulePacket(int entityId, boolean enabled, List<CompoundTag> entries) {
        this.entityId = entityId;
        this.enabled = enabled;
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    public static void encode(UpdateSchedulePacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeBoolean(packet.enabled);
        int n = Math.min(packet.entries.size(), QuestNPCEntity.MAX_SCHEDULE_ENTRIES);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeNbt(packet.entries.get(i));
        }
    }

    public static UpdateSchedulePacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        boolean enabled = buf.readBoolean();
        int n = Math.min(buf.readVarInt(), QuestNPCEntity.MAX_SCHEDULE_ENTRIES);
        List<CompoundTag> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            CompoundTag tag = buf.readNbt();
            if (tag != null) entries.add(tag);
        }
        return new UpdateSchedulePacket(entityId, enabled, entries);
    }

    public static void handle(UpdateSchedulePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity entity = player.serverLevel().getEntity(packet.entityId);
            if (!(entity instanceof QuestNPCEntity npc)) {
                QuestNPCLogger.warn("UpdateSchedule: NPC {} не найден", packet.entityId);
                return;
            }

            if (!NPCMenuSessionManager.getInstance().isSessionActive(player.getUUID(), packet.entityId)) {
                QuestNPCLogger.warn("UpdateSchedule: нет активной сессии для игрока {} NPC {}",
                        player.getName().getString(), packet.entityId);
                return;
            }

            List<ScheduleEntry> parsed = new ArrayList<>(packet.entries.size());
            for (CompoundTag tag : packet.entries) {
                parsed.add(ScheduleEntry.load(tag));
            }
            npc.setScheduleEnabled(packet.enabled);
            npc.setSchedule(parsed);

            // v2.6.0: рассылаем ScheduleSyncPacket трекинг-игрокам для debug-рендера
            List<CompoundTag> serialized = new ArrayList<>();
            for (ScheduleEntry e : npc.getSchedule()) serialized.add(e.save());
            ModNetwork.INSTANCE.send(
                    PacketDistributor.TRACKING_ENTITY.with(() -> npc),
                    new ScheduleSyncPacket(npc.getId(), npc.isScheduleEnabled(), serialized)
            );

            QuestNPCLogger.info("Игрок {} обновил расписание NPC {}: enabled={}, slots={}",
                    player.getName().getString(), packet.entityId, packet.enabled, parsed.size());
        });
        ctx.get().setPacketHandled(true);
    }
}
