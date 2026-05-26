package com.questnpc.network;

import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class RequestDialoguePacket {
    private final int entityId;

    public RequestDialoguePacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(RequestDialoguePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
    }

    public static RequestDialoguePacket decode(FriendlyByteBuf buf) {
        return new RequestDialoguePacket(buf.readInt());
    }

    public static void handle(RequestDialoguePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null && player.level().getEntity(msg.entityId) instanceof QuestNPCEntity npc) {
                // Перенаправляем логику в NPC на сервере (этот метод мы создадим на Шаге 4)
                npc.startServerDialogue(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}