package com.questnpc.network;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.schedule.ScheduleEntry;
import com.questnpc.item.PatrolBrushItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * C→S-пакет: игрок завершает рисование зоны (ЛКМ с кистью в руке).
 * Сервер берёт данные из paint-сессии, применяет зону к соответствующему слоту расписания,
 * удаляет кисть из инвентаря и рассылает {@link ScheduleSyncPacket} трекинг-игрокам.
 */
public class FinishPatrolPaintPacket {

    public FinishPatrolPaintPacket() {}

    public static void encode(FinishPatrolPaintPacket p, FriendlyByteBuf buf) {
        // Нет полей — состояние в paint-session и NBT предмета
    }

    public static FinishPatrolPaintPacket decode(FriendlyByteBuf buf) {
        return new FinishPatrolPaintPacket();
    }

    public static void handle(FinishPatrolPaintPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            PatrolPaintSessionManager.PaintSession session =
                    PatrolPaintSessionManager.getInstance().getSession(player.getUUID());
            if (session == null) {
                QuestNPCLogger.warn("FinishPatrolPaint: нет активной paint-сессии для игрока {}",
                        player.getName().getString());
                removeAnyBrush(player);
                return;
            }

            ServerLevel level = player.serverLevel();
            Entity entity = level.getEntity(session.npcUuid);
            if (!(entity instanceof QuestNPCEntity npc)) {
                player.sendSystemMessage(Component.translatable("message.questnpc.patrol_brush.npc_not_found")
                        .withStyle(ChatFormatting.RED));
                PatrolPaintSessionManager.getInstance().closeSession(player.getUUID());
                removeAnyBrush(player);
                return;
            }

            // Ищем кисть в инвентаре
            int brushSlot = findBrushSlot(player);
            if (brushSlot < 0) {
                QuestNPCLogger.warn("FinishPatrolPaint: кисть не найдена у игрока {}",
                        player.getName().getString());
                PatrolPaintSessionManager.getInstance().closeSession(player.getUUID());
                return;
            }
            ItemStack brush = player.getInventory().getItem(brushSlot);

            // Извлекаем блоки из NBT предмета
            CompoundTag tag = brush.getTag();
            long[] packed = tag != null ? tag.getLongArray("PaintedBlocks") : new long[0];
            List<BlockPos> zone = new ArrayList<>(packed.length);
            for (long l : packed) zone.add(BlockPos.of(l));

            List<ScheduleEntry> schedule = npc.getSchedule();
            if (session.slotIndex < 0 || session.slotIndex >= schedule.size()) {
                QuestNPCLogger.warn("FinishPatrolPaint: слот {} вне диапазона [0..{})",
                        session.slotIndex, schedule.size());
                PatrolPaintSessionManager.getInstance().closeSession(player.getUUID());
                brush.setCount(0);
                return;
            }
            ScheduleEntry slot = schedule.get(session.slotIndex);

            if (zone.isEmpty()) {
                player.sendSystemMessage(Component.translatable("message.questnpc.patrol_brush.empty_zone")
                        .withStyle(ChatFormatting.YELLOW));
                PatrolPaintSessionManager.getInstance().closeSession(player.getUUID());
                PatrolBrushItem.replaceWithStickOrRemove(player, brush, brushSlot);
                return;
            }

            slot.patrolZone.clear();
            slot.patrolZone.addAll(zone);

            // Рассылаем обновление всем трекинг-игрокам
            List<CompoundTag> serialized = new ArrayList<>();
            for (ScheduleEntry e : schedule) serialized.add(e.save());
            ModNetwork.INSTANCE.send(
                    PacketDistributor.TRACKING_ENTITY.with(() -> npc),
                    new ScheduleSyncPacket(npc.getId(), npc.isScheduleEnabled(), serialized)
            );

            player.sendSystemMessage(Component.translatable("message.questnpc.patrol_brush.finished", zone.size())
                    .withStyle(ChatFormatting.GREEN));
            QuestNPCLogger.info("Игрок {} сохранил зону патруля для NPC {} slot {}: {} блоков",
                    player.getName().getString(), npc.getId(), session.slotIndex, zone.size());

            PatrolPaintSessionManager.getInstance().closeSession(player.getUUID());
            PatrolBrushItem.replaceWithStickOrRemove(player, brush, brushSlot);
        });
        ctx.get().setPacketHandled(true);
    }

    private static int findBrushSlot(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() instanceof PatrolBrushItem) return i;
        }
        return -1;
    }

    private static void removeAnyBrush(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() instanceof PatrolBrushItem) {
                PatrolBrushItem.replaceWithStickOrRemove(player, s, i);
            }
        }
    }
}
