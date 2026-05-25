package com.questnpc.entity.quest.reward;

import com.questnpc.entity.quest.QuestReward;
import com.questnpc.entity.quest.RewardGrantContext;
import com.questnpc.entity.quest.RewardType;
import net.minecraft.nbt.CompoundTag;

/**
 * Награда «разблокировать named TradeSet на NPC». Согласовано 2026-05-25 (research §0.5 п.3).
 *
 * <p>Этап 1: grant no-op. Реализация — этап 6: резолв NPC через {@code ctx.npc()},
 * поиск TradeSet by name, set enabled=true, broadcast OpenNPCMenuPacket reload
 * подключённым админам и UpdateTradeOffersPacket tracking-игрокам.
 */
public class UnlockTradeSetReward extends QuestReward {

    private String tradeSetName = "";

    @Override
    public RewardType getType() { return RewardType.UNLOCK_TRADE_SET; }

    public String getTradeSetName() { return tradeSetName; }
    public void setTradeSetName(String v) { this.tradeSetName = v != null ? v : ""; }

    @Override
    public void grant(RewardGrantContext ctx) {
        // TODO Stage 6: ctx.npc() → найти TradeSet by name → enable + broadcast
    }

    @Override
    protected void writeData(CompoundTag tag) {
        tag.putString("TradeSetName", tradeSetName);
    }

    @Override
    protected void readData(CompoundTag tag) {
        this.tradeSetName = tag.contains("TradeSetName") ? tag.getString("TradeSetName") : "";
    }
}
