package com.questnpc.network;

import com.questnpc.capability.QuestKey;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stage 8 (v2.9.8): client-side DTO для одного квеста в Player Quest Journal.
 *
 * <p>Содержит всё что нужно для рендера карточки в journal'е — title/description/source NPC/
 * objectives (с прогрессом current/max) / rewards. Создаётся на сервере при отправке
 * {@link SyncPlayerQuestProgressPacket}, кэшируется на клиенте в
 * {@link com.questnpc.client.ClientJournalCache}.
 *
 * <p>Принципиальное отличие от {@link QuestSnapshots} — entries приходят для ВСЕХ квестов
 * игрока (с любого NPC и любого мира), не только для конкретного NPC. NPC может быть
 * в другом измерении / далеко от игрока — клиент не имеет к нему доступа, поэтому все
 * descriptions формируются server-side.
 *
 * @param questKey   ключ (npcUuid + questId) — используется для abandon из journal'а
 * @param npcDisplayName имя NPC, у которого взят квест (для строки «От: %s»)
 * @param title       заголовок квеста
 * @param description описание (может быть пустым)
 * @param objectives  для активных — описания + прогресс current/max; для completed — empty list
 * @param rewardDescriptions  строки наград — для активных рендерим как hint, для completed скрываем
 * @param completedAt timestamp выполнения (0 если активный); используется для строки «Завершён: %s»
 * @param completed   признак что квест в completed (true) vs active (false)
 */
public record QuestJournalEntry(
        QuestKey questKey,
        String npcDisplayName,
        String title,
        String description,
        List<QuestSnapshots.ObjectiveProgressSnapshot> objectives,
        List<String> rewardDescriptions,
        long completedAt,
        boolean completed
) {

    public static void encode(QuestJournalEntry e, FriendlyByteBuf buf) {
        buf.writeUUID(e.questKey.npcUuid());
        buf.writeUtf(e.questKey.questId());
        buf.writeUtf(e.npcDisplayName);
        buf.writeUtf(e.title);
        buf.writeUtf(e.description);
        buf.writeVarInt(e.objectives.size());
        for (QuestSnapshots.ObjectiveProgressSnapshot o : e.objectives) {
            QuestSnapshots.ObjectiveProgressSnapshot.encode(o, buf);
        }
        buf.writeVarInt(e.rewardDescriptions.size());
        for (String s : e.rewardDescriptions) buf.writeUtf(s);
        buf.writeVarLong(e.completedAt);
        buf.writeBoolean(e.completed);
    }

    public static QuestJournalEntry decode(FriendlyByteBuf buf) {
        UUID npcUuid = buf.readUUID();
        String questId = buf.readUtf();
        QuestKey key = new QuestKey(npcUuid, questId);
        String npcDisplayName = buf.readUtf();
        String title = buf.readUtf();
        String description = buf.readUtf();
        int oc = buf.readVarInt();
        List<QuestSnapshots.ObjectiveProgressSnapshot> objectives = new ArrayList<>(oc);
        for (int i = 0; i < oc; i++) objectives.add(QuestSnapshots.ObjectiveProgressSnapshot.decode(buf));
        int rc = buf.readVarInt();
        List<String> rewards = new ArrayList<>(rc);
        for (int i = 0; i < rc; i++) rewards.add(buf.readUtf());
        long completedAt = buf.readVarLong();
        boolean completed = buf.readBoolean();
        return new QuestJournalEntry(key, npcDisplayName, title, description, objectives, rewards, completedAt, completed);
    }
}
