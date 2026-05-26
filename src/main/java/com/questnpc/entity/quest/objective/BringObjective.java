package com.questnpc.entity.quest.objective;

import com.questnpc.entity.quest.ObjectiveType;
import com.questnpc.entity.quest.QuestObjective;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Цель «принести/иметь N предметов».
 * При сдаче квеста NPC проверяет инвентарь игрока; если {@link #consumeOnTurnIn}=true —
 * предметы изымаются. Этап 1: skeleton. Реальная проверка — в этапе 5 (mobInteract).
 */
public class BringObjective extends QuestObjective {

    private ItemStack stack = ItemStack.EMPTY;
    private int count = 1;
    private boolean consumeOnTurnIn = true;
    /**
     * Stage 7.5 (v2.9.7): опциональный источник дропа — пока квест активен и stack ещё не
     * собран до {@link #count}, убийство сущности этого типа добавляет 1 копию {@link #stack}
     * к loot. Реализация через {@link com.questnpc.loot.QuestKillDropModifier} (GLM).
     */
    @Nullable private ResourceLocation dropSourceEntityType;

    @Override
    public ObjectiveType getType() { return ObjectiveType.BRING; }

    @Override
    public long getMaxProgress() { return Math.max(1, count); }

    public ItemStack getStack() { return stack; }
    public void setStack(ItemStack v) { this.stack = v != null ? v : ItemStack.EMPTY; }
    public int getCount() { return count; }
    public void setCount(int v) { this.count = Math.max(1, v); }
    public boolean isConsumeOnTurnIn() { return consumeOnTurnIn; }
    public void setConsumeOnTurnIn(boolean v) { this.consumeOnTurnIn = v; }
    @Nullable public ResourceLocation getDropSourceEntityType() { return dropSourceEntityType; }
    public void setDropSourceEntityType(@Nullable ResourceLocation v) { this.dropSourceEntityType = v; }

    /**
     * Stage 7.5 (v2.9.7): проверка — этот моб является {@link #dropSourceEntityType}
     * для квестового drop'а. Используется в {@link com.questnpc.loot.QuestKillDropModifier}.
     */
    public boolean matches(Entity victim) {
        if (victim == null || dropSourceEntityType == null) return false;
        ResourceLocation victimId = ForgeRegistries.ENTITY_TYPES.getKey(victim.getType());
        return dropSourceEntityType.equals(victimId);
    }

    // -------------------------------------------------------------------------
    // Stage 5: реальные методы для server-side проверки на turn-in.
    // BringObjective НЕ обновляет progress через event handlers — проверка только
    // в момент попытки сдать квест (через mobInteract / RequestQuestTurnInPacket).
    // -------------------------------------------------------------------------

    /** Сколько штук подходящего предмета у игрока в инвентаре (включая hotbar/offhand). */
    public int checkInventoryCount(Player player) {
        if (player == null || stack.isEmpty()) return 0;
        int total = 0;
        // .items покрывает 36 main slots (hotbar+inventory). Дополнительно — offhand.
        for (ItemStack inv : player.getInventory().items) {
            if (ItemStack.isSameItemSameTags(inv, stack)) total += inv.getCount();
        }
        for (ItemStack inv : player.getInventory().offhand) {
            if (ItemStack.isSameItemSameTags(inv, stack)) total += inv.getCount();
        }
        return total;
    }

    public boolean canFulfill(Player player) {
        return checkInventoryCount(player) >= count;
    }

    /**
     * Списать из инвентаря {@link #count} штук подходящего предмета. Никакого эффекта
     * если {@link #consumeOnTurnIn}=false или инвентарь не содержит достаточно.
     * Вызывающий должен ПРЕДВАРИТЕЛЬНО проверить {@link #canFulfill(Player)}.
     */
    public void consumeFromInventory(Player player) {
        if (player == null || !consumeOnTurnIn || stack.isEmpty()) return;
        int remaining = count;
        for (ItemStack inv : player.getInventory().items) {
            if (remaining <= 0) break;
            if (ItemStack.isSameItemSameTags(inv, stack)) {
                int take = Math.min(remaining, inv.getCount());
                inv.shrink(take);
                remaining -= take;
            }
        }
        for (ItemStack inv : player.getInventory().offhand) {
            if (remaining <= 0) break;
            if (ItemStack.isSameItemSameTags(inv, stack)) {
                int take = Math.min(remaining, inv.getCount());
                inv.shrink(take);
                remaining -= take;
            }
        }
    }

    @Override
    protected void writeData(CompoundTag tag) {
        if (!stack.isEmpty()) {
            tag.put("Item", stack.save(new CompoundTag()));
        }
        tag.putInt("Count", count);
        tag.putBoolean("Consume", consumeOnTurnIn);
        // Stage 7.5 (v2.9.7): сохраняем dropSource только если задан
        if (dropSourceEntityType != null) {
            tag.putString("DropSource", dropSourceEntityType.toString());
        }
    }

    @Override
    protected void readData(CompoundTag tag) {
        if (tag.contains("Item", Tag.TAG_COMPOUND)) {
            this.stack = ItemStack.of(tag.getCompound("Item"));
        } else {
            this.stack = ItemStack.EMPTY;
        }
        this.count = Math.max(1, tag.getInt("Count"));
        this.consumeOnTurnIn = tag.contains("Consume") ? tag.getBoolean("Consume") : true;
        // Stage 7.5 (v2.9.7): загрузка dropSource (backward compat — отсутствие = null)
        this.dropSourceEntityType = tag.contains("DropSource")
                ? ResourceLocation.tryParse(tag.getString("DropSource"))
                : null;
    }
}
