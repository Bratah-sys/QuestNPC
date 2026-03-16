package com.questnpc.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Кастомная кнопка-вкладка для меню NPC.
 * Рисует тёмный фон с подчёркиванием для активной вкладки и hover-эффектом.
 */
public class TabButton extends Button {

    private static final int COLOR_BG_NORMAL    = 0xFF1A1A2E;
    private static final int COLOR_BG_HOVER     = 0xFF252547;
    private static final int COLOR_BG_ACTIVE    = 0xFF1F2937;
    private static final int COLOR_TEXT_INACTIVE = 0xFF9CA3AF;
    private static final int COLOR_TEXT_HOVER    = 0xFFD1D5DB;
    private static final int COLOR_TEXT_ACTIVE   = 0xFFE2E8F0;
    private static final int COLOR_UNDERLINE     = 0xFF3B82F6;

    private boolean selected;

    public TabButton(int x, int y, int width, int height, Component text, OnPress onPress) {
        super(x, y, width, height, text, onPress, DEFAULT_NARRATION);
        this.selected = false;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isSelected() {
        return selected;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isHoveredOrFocused();

        // Фон вкладки
        int bgColor;
        if (selected) {
            bgColor = COLOR_BG_ACTIVE;
        } else if (hovered) {
            bgColor = COLOR_BG_HOVER;
        } else {
            bgColor = COLOR_BG_NORMAL;
        }
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);

        // Подчёркивание активной вкладки
        if (selected) {
            graphics.fill(getX() + 2, getY() + getHeight() - 2, getX() + getWidth() - 2, getY() + getHeight(), COLOR_UNDERLINE);
        }

        // Текст
        int textColor;
        if (selected) {
            textColor = COLOR_TEXT_ACTIVE;
        } else if (hovered) {
            textColor = COLOR_TEXT_HOVER;
        } else {
            textColor = COLOR_TEXT_INACTIVE;
        }

        // Центрируем текст в кнопке
        int textX = getX() + (getWidth() - net.minecraft.client.Minecraft.getInstance().font.width(getMessage())) / 2;
        int textY = getY() + (getHeight() - 8) / 2;
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, getMessage(), textX, textY, textColor, false);
    }
}
