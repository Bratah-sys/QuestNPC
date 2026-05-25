package com.questnpc.entity.quest.condition;

import com.mojang.datafixers.util.Pair;
import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.quest.ConditionType;
import com.questnpc.entity.quest.QuestCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;

import javax.annotation.Nullable;

/**
 * Условие «дистанция до ближайшей структуры данного типа &ge; minDistance блоков».
 * Главный use-case (с флагом inverted=true): «квест НЕ выдаётся, если игрок ближе чем N
 * к структуре W» — это критическое требование пользователя.
 *
 * <p>v2.9.3 (Stage 4): реализован {@link #isMet} через
 * {@code ChunkGenerator.findNearestMapStructure} с per-NPC кэшем
 * {@link QuestNPCEntity#getNearestStructureCached}. Search radius — 100 чанков
 * (1600 блоков). Если admin задаёт {@code minDistance > 1600} и структура есть
 * вне search radius — возможен false-negative (условие проходит, хотя структура
 * существует за пределами поиска). Документировано в research §3.7.
 */
public class DistanceToStructureCondition extends QuestCondition {

    /** Search radius для findNearestMapStructure (в чанках). 100 = 1600 блоков, как у /locate. */
    private static final int SEARCH_RADIUS_CHUNKS = 100;

    @Nullable private ResourceLocation structureId;
    private int minDistance = 64;

    @Override
    public ConditionType getType() { return ConditionType.DISTANCE_TO_STRUCTURE; }

    @Nullable public ResourceLocation getStructureId() { return structureId; }
    public void setStructureId(@Nullable ResourceLocation v) { this.structureId = v; }
    public int getMinDistance() { return minDistance; }
    public void setMinDistance(int v) { this.minDistance = Math.max(0, v); }

    @Override
    public boolean isMet(ServerPlayer player, BlockPos npcPos, @Nullable QuestNPCEntity npc) {
        if (structureId == null) return true; // не сконфигурировано — пропускаем (UI ловит на validate)

        ServerLevel lvl = player.serverLevel();
        Registry<Structure> registry = lvl.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Structure structure = registry.get(structureId);
        if (structure == null) {
            QuestNPCLogger.debug("DistanceToStructure: unknown structureId={}, condition passes by default",
                    structureId);
            return true;
        }

        BlockPos nearest;
        if (npc != null) {
            // Cache-aware path (typical)
            nearest = npc.getNearestStructureCached(structureId, lvl);
        } else {
            // Fallback: прямой запрос без кэша
            nearest = findNearestStructureDirect(lvl, registry, structure, npcPos);
        }

        if (nearest == null) return true; // ничего не нашли в радиусе поиска — условие выполнено

        double distSqr = nearest.distSqr(npcPos);
        return distSqr >= (double) minDistance * minDistance;
    }

    /**
     * Прямой вызов {@code findNearestMapStructure}, без кэша. Используется когда
     * {@link #isMet} вызван без NPC-контекста.
     */
    public static @Nullable BlockPos findNearestStructureDirect(ServerLevel lvl,
                                                                Registry<Structure> registry,
                                                                Structure structure,
                                                                BlockPos from) {
        try {
            HolderSet<Structure> holderSet = HolderSet.direct(registry.wrapAsHolder(structure));
            Pair<BlockPos, Holder<Structure>> result = lvl.getChunkSource().getGenerator()
                    .findNearestMapStructure(lvl, holderSet, from, SEARCH_RADIUS_CHUNKS, false);
            return result != null ? result.getFirst() : null;
        } catch (Exception ex) {
            QuestNPCLogger.warn("findNearestStructureDirect failed: {}", ex.getMessage());
            return null;
        }
    }

    public static int searchRadiusChunks() { return SEARCH_RADIUS_CHUNKS; }

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
