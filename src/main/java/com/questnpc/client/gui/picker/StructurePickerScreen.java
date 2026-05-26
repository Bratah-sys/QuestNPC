package com.questnpc.client.gui.picker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Picker для выбора структуры. Используется в ReachStructureObjective.
 *
 * <p>Источник: client-side registry через {@link Minecraft#getConnection()}.
 * Structures — datapack registry, обязательно нужно подключение к миру.
 * Если игрок в main menu — empty-state placeholder из {@link BaseListPickerScreen}.
 */
public class StructurePickerScreen extends BaseListPickerScreen<ResourceLocation> {

    public StructurePickerScreen(Screen parent,
                                 @Nullable ResourceLocation initialSelection,
                                 Consumer<ResourceLocation> onConfirm) {
        super(parent, initialSelection, onConfirm);
    }

    @Override
    protected String screenTitleKey() {
        return "gui.questnpc.pickers.structure.title";
    }

    @Override
    protected List<ResourceLocation> buildEntries() {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null) return Collections.emptyList();
        try {
            Registry<Structure> reg = conn.registryAccess().registryOrThrow(Registries.STRUCTURE);
            List<ResourceLocation> out = new ArrayList<>(reg.keySet());
            out.sort((a, b) -> a.toString().compareTo(b.toString()));
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    protected String displayName(ResourceLocation entry) {
        return entry.getPath();
    }

    @Override
    protected String subText(ResourceLocation entry) {
        return entry.getNamespace();
    }
}
