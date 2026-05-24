package com.questnpc.entity.quest;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.quest.objective.BreakBlockObjective;
import com.questnpc.entity.quest.objective.BringObjective;
import com.questnpc.entity.quest.objective.KillObjective;
import com.questnpc.entity.quest.objective.ReachBiomeObjective;
import com.questnpc.entity.quest.objective.ReachStructureObjective;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Абстрактный базовый класс для всех типов целей квеста.
 *
 * <p>Этап 1 (foundation): только сериализация + skeleton. Бизнес-логика
 * {@code isComplete}/matches/grant — заглушки, реализация в этапах 4–6.
 *
 * <p>Pattern заимствован у FTB Quests {@code Task} — каждый objective сам декларирует
 * свой режим триггера через {@link #autoCheckIntervalTicks()} и {@link #checkOnLogin()},
 * чтобы триггер-handler оставался тонким диспетчером.
 */
public abstract class QuestObjective {

    protected String id;         // UUID v4 (стабильный идентификатор для PlayerQuestProgress)
    protected boolean optional;  // FTB-style "this objective is optional for quest completion"

    protected QuestObjective() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public boolean isOptional() { return optional; }
    public void setOptional(boolean v) { this.optional = v; }

    public abstract ObjectiveType getType();

    /** 0 = event-driven only; &gt;0 = polling каждые N тиков в PlayerTickEvent. */
    public int autoCheckIntervalTicks() { return 0; }

    /** Проверять при логине игрока (для location-objectives). */
    public boolean checkOnLogin() { return false; }

    /** Максимальный прогресс. Для бинарных целей — 1, для счётных — count. */
    public long getMaxProgress() { return 1L; }

    /** Этап 1 stub: всегда false. Реальная проверка в этапе 5 (PlayerQuestProgress). */
    public boolean isComplete(long currentProgress) {
        return currentProgress >= getMaxProgress();
    }

    public final CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("Type", getType().name());
        tag.putBoolean("Optional", optional);
        CompoundTag params = new CompoundTag();
        writeData(params);
        tag.put("Params", params);
        return tag;
    }

    public final void load(CompoundTag tag) {
        this.id = tag.contains("Id") ? tag.getString("Id") : UUID.randomUUID().toString();
        this.optional = tag.getBoolean("Optional");
        readData(tag.getCompound("Params"));
    }

    /** Сериализовать параметры конкретного типа в Params CompoundTag. */
    protected abstract void writeData(CompoundTag tag);

    /** Восстановить параметры из Params CompoundTag. */
    protected abstract void readData(CompoundTag tag);

    /** Фабрика — создаёт пустой objective указанного типа. Загрузка параметров — через {@link #load}. */
    public static QuestObjective createEmpty(ObjectiveType type) {
        return switch (type) {
            case KILL -> new KillObjective();
            case BRING -> new BringObjective();
            case BREAK_BLOCK -> new BreakBlockObjective();
            case REACH_BIOME -> new ReachBiomeObjective();
            case REACH_STRUCTURE -> new ReachStructureObjective();
        };
    }

    /** Полная загрузка objective из NBT — резолвит тип, создаёт пустой, грузит параметры. */
    public static QuestObjective loadByType(CompoundTag tag) {
        ObjectiveType type = ObjectiveType.byNameOrNull(tag.getString("Type"));
        if (type == null) {
            QuestNPCLogger.warn("QuestObjective.loadByType: unknown type '{}', skipping",
                    tag.getString("Type"));
            return null;
        }
        QuestObjective obj = createEmpty(type);
        obj.load(tag);
        return obj;
    }
}
