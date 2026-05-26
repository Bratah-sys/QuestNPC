package com.questnpc.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Хранилище прогресса квестов для конкретного игрока.
 */
public class PlayerQuestData {

    // K: Quest ID (UUID), V: Текущий статус квеста
    private final Map<String, QuestState> questStates = new HashMap<>();

    // K: Objective ID (UUID), V: Текущий прогресс (число)
    private final Map<String, Long> objectiveProgress = new HashMap<>();

    // --- Управление квестами ---

    public QuestState getQuestState(String questId) {
        return questStates.get(questId);
    }

    public void setQuestState(String questId, QuestState state) {
        if (state == null) {
            questStates.remove(questId);
        } else {
            questStates.put(questId, state);
        }
    }

    public boolean hasQuest(String questId) {
        return questStates.containsKey(questId);
    }

    public boolean isActive(String questId) {
        return questStates.get(questId) == QuestState.ACTIVE;
    }

    // --- Управление целями (Objectives) ---

    public long getObjectiveProgress(String objectiveId) {
        return objectiveProgress.getOrDefault(objectiveId, 0L);
    }

    public void setObjectiveProgress(String objectiveId, long progress) {
        objectiveProgress.put(objectiveId, Math.max(0, progress));
    }

    public void addObjectiveProgress(String objectiveId, long amount) {
        long current = getObjectiveProgress(objectiveId);
        setObjectiveProgress(objectiveId, current + amount);
    }

    // --- Сериализация NBT ---

    public CompoundTag saveNBT() {
        CompoundTag tag = new CompoundTag();

        // Сохраняем статусы квестов
        ListTag questsTag = new ListTag();
        for (Map.Entry<String, QuestState> entry : questStates.entrySet()) {
            CompoundTag qTag = new CompoundTag();
            qTag.putString("QuestId", entry.getKey());
            qTag.putString("State", entry.getValue().name());
            questsTag.add(qTag);
        }
        tag.put("Quests", questsTag);

        // Сохраняем прогресс целей
        ListTag objectivesTag = new ListTag();
        for (Map.Entry<String, Long> entry : objectiveProgress.entrySet()) {
            CompoundTag oTag = new CompoundTag();
            oTag.putString("ObjectiveId", entry.getKey());
            oTag.putLong("Progress", entry.getValue());
            objectivesTag.add(oTag);
        }
        tag.put("Objectives", objectivesTag);

        return tag;
    }

    public void loadNBT(CompoundTag tag) {
        questStates.clear();
        objectiveProgress.clear();

        if (tag.contains("Quests", Tag.TAG_LIST)) {
            ListTag questsTag = tag.getList("Quests", Tag.TAG_COMPOUND);
            for (int i = 0; i < questsTag.size(); i++) {
                CompoundTag qTag = questsTag.getCompound(i);
                QuestState state = QuestState.byNameOrNull(qTag.getString("State"));
                if (state != null) {
                    questStates.put(qTag.getString("QuestId"), state);
                }
            }
        }

        if (tag.contains("Objectives", Tag.TAG_LIST)) {
            ListTag objectivesTag = tag.getList("Objectives", Tag.TAG_COMPOUND);
            for (int i = 0; i < objectivesTag.size(); i++) {
                CompoundTag oTag = objectivesTag.getCompound(i);
                objectiveProgress.put(oTag.getString("ObjectiveId"), oTag.getLong("Progress"));
            }
        }
    }

    // Копирование данных (нужно при смерти игрока)
    public void copyFrom(PlayerQuestData source) {
        this.questStates.clear();
        this.questStates.putAll(source.questStates);

        this.objectiveProgress.clear();
        this.objectiveProgress.putAll(source.objectiveProgress);
    }

    /**
     * Возвращает список ID всех квестов, которые находятся в статусе ACTIVE.
     */
    public List<String> getActiveQuests() {
        List<String> activeQuests = new ArrayList<>();
        for (Map.Entry<String, QuestState> entry : questStates.entrySet()) {
            if (entry.getValue() == QuestState.ACTIVE) {
                activeQuests.add(entry.getKey());
            }
        }
        return activeQuests;
    }
}