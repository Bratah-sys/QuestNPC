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
 * C2S-пакет: удаление NPC.
 * Сервер проверяет расстояние и вызывает entity.discard().
 */
public class DeleteNPCPacket {

    private final int entityId;

    public DeleteNPCPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(DeleteNPCPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
    }

    public static DeleteNPCPacket decode(FriendlyByteBuf buf) {
        return new DeleteNPCPacket(buf.readVarInt());
    }

    public static void handle(DeleteNPCPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity entity = player.serverLevel().getEntity(packet.entityId);
            if (!(entity instanceof QuestNPCEntity npc)) {
                QuestNPCLogger.warn("Игрок {} попытался удалить несуществующего NPC {}",
                        player.getName().getString(), packet.entityId);
                return;
            }

            // Проверка расстояния (≤16 блоков)
            if (player.distanceToSqr(npc) > 256.0) {
                QuestNPCLogger.warn("Игрок {} слишком далеко от NPC {} для удаления",
                        player.getName().getString(), packet.entityId);
                return;
            }

            int x = (int) npc.getX();
            int y = (int) npc.getY();
            int z = (int) npc.getZ();

            npc.discard();

            QuestNPCLogger.info("Игрок {} удалил NPC {} на [{}, {}, {}]",
                    player.getName().getString(), packet.entityId, x, y, z);

            player.sendSystemMessage(Component.translatable("message.questnpc.npc_deleted"));
        });
        ctx.get().setPacketHandled(true);
    }
}
