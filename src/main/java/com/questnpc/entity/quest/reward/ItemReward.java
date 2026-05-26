package com.questnpc.entity.quest.reward;

import com.questnpc.entity.quest.QuestReward;
import com.questnpc.entity.quest.RewardGrantContext;
import com.questnpc.entity.quest.RewardType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
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

    /**
     * Stage 6 (v2.9.5): выдаёт предмет в инвентарь игрока.
     * Если инвентарь полон (или частично заполнился) — drop'ает остаток как ItemEntity
     * рядом с игроком (без pickup-delay, с thrower=player).
     */
    @Override
    public void grant(RewardGrantContext ctx) {
        ServerPlayer player = ctx.player();
        if (player == null || stack.isEmpty()) return;
        ItemStack copy = stack.copy();                          // never mutate template
        boolean fullyAdded = player.getInventory().add(copy);
        if (!fullyAdded && !copy.isEmpty()) {
            ItemEntity dropped = player.drop(copy, false);
            if (dropped != null) {
                dropped.setNoPickUpDelay();
                dropped.setThrower(player.getUUID());
            }
        }
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
