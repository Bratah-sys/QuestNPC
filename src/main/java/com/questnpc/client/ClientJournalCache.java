package com.questnpc.client;

import com.questnpc.capability.QuestKey;
import com.questnpc.network.QuestJournalEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 8 (v2.9.8): client-side singleton кэш {@link QuestJournalEntry} для
 * {@link com.questnpc.client.gui.PlayerQuestJournalScreen}.
 *
 * <p>Обновляется через {@link com.questnpc.network.SyncPlayerQuestProgressPacket}
 * после каждой мутации прогресса (accept/turn-in/abandon/progress update) — журнал
 * читает отсюда при открытии. Manual refresh — через
 * {@link com.questnpc.network.RequestJournalRefreshPacket}.
 *
 * <p>Очищается при logout (см. {@link ClientForgeEvents#onClientLoggedOut}).
 */
public final class ClientJournalCache {

    private static final ClientJournalCache INSTANCE = new ClientJournalCache();

    public static ClientJournalCache get() {
        return INSTANCE;
    }

    /** active entries, ordered insertion (server-side LinkedHashMap-friendly). */
    private final Map<QuestKey, QuestJournalEntry> active = new LinkedHashMap<>();
    /** completed entries. */
    private final Map<QuestKey, QuestJournalEntry> completed = new LinkedHashMap<>();
    /** Tracks if we've ever received a sync (used by journal to detect "loading" state). */
    private boolean initialised = false;

    private ClientJournalCache() {}

    /**
     * Полностью заменяет содержимое кэша новым snapshot'ом.
     * Вызывается из {@link com.questnpc.network.SyncPlayerQuestProgressPacket} client handler.
     */
    public void update(List<QuestJournalEntry> entries) {
        active.clear();
        completed.clear();
        if (entries != null) {
            for (QuestJournalEntry e : entries) {
                if (e.completed()) completed.put(e.questKey(), e);
                else active.put(e.questKey(), e);
            }
        }
        initialised = true;
    }

    /** Сбрасывает кэш — вызывается на logout. */
    public void clear() {
        active.clear();
        completed.clear();
        initialised = false;
    }

    public boolean isInitialised() { return initialised; }

    public List<QuestJournalEntry> getActive() {
        return Collections.unmodifiableList(new ArrayList<>(active.values()));
    }

    public List<QuestJournalEntry> getCompleted() {
        return Collections.unmodifiableList(new ArrayList<>(completed.values()));
    }

    public int activeCount() { return active.size(); }
    public int completedCount() { return completed.size(); }
}
