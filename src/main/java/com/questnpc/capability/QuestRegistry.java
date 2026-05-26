package com.questnpc.capability;

import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.quest.QuestDefinition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side helper для резолва {@link QuestKey} → {@link QuestDefinition}.
 *
 * <p>Используется в {@link com.questnpc.events.QuestEventHandler} (kill/break/tick
 * events) и {@link com.questnpc.entity.QuestNPCEntity#startServerQuestInteraction}.
 *
 * <p><b>Cost:</b> {@code lookup} = O(W) где W ≈ 3 (загруженные миры). Forge
 * {@code ServerLevel.getEntity(UUID)} — кэш по uuid, O(1) per level. Для kill/break
 * events (≤10/сек) — pesto.
 *
 * <p>{@code collectAllValidKeys} итерирует ВСЕ entities во всех загруженных мирах
 * — дорого, вызывается ТОЛЬКО на login (см. cleanupOrphaned).
 */
public final class QuestRegistry {

    private QuestRegistry() {}

    /** Найти QuestDefinition по (npcUuid, questId). Возвращает null если NPC или квест не найдены. */
    @Nullable
    public static QuestDefinition lookup(QuestKey key, MinecraftServer server) {
        if (server == null) return null;
        QuestNPCEntity npc = lookupNpc(key.npcUuid(), server);
        if (npc == null) return null;
        for (QuestDefinition q : npc.getQuests()) {
            if (q.getId().equals(key.questId())) return q;
        }
        return null;
    }

    /** Найти QuestNPCEntity по UUID во всех загруженных мирах. */
    @Nullable
    public static QuestNPCEntity lookupNpc(UUID npcUuid, MinecraftServer server) {
        if (server == null) return null;
        for (ServerLevel lvl : server.getAllLevels()) {
            Entity e = lvl.getEntity(npcUuid);
            if (e instanceof QuestNPCEntity npc) return npc;
        }
        return null;
    }

    /**
     * Собирает множество всех валидных {@link QuestKey} (npcUuid+questId) на сервере.
     * Используется в {@link PlayerQuestProgress#cleanupOrphaned(Set)} на login.
     */
    public static Set<QuestKey> collectAllValidKeys(MinecraftServer server) {
        Set<QuestKey> all = new HashSet<>();
        if (server == null) return all;
        for (ServerLevel lvl : server.getAllLevels()) {
            for (Entity e : lvl.getAllEntities()) {
                if (e instanceof QuestNPCEntity npc) {
                    for (QuestDefinition q : npc.getQuests()) {
                        all.add(new QuestKey(npc.getUUID(), q.getId()));
                    }
                }
            }
        }
        return all;
    }
}
