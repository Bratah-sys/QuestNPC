package com.questnpc.capability;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Уникальный идентификатор квеста в контексте одного NPC.
 * Используется в {@link PlayerQuestProgress} как ключ Map для active/completed/startedAt.
 *
 * <p>Stage 5 (v2.9.4) — добавлено для player-progress capability.
 */
public record QuestKey(UUID npcUuid, String questId) {

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("NpcUuid", npcUuid);
        tag.putString("QuestId", questId);
        return tag;
    }

    public static QuestKey load(CompoundTag tag) {
        return new QuestKey(tag.getUUID("NpcUuid"), tag.getString("QuestId"));
    }
}
