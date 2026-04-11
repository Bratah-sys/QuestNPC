package com.questnpc.network;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S-пакет: запрос на смену точки патруля NPC.
 * Выдаёт игроку спец-палку для назначения новой точки.
 */
public class RequestPatrolChangePacket {

    private final int entityId;

    public RequestPatrolChangePacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(RequestPatrolChangePacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
    }

    public static RequestPatrolChangePacket decode(FriendlyByteBuf buf) {
        return new RequestPatrolChangePacket(buf.readVarInt());
    }

    public static void handle(RequestPatrolChangePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Entity entity = player.serverLevel().getEntity(packet.entityId);
            if (!(entity instanceof QuestNPCEntity npc)) return;

            // Проверка серверной сессии (v2.5.4, BUG-002)
            if (!NPCMenuSessionManager.getInstance().isSessionActive(player.getUUID(), packet.entityId)) {
                QuestNPCLogger.warn("Игрок {} не имеет активной сессии для NPC {} — пакет RequestPatrolChange отклонён",
                        player.getName().getString(), packet.entityId);
                return;
            }

            // Проверка: в руке палка
            ItemStack heldItem = player.getMainHandItem();
            if (!heldItem.is(Items.STICK)) return;

            QuestNPCLogger.info("Игрок {} запросил смену точки для NPC {}", player.getName().getString(), npc.getId());

            // Уменьшаем стек палок на 1
            heldItem.shrink(1);

            // Создаём спец-палку
            ItemStack patrolStick = new ItemStack(Items.STICK);
            CompoundTag tag = patrolStick.getOrCreateTagElement("QuestNPC");
            tag.putBoolean("PatrolChange", true);
            tag.putUUID("TargetNPC", npc.getUUID());
            patrolStick.setHoverName(Component.translatable("item.questnpc.patrol_change_stick")
                    .withStyle(style -> style.withColor(ChatFormatting.GOLD).withItalic(false)));
            // Визуальный эффект зачарования (уровень 0 — не даёт реального эффекта, только блеск)
            patrolStick.enchant(Enchantments.UNBREAKING, 1);
            patrolStick.hideTooltipPart(ItemStack.TooltipPart.ENCHANTMENTS);

            // Выдаём в руку
            if (!player.getInventory().add(patrolStick)) {
                player.drop(patrolStick, false);
            }

            // Сообщение
            player.sendSystemMessage(Component.translatable("message.questnpc.patrol_stick_given"));

            QuestNPCLogger.info("Спец-палка 'Смена точки' выдана игроку {} для NPC {}",
                    player.getName().getString(), npc.getId());

            // Закрыть меню
            player.closeContainer();
        });
        ctx.get().setPacketHandled(true);
    }
}
