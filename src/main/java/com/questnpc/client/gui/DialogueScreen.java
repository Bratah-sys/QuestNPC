package com.questnpc.client.gui;

import com.questnpc.dialogue.DialogueNode;
import com.questnpc.dialogue.DialogueOption;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.network.DialogueChoicePacket;
import com.questnpc.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

public class DialogueScreen extends Screen {
    private final QuestNPCEntity npc;
    private final DialogueNode node;
    private float xMouse;
    private float yMouse;

    public DialogueScreen(QuestNPCEntity npc, DialogueNode node) {
        super(Component.literal("Диалог с " + npc.getDisplayName().getString()));
        this.npc = npc;
        this.node = node;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 260; // Делаем кнопки чуть шире, чтобы влез длинный текст ответов
        int buttonHeight = 20;
        int startX = (this.width - buttonWidth) / 2;

        // Рассчитываем, где будут начинаться кнопки.
        // Мы отступаем от центра вниз, чтобы оставить место для текста NPC
        int buttonsStartY = this.height / 2 + 30;
        int spacing = 24;

        // Динамически создаем кнопки для каждого варианта ответа из JSON'а
        for (int i = 0; i < node.getOptions().size(); i++) {
            DialogueOption option = node.getOptions().get(i);

            Button btn = Button.builder(Component.literal(option.getText()), button -> {
                if (this.minecraft.player != null) {
                    // Отправляем на сервер информацию о том, какую кнопку нажал игрок
                    ModNetwork.INSTANCE.sendToServer(new DialogueChoicePacket(
                            npc.getId(),
                            option.getNextNodeId(),
                            option.getAction()
                    ));
                }
            }).bounds(startX, buttonsStartY + (i * spacing), buttonWidth, buttonHeight).build();

            this.addRenderableWidget(btn);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics); // Затемняем задний фон

        this.xMouse = (float) mouseX;
        this.yMouse = (float) mouseY;

        // 1. Рисуем имя NPC в самом верху
        guiGraphics.drawCenteredString(this.font, npc.getDisplayName().getString(), this.width / 2, 15, 0xFFD700); // Золотой цвет имени

        // 2. Рендерим 3D модельку NPC (Верхняя половина экрана)
        int entityPosX = this.width / 2;
        int entityPosY = this.height / 2 - 40; // Сдвигаем модельку повыше
        int scale = 60;

        float lookX = (float) entityPosX - this.xMouse;
        float lookY = (float) (entityPosY - scale) - this.yMouse;

        InventoryScreen.renderEntityInInventoryFollowsMouse(
                guiGraphics,
                entityPosX, entityPosY, scale,
                lookX, lookY,
                this.npc
        );

        // 3. Отрисовка текста NPC (По центру экрана)
        int textWidth = 300; // Максимальная ширина строки текста
        int textX = (this.width - textWidth) / 2;
        int textY = this.height / 2 - 10; // Под моделькой, над кнопками

        // drawWordWrap автоматически переносит слишком длинный текст на новые строчки
        guiGraphics.drawWordWrap(this.font, Component.literal(node.getText()), textX, textY, textWidth, 0xFFFFFF);

        // 4. Рендерим сами кнопки (нижняя часть)
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Чтобы мир на фоне не ставился на паузу в синглплеере
    }
}