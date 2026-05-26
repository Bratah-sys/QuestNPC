package com.questnpc.network;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S-пакет: включает/выключает торговлю для NPC.
 */
public class UpdateTradingEnabledPacket {

    private final int entityId;
    private final boolean enabled;

    public UpdateTradingEnabledPacket(int entityId, boolean enabled) {
        this.entityId = entityId;
        this.enabled = enabled;
    }

    public static void encode(UpdateTradingEnabledPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeBoolean(packet.enabled);
    }

    public static UpdateTradingEnabledPacket decode(FriendlyByteBuf buf) {
        return new UpdateTradingEnabledPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(UpdateTradingEnabledPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity entity = player.serverLevel().getEntity(packet.entityId);
            if (!(entity instanceof QuestNPCEntity npc)) {
                QuestNPCLogger.warn("UpdateTradingEnabled: NPC {} не найден", packet.entityId);
                return;
            }

            if (!NPCMenuSessionManager.getInstance().isSessionActive(player.getUUID(), packet.entityId)) {
                QuestNPCLogger.warn("UpdateTradingEnabled: нет активной сессии для игрока {} NPC {}",
                        player.getName().getString(), packet.entityId);
                return;
            }

            npc.setTradingEnabled(packet.enabled);
            QuestNPCLogger.info("Игрок {} {} торговлю для NPC {}",
                    player.getName().getString(), packet.enabled ? "включил" : "выключил", packet.entityId);
        });
        ctx.get().setPacketHandled(true);
    }
}
