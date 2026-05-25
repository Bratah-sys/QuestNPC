package com.questnpc.client.gui.picker;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Picker для выбора {@link EntityType} (Stage 3 v2.9.2). Используется из
 * {@link com.questnpc.client.gui.QuestsScreen} для KillObjective в режиме «конкретный».
 *
 * <p>Источник: {@link ForgeRegistries#ENTITY_TYPES}. Дефолтный фильтр — живые сущности
 * ({@link MobCategory} != MISC, плюс whitelist специальных мобов вроде Iron Golem).
 *
 * <p>Иконка: spawn-egg моба ({@link SpawnEggItem#byId}) если есть, иначе {@link Items#SPAWNER}.
 */
public class EntityCatalogScreen extends BaseGridPickerScreen<EntityType<?>> {

    public EntityCatalogScreen(Screen parent,
                               @Nullable ResourceLocation initialSelection,
                               Consumer<ResourceLocation> onConfirm) {
        super(parent, initialSelection, onConfirm);
    }

    @Override
    protected String screenTitleKey() {
        return "gui.questnpc.pickers.entity.title";
    }

    @Override
    protected List<Entry<EntityType<?>>> buildEntries() {
        List<Entry<EntityType<?>>> out = new ArrayList<>();
        for (var e : ForgeRegistries.ENTITY_TYPES.getEntries()) {
            EntityType<?> type = e.getValue();
            if (type == EntityType.PLAYER) continue;
            if (!isLivingMob(type)) continue;
            ResourceLocation id = e.getKey().location();
            String displayName;
            try {
                displayName = Component.translatable(type.getDescriptionId()).getString();
            } catch (Exception ex) {
                displayName = id.getPath();
            }
            out.add(new Entry<>(id, type, displayName));
        }
        return out;
    }

    @Override
    protected void renderIcon(GuiGraphics g, Entry<EntityType<?>> entry, int x, int y) {
        Item icon = SpawnEggItem.byId(entry.value);
        if (icon == null) icon = Items.SPAWNER;
        g.renderItem(new ItemStack(icon), x, y);
    }

    private static boolean isLivingMob(EntityType<?> type) {
        try {
            MobCategory cat = type.getCategory();
            return cat != MobCategory.MISC
                    || type == EntityType.IRON_GOLEM
                    || type == EntityType.SNOW_GOLEM
                    || type == EntityType.VILLAGER
                    || type == EntityType.WANDERING_TRADER;
        } catch (Exception e) {
            return false;
        }
    }
}
