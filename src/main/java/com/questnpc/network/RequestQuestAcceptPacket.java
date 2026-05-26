package com.questnpc.network;

import com.questnpc.capability.PlayerQuestProgress;
import com.questnpc.capability.PlayerQuestProvider;
import com.questnpc.capability.QuestKey;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.quest.QuestDefinition;
import com.questnpc.entity.quest.QuestObjective;
import com.questnpc.events.QuestChatHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * C→S: игрок принимает квест из вкладки «Доступные» в {@link com.questnpc.client.gui.PlayerQuestScreen}.
 * Defense-in-depth: сервер ПОВТОРНО проверяет {@link QuestDefinition#canOfferTo}.
 *
 * <p>Stage 5 (v2.9.4), пакет ID 21.
 */
public class RequestQuestAcceptPacket {

    private final int npcEntityId;
    private final String questId;

    public RequestQuestAcceptPacket(int npcEntityId, String questId) {
        this.npcEntityId = npcEntityId;
        this.questId = questId;
    }

    public static void encode(RequestQuestAcceptPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.npcEntityId);
        buf.writeUtf(msg.questId);
    }

    public static RequestQuestAcceptPacket decode(FriendlyByteBuf buf) {
        return new RequestQuestAcceptPacket(buf.readInt(), buf.readUtf());
    }

    public static void handle(RequestQuestAcceptPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (!(player.level().getEntity(msg.npcEntityId) instanceof QuestNPCEntity npc)) return;
            if (!npc.isQuestsEnabled()) return;

            QuestDefinition quest = null;
            for (QuestDefinition q : npc.getQuests()) {
                if (q.getId().equals(msg.questId)) { quest = q; break; }
            }
            if (quest == null) return;

            QuestKey key = new QuestKey(npc.getUUID(), quest.getId());
            QuestDefinition finalQuest = quest;
            player.getCapability(PlayerQuestProvider.CAP).ifPresent(prog -> {
                if (prog.isActive(key) || prog.isCompleted(key)) return;
                if (!finalQuest.canOfferTo(player, npc.blockPosition(), npc)) return;

                List<String> objIds = new ArrayList<>();
                for (QuestObjective obj : finalQuest.getObjectives()) objIds.add(obj.getId());
                prog.acceptQuest(key, player.level().getGameTime(), objIds);

                QuestChatHelper.sendQuestAccepted(player, finalQuest);
                QuestChatHelper.syncProgressToClient(player);
            });
        });
        ctx.setPacketHandled(true);
    }
}
