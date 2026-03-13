package com.questnpc.client.gui;

import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.RequestPatrolChangePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * GUI-экран меню NPC. Открывается при ПКМ палкой по QuestNPCEntity.
 * Содержит кнопку смены точки патруля.
 */
public class NPCMenuScreen extends Screen {

    private final QuestNPCEntity npc;

    public NPCMenuScreen(QuestNPCEntity npc) {
        super(Component.translatable("gui.questnpc.npc_menu.title"));
        this.npc = npc;
    }

    @Override
    protected void init() {
        super.init();
        int buttonWidth = 200;
        int buttonHeight = 20;
        int x = (this.width - buttonWidth) / 2;
        int y = (this.height / 2);

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.questnpc.npc_menu.change_patrol"),
                button -> {
                    // Отправляем пакет на сервер
                    ModNetwork.INSTANCE.sendToServer(new RequestPatrolChangePacket(npc.getId()));
                    this.onClose();
                }
        ).bounds(x, y, buttonWidth, buttonHeight).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        // Заголовок по центру сверху
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 30, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
