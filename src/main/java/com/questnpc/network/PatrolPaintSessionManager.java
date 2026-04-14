package com.questnpc.network;

import com.questnpc.QuestNPCLogger;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверный менеджер сессий рисования зон патруля.
 * Хранит связку playerUUID → {npcUuid, slotIndex, timestamp}.
 * Отдельный от {@link NPCMenuSessionManager} — меню уже закрыто, пока игрок рисует зону.
 *
 * <p>Открывается при {@link RequestPatrolBrushPacket}, закрывается при {@link FinishPatrolPaintPacket},
 * отмене (смена активного слота инвентаря), выходе игрока или по таймауту.
 */
public final class PatrolPaintSessionManager {

    private static final PatrolPaintSessionManager INSTANCE = new PatrolPaintSessionManager();

    /** Таймаут сессии в миллисекундах (300 секунд — у игрока есть 5 минут на рисование). */
    private static final long SESSION_TIMEOUT_MS = 300_000L;

    /** Интервал очистки просроченных сессий (в тиках). */
    public static final int CLEANUP_INTERVAL_TICKS = 400;

    private final Map<UUID, PaintSession> activeSessions = new ConcurrentHashMap<>();

    private PatrolPaintSessionManager() {}

    public static PatrolPaintSessionManager getInstance() {
        return INSTANCE;
    }

    public void openSession(UUID playerUUID, UUID npcUuid, int slotIndex) {
        activeSessions.put(playerUUID,
                new PaintSession(npcUuid, slotIndex, System.currentTimeMillis()));
        QuestNPCLogger.debug("Paint-сессия открыта: игрок {} -> NPC {} slot {}",
                playerUUID, npcUuid, slotIndex);
    }

    public void closeSession(UUID playerUUID) {
        PaintSession removed = activeSessions.remove(playerUUID);
        if (removed != null) {
            QuestNPCLogger.debug("Paint-сессия закрыта: игрок {} (NPC {} slot {})",
                    playerUUID, removed.npcUuid, removed.slotIndex);
        }
    }

    public PaintSession getSession(UUID playerUUID) {
        PaintSession s = activeSessions.get(playerUUID);
        if (s == null) return null;
        if (System.currentTimeMillis() - s.openedAt > SESSION_TIMEOUT_MS) {
            activeSessions.remove(playerUUID);
            QuestNPCLogger.debug("Paint-сессия очищена по таймауту: игрок {}", playerUUID);
            return null;
        }
        return s;
    }

    public boolean hasSession(UUID playerUUID) {
        return getSession(playerUUID) != null;
    }

    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, PaintSession>> it = activeSessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PaintSession> entry = it.next();
            if (now - entry.getValue().openedAt > SESSION_TIMEOUT_MS) {
                QuestNPCLogger.debug("Paint-сессия очищена по таймауту: игрок {}", entry.getKey());
                it.remove();
            }
        }
    }

    public void clearAll() {
        activeSessions.clear();
    }

    public static final class PaintSession {
        public final UUID npcUuid;
        public final int slotIndex;
        public final long openedAt;

        public PaintSession(UUID npcUuid, int slotIndex, long openedAt) {
            this.npcUuid = npcUuid;
            this.slotIndex = slotIndex;
            this.openedAt = openedAt;
        }
    }
}
