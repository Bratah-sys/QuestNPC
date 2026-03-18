package com.questnpc.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Кнопка в тёмном стиле для меню NPC.
 * Поддерживает настройку цветов фона, hover и текста.
 */
public class DarkButton extends Button {

    // Цвета по умолчанию для кнопок сетки
    public static final int DEFAULT_BG       = 0xFF1F2937;
    public static final int DEFAULT_HOVER    = 0xFF374151;
    public static final int DEFAULT_TEXT     = 0xFFE2E8F0;
    public static final int DEFAULT_BORDER   = 0xFF4A5568;

    private final int bgColor;
    private final int hoverColor;
    private final int textColor;

    public DarkButton(int x, int y, int w, int h, Component text, OnPress onPress) {
        this(x, y, w, h, text, onPress, DEFAULT_BG, DEFAULT_HOVER, DEFAULT_TEXT);
    }

    public DarkButton(int x, int y, int w, int h, Component text, OnPress onPress,
                      int bg, int hover, int textColor) {
        super(x, y, w, h, text, onPress, DEFAULT_NARRATION);
        this.bgColor = bg;
        this.hoverColor = hover;
        this.textColor = textColor;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int bg = this.isHoveredOrFocused() ? hoverColor : bgColor;
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);

        // Рамка
        int border = this.isHoveredOrFocused() ? 0xFF6B7280 : DEFAULT_BORDER;
        // Верх
        g.fill(getX(), getY(), getX() + getWidth(), getY() + 1, border);
        // Низ
        g.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), border);
        // Лево
        g.fill(getX(), getY(), getX() + 1, getY() + getHeight(), border);
        // Право
        g.fill(getX() + getWidth() - 1, getY(), getX() + getWidth(), getY() + getHeight(), border);

        // Текст по центру
        var font = Minecraft.getInstance().font;
        int tx = getX() + (getWidth() - font.width(getMessage())) / 2;
        int ty = getY() + (getHeight() - 8) / 2;
        int color = this.active ? (textColor & 0x00FFFFFF) : 0x666666;
        g.drawString(font, getMessage(), tx, ty, color, false);
    }
}
