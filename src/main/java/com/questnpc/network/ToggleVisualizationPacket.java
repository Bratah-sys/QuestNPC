package com.questnpc.network;

import com.questnpc.client.debug.NPCDebugRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C-пакет: включает/выключает дебаг-визуализацию NPC на клиенте.
 */
public class ToggleVisualizationPacket {

    private final boolean enabled;

    public ToggleVisualizationPacket(boolean enabled) {
        this.enabled = enabled;
    }

    public static void encode(ToggleVisualizationPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.enabled);
    }

    public static ToggleVisualizationPacket decode(FriendlyByteBuf buf) {
        return new ToggleVisualizationPacket(buf.readBoolean());
    }

    public static void handle(ToggleVisualizationPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> NPCDebugRenderer.setEnabled(packet.enabled));
        ctx.get().setPacketHandled(true);
    }
}
