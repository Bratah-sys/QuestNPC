package com.questnpc.entity.quest.objective;

import com.questnpc.entity.quest.ObjectiveType;
import com.questnpc.entity.quest.QuestObjective;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;

import javax.annotation.Nullable;

/**
 * Цель «посетить структуру W». Триггер — polling в PlayerTickEvent.
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

    // === НОВЫЙ МЕТОД: Реальная проверка структуры ===
    public boolean matches(ServerPlayer player) {
        if (structureId == null) return false;
        ServerLevel level = player.serverLevel();

        // Получаем объект структуры из реестра игры
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structureId);
        if (structure == null) return false;

        // Проверяем, находится ли игрок внутри bounding box (границ) этой структуры
        return level.structureManager().getStructureWithPieceAt(player.blockPosition(), structure).isValid();
    }
}