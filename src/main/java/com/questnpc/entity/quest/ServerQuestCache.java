package com.questnpc.entity.quest;

import com.questnpc.entity.QuestNPCEntity;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Глобальный кэш квестов. Нужен для того, чтобы отслеживать прогресс (убийства, блоки),
 * даже если чанк с NPC выгружен из памяти сервера.
 */
public class ServerQuestCache {
    private static final Map<String, QuestDefinition> CACHE = new ConcurrentHashMap<>();

    // Вызывать этот метод из QuestNPCEntity.java в методах addQuest, setQuestsFromSnapshot и readAdditionalSaveData
    public static void registerQuestsFromNPC(QuestNPCEntity npc) {
        for (QuestDefinition quest : npc.getQuests()) {
            if (quest.isEnabled()) {
                CACHE.put(quest.getId(), quest);
            }
        }
    }

    public static QuestDefinition getQuest(String questId) {
        return CACHE.get(questId);
    }
}