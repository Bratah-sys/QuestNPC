package com.questnpc.client.gui;

import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.OpenNPCTradePacket;
import com.questnpc.network.RequestDialoguePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

public class NPCInteractionScreen extends Screen {
    private final QuestNPCEntity npc;
    private float xMouse;
    private float yMouse;

    public NPCInteractionScreen(QuestNPCEntity npc) {
        super(Component.literal(npc.getDisplayName().getString()));
        this.npc = npc;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 200;
        int buttonHeight = 20;
        int startX = (this.width - buttonWidth) / 2; // Центр экрана
        int startY = this.height / 2 + 30;           // Ниже модельки
        int spacing = 24;                            // Расстояние между кнопками

        // ==========================================
        // 1. КНОПКА ТОРГОВЛИ
        // ==========================================
        Button tradeButton = Button.builder(Component.literal("Торговать"), button -> {
            if (this.minecraft.player != null) {
                // Просто шлем пакет на сервер.
                // Сервер сам пришлет команду закрыть это меню и открыть торги.
                // МЫ БОЛЬШЕ НЕ ВЫЗЫВАЕМ this.onClose() ЗДЕСЬ!
                ModNetwork.INSTANCE.sendToServer(new OpenNPCTradePacket(npc.getId()));
            }
        }).bounds(startX, startY, buttonWidth, buttonHeight).build();

        tradeButton.active = true;
        this.addRenderableWidget(tradeButton);

        // ==========================================
        // 2. КНОПКА КВЕСТОВ
        // ==========================================
        Button questsButton = Button.builder(Component.literal("Квесты"), button -> {
            if (this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(Component.literal("§6[Квесты] §fПанель квестов в разработке..."));
                this.onClose(); // Здесь оставляем закрытие меню, так как мы просто пишем в чат
            }
        }).bounds(startX, startY + spacing, buttonWidth, buttonHeight).build();
        this.addRenderableWidget(questsButton);

        // ==========================================
        // 3. КНОПКА ДИАЛОГОВ
        // ==========================================
        Button dialogueButton = Button.builder(Component.literal("Разговор"), button -> {
            if (this.minecraft.player != null) {
                // Шлем пакет-запрос на сервер, экран сам закроется/сменится сервером
                ModNetwork.INSTANCE.sendToServer(new RequestDialoguePacket(npc.getId()));
            }
        }).bounds(startX, startY + spacing * 2, buttonWidth, buttonHeight).build();
        this.addRenderableWidget(dialogueButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics); // Затемнение заднего фона

        this.xMouse = (float) mouseX;
        this.yMouse = (float) mouseY;

        // Рисуем имя NPC по центру вверху экрана
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        // Рендерим 3D модельку нашего NPC
        int entityPosX = this.width / 2;
        int entityPosY = this.height / 2 + 15;
        int scale = 60;

        float lookX = (float) entityPosX - this.xMouse;
        float lookY = (float) (entityPosY - scale) - this.yMouse;

        InventoryScreen.renderEntityInInventoryFollowsMouse(
                guiGraphics,
                entityPosX, entityPosY, scale,
                lookX, lookY,
                this.npc
        );

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}