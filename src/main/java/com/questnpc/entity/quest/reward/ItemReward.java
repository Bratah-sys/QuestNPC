package com.questnpc.entity.quest.reward;

import com.questnpc.entity.quest.QuestReward;
import com.questnpc.entity.quest.RewardGrantContext;
import com.questnpc.entity.quest.RewardType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Награда «выдать предмет игроку». Этап 1: grant no-op. Реальная выдача — этап 6.
 */
public class ItemReward extends QuestReward {

    private ItemStack stack = ItemStack.EMPTY;

    @Override
    public RewardType getType() { return RewardType.ITEM; }

    public ItemStack getStack() { return stack; }
    public void setStack(ItemStack v) { this.stack = v != null ? v : ItemStack.EMPTY; }

    @Override
    public void grant(RewardGrantContext ctx) {
        // TODO Stage 6: player.getInventory().add(stack.copy()) + fallback drop
    }

    @Override
    protected void writeData(CompoundTag tag) {
        if (!stack.isEmpty()) {
            tag.put("Item", stack.save(new CompoundTag()));
        }
    }

    @Override
    protected void readData(CompoundTag tag) {
        if (tag.contains("Item", Tag.TAG_COMPOUND)) {
            this.stack = ItemStack.of(tag.getCompound("Item"));
        } else {
            this.stack = ItemStack.EMPTY;
        }
    }
}
