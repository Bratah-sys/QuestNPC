package com.questnpc.entity.quest.objective;

import com.questnpc.entity.quest.ObjectiveType;
import com.questnpc.entity.quest.QuestObjective;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

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

    // === НОВЫЙ МЕТОД Добавлен здесь ===
    public boolean matches(net.minecraft.server.level.ServerPlayer player) {
        if (biomeId == null) return false;
        // Проверяем, совпадает ли ID биома, в котором стоит игрок, с нужным
        return player.serverLevel().getBiome(player.blockPosition())
                .unwrapKey()
                .map(key -> key.location().equals(biomeId))
                .orElse(false);
    }
}