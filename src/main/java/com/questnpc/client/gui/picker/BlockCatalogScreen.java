package com.questnpc.client.gui.picker;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.RenderShape;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Picker для выбора {@link Block} (Stage 3 v2.9.2). Используется из
 * {@link com.questnpc.client.gui.QuestsScreen} для BreakBlockObjective в режиме «конкретный».
 *
 * <p>Источник: {@link ForgeRegistries#BLOCKS}. Дефолтный фильтр исключает:
 * <ul>
 *   <li>Блоки без {@code asItem()} (например, технические fluid-блоки)</li>
 *   <li>Блоки с {@link RenderShape#INVISIBLE} (барьеры, спавнеры структур)</li>
 * </ul>
 *
 * <p>Иконка: {@link ItemStack} для предметной формы блока.
 */
public class BlockCatalogScreen extends BaseGridPickerScreen<Block> {

    public BlockCatalogScreen(Screen parent,
                              @Nullable ResourceLocation initialSelection,
                              Consumer<ResourceLocation> onConfirm) {
        super(parent, initialSelection, onConfirm);
    }

    @Override
    protected String screenTitleKey() {
        return "gui.questnpc.pickers.block.title";
    }

    @Override
    protected List<Entry<Block>> buildEntries() {
        List<Entry<Block>> out = new ArrayList<>();
        for (var e : ForgeRegistries.BLOCKS.getEntries()) {
            Block block = e.getValue();
            if (block == null) continue;
            if (block.asItem() == Items.AIR) continue;
            try {
                BlockState defaultState = block.defaultBlockState();
                if (defaultState.getRenderShape() == RenderShape.INVISIBLE) continue;
            } catch (Exception ex) {
                continue;
            }
            ResourceLocation id = e.getKey().location();
            String displayName;
            try {
                displayName = Component.translatable(block.getDescriptionId()).getString();
            } catch (Exception ex) {
                displayName = id.getPath();
            }
            out.add(new Entry<>(id, block, displayName));
        }
        return out;
    }

    @Override
    protected void renderIcon(GuiGraphics g, Entry<Block> entry, int x, int y) {
        ItemStack stack = new ItemStack(entry.value.asItem());
        g.renderItem(stack, x, y);
    }
}
