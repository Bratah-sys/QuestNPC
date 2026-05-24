package com.questnpc.entity.quest.objective;

import com.questnpc.entity.quest.ObjectiveType;
import com.questnpc.entity.quest.QuestObjective;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Цель «посетить структуру W». Триггер — polling в PlayerTickEvent (этап 5),
 * проверка через {@code ServerLevel.structureManager().getStructureWithPieceAt(...)}.
 * Pattern заимствован у FTB Quests {@code StructureTask}.
 */
public class ReachStructureObjective extends QuestObjective {

    @Nullable private ResourceLocation structureId;

    @Override
    public ObjectiveType getType() { return ObjectiveType.REACH_STRUCTURE; }

    @Override
    public int autoCheckIntervalTicks() { return 20; }

    @Override
    public boolean checkOnLogin() { return true; }

    @Nullable public ResourceLocation getStructureId() { return structureId; }
    public void setStructureId(@Nullable ResourceLocation v) { this.structureId = v; }

    @Override
    protected void writeData(CompoundTag tag) {
        if (structureId != null) tag.putString("StructureId", structureId.toString());
    }

    @Override
    protected void readData(CompoundTag tag) {
        this.structureId = tag.contains("StructureId")
                ? ResourceLocation.tryParse(tag.getString("StructureId"))
                : null;
    }
}
