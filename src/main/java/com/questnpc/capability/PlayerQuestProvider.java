package com.questnpc.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerQuestProvider implements ICapabilitySerializable<CompoundTag> {

    // Токен для доступа к Capability в любой точке кода
    public static final Capability<PlayerQuestData> PLAYER_QUEST_DATA = CapabilityManager.get(new CapabilityToken<>() {});

    private PlayerQuestData questData = null;
    private final LazyOptional<PlayerQuestData> optional = LazyOptional.of(this::createQuestData);

    private PlayerQuestData createQuestData() {
        if (this.questData == null) {
            this.questData = new PlayerQuestData();
        }
        return this.questData;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == PLAYER_QUEST_DATA) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return createQuestData().saveNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        createQuestData().loadNBT(nbt);
    }
}