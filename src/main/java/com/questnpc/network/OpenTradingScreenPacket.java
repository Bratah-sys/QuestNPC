package com.questnpc.network;

import com.questnpc.client.gui.WIPScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C-пакет: открывает экран торговли для игрока.
 * Сейчас открывает заглушку WIPScreen с ключом gui.questnpc.trading.wip.
 */
public class OpenTradingScreenPacket {

    private final int entityId;

    public OpenTradingScreenPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(OpenTradingScreenPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
    }

    public static OpenTradingScreenPacket decode(FriendlyByteBuf buf) {
        return new OpenTradingScreenPacket(buf.readVarInt());
    }

    public static void handle(OpenTradingScreenPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new WIPScreen(
                    mc.screen,
                    Component.translatable("gui.questnpc.trading.wip")
            ));
        });
        ctx.get().setPacketHandled(true);
    }
}
