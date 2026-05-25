package com.questnpc.entity.quest.objective;

import com.questnpc.entity.quest.ObjectiveType;
import com.questnpc.entity.quest.QuestObjective;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Цель «сломать N блоков типа Y» либо «сломать N блоков из тега #tag».
 *
 * <p>Этап 1: skeleton. Tag-mode согласован 2026-05-25.
 * {@link #dropRequired} в MVP игнорируется (см. research §3.8 п.3).
 * Реальный триггер — {@code BlockEvent.BreakEvent} в этапе 5.
 */
public class BreakBlockObjective extends QuestObjective {

    @Nullable private ResourceLocation blockId;  // если tagMode=false
    @Nullable private TagKey<Block> blockTag;    // если tagMode=true
    private boolean tagMode;
    private int count = 1;
    private boolean dropRequired = false;

    @Override
    public ObjectiveType getType() { return ObjectiveType.BREAK_BLOCK; }

    @Override
    public long getMaxProgress() { return Math.max(1, count); }

    @Nullable public ResourceLocation getBlockId() { return blockId; }
    public void setBlockId(@Nullable ResourceLocation v) { this.blockId = v; }
    @Nullable public TagKey<Block> getBlockTag() { return blockTag; }
    public void setBlockTag(@Nullable TagKey<Block> v) { this.blockTag = v; }
    public boolean isTagMode() { return tagMode; }
    public void setTagMode(boolean v) { this.tagMode = v; }
    public int getCount() { return count; }
    public void setCount(int v) { this.count = Math.max(1, v); }
    public boolean isDropRequired() { return dropRequired; }
    public void setDropRequired(boolean v) { this.dropRequired = v; }

    /**
     * Проверяет, соответствует ли сломанный блок типу/тегу из этого objective.
     * {@code dropRequired} в MVP игнорируется (см. research §3.8 п.3) — любой break засчитывается.
     * Stage 5 (v2.9.4): real implementation.
     */
    public boolean matches(BlockState state) {
        if (state == null) return false;
        if (tagMode) {
            if (blockTag == null) return false;
            return state.is(blockTag);
        }
        if (blockId == null) return false;
        ResourceLocation brokenId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return blockId.equals(brokenId);
    }

    @Override
    protected void writeData(CompoundTag tag) {
        tag.putBoolean("TagMode", tagMode);
        tag.putInt("Count", count);
        tag.putBoolean("DropRequired", dropRequired);
        if (tagMode) {
            if (blockTag != null) {
                tag.putString("BlockTag", blockTag.location().toString());
            }
        } else {
            if (blockId != null) {
                tag.putString("BlockId", blockId.toString());
            }
        }
    }

    @Override
    protected void readData(CompoundTag tag) {
        this.tagMode = tag.getBoolean("TagMode");
        this.count = Math.max(1, tag.getInt("Count"));
        this.dropRequired = tag.getBoolean("DropRequired");
        this.blockId = null;
        this.blockTag = null;
        if (tagMode) {
            if (tag.contains("BlockTag")) {
                ResourceLocation rl = ResourceLocation.tryParse(tag.getString("BlockTag"));
                if (rl != null) blockTag = TagKey.create(Registries.BLOCK, rl);
            }
        } else {
            if (tag.contains("BlockId")) {
                blockId = ResourceLocation.tryParse(tag.getString("BlockId"));
            }
        }
    }
}
