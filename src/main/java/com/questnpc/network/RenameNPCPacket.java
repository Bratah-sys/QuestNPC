package com.questnpc.network;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S-пакет: переименование NPC.
 * Клиент отправляет entityId и новое имя, сервер применяет setCustomName.
 */
public class RenameNPCPacket {

    private final int entityId;
    private final String newName;

    public RenameNPCPacket(int entityId, String newName) {
        this.entityId = entityId;
        this.newName = newName;
    }

    public static void encode(RenameNPCPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeUtf(packet.newName, 32);
    }

    public static RenameNPCPacket decode(FriendlyByteBuf buf) {
        return new RenameNPCPacket(buf.readVarInt(), buf.readUtf(32));
    }

    public static void handle(RenameNPCPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity entity = player.serverLevel().getEntity(packet.entityId);
            if (!(entity instanceof QuestNPCEntity npc)) {
                QuestNPCLogger.warn("Игрок {} попытался переименовать несуществующего NPC {}",
                        player.getName().getString(), packet.entityId);
                return;
            }

            // Проверка серверной сессии (заменяет проверку расстояния)
            if (!NPCMenuSessionManager.getInstance().isSessionActive(player.getUUID(), packet.entityId)) {
                QuestNPCLogger.warn("Игрок {} не имеет активной сессии для NPC {} — пакет Rename отклонён",
                        player.getName().getString(), packet.entityId);
                return;
            }

            // Валидация имени
            String name = packet.newName.trim();
            if (name.isEmpty() || name.length() > 32) {
                QuestNPCLogger.warn("Невалидное имя от игрока {} для NPC {}: '{}'",
                        player.getName().getString(), packet.entityId, packet.newName);
                return;
            }

            String oldName = npc.hasCustomName() ? npc.getCustomName().getString() : npc.getName().getString();
            npc.setCustomName(Component.literal(name));
            npc.setCustomNameVisible(true);

            QuestNPCLogger.info("Игрок {} переименовал NPC {}: '{}' → '{}'",
                    player.getName().getString(), npc.getId(), oldName, name);

            player.sendSystemMessage(Component.translatable("message.questnpc.npc_renamed"));
        });
        ctx.get().setPacketHandled(true);
    }
}
