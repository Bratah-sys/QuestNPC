package com.questnpc.network;

import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenNPCTradePacket {
    private final int entityId;

    public OpenNPCTradePacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(OpenNPCTradePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
    }

    public static OpenNPCTradePacket decode(FriendlyByteBuf buf) {
        return new OpenNPCTradePacket(buf.readInt());
    }

    public static void handle(OpenNPCTradePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null && player.level().getEntity(msg.entityId) instanceof QuestNPCEntity npc) {
                // Сервер вызывает запуск оригинальных торгов
                npc.startServerTrading(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}