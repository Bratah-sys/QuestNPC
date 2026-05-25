package com.questnpc.client.gui.picker;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Picker для выбора тега сущностей. Используется в KillObjective в режиме «по тегу».
 *
 * <p>Источник: {@code ForgeRegistries.ENTITY_TYPES.tags().getTagNames()}.
 * Теги загружаются при join world, в main menu список может быть пуст —
 * {@link BaseListPickerScreen} отрисует empty-state placeholder.
 */
public class EntityTagPickerScreen extends BaseListPickerScreen<TagKey<EntityType<?>>> {

    public EntityTagPickerScreen(Screen parent,
                                 @Nullable TagKey<EntityType<?>> initialSelection,
                                 Consumer<TagKey<EntityType<?>>> onConfirm) {
        super(parent, initialSelection, onConfirm);
    }

    @Override
    protected String screenTitleKey() {
        return "gui.questnpc.pickers.entity_tag.title";
    }

    @Override
    protected List<TagKey<EntityType<?>>> buildEntries() {
        try {
            List<TagKey<EntityType<?>>> out = new ArrayList<>();
            ForgeRegistries.ENTITY_TYPES.tags().getTagNames().forEach(out::add);
            out.sort((a, b) -> a.location().toString().compareTo(b.location().toString()));
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    protected String displayName(TagKey<EntityType<?>> entry) {
        return "#" + entry.location().toString();
    }

    @Override
    protected String subText(TagKey<EntityType<?>> entry) {
        return entry.location().getNamespace();
    }
}
