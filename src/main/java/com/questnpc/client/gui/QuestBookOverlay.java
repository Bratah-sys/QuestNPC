package com.questnpc.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.questnpc.QuestNPCLogger;
import com.questnpc.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import top.theillusivec4.curios.api.CuriosApi; // Если используешь Curios

public class QuestBookOverlay {
    private static final ResourceLocation ICON_NORMAL = new ResourceLocation("questnpc", "textures/item/quest_book.png");
    private static final ResourceLocation ICON_NOTIFICATION = new ResourceLocation("questnpc", "textures/item/quest_book_yes.png"); // Сделай копию иконки и нарисуй на ней восклицательный знак

    // Наше состояние: true - есть уведомление, false - нет
    public static boolean hasNewQuest = false;

    public static final IGuiOverlay HUD_QUEST_BOOK = (gui, guiGraphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        boolean hasBookEquipped = CuriosApi.getCuriosInventory(mc.player).map(inventory ->
                inventory.findFirstCurio(stack -> stack.is(ModItems.QUEST_BOOK.get())).isPresent()
        ).orElse(false);

        if (hasBookEquipped) {
            int x = (width / 2) + 98;
            int y = height - 21;

            // ВЫБИРАЕМ ТЕКСТУРУ В ЗАВИСИМОСТИ ОТ СОСТОЯНИЯ
            ResourceLocation currentIcon = hasNewQuest ? ICON_NOTIFICATION : ICON_NORMAL;

            RenderSystem.enableBlend();
            guiGraphics.blit(currentIcon, x, y, 0, 0, 16, 16, 16, 16);
            RenderSystem.disableBlend();
        }
    };
}