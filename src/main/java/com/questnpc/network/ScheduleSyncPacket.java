package com.questnpc.network;

import com.questnpc.QuestNPCLogger;
import com.questnpc.client.debug.NPCDebugRenderer;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.schedule.ScheduleEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S→C-пакет: синхронизирует расписание NPC на клиент для визуализации через /npc_vis.
 * Отправляется при:
 *  - {@link net.minecraftforge.event.entity.player.PlayerEvent.StartTracking} — начало трекинга NPC
 *  - Успешном {@link UpdateSchedulePacket#handle} — после изменения расписания игроком
 *
 * <p>Кэш зон в {@link NPCDebugRenderer} инвалидируется при получении пакета.
 */
public class ScheduleSyncPacket {

    private final int entityId;
    private final boolean enabled;
    private final List<CompoundTag> entries;

    public ScheduleSyncPacket(int entityId, boolean enabled, List<CompoundTag> entries) {
        this.entityId = entityId;
        this.enabled = enabled;
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    public static void encode(ScheduleSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeBoolean(packet.enabled);
        int n = Math.min(packet.entries.size(), QuestNPCEntity.MAX_SCHEDULE_ENTRIES);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeNbt(packet.entries.get(i));
        }
    }

    public static ScheduleSyncPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        boolean enabled = buf.readBoolean();
        int n = Math.min(buf.readVarInt(), QuestNPCEntity.MAX_SCHEDULE_ENTRIES);
        List<CompoundTag> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            CompoundTag tag = buf.readNbt();
            if (tag != null) entries.add(tag);
        }
        return new ScheduleSyncPacket(entityId, enabled, entries);
    }

    public static void handle(ScheduleSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            Entity entity = mc.level.getEntity(packet.entityId);
            if (!(entity instanceof QuestNPCEntity npc)) return;

            List<ScheduleEntry> parsed = new ArrayList<>(packet.entries.size());
            for (CompoundTag tag : packet.entries) {
                parsed.add(ScheduleEntry.load(tag));
            }
            npc.setClientSchedule(parsed);
            npc.setClientScheduleEnabled(packet.enabled);

            // Инвалидируем кэш зон расписания, чтобы renderer пересобрал их из новых данных
            NPCDebugRenderer.invalidateScheduleZones(packet.entityId);

            QuestNPCLogger.debug("ScheduleSync: NPC {} обновлён: enabled={}, slots={}",
                    packet.entityId, packet.enabled, parsed.size());
        });
        ctx.get().setPacketHandled(true);
    }
}
