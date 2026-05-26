package com.questnpc.network;

import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class DialogueChoicePacket {
    private final int entityId;
    private final String nextNodeId;
    private final String action;

    public DialogueChoicePacket(int entityId, String nextNodeId, String action) {
        this.entityId = entityId;
        this.nextNodeId = nextNodeId;
        this.action = action;
    }

    public static void encode(DialogueChoicePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeUtf(msg.nextNodeId);
        buf.writeUtf(msg.action);
    }

    public static DialogueChoicePacket decode(FriendlyByteBuf buf) {
        return new DialogueChoicePacket(buf.readInt(), buf.readUtf(), buf.readUtf());
    }

    public static void handle(DialogueChoicePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null && player.level().getEntity(msg.entityId) instanceof QuestNPCEntity npc) {
                // Передаем весь контекст выбора (кто нажал, какой следующий узел, какое действие) в сущность NPC
                npc.handleDialogueChoice(player, msg.nextNodeId, msg.action);
            }
        });
        ctx.setPacketHandled(true);
    }
}