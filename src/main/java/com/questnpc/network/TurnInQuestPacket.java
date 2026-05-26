package com.questnpc.network;

import com.questnpc.QuestNPCLogger;
import com.questnpc.capability.PlayerQuestProvider;
import com.questnpc.capability.QuestState;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.quest.QuestDefinition;
import com.questnpc.entity.quest.QuestObjective;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TurnInQuestPacket {
    private final int entityId;
    private final String questId;

    public TurnInQuestPacket(int entityId, String questId) {
        this.entityId = entityId;
        this.questId = questId;
    }

    public static void encode(TurnInQuestPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeUtf(msg.questId);
    }

    public static TurnInQuestPacket decode(FriendlyByteBuf buf) {
        return new TurnInQuestPacket(buf.readInt(), buf.readUtf());
    }

    public static void handle(TurnInQuestPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            if (player.level().getEntity(msg.entityId) instanceof QuestNPCEntity npc) {
                player.getCapability(PlayerQuestProvider.PLAYER_QUEST_DATA).ifPresent(data -> {

                    if (data.getQuestState(msg.questId) != QuestState.ACTIVE) return;

                    // Ищем квест у NPC
                    QuestDefinition targetQuest = null;
                    for (QuestDefinition q : npc.getQuests()) {
                        if (q.getId().equals(msg.questId)) {
                            targetQuest = q;
                            break;
                        }
                    }

                    if (targetQuest == null) return;

                    // Финальная валидация на сервере (защита от читов)
                    boolean allComplete = true;
                    for (QuestObjective obj : targetQuest.getObjectives()) {
                        long currentProgress = data.getObjectiveProgress(obj.getId());
                        if (!obj.isOptional() && !obj.isComplete(currentProgress)) {
                            allComplete = false;
                            break;
                        }
                    }

                    if (allComplete) {
                        // TODO Stage 6: Проверка BringObjective и изъятие предметов
                        // TODO Stage 6: Выдача наград QuestReward.grant(ctx)

                        data.setQuestState(msg.questId, QuestState.COMPLETED);
                        player.sendSystemMessage(Component.literal("§aЗадание завершено: §e" + targetQuest.getTitle()));
                        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
                        QuestNPCLogger.debug("Игрок {} сдал квест {}", player.getName().getString(), targetQuest.getTitle());
                    } else {
                        player.sendSystemMessage(Component.literal("§cВы еще не выполнили все цели!"));
                    }
                });
            }
        });
        ctx.setPacketHandled(true);
    }
}