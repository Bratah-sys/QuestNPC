package com.questnpc.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Прогресс квестов одного игрока. Хранит:
 * <ul>
 *   <li>{@code active} — активные квесты (QuestKey → Map&lt;objectiveId, progress&gt;)</li>
 *   <li>{@code completed} — выполненные квесты (QuestKey → timestamp выполнения)</li>
 *   <li>{@code startedAt} — когда квест был принят (для будущей UI/journal)</li>
 * </ul>
 *
 * <p>Сериализуется через Forge Capability в {@link PlayerQuestProvider}.
 *
 * <p>Согласовано (decision §5 от 2026-05-25): once-per-player — completed квесты
 * не предлагаются повторно (нет cooldown).
 *
 * <p>Согласовано (decision §2 от 2026-05-25): silent cleanup orphaned —
 * админ удалил квест → {@link #cleanupOrphaned(Set)} тихо убирает из active.
 */
public class PlayerQuestProgress implements INBTSerializable<CompoundTag> {

    private final Map<QuestKey, Map<String, Long>> active = new HashMap<>();
    private final Map<QuestKey, Long> completed = new HashMap<>();
    private final Map<QuestKey, Long> startedAt = new HashMap<>();

    // -------------------------------------------------------------------------
    // State queries
    // -------------------------------------------------------------------------

    public boolean isActive(QuestKey k) {
        return active.containsKey(k);
    }

    public boolean isCompleted(QuestKey k) {
        return completed.containsKey(k);
    }

    public long getProgress(QuestKey k, String objId) {
        Map<String, Long> map = active.get(k);
        if (map == null) return 0L;
        Long v = map.get(objId);
        return v != null ? v : 0L;
    }

    /**
     * Прибавить прогресс к objective и вернуть новое значение (clamped до {@code max}).
     * Если квест не активен — операция игнорируется, возвращается 0.
     */
    public long addProgress(QuestKey k, String objId, long delta, long max) {
        Map<String, Long> map = active.get(k);
        if (map == null) return 0L;
        long current = map.getOrDefault(objId, 0L);
        long target = Math.min(current + delta, max);
        if (target < 0L) target = 0L;
        map.put(objId, target);
        return target;
    }

    /**
     * Снимок прогресса всех objective'ов квеста — для UI/sync.
     * Возвращает unmodifiable view (или пустую карту, если квест не активен).
     */
    public Map<String, Long> getObjectiveProgressMap(QuestKey k) {
        Map<String, Long> map = active.get(k);
        return map == null ? Collections.emptyMap() : Collections.unmodifiableMap(map);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void acceptQuest(QuestKey k, long timestamp, List<String> objectiveIds) {
        Map<String, Long> objs = new HashMap<>();
        for (String id : objectiveIds) objs.put(id, 0L);
        active.put(k, objs);
        startedAt.put(k, timestamp);
    }

    public void abandonQuest(QuestKey k) {
        active.remove(k);
        startedAt.remove(k);
    }

    public void markCompleted(QuestKey k, long timestamp) {
        active.remove(k);
        startedAt.remove(k);
        completed.put(k, timestamp);
    }

    // -------------------------------------------------------------------------
    // Iteration (defensive copies — safe to mutate during iteration)
    // -------------------------------------------------------------------------

    public Set<QuestKey> getActiveKeys() {
        return new HashSet<>(active.keySet());
    }

    public Set<QuestKey> getCompletedKeys() {
        return new HashSet<>(completed.keySet());
    }

    @Nullable
    public Long getStartedAt(QuestKey k) {
        return startedAt.get(k);
    }

    /**
     * Stage 8 (v2.9.8): timestamp выполнения квеста, 0 если квест не в completed.
     * Используется для строки «Завершён: %s» в Player Quest Journal.
     */
    public long getCompletedAt(QuestKey k) {
        Long v = completed.get(k);
        return v != null ? v : 0L;
    }

    /**
     * Тихо удаляет из active все ключи, которые НЕ присутствуют в {@code validKeys}
     * (decision §2 от 2026-05-25 — no chat notification).
     * Вызывается на login для случая «админ удалил квест/NPC, пока игрок офлайн».
     */
    public void cleanupOrphaned(Set<QuestKey> validKeys) {
        active.keySet().removeIf(k -> !validKeys.contains(k));
        startedAt.keySet().removeIf(k -> !validKeys.contains(k));
        // completed мы НЕ чистим — даже если квест удалён, факт его прохождения остаётся
    }

    // -------------------------------------------------------------------------
    // NBT serialization
    // -------------------------------------------------------------------------

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag root = new CompoundTag();

        ListTag activeList = new ListTag();
        for (Map.Entry<QuestKey, Map<String, Long>> e : active.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.put("Key", e.getKey().save());
            CompoundTag progs = new CompoundTag();
            for (Map.Entry<String, Long> p : e.getValue().entrySet()) {
                progs.putLong(p.getKey(), p.getValue());
            }
            entry.put("Progress", progs);
            activeList.add(entry);
        }
        root.put("Active", activeList);

        ListTag completedList = new ListTag();
        for (Map.Entry<QuestKey, Long> e : completed.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.put("Key", e.getKey().save());
            entry.putLong("Timestamp", e.getValue());
            completedList.add(entry);
        }
        root.put("Completed", completedList);

        ListTag startedList = new ListTag();
        for (Map.Entry<QuestKey, Long> e : startedAt.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.put("Key", e.getKey().save());
            entry.putLong("Timestamp", e.getValue());
            startedList.add(entry);
        }
        root.put("StartedAt", startedList);

        return root;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        active.clear();
        completed.clear();
        startedAt.clear();

        if (nbt.contains("Active", Tag.TAG_LIST)) {
            ListTag list = nbt.getList("Active", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                QuestKey key = QuestKey.load(entry.getCompound("Key"));
                CompoundTag progs = entry.getCompound("Progress");
                Map<String, Long> objs = new HashMap<>();
                for (String objId : progs.getAllKeys()) {
                    objs.put(objId, progs.getLong(objId));
                }
                active.put(key, objs);
            }
        }

        if (nbt.contains("Completed", Tag.TAG_LIST)) {
            ListTag list = nbt.getList("Completed", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                QuestKey key = QuestKey.load(entry.getCompound("Key"));
                completed.put(key, entry.getLong("Timestamp"));
            }
        }

        if (nbt.contains("StartedAt", Tag.TAG_LIST)) {
            ListTag list = nbt.getList("StartedAt", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                QuestKey key = QuestKey.load(entry.getCompound("Key"));
                startedAt.put(key, entry.getLong("Timestamp"));
            }
        }
    }
}
