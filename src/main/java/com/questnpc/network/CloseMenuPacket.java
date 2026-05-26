package com.questnpc.network;

import com.questnpc.QuestNPCLogger;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S-пакет: клиент сообщает серверу что закрыл меню NPC.
 * Сервер закрывает сессию в NPCMenuSessionManager.
 */
public class CloseMenuPacket {

    private final int entityId;

    public CloseMenuPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(CloseMenuPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
    }

    public static CloseMenuPacket decode(FriendlyByteBuf buf) {
        return new CloseMenuPacket(buf.readVarInt());
    }

    public static void handle(CloseMenuPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            NPCMenuSessionManager.getInstance().closeSession(
                    player.getUUID(), player.getName().getString());

            QuestNPCLogger.info("Игрок {} закрыл меню NPC {}",
                    player.getName().getString(), packet.entityId);
        });
        ctx.get().setPacketHandled(true);
    }
}
