package com.questnpc.entity.quest;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.quest.condition.DistanceToStructureCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Абстрактный базовый класс prerequisite-условия квеста.
 *
 * <p>Этап 1: все {@link #isMet(ServerPlayer, BlockPos)} — заглушки {@code return true}.
 * Реальные проверки — этап 4 (DistanceToStructure через findNearestMapStructure)
 * и этап 5 (PlayerLevel / CompletedQuest).
 *
 * <p>{@link #inverted} — флаг NOT-логики: если true, итог isMet инвертируется при
 * проверке в {@link QuestDefinition#canOfferTo}. Нужно для условия «квест НЕ выдаётся
 * рядом со структурой».
 */
public abstract class QuestCondition {

    protected boolean inverted;

    public boolean isInverted() { return inverted; }
    public void setInverted(boolean v) { this.inverted = v; }

    public abstract ConditionType getType();

    /** Этап 1 stub: подклассы возвращают {@code true}. Реальная логика — этапы 4–5. */
    public abstract boolean isMet(ServerPlayer player, BlockPos npcPos);

    public final CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", getType().name());
        tag.putBoolean("Inverted", inverted);
        CompoundTag params = new CompoundTag();
        writeData(params);
        tag.put("Params", params);
        return tag;
    }

    public final void load(CompoundTag tag) {
        this.inverted = tag.getBoolean("Inverted");
        readData(tag.getCompound("Params"));
    }

    protected abstract void writeData(CompoundTag tag);
    protected abstract void readData(CompoundTag tag);

    public static QuestCondition createEmpty(ConditionType type) {
        return switch (type) {
            case DISTANCE_TO_STRUCTURE -> new DistanceToStructureCondition();
            case PLAYER_LEVEL -> throw new UnsupportedOperationException(
                    "PLAYER_LEVEL condition not yet implemented (TODO Stage 4+)");
            case COMPLETED_QUEST -> throw new UnsupportedOperationException(
                    "COMPLETED_QUEST condition not yet implemented (TODO Stage 4+)");
        };
    }

    public static QuestCondition loadByType(CompoundTag tag) {
        ConditionType type = ConditionType.byNameOrNull(tag.getString("Type"));
        if (type == null) {
            QuestNPCLogger.warn("QuestCondition.loadByType: unknown type '{}', skipping",
                    tag.getString("Type"));
            return null;
        }
        try {
            QuestCondition c = createEmpty(type);
            c.load(tag);
            return c;
        } catch (UnsupportedOperationException ex) {
            QuestNPCLogger.warn("QuestCondition.loadByType: type '{}' not implemented yet — skipping",
                    type.name());
            return null;
        }
    }
}
