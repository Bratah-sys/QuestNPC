package com.questnpc.client.gui;

import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.RequestPatrolChangePacket;
import com.questnpc.network.UpdateNPCSettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * GUI-экран меню NPC. Открывается при ПКМ палкой по QuestNPCEntity.
 * Содержит кнопку смены точки патруля, настройки скорости и задержки.
 */
public class NPCMenuScreen extends Screen {

    private final QuestNPCEntity npc;
    private final double currentSpeed;
    private final int currentDelayMin;
    private final int currentDelayMax;

    private EditBox speedField;
    private EditBox delayMinField;
    private EditBox delayMaxField;

    // Флаги валидности для подсветки
    private boolean speedValid = true;
    private boolean delayMinValid = true;
    private boolean delayMaxValid = true;
    private boolean delayRangeValid = true;

    public NPCMenuScreen(QuestNPCEntity npc, double speed, int delayMin, int delayMax) {
        super(Component.translatable("gui.questnpc.npc_menu.title"));
        this.npc = npc;
        this.currentSpeed = speed;
        this.currentDelayMin = delayMin;
        this.currentDelayMax = delayMax;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = this.height / 2 - 70;
        int buttonWidth = 200;
        int fieldWidth = 50;
        int buttonHeight = 20;

        // --- Кнопка "Изменить точку патруля" ---
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.questnpc.npc_menu.change_patrol"),
                button -> {
                    ModNetwork.INSTANCE.sendToServer(new RequestPatrolChangePacket(npc.getId()));
                    this.onClose();
                }
        ).bounds(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight).build());

        // --- Скорость ---
        int speedY = startY + 35;
        // Лейбл "Скорость:" рисуется в render()

        speedField = new EditBox(this.font, centerX - 10, speedY, fieldWidth, buttonHeight,
                Component.translatable("gui.questnpc.npc_menu.speed"));
        speedField.setMaxLength(6);
        speedField.setValue(formatSpeed(currentSpeed));
        speedField.setResponder(text -> validateAll());
        this.addRenderableWidget(speedField);

        // Кнопка сброса скорости (квадратная "R")
        this.addRenderableWidget(Button.builder(
                Component.literal("R"),
                button -> {
                    speedField.setValue(formatSpeed(QuestNPCEntity.DEFAULT_PATROL_SPEED));
                    validateAll();
                }
        ).bounds(centerX - 10 + fieldWidth + 4, speedY, buttonHeight, buttonHeight).build());

        // --- Задержка ---
        int delayY = speedY + 40;
        // Лейбл "Задержка патруля:" рисуется в render()

        delayMinField = new EditBox(this.font, centerX - 30, delayY, 40, buttonHeight,
                Component.translatable("gui.questnpc.npc_menu.delay_min"));
        delayMinField.setMaxLength(3);
        delayMinField.setValue(String.valueOf(currentDelayMin));
        delayMinField.setResponder(text -> validateAll());
        this.addRenderableWidget(delayMinField);

        delayMaxField = new EditBox(this.font, centerX + 30, delayY, 40, buttonHeight,
                Component.translatable("gui.questnpc.npc_menu.delay_max"));
        delayMaxField.setMaxLength(3);
        delayMaxField.setValue(String.valueOf(currentDelayMax));
        delayMaxField.setResponder(text -> validateAll());
        this.addRenderableWidget(delayMaxField);

        // Кнопка сброса задержки (квадратная "R")
        this.addRenderableWidget(Button.builder(
                Component.literal("R"),
                button -> {
                    delayMinField.setValue(String.valueOf(QuestNPCEntity.DEFAULT_DELAY_MIN));
                    delayMaxField.setValue(String.valueOf(QuestNPCEntity.DEFAULT_DELAY_MAX));
                    validateAll();
                }
        ).bounds(centerX + 30 + 40 + 4, delayY, buttonHeight, buttonHeight).build());

        // --- Кнопка "Применить" ---
        int applyY = delayY + 40;
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.questnpc.npc_menu.apply"),
                button -> applySettings()
        ).bounds(centerX - buttonWidth / 2, applyY, buttonWidth, buttonHeight).build());

        validateAll();
    }

    /** Форматирование скорости без лишних нулей. */
    private static String formatSpeed(double speed) {
        // Показываем до 2 знаков после запятой
        String s = String.format("%.2f", speed);
        // Убираем trailing zeros, но оставляем хотя бы один знак после точки
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) s += "0";
        }
        return s;
    }

    /** Валидация всех полей. */
    private void validateAll() {
        // Скорость
        speedValid = false;
        try {
            double speed = Double.parseDouble(speedField.getValue());
            speedValid = speed >= 0.05 && speed <= 1.0;
        } catch (NumberFormatException ignored) {}

        // Задержка мин
        delayMinValid = false;
        int minVal = -1;
        try {
            minVal = Integer.parseInt(delayMinField.getValue());
            delayMinValid = minVal >= 1 && minVal <= 120;
        } catch (NumberFormatException ignored) {}

        // Задержка макс
        delayMaxValid = false;
        int maxVal = -1;
        try {
            maxVal = Integer.parseInt(delayMaxField.getValue());
            delayMaxValid = maxVal >= 1 && maxVal <= 120;
        } catch (NumberFormatException ignored) {}

        // Проверка мин <= макс
        delayRangeValid = delayMinValid && delayMaxValid && minVal <= maxVal;

        // Подсветка полей
        speedField.setTextColor(speedValid ? 0xFFFFFF : 0xFF5555);
        delayMinField.setTextColor(delayMinValid && delayRangeValid ? 0xFFFFFF : 0xFF5555);
        delayMaxField.setTextColor(delayMaxValid && delayRangeValid ? 0xFFFFFF : 0xFF5555);
    }

    /** Отправка настроек на сервер. */
    private void applySettings() {
        validateAll();
        if (!speedValid || !delayMinValid || !delayMaxValid || !delayRangeValid) return;

        double speed = Double.parseDouble(speedField.getValue());
        int delayMin = Integer.parseInt(delayMinField.getValue());
        int delayMax = Integer.parseInt(delayMaxField.getValue());

        ModNetwork.INSTANCE.sendToServer(
                new UpdateNPCSettingsPacket(npc.getId(), speed, delayMin, delayMax)
        );
        this.onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int centerX = this.width / 2;
        int startY = this.height / 2 - 70;

        // Заголовок
        guiGraphics.drawCenteredString(this.font, this.title, centerX, startY - 15, 0xFFFFFF);

        // --- Лейбл скорости ---
        int speedY = startY + 35;
        String speedLabel = Component.translatable("gui.questnpc.npc_menu.speed").getString();
        guiGraphics.drawString(this.font, speedLabel, centerX - 10 - this.font.width(speedLabel) - 4, speedY + 6, 0xFFFFFF);

        // Подсказка скорости
        String speedHint = Component.translatable("gui.questnpc.npc_menu.speed_hint").getString();
        guiGraphics.drawCenteredString(this.font, speedHint, centerX, speedY + 22, 0x888888);

        // --- Лейбл задержки ---
        int delayY = speedY + 40;
        String delayLabel = Component.translatable("gui.questnpc.npc_menu.delay").getString();
        guiGraphics.drawCenteredString(this.font, delayLabel, centerX, delayY - 12, 0xFFFFFF);

        // Тире между полями мин и макс
        guiGraphics.drawString(this.font, "-", centerX + 14, delayY + 6, 0xFFFFFF);

        // Подсказка задержки
        String delayHint = Component.translatable("gui.questnpc.npc_menu.delay_hint").getString();
        guiGraphics.drawCenteredString(this.font, delayHint, centerX, delayY + 22, 0x888888);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
