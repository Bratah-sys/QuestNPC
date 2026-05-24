package com.questnpc.entity.quest;

import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * Контекст выдачи награды. Передаётся в {@link QuestReward#grant(RewardGrantContext)}.
 *
 * <p>{@link #npc} может быть {@code null}, если NPC был unload'нут к моменту выдачи —
 * {@link com.questnpc.entity.quest.reward.UnlockTradeSetReward} в этом случае skip'нется,
 * а простые ItemReward/XPPointsReward игнорируют npc вовсе.
 */
public class RewardGrantContext {

    private final ServerPlayer player;
    @Nullable private final QuestNPCEntity npc;
    private final String questId;

    public RewardGrantContext(ServerPlayer player, @Nullable QuestNPCEntity npc, String questId) {
        this.player = player;
        this.npc = npc;
        this.questId = questId;
    }

    public ServerPlayer player() { return player; }
    @Nullable public QuestNPCEntity npc() { return npc; }
    public String questId() { return questId; }
}
