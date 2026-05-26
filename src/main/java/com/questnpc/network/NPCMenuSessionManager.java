package com.questnpc.network;

import com.questnpc.QuestNPCLogger;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверный менеджер сессий NPC-меню.
 * Хранит связку playerUUID -> npcEntityId с таймстемпом.
 * Используется для валидации C2S-пакетов вместо проверки расстояния.
 */
public final class NPCMenuSessionManager {

    private static final NPCMenuSessionManager INSTANCE = new NPCMenuSessionManager();

    /** Таймаут сессии в миллисекундах (120 секунд). */
    private static final long SESSION_TIMEOUT_MS = 120_000L;

    /** Интервал очистки просроченных сессий (20 секунд в тиках = 400 тиков). */
    public static final int CLEANUP_INTERVAL_TICKS = 400;

    private final Map<UUID, SessionEntry> activeSessions = new ConcurrentHashMap<>();

    private NPCMenuSessionManager() {}

    public static NPCMenuSessionManager getInstance() {
        return INSTANCE;
    }

    /**
     * Открывает сессию для игрока.
     *
     * @param playerUUID UUID игрока
     * @param npcEntityId entity id NPC
     * @param playerName имя игрока (для логов)
     */
    public void openSession(UUID playerUUID, int npcEntityId, String playerName) {
        activeSessions.put(playerUUID, new SessionEntry(npcEntityId, System.currentTimeMillis()));
        QuestNPCLogger.debug("Сессия открыта: игрок {} -> NPC {}", playerName, npcEntityId);
    }

    /**
     * Закрывает сессию игрока.
     *
     * @param playerUUID UUID игрока
     * @param playerName имя игрока (для логов), может быть null
     */
    public void closeSession(UUID playerUUID, String playerName) {
        SessionEntry removed = activeSessions.remove(playerUUID);
        if (removed != null) {
            QuestNPCLogger.debug("Сессия закрыта: игрок {} (NPC {})",
                    playerName != null ? playerName : playerUUID, removed.npcEntityId);
        }
    }

    /**
     * Проверяет, активна ли сессия для данного игрока и NPC.
     * Также проверяет таймаут — если сессия просрочена, закрывает и возвращает false.
     */
    public boolean isSessionActive(UUID playerUUID, int npcEntityId) {
        SessionEntry entry = activeSessions.get(playerUUID);
        if (entry == null) return false;

        // Проверка таймаута
        if (System.currentTimeMillis() - entry.openedAt > SESSION_TIMEOUT_MS) {
            activeSessions.remove(playerUUID);
            QuestNPCLogger.debug("Сессия очищена по таймауту: игрок {}", playerUUID);
            return false;
        }

        return entry.npcEntityId == npcEntityId;
    }

    /**
     * Очищает все просроченные сессии. Вызывается из ServerTickEvent.
     */
    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, SessionEntry>> it = activeSessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, SessionEntry> entry = it.next();
            if (now - entry.getValue().openedAt > SESSION_TIMEOUT_MS) {
                QuestNPCLogger.debug("Сессия очищена по таймауту: игрок {}", entry.getKey());
                it.remove();
            }
        }
    }

    /**
     * Закрывает все сессии. Вызывается при остановке сервера.
     */
    public void clearAll() {
        activeSessions.clear();
    }

    private record SessionEntry(int npcEntityId, long openedAt) {}
}
