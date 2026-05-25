package com.questnpc.entity.quest.objective;

import com.questnpc.entity.quest.ObjectiveType;
import com.questnpc.entity.quest.QuestObjective;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

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

    /**
     * Stage 5 (v2.9.4): проверка нахождения игрока внутри bounding box целевой структуры.
     * Использует {@code structureManager().getStructureWithPieceAt(BlockPos, ResourceKey)}.
     * Возвращает {@code false} если структура не найдена в registry или игрок не в её bbox.
     */
    public boolean isInStructure(ServerPlayer p) {
        if (structureId == null || p == null) return false;
        ServerLevel lvl = p.serverLevel();
        ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, structureId);
        StructureStart start = lvl.structureManager().getStructureWithPieceAt(p.blockPosition(), structureKey);
        return start != null && start.isValid();
    }

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
