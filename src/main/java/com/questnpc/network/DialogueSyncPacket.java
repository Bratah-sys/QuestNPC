package com.questnpc.network;

import com.questnpc.dialogue.DialogueNode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class DialogueSyncPacket {
    private final int entityId;
    private final DialogueNode node;

    public DialogueSyncPacket(int entityId, DialogueNode node) {
        this.entityId = entityId;
        this.node = node;
    }

    public static void encode(DialogueSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        msg.node.toNetwork(buf);
    }

    public static DialogueSyncPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        DialogueNode node = DialogueNode.fromNetwork(buf);
        return new DialogueSyncPacket(entityId, node);
    }

    public static void handle(DialogueSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            // ВАЖНО: Выполняем только на КЛИЕНТЕ
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getEntity(msg.entityId) instanceof com.questnpc.entity.QuestNPCEntity npc) {
                    // Открываем экран диалога или обновляем его, если он уже открыт
                    Minecraft.getInstance().setScreen(new com.questnpc.client.gui.DialogueScreen(npc, msg.node));
                }
            });
        });
        ctx.setPacketHandled(true);
    }
}