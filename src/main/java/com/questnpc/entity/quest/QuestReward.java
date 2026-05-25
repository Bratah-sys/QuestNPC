package com.questnpc.entity.quest;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.quest.reward.CommandReward;
import com.questnpc.entity.quest.reward.ItemReward;
import com.questnpc.entity.quest.reward.UnlockTradeSetReward;
import com.questnpc.entity.quest.reward.XPPointsReward;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Абстрактный базовый класс награды квеста.
 *
 * <p>Этап 1: все {@link #grant(RewardGrantContext)} — заглушки no-op.
 * Реальная выдача — в этапе 6.
 */
public abstract class QuestReward {

    protected String id; // UUID для идентификации в логах/UI

    protected QuestReward() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }

    public abstract RewardType getType();

    /**
     * Выдать награду игроку. Этап 1 stub — реализация в подклассах в этапе 6.
     * {@code ctx} обязателен в сигнатуре даже для классов, которые его не используют.
     */
    public abstract void grant(RewardGrantContext ctx);

    public final CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("Type", getType().name());
        CompoundTag params = new CompoundTag();
        writeData(params);
        tag.put("Params", params);
        return tag;
    }

    public final void load(CompoundTag tag) {
        this.id = tag.contains("Id") ? tag.getString("Id") : UUID.randomUUID().toString();
        readData(tag.getCompound("Params"));
    }

    protected abstract void writeData(CompoundTag tag);
    protected abstract void readData(CompoundTag tag);

    public static QuestReward createEmpty(RewardType type) {
        return switch (type) {
            case ITEM -> new ItemReward();
            case XP_POINTS -> new XPPointsReward();
            case COMMAND -> new CommandReward();
            case UNLOCK_TRADE_SET -> new UnlockTradeSetReward();
        };
    }

    public static QuestReward loadByType(CompoundTag tag) {
        RewardType type = RewardType.byNameOrNull(tag.getString("Type"));
        if (type == null) {
            QuestNPCLogger.warn("QuestReward.loadByType: unknown type '{}', skipping",
                    tag.getString("Type"));
            return null;
        }
        QuestReward r = createEmpty(type);
        r.load(tag);
        return r;
    }
}
