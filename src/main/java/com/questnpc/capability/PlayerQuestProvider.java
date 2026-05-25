package com.questnpc.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Forge Capability provider для {@link PlayerQuestProgress}.
 * Attached к каждому Player через {@code AttachCapabilitiesEvent} в
 * {@link com.questnpc.events.QuestEventHandler#attachCaps}.
 *
 * <p>Capability регистрируется в {@link com.questnpc.events.ModCapabilities}.
 */
public class PlayerQuestProvider implements ICapabilitySerializable<CompoundTag> {

    public static final Capability<PlayerQuestProgress> CAP =
            CapabilityManager.get(new CapabilityToken<>() {});

    private final PlayerQuestProgress instance = new PlayerQuestProgress();
    private final LazyOptional<PlayerQuestProgress> opt = LazyOptional.of(() -> instance);

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == CAP ? opt.cast() : LazyOptional.empty();
    }

    /** Вызывается в {@code PlayerEvent.Clone} (см. {@link com.questnpc.events.QuestEventHandler}). */
    public void invalidate() {
        opt.invalidate();
    }

    @Override
    public CompoundTag serializeNBT() {
        return instance.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        instance.deserializeNBT(nbt);
    }
}
