package com.questnpc.network;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.schedule.ScheduleEntry;
import com.questnpc.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * C→S-пакет: игрок нажал кнопку-кисть в ScheduleScreen.
 * Сервер валидирует сессию меню, выдаёт игроку предмет {@link com.questnpc.item.PatrolBrushItem}
 * с NBT {TargetNPC, SlotIndex, PaintedBlocks} и открывает paint-сессию.
 *
 * <p>Если {@code startFresh=true} — кисть пустая. Иначе — загружает существующую зону слота.
 */
public class RequestPatrolBrushPacket {

    private final int entityId;
    private final int slotIndex;
    private final boolean startFresh;

    public RequestPatrolBrushPacket(int entityId, int slotIndex, boolean startFresh) {
        this.entityId = entityId;
        this.slotIndex = slotIndex;
        this.startFresh = startFresh;
    }

    public static void encode(RequestPatrolBrushPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entityId);
        buf.writeVarInt(p.slotIndex);
        buf.writeBoolean(p.startFresh);
    }

    public static RequestPatrolBrushPacket decode(FriendlyByteBuf buf) {
        return new RequestPatrolBrushPacket(buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(RequestPatrolBrushPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity entity = player.serverLevel().getEntity(packet.entityId);
            if (!(entity instanceof QuestNPCEntity npc)) {
                QuestNPCLogger.warn("RequestPatrolBrush: NPC {} не найден", packet.entityId);
                return;
            }

            // Валидация сессии меню (меню должно быть только что закрыто — сессия ещё активна)
            if (!NPCMenuSessionManager.getInstance().isSessionActive(player.getUUID(), packet.entityId)) {
                QuestNPCLogger.warn("RequestPatrolBrush: нет активной сессии для игрока {}",
                        player.getName().getString());
                player.sendSystemMessage(Component.translatable("message.questnpc.patrol_brush.no_session")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            List<ScheduleEntry> schedule = npc.getSchedule();
            if (packet.slotIndex < 0 || packet.slotIndex >= schedule.size()) {
                QuestNPCLogger.warn("RequestPatrolBrush: невалидный slotIndex {} (max {})",
                        packet.slotIndex, schedule.size());
                return;
            }
            ScheduleEntry slot = schedule.get(packet.slotIndex);

            // Открываем paint-сессию (отдельно от menu session)
            PatrolPaintSessionManager.getInstance().openSession(
                    player.getUUID(), npc.getUUID(), packet.slotIndex);

            // Закрываем menu-сессию: игрок ушёл из GUI в режим рисования
            NPCMenuSessionManager.getInstance().closeSession(
                    player.getUUID(), player.getName().getString());

            // v2.6.1: если в main-hand — обычная палка (которой игрок открывал меню),
            // заменяем её на кисть прямо в main-hand слоте. Флаг WasStick запомним в NBT,
            // чтобы при финализации/отмене вернуть палку обратно.
            ItemStack mainHand = player.getMainHandItem();
            boolean wasStick = mainHand.is(Items.STICK)
                    && (mainHand.getTagElement("QuestNPC") == null
                        || !mainHand.getTagElement("QuestNPC").getBoolean("PatrolChange"));

            // Создаём предмет
            ItemStack brush = new ItemStack(ModItems.PATROL_BRUSH.get());
            CompoundTag tag = brush.getOrCreateTag();
            tag.putUUID("TargetNPC", npc.getUUID());
            tag.putInt("SlotIndex", packet.slotIndex);
            tag.putBoolean("WasStick", wasStick);
            if (!packet.startFresh && !slot.patrolZone.isEmpty()) {
                long[] packed = new long[slot.patrolZone.size()];
                for (int i = 0; i < slot.patrolZone.size(); i++) {
                    packed[i] = slot.patrolZone.get(i).asLong();
                }
                tag.putLongArray("PaintedBlocks", packed);
            } else {
                tag.putLongArray("PaintedBlocks", new long[0]);
            }

            // Красивое имя
            brush.setHoverName(Component.translatable("item.questnpc.patrol_brush")
                    .withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(false)));

            if (wasStick) {
                // Заменяем палку в main-hand на кисть. Палка будет возвращена позже.
                player.setItemInHand(InteractionHand.MAIN_HAND, brush);
            } else {
                // Main-hand не палка — пытаемся добавить в свободный слот (или дроп)
                if (!player.getInventory().add(brush)) {
                    player.drop(brush, false);
                }
            }

            player.sendSystemMessage(Component.translatable("message.questnpc.patrol_brush.start")
                    .withStyle(ChatFormatting.AQUA));

            QuestNPCLogger.info("Игрок {} получил PatrolBrush для NPC {} slot {} (fresh={})",
                    player.getName().getString(), npc.getId(), packet.slotIndex, packet.startFresh);

            // Закрыть открытый контейнер (если GUI ещё не закрыто)
            player.closeContainer();
        });
        ctx.get().setPacketHandled(true);
    }
}
