package com.questnpc.entity.quest;

import com.questnpc.QuestNPCLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Определение одного квеста. Хранится на {@link com.questnpc.entity.QuestNPCEntity}
 * в NBT-ключе {@code Quests}.
 *
 * <p>Лимиты согласованы 2026-05-25 (research §0.5 п.4): 20 квестов на NPC,
 * 10 objectives / 5 rewards / 10 prerequisites на квест.
 *
 * <p>Этап 1: skeleton + сериализация. {@link #canOfferTo} — заглушка
 * (без проверки prerequisites). Реальная проверка conditions — этап 4.
 */
public final class QuestDefinition {

    public static final int MAX_OBJECTIVES = 10;
    public static final int MAX_REWARDS = 5;
    public static final int MAX_PREREQUISITES = 10;
    public static final int MAX_TITLE_LENGTH = 48;
    public static final int MAX_DESCRIPTION_LENGTH = 256;

    private String id;
    private String title = "";
    private String description = "";
    private boolean enabled = true;

    private final List<QuestObjective> objectives = new ArrayList<>();
    private final List<QuestReward> rewards = new ArrayList<>();
    private final List<QuestCondition> prerequisites = new ArrayList<>();

    public QuestDefinition() {
        this.id = UUID.randomUUID().toString();
    }

    // --- getters/setters ---
    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String v) {
        if (v == null) v = "";
        if (v.length() > MAX_TITLE_LENGTH) v = v.substring(0, MAX_TITLE_LENGTH);
        this.title = v;
    }
    public String getDescription() { return description; }
    public void setDescription(String v) {
        if (v == null) v = "";
        if (v.length() > MAX_DESCRIPTION_LENGTH) v = v.substring(0, MAX_DESCRIPTION_LENGTH);
        this.description = v;
    }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }

    public List<QuestObjective> getObjectives() { return Collections.unmodifiableList(objectives); }
    public List<QuestReward> getRewards() { return Collections.unmodifiableList(rewards); }
    public List<QuestCondition> getPrerequisites() { return Collections.unmodifiableList(prerequisites); }

    public boolean addObjective(QuestObjective obj) {
        if (obj == null || objectives.size() >= MAX_OBJECTIVES) return false;
        objectives.add(obj);
        return true;
    }
    public void removeObjective(int index) {
        if (index >= 0 && index < objectives.size()) objectives.remove(index);
    }
    public void clearObjectives() { objectives.clear(); }

    public boolean addReward(QuestReward r) {
        if (r == null || rewards.size() >= MAX_REWARDS) return false;
        rewards.add(r);
        return true;
    }
    public void removeReward(int index) {
        if (index >= 0 && index < rewards.size()) rewards.remove(index);
    }
    public void clearRewards() { rewards.clear(); }

    public boolean addPrerequisite(QuestCondition c) {
        if (c == null || prerequisites.size() >= MAX_PREREQUISITES) return false;
        prerequisites.add(c);
        return true;
    }
    public void removePrerequisite(int index) {
        if (index >= 0 && index < prerequisites.size()) prerequisites.remove(index);
    }
    public void clearPrerequisites() { prerequisites.clear(); }

    /**
     * Этап 1 stub: возвращает {@link #enabled}, не проверяет prerequisites.
     * Реальная проверка conditions — этап 4.
     */
    public boolean canOfferTo(ServerPlayer player, BlockPos npcPos) {
        // TODO Stage 4: iterate prerequisites, apply inverted-logic, return AND of all
        return enabled;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("Title", title);
        tag.putString("Description", description);
        tag.putBoolean("Enabled", enabled);

        ListTag objList = new ListTag();
        for (QuestObjective o : objectives) objList.add(o.save());
        tag.put("Objectives", objList);

        ListTag rewList = new ListTag();
        for (QuestReward r : rewards) rewList.add(r.save());
        tag.put("Rewards", rewList);

        ListTag prereqList = new ListTag();
        for (QuestCondition c : prerequisites) prereqList.add(c.save());
        tag.put("Prerequisites", prereqList);

        return tag;
    }

    public static QuestDefinition load(CompoundTag tag) {
        QuestDefinition q = new QuestDefinition();
        q.id = tag.contains("Id") ? tag.getString("Id") : UUID.randomUUID().toString();
        q.title = tag.contains("Title") ? tag.getString("Title") : "";
        q.description = tag.contains("Description") ? tag.getString("Description") : "";
        q.enabled = tag.contains("Enabled") ? tag.getBoolean("Enabled") : true;

        if (tag.contains("Objectives", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Objectives", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && q.objectives.size() < MAX_OBJECTIVES; i++) {
                QuestObjective obj = QuestObjective.loadByType(list.getCompound(i));
                if (obj != null) {
                    q.objectives.add(obj);
                } else {
                    QuestNPCLogger.warn("QuestDefinition '{}': failed to load objective at index {}",
                            q.id, i);
                }
            }
        }

        if (tag.contains("Rewards", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Rewards", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && q.rewards.size() < MAX_REWARDS; i++) {
                QuestReward r = QuestReward.loadByType(list.getCompound(i));
                if (r != null) {
                    q.rewards.add(r);
                } else {
                    QuestNPCLogger.warn("QuestDefinition '{}': failed to load reward at index {}",
                            q.id, i);
                }
            }
        }

        if (tag.contains("Prerequisites", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Prerequisites", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && q.prerequisites.size() < MAX_PREREQUISITES; i++) {
                QuestCondition c = QuestCondition.loadByType(list.getCompound(i));
                if (c != null) {
                    q.prerequisites.add(c);
                } else {
                    QuestNPCLogger.warn("QuestDefinition '{}': failed to load prerequisite at index {}",
                            q.id, i);
                }
            }
        }

        return q;
    }
}
