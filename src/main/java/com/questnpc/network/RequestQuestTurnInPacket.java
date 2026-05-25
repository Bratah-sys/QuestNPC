package com.questnpc.network;

import com.questnpc.capability.PlayerQuestProvider;
import com.questnpc.capability.QuestKey;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.quest.QuestDefinition;
import com.questnpc.entity.quest.QuestObjective;
import com.questnpc.entity.quest.QuestReward;
import com.questnpc.entity.quest.objective.BringObjective;
import com.questnpc.events.QuestChatHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S: игрок сдаёт готовый квест из вкладки «Сдать».
 * Defense-in-depth: сервер ПОВТОРНО проверяет {@link QuestDefinition#isReadyToTurnIn}.
 *
 * <p>Stage 5 (v2.9.4): rewards остаются stub'ом — {@link QuestChatHelper#sendRewardGranted}
 * шлёт chat «Награда: X», реальный {@link QuestReward#grant} не вызывается. Реальная
 * выдача — Этап 6.
 *
 * <p>Пакет ID 22.
 */
public class RequestQuestTurnInPacket {

    private final int npcEntityId;
    private final String questId;

    public RequestQuestTurnInPacket(int npcEntityId, String questId) {
        this.npcEntityId = npcEntityId;
        this.questId = questId;
    }

    public static void encode(RequestQuestTurnInPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.npcEntityId);
        buf.writeUtf(msg.questId);
    }

    public static RequestQuestTurnInPacket decode(FriendlyByteBuf buf) {
        return new RequestQuestTurnInPacket(buf.readInt(), buf.readUtf());
    }

    public static void handle(RequestQuestTurnInPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
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
                if (!prog.isActive(key)) return;
                if (!finalQuest.isReadyToTurnIn(prog, key, player)) {
                    QuestChatHelper.sendBringFailed(player);
                    return;
                }

                // Списать Bring-предметы
                for (QuestObjective obj : finalQuest.getObjectives()) {
                    if (obj instanceof BringObjective bo) {
                        bo.consumeFromInventory(player);
                    }
                }

                // Stage 5: rewards — только chat-stub. Реальная выдача — Этап 6.
                for (QuestReward reward : finalQuest.getRewards()) {
                    QuestChatHelper.sendRewardGranted(player, reward);
                }

                prog.markCompleted(key, player.level().getGameTime());
                QuestChatHelper.sendQuestCompleted(player, finalQuest);
                QuestChatHelper.syncProgressToClient(player);
            });
        });
        ctx.setPacketHandled(true);
    }
}
