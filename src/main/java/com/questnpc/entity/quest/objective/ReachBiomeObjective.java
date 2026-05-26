package com.questnpc.entity.quest.objective;

import com.questnpc.entity.quest.ObjectiveType;
import com.questnpc.entity.quest.QuestObjective;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;

import javax.annotation.Nullable;

/**
 * Цель «достичь биома Z». Триггер — polling в PlayerTickEvent (этап 5),
 * + check-on-login для случая, когда игрок офлайн уже в биоме.
 */
public class ReachBiomeObjective extends QuestObjective {

    @Nullable private ResourceLocation biomeId;

    @Override
    public ObjectiveType getType() { return ObjectiveType.REACH_BIOME; }

    @Override
    public int autoCheckIntervalTicks() { return 20; }

    @Override
    public boolean checkOnLogin() { return true; }

    @Nullable public ResourceLocation getBiomeId() { return biomeId; }
    public void setBiomeId(@Nullable ResourceLocation v) { this.biomeId = v; }

    /**
     * Stage 5 (v2.9.4): проверка нахождения игрока в целевом биоме.
     * Сравнение по ResourceKey (point-sample на координате игрока).
     */
    public boolean isInBiome(ServerPlayer p) {
        if (biomeId == null || p == null) return false;
        Holder<Biome> biome = p.level().getBiome(p.blockPosition());
        return biome.unwrapKey()
                .map(k -> k.location().equals(biomeId))
                .orElse(false);
    }

    @Override
    protected void writeData(CompoundTag tag) {
        if (biomeId != null) tag.putString("BiomeId", biomeId.toString());
    }

    @Override
    protected void readData(CompoundTag tag) {
        this.biomeId = tag.contains("BiomeId")
                ? ResourceLocation.tryParse(tag.getString("BiomeId"))
                : null;
    }
}
