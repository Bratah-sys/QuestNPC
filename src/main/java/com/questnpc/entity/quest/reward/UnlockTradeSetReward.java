package com.questnpc.entity.quest.reward;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
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

    /**
     * Stage 6 (v2.9.5): разблокирует named TradeSet на NPC.
     *
     * <p>Реализация через Set&lt;String&gt; lockedTradeSets на QuestNPCEntity (decision Егора
     * 2026-05-26). Lazy approach — обновление видно при следующем mobInteract.
     *
     * <p>No-op если:
     * <ul>
     *   <li>NPC unload'нут к моменту выдачи</li>
     *   <li>tradeSetName пустой</li>
     *   <li>TradeSet с таким именем не найден на NPC (log warn)</li>
     *   <li>TradeSet уже unlocked</li>
     * </ul>
     */
    @Override
    public void grant(RewardGrantContext ctx) {
        QuestNPCEntity npc = ctx.npc();
        if (npc == null || tradeSetName == null || tradeSetName.isBlank()) return;
        if (npc.getTradeSetByName(tradeSetName) == null) {
            QuestNPCLogger.warn("UnlockTradeSetReward: trade set '{}' not found on NPC {}",
                    tradeSetName, npc.getUUID());
            return;
        }
        if (npc.isTradeSetLocked(tradeSetName)) {
            npc.unlockTradeSet(tradeSetName);
            QuestNPCLogger.debug("Unlocked trade set '{}' on NPC {} by player {}",
                    tradeSetName, npc.getUUID(),
                    ctx.player() != null ? ctx.player().getName().getString() : "?");
        }
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
