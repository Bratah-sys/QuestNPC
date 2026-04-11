package com.questnpc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C-пакет: открывает экран торговли для игрока.
 *
 * <p>WIP-001: будет использован для player-facing trading. Сейчас не отправляется ниоткуда
 * и handler пуст — до реализации настоящего торгового флоу через {@code Merchant}.
 */
public class OpenTradingScreenPacket {

    private final int entityId;

    public OpenTradingScreenPacket(int entityId) {
        this.entityId = entityId;
    }

    public int getEntityId() {
        return entityId;
    }

    public static void encode(OpenTradingScreenPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
    }

    public static OpenTradingScreenPacket decode(FriendlyByteBuf buf) {
        return new OpenTradingScreenPacket(buf.readVarInt());
    }

    public static void handle(OpenTradingScreenPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // TODO (WIP-001): открыть player-facing торговый экран, когда будет реализован.
        });
        ctx.get().setPacketHandled(true);
    }
}
