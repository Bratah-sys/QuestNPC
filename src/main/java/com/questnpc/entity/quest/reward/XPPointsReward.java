package com.questnpc.entity.quest.reward;

import com.questnpc.entity.quest.QuestReward;
import com.questnpc.entity.quest.RewardGrantContext;
import com.questnpc.entity.quest.RewardType;
import net.minecraft.nbt.CompoundTag;

/**
 * Награда «опыт (points)». Этап 1: grant no-op. Реализация — этап 6.
 */
public class XPPointsReward extends QuestReward {

    private int amount = 0;

    @Override
    public RewardType getType() { return RewardType.XP_POINTS; }

    public int getAmount() { return amount; }
    public void setAmount(int v) { this.amount = Math.max(0, v); }

    @Override
    public void grant(RewardGrantContext ctx) {
        // TODO Stage 6: ctx.player().giveExperiencePoints(amount)
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
