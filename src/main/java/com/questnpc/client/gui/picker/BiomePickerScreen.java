package com.questnpc.client.gui.picker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Picker для выбора биома. Используется в ReachBiomeObjective.
 *
 * <p>Источник: client-side registry через {@link Minecraft#getConnection()}.
 * Biomes — datapack registry, не доступен через {@code BuiltInRegistries}.
 * Если игрок не в мире (main menu) — {@link #buildEntries()} вернёт пустой список,
 * который {@link BaseListPickerScreen} отрисует как "Not available in this world".
 */
public class BiomePickerScreen extends BaseListPickerScreen<ResourceLocation> {

    public BiomePickerScreen(Screen parent,
                             @Nullable ResourceLocation initialSelection,
                             Consumer<ResourceLocation> onConfirm) {
        super(parent, initialSelection, onConfirm);
    }

    @Override
    protected String screenTitleKey() {
        return "gui.questnpc.pickers.biome.title";
    }

    @Override
    protected List<ResourceLocation> buildEntries() {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null) return Collections.emptyList();
        try {
            Registry<Biome> reg = conn.registryAccess().registryOrThrow(Registries.BIOME);
            List<ResourceLocation> out = new ArrayList<>(reg.keySet());
            out.sort((a, b) -> a.toString().compareTo(b.toString()));
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    protected String displayName(ResourceLocation entry) {
        // Биомы редко имеют translated name — показываем slug.
        return entry.getPath();
    }

    @Override
    protected String subText(ResourceLocation entry) {
        return entry.getNamespace();
    }
}
