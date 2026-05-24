package com.questnpc.entity.quest.objective;

import com.questnpc.entity.quest.ObjectiveType;
import com.questnpc.entity.quest.QuestObjective;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Цель «принести/иметь N предметов».
 * При сдаче квеста NPC проверяет инвентарь игрока; если {@link #consumeOnTurnIn}=true —
 * предметы изымаются. Этап 1: skeleton. Реальная проверка — в этапе 5 (mobInteract).
 */
public class BringObjective extends QuestObjective {

    private ItemStack stack = ItemStack.EMPTY;
    private int count = 1;
    private boolean consumeOnTurnIn = true;

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

    @Override
    protected void writeData(CompoundTag tag) {
        if (!stack.isEmpty()) {
            tag.put("Item", stack.save(new CompoundTag()));
        }
        tag.putInt("Count", count);
        tag.putBoolean("Consume", consumeOnTurnIn);
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
    }
}
