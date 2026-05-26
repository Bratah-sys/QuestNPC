package com.questnpc.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO snapshots для S→C передачи списка квестов NPC.
 * Все описания (objective/reward) формируются на сервере как готовые строки —
 * клиент только рендерит.
 *
 * <p>Stage 5 (v2.9.4) — для {@link OpenPlayerQuestListPacket}.
 */
public final class QuestSnapshots {

    private QuestSnapshots() {}

    /** Базовый snapshot — для offerable и turnInReady (без прогресса). */
    public record QuestSnapshot(
            String questId,
            String title,
            String description,
            List<String> objectiveDescriptions,
            List<String> rewardDescriptions
    ) {
        public static void encode(QuestSnapshot q, FriendlyByteBuf buf) {
            buf.writeUtf(q.questId);
            buf.writeUtf(q.title);
            buf.writeUtf(q.description);
            buf.writeVarInt(q.objectiveDescriptions.size());
            for (String s : q.objectiveDescriptions) buf.writeUtf(s);
            buf.writeVarInt(q.rewardDescriptions.size());
            for (String s : q.rewardDescriptions) buf.writeUtf(s);
        }

        public static QuestSnapshot decode(FriendlyByteBuf buf) {
            String questId = buf.readUtf();
            String title = buf.readUtf();
            String description = buf.readUtf();
            int oc = buf.readVarInt();
            List<String> objs = new ArrayList<>(oc);
            for (int i = 0; i < oc; i++) objs.add(buf.readUtf());
            int rc = buf.readVarInt();
            List<String> rewards = new ArrayList<>(rc);
            for (int i = 0; i < rc; i++) rewards.add(buf.readUtf());
            return new QuestSnapshot(questId, title, description, objs, rewards);
        }
    }

    /** Snapshot одной цели с прогрессом — для active-вкладки. */
    public record ObjectiveProgressSnapshot(String description, long current, long max) {
        public static void encode(ObjectiveProgressSnapshot o, FriendlyByteBuf buf) {
            buf.writeUtf(o.description);
            buf.writeVarLong(o.current);
            buf.writeVarLong(o.max);
        }

        public static ObjectiveProgressSnapshot decode(FriendlyByteBuf buf) {
            return new ObjectiveProgressSnapshot(buf.readUtf(), buf.readVarLong(), buf.readVarLong());
        }
    }

    /** Snapshot активного квеста с прогрессом по каждой цели. */
    public record QuestProgressSnapshot(
            String questId,
            String title,
            String description,
            List<ObjectiveProgressSnapshot> objectives,
            List<String> rewardDescriptions
    ) {
        public static void encode(QuestProgressSnapshot q, FriendlyByteBuf buf) {
            buf.writeUtf(q.questId);
            buf.writeUtf(q.title);
            buf.writeUtf(q.description);
            buf.writeVarInt(q.objectives.size());
            for (ObjectiveProgressSnapshot o : q.objectives) ObjectiveProgressSnapshot.encode(o, buf);
            buf.writeVarInt(q.rewardDescriptions.size());
            for (String s : q.rewardDescriptions) buf.writeUtf(s);
        }

        public static QuestProgressSnapshot decode(FriendlyByteBuf buf) {
            String questId = buf.readUtf();
            String title = buf.readUtf();
            String description = buf.readUtf();
            int oc = buf.readVarInt();
            List<ObjectiveProgressSnapshot> objs = new ArrayList<>(oc);
            for (int i = 0; i < oc; i++) objs.add(ObjectiveProgressSnapshot.decode(buf));
            int rc = buf.readVarInt();
            List<String> rewards = new ArrayList<>(rc);
            for (int i = 0; i < rc; i++) rewards.add(buf.readUtf());
            return new QuestProgressSnapshot(questId, title, description, objs, rewards);
        }
    }
}
