package com.questnpc.network;

import com.questnpc.capability.PlayerQuestProvider;
import com.questnpc.capability.QuestState;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.quest.QuestDefinition;
import com.questnpc.entity.quest.QuestCondition;
import com.questnpc.entity.quest.QuestObjective;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class RequestPlayerQuestsPacket {
    private final int entityId;

    public RequestPlayerQuestsPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(RequestPlayerQuestsPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
    }

    public static RequestPlayerQuestsPacket decode(FriendlyByteBuf buf) {
        return new RequestPlayerQuestsPacket(buf.readInt());
    }

    public static void handle(RequestPlayerQuestsPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            if (player.level().getEntity(msg.entityId) instanceof QuestNPCEntity npc) {
                player.getCapability(PlayerQuestProvider.PLAYER_QUEST_DATA).ifPresent(data -> {
                    List<CompoundTag> questsData = new ArrayList<>();

                    for (QuestDefinition quest : npc.getQuests()) {
                        if (!quest.isEnabled()) continue;

                        QuestState state = data.getQuestState(quest.getId());
                        boolean canAccept = true;

                        // Если квест еще не взят, проверяем условия (prerequisites)
                        if (state == null) {
                            for (QuestCondition condition : quest.getPrerequisites()) {
                                boolean met = condition.isMet(player, npc.blockPosition());
                                if (condition.isInverted()) met = !met;
                                if (!met) {
                                    canAccept = false;
                                    break;
                                }
                            }
                        }

                        // Отправляем квест, если он активен, выполнен или доступен для взятия
                        if (state != null || canAccept) {
                            CompoundTag qTag = quest.save(); // Сохраняем базу квеста
                            qTag.putString("PlayerState", state != null ? state.name() : "AVAILABLE");

                            // Записываем текущий прогресс для каждой цели
                            ListTag progressList = new ListTag();
                            for (QuestObjective obj : quest.getObjectives()) {
                                CompoundTag pTag = new CompoundTag();
                                pTag.putString("ObjectiveId", obj.getId());
                                pTag.putLong("CurrentProgress", data.getObjectiveProgress(obj.getId()));
                                progressList.add(pTag);
                            }
                            qTag.put("PlayerProgress", progressList);
                            questsData.add(qTag);
                        }
                    }

                    // Отправляем ответ клиенту
                    ModNetwork.INSTANCE.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new SyncPlayerQuestsPacket(npc.getId(), questsData)
                    );
                });
            }
        });
        ctx.setPacketHandled(true);
    }
}