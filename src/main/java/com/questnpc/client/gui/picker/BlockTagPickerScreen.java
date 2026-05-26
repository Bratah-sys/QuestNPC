package com.questnpc.client.gui.picker;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Picker для выбора тега блоков. Используется в BreakBlockObjective в режиме «по тегу».
 *
 * <p>Источник: {@code ForgeRegistries.BLOCKS.tags().getTagNames()}.
 * Теги загружаются при join world, в main menu список может быть пуст.
 */
public class BlockTagPickerScreen extends BaseListPickerScreen<TagKey<Block>> {

    public BlockTagPickerScreen(Screen parent,
                                @Nullable TagKey<Block> initialSelection,
                                Consumer<TagKey<Block>> onConfirm) {
        super(parent, initialSelection, onConfirm);
    }

    @Override
    protected String screenTitleKey() {
        return "gui.questnpc.pickers.block_tag.title";
    }

    @Override
    protected List<TagKey<Block>> buildEntries() {
        try {
            List<TagKey<Block>> out = new ArrayList<>();
            ForgeRegistries.BLOCKS.tags().getTagNames().forEach(out::add);
            out.sort((a, b) -> a.location().toString().compareTo(b.location().toString()));
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    protected String displayName(TagKey<Block> entry) {
        return "#" + entry.location().toString();
    }

    @Override
    protected String subText(TagKey<Block> entry) {
        return entry.location().getNamespace();
    }
}
