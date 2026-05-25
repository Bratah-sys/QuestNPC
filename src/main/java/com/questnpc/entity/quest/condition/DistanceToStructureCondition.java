package com.questnpc.entity.quest.condition;

import com.questnpc.entity.quest.ConditionType;
import com.questnpc.entity.quest.QuestCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * Условие «дистанция до ближайшей структуры данного типа &ge; minDistance блоков».
 * Главный use-case (с флагом inverted=true): «квест НЕ выдаётся, если игрок ближе чем N
 * к структуре W» — это критическое требование пользователя.
 *
 * <p>Этап 1: isMet — заглушка {@code return true}. Реальная реализация через
 * {@code ServerLevel.getChunkSource().getGenerator().findNearestMapStructure(...)} — этап 4.
 */
public class DistanceToStructureCondition extends QuestCondition {

    @Nullable private ResourceLocation structureId;
    private int minDistance = 64;

    @Override
    public ConditionType getType() { return ConditionType.DISTANCE_TO_STRUCTURE; }

    @Nullable public ResourceLocation getStructureId() { return structureId; }
    public void setStructureId(@Nullable ResourceLocation v) { this.structureId = v; }
    public int getMinDistance() { return minDistance; }
    public void setMinDistance(int v) { this.minDistance = Math.max(0, v); }

    @Override
    public boolean isMet(ServerPlayer player, BlockPos npcPos) {
        // TODO Stage 4: реальная проверка через findNearestMapStructure + sqrDist
        return true;
    }

    @Override
    protected void writeData(CompoundTag tag) {
        if (structureId != null) tag.putString("StructureId", structureId.toString());
        tag.putInt("MinDistance", minDistance);
    }

    @Override
    protected void readData(CompoundTag tag) {
        this.structureId = tag.contains("StructureId")
                ? ResourceLocation.tryParse(tag.getString("StructureId"))
                : null;
        this.minDistance = Math.max(0, tag.getInt("MinDistance"));
    }
}
