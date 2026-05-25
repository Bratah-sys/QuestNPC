package com.questnpc.network;

import com.questnpc.capability.PlayerQuestProvider;
import com.questnpc.capability.QuestKey;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.quest.QuestDefinition;
import com.questnpc.events.QuestChatHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S: игрок отказывается от активного квеста.
 * Stage 5 (v2.9.4), пакет ID 23.
 */
public class RequestQuestAbandonPacket {

    private final int npcEntityId;
    private final String questId;

    public RequestQuestAbandonPacket(int npcEntityId, String questId) {
        this.npcEntityId = npcEntityId;
        this.questId = questId;
    }

    public static void encode(RequestQuestAbandonPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.npcEntityId);
        buf.writeUtf(msg.questId);
    }

    public static RequestQuestAbandonPacket decode(FriendlyByteBuf buf) {
        return new RequestQuestAbandonPacket(buf.readInt(), buf.readUtf());
    }

    public static void handle(RequestQuestAbandonPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (!(player.level().getEntity(msg.npcEntityId) instanceof QuestNPCEntity npc)) return;

            QuestDefinition quest = null;
            for (QuestDefinition q : npc.getQuests()) {
                if (q.getId().equals(msg.questId)) { quest = q; break; }
            }
            if (quest == null) return;

            QuestKey key = new QuestKey(npc.getUUID(), quest.getId());
            QuestDefinition finalQuest = quest;
            player.getCapability(PlayerQuestProvider.CAP).ifPresent(prog -> {
                if (!prog.isActive(key)) return;
                prog.abandonQuest(key);
                QuestChatHelper.sendQuestAbandoned(player, finalQuest);
                QuestChatHelper.syncProgressToClient(player);
            });
        });
        ctx.setPacketHandled(true);
    }
}
