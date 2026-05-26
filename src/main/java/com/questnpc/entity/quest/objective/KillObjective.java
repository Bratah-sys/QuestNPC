package com.questnpc.entity.quest.objective;

import com.questnpc.entity.quest.ObjectiveType;
import com.questnpc.entity.quest.QuestObjective;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Цель «убить N мобов типа Y» либо «убить N мобов из тега #tag».
 *
 * <p>Этап 1: skeleton — поля + сериализация. {@link #matches(Entity)} — заглушка {@code false}.
 * Реальная проверка убийства — в этапе 5 через {@code LivingDeathEvent}.
 *
 * <p>Tag-mode согласован с пользователем 2026-05-25 (research §0.5 п.6).
 * UI выбора тега ({@code EntityTagPickerScreen}) — этап 3.
 *
 * <p>{@link #lootDrop} — опциональный itemstack, который GLM (этап 7) добавит в drop
 * убитого моба, если у игрока активен этот objective. В этапе 1 не используется.
 */
public class KillObjective extends QuestObjective {

    @Nullable private ResourceLocation entityType;          // если tagMode=false
    @Nullable private TagKey<EntityType<?>> entityTypeTag;  // если tagMode=true
    private boolean tagMode;
    private int count = 1;
    private ItemStack lootDrop = ItemStack.EMPTY;

    @Override
    public ObjectiveType getType() { return ObjectiveType.KILL; }

    @Override
    public long getMaxProgress() { return Math.max(1, count); }

    // --- getters/setters ---
    @Nullable public ResourceLocation getEntityType() { return entityType; }
    public void setEntityType(@Nullable ResourceLocation v) { this.entityType = v; }
    @Nullable public TagKey<EntityType<?>> getEntityTypeTag() { return entityTypeTag; }
    public void setEntityTypeTag(@Nullable TagKey<EntityType<?>> v) { this.entityTypeTag = v; }
    public boolean isTagMode() { return tagMode; }
    public void setTagMode(boolean v) { this.tagMode = v; }
    public int getCount() { return count; }
    public void setCount(int v) { this.count = Math.max(1, v); }
    public ItemStack getLootDrop() { return lootDrop; }
    public void setLootDrop(ItemStack v) { this.lootDrop = v != null ? v : ItemStack.EMPTY; }

    /**
     * Проверяет, соответствует ли убитая сущность типу/тегу из этого objective.
     * Stage 5 (v2.9.4): real implementation.
     */
    public boolean matches(Entity killed) {
        if (killed == null) return false;
        if (tagMode) {
            if (entityTypeTag == null) return false;
            return killed.getType().is(entityTypeTag);
        }
        if (entityType == null) return false;
        ResourceLocation killedId = ForgeRegistries.ENTITY_TYPES.getKey(killed.getType());
        return entityType.equals(killedId);
    }

    @Override
    protected void writeData(CompoundTag tag) {
        tag.putBoolean("TagMode", tagMode);
        tag.putInt("Count", count);
        if (tagMode) {
            if (entityTypeTag != null) {
                tag.putString("EntityTag", entityTypeTag.location().toString());
            }
        } else {
            if (entityType != null) {
                tag.putString("EntityId", entityType.toString());
            }
        }
        if (!lootDrop.isEmpty()) {
            tag.put("LootDrop", lootDrop.save(new CompoundTag()));
        }
    }

    @Override
    protected void readData(CompoundTag tag) {
        this.tagMode = tag.getBoolean("TagMode");
        this.count = Math.max(1, tag.getInt("Count"));
        this.entityType = null;
        this.entityTypeTag = null;
        if (tagMode) {
            if (tag.contains("EntityTag")) {
                ResourceLocation rl = ResourceLocation.tryParse(tag.getString("EntityTag"));
                if (rl != null) entityTypeTag = TagKey.create(Registries.ENTITY_TYPE, rl);
            }
        } else {
            if (tag.contains("EntityId")) {
                entityType = ResourceLocation.tryParse(tag.getString("EntityId"));
            }
        }
        if (tag.contains("LootDrop", Tag.TAG_COMPOUND)) {
            this.lootDrop = ItemStack.of(tag.getCompound("LootDrop"));
        } else {
            this.lootDrop = ItemStack.EMPTY;
        }
    }
}
