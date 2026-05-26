package com.questnpc.entity.quest.reward;

import com.questnpc.entity.quest.QuestReward;
import com.questnpc.entity.quest.RewardGrantContext;
import com.questnpc.entity.quest.RewardType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Награда «опыт (points)». Этап 1: grant no-op. Реализация — этап 6.
 */
public class XPPointsReward extends QuestReward {

    private int amount = 0;

    @Override
    public RewardType getType() { return RewardType.XP_POINTS; }

    public int getAmount() { return amount; }
    public void setAmount(int v) { this.amount = Math.max(0, v); }

    /**
     * Stage 6 (v2.9.5): даёт игроку XP points через vanilla {@code giveExperiencePoints}.
     * Дополнительно проигрывает звук {@code EXPERIENCE_ORB_PICKUP} для UX-feedback'а.
     */
    @Override
    public void grant(RewardGrantContext ctx) {
        ServerPlayer player = ctx.player();
        if (player == null || amount <= 0) return;
        player.giveExperiencePoints(amount);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.0f);
    }

    @Override
    protected void writeData(CompoundTag tag) {
        tag.putInt("Amount", amount);
    }

    @Override
    protected void readData(CompoundTag tag) {
        this.amount = Math.max(0, tag.getInt("Amount"));
    }
}
