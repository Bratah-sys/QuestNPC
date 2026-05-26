package com.questnpc.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class QuestBookOverlay {
    private static final ResourceLocation ICON_NORMAL = new ResourceLocation("questnpc", "textures/item/quest_book.png");
    private static final ResourceLocation ICON_NOTIFICATION = new ResourceLocation("questnpc", "textures/item/quest_book_yes.png");

    /** Кэш «надет ли квестбук в Curios» — обновляется в {@link com.questnpc.client.ClientEvents} раз в 20 тиков. */
    public static volatile boolean hasBookEquippedCached = false;

    /** Флаг «есть новый квест» — переключается биндингом J, в будущем будет дёргаться из сетевого ивента. */
    private static volatile boolean hasNewQuest = false;

    public static boolean hasNewQuest() { return hasNewQuest; }
    public static void setHasNewQuest(boolean value) { hasNewQuest = value; }

    public static final IGuiOverlay HUD_QUEST_BOOK = (gui, guiGraphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (!hasBookEquippedCached) return;

        int x = (width / 2) + 98;
        int y = height - 21;
        ResourceLocation currentIcon = hasNewQuest() ? ICON_NOTIFICATION : ICON_NORMAL;

        RenderSystem.enableBlend();
        guiGraphics.blit(currentIcon, x, y, 0, 0, 16, 16, 16, 16);
        RenderSystem.disableBlend();
    };
}
