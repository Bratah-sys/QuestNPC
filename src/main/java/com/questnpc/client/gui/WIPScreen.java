package com.questnpc.client.gui;

import com.questnpc.client.gui.widget.DarkButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Экран-заглушка "В разработке" для кнопки загрузки из файла.
 */
public class WIPScreen extends Screen {

    private final Screen parent;

    public WIPScreen(Screen parent) {
        super(Component.translatable("gui.questnpc.catalog.wip"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int btnW = 80;
        int btnH = 20;
        this.addRenderableWidget(new DarkButton(
                (this.width - btnW) / 2, this.height / 2 + 20, btnW, btnH,
                Component.translatable("gui.questnpc.catalog.back"),
                button -> Minecraft.getInstance().setScreen(parent)
        ));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, NPCMenuScreen.BG_OVERLAY);
        NPCMenuScreen.drawPanel(g, this.width / 2 - 100, this.height / 2 - 40, 200, 80);
        g.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 20, NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
