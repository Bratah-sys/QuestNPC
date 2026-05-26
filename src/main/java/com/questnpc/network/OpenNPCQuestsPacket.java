package com.questnpc.network;

import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S: игрок кликнул «Квесты» в хабе ({@link com.questnpc.client.gui.NPCInteractionScreen}).
 * Сервер собирает три списка (offerable / active / turnInReady) через
 * {@link QuestNPCEntity#startServerQuestInteraction} и шлёт обратно {@link OpenPlayerQuestListPacket}.
 *
 * <p>Stage 5 (v2.9.4), пакет ID 19.
 */
public class OpenNPCQuestsPacket {

    private final int entityId;

    public OpenNPCQuestsPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(OpenNPCQuestsPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
    }

    public static OpenNPCQuestsPacket decode(FriendlyByteBuf buf) {
        return new OpenNPCQuestsPacket(buf.readInt());
    }

    public static void handle(OpenNPCQuestsPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (player.level().getEntity(msg.entityId) instanceof QuestNPCEntity npc) {
                npc.startServerQuestInteraction(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
