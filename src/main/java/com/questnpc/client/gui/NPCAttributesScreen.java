package com.questnpc.client.gui;

import com.questnpc.client.gui.widget.DarkButton;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.UpdateNPCSettingsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Подменю "Атрибуты" — настройка скорости и задержки патруля.
 * Тёмная тема, кнопка "Назад" возвращает к главному меню.
 */
public class NPCAttributesScreen extends Screen {

    private static final int PANEL_WIDTH  = 280;
    private static final int PANEL_HEIGHT = 320;
    private static final int PADDING      = 12;
    private static final int SECTION_PAD  = 8;

    private final QuestNPCEntity npc;
    private final double currentSpeed;
    private final int currentDelayMin;
    private final int currentDelayMax;
    private final Screen parentScreen;

    private EditBox speedField;
    private EditBox delayMinField;
    private EditBox delayMaxField;
    private EditBox healthMaxField;
    private boolean pendingHeal = false;

    private boolean speedValid = true;
    private boolean delayMinValid = true;
    private boolean delayMaxValid = true;
    private boolean delayRangeValid = true;
    private boolean healthValid = true;

    // Y-координаты секций — кэшируем в init() чтобы render() читал те же значения
    private int healthSectionY;
    private int healthFieldY;

    private int panelX, panelY;

    public NPCAttributesScreen(QuestNPCEntity npc, double speed, int delayMin, int delayMax, Screen parent) {
        super(Component.translatable("gui.questnpc.menu.btn.attributes"));
        this.npc = npc;
        this.currentSpeed = speed;
        this.currentDelayMin = delayMin;
        this.currentDelayMax = delayMax;
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();

        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;

        int contentX = panelX + PADDING;
        int contentW = PANEL_WIDTH - PADDING * 2;

        // ═══ Кнопка "Назад" ═══
        this.addRenderableWidget(new DarkButton(
            contentX, panelY + 30, 60, 18,
            Component.translatable("gui.questnpc.menu.btn.back"),
            button -> Minecraft.getInstance().setScreen(parentScreen)
        ));

        // ═══ Секция: Скорость ═══
        int speedSectionY = panelY + 56;
        int speedFieldY = speedSectionY + 22;
        int fieldHeight = 16;

        speedField = new EditBox(this.font, contentX + SECTION_PAD, speedFieldY, 60, fieldHeight,
                Component.translatable("gui.questnpc.npc_menu.speed"));
        speedField.setMaxLength(6);
        speedField.setValue(formatSpeed(currentSpeed));
        speedField.setResponder(text -> validateAll());
        speedField.setBordered(false);
        this.addRenderableWidget(speedField);

        // Кнопка сброса скорости
        this.addRenderableWidget(new DarkButton(
            contentX + SECTION_PAD + 66, speedFieldY, 16, fieldHeight,
            Component.literal("\u27F3"),
            button -> {
                speedField.setValue(formatSpeed(QuestNPCEntity.DEFAULT_PATROL_SPEED));
                validateAll();
            }
        ));

        // ═══ Секция: Задержка ═══
        int delaySectionY = speedFieldY + fieldHeight + 30;
        int delayFieldY = delaySectionY + 30;

        delayMinField = new EditBox(this.font, contentX + SECTION_PAD, delayFieldY, 40, fieldHeight,
                Component.translatable("gui.questnpc.npc_menu.delay_min"));
        delayMinField.setMaxLength(3);
        delayMinField.setValue(String.valueOf(currentDelayMin));
        delayMinField.setResponder(text -> validateAll());
        delayMinField.setBordered(false);
        this.addRenderableWidget(delayMinField);

        delayMaxField = new EditBox(this.font, contentX + SECTION_PAD + 62, delayFieldY, 40, fieldHeight,
                Component.translatable("gui.questnpc.npc_menu.delay_max"));
        delayMaxField.setMaxLength(3);
        delayMaxField.setValue(String.valueOf(currentDelayMax));
        delayMaxField.setResponder(text -> validateAll());
        delayMaxField.setBordered(false);
        this.addRenderableWidget(delayMaxField);

        // Кнопка сброса задержки
        this.addRenderableWidget(new DarkButton(
            contentX + SECTION_PAD + 108, delayFieldY, 16, fieldHeight,
            Component.literal("\u27F3"),
            button -> {
                delayMinField.setValue(String.valueOf(QuestNPCEntity.DEFAULT_DELAY_MIN));
                delayMaxField.setValue(String.valueOf(QuestNPCEntity.DEFAULT_DELAY_MAX));
                validateAll();
            }
        ));

        // ═══ Секция: Здоровье (v2.8.0) ═══
        healthSectionY = delayFieldY + fieldHeight + 30;
        healthFieldY = healthSectionY + 30;

        healthMaxField = new EditBox(this.font, contentX + SECTION_PAD, healthFieldY, 60, fieldHeight,
                Component.translatable("gui.questnpc.attributes.health_max"));
        healthMaxField.setMaxLength(6);
        healthMaxField.setValue(formatHealth(npc.getMaxHealth()));
        healthMaxField.setResponder(text -> validateAll());
        healthMaxField.setBordered(false);
        this.addRenderableWidget(healthMaxField);

        // Кнопка "Исцелить" — выставляет флаг, реальный heal — в applySettings().
        this.addRenderableWidget(new DarkButton(
            contentX + SECTION_PAD + 66, healthFieldY, 80, fieldHeight,
            Component.translatable("gui.questnpc.attributes.heal_btn"),
            button -> {
                pendingHeal = true;
                button.setMessage(Component.translatable("gui.questnpc.attributes.heal_btn_pending"));
            },
            NPCMenuScreen.BTN_GREEN_BG, NPCMenuScreen.BTN_GREEN_HOVER, 0xFFFFFFFF
        ));

        // ═══ Кнопки внизу ═══
        int btnY = panelY + PANEL_HEIGHT - 36;
        int btnW = (contentW - 8) / 2;

        this.addRenderableWidget(new DarkButton(
            contentX, btnY, btnW, 20,
            Component.translatable("gui.questnpc.npc_menu.cancel"),
            button -> Minecraft.getInstance().setScreen(parentScreen),
            NPCMenuScreen.BTN_GRAY_BG, NPCMenuScreen.BTN_GRAY_HOVER, NPCMenuScreen.TEXT_WHITE
        ));

        this.addRenderableWidget(new DarkButton(
            contentX + btnW + 8, btnY, btnW, 20,
            Component.translatable("gui.questnpc.npc_menu.apply"),
            button -> applySettings(),
            NPCMenuScreen.BTN_GREEN_BG, NPCMenuScreen.BTN_GREEN_HOVER, 0xFFFFFFFF
        ));

        validateAll();
    }

    private static String formatSpeed(double speed) {
        String s = String.format("%.2f", speed);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) s += "0";
        }
        return s;
    }

    private static String formatHealth(double hp) {
        return String.format(java.util.Locale.ROOT, "%.1f", hp);
    }

    private void validateAll() {
        speedValid = false;
        try {
            double speed = Double.parseDouble(speedField.getValue());
            speedValid = speed >= 0.05 && speed <= 1.0;
        } catch (NumberFormatException ignored) {}

        delayMinValid = false;
        int minVal = -1;
        try {
            minVal = Integer.parseInt(delayMinField.getValue());
            delayMinValid = minVal >= 1 && minVal <= 120;
        } catch (NumberFormatException ignored) {}

        delayMaxValid = false;
        int maxVal = -1;
        try {
            maxVal = Integer.parseInt(delayMaxField.getValue());
            delayMaxValid = maxVal >= 1 && maxVal <= 120;
        } catch (NumberFormatException ignored) {}

        delayRangeValid = delayMinValid && delayMaxValid && minVal <= maxVal;

        healthValid = false;
        try {
            double hp = Double.parseDouble(healthMaxField.getValue());
            healthValid = hp >= 1.0 && hp <= 1024.0;
        } catch (NumberFormatException ignored) {}

        speedField.setTextColor(speedValid ? 0xFFFFFF : (NPCMenuScreen.TEXT_RED & 0x00FFFFFF));
        delayMinField.setTextColor(delayMinValid && delayRangeValid ? 0xFFFFFF : (NPCMenuScreen.TEXT_RED & 0x00FFFFFF));
        delayMaxField.setTextColor(delayMaxValid && delayRangeValid ? 0xFFFFFF : (NPCMenuScreen.TEXT_RED & 0x00FFFFFF));
        healthMaxField.setTextColor(healthValid ? 0xFFFFFF : (NPCMenuScreen.TEXT_RED & 0x00FFFFFF));
    }

    private void applySettings() {
        validateAll();
        if (!speedValid || !delayMinValid || !delayMaxValid || !delayRangeValid || !healthValid) return;

        double speed = Double.parseDouble(speedField.getValue());
        int delayMin = Integer.parseInt(delayMinField.getValue());
        int delayMax = Integer.parseInt(delayMaxField.getValue());
        double maxHealth = Double.parseDouble(healthMaxField.getValue());

        ModNetwork.INSTANCE.sendToServer(
                new UpdateNPCSettingsPacket(npc.getId(), speed, delayMin, delayMax, maxHealth, pendingHeal)
        );
        this.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, NPCMenuScreen.BG_OVERLAY);

        int contentX = panelX + PADDING;
        int contentW = PANEL_WIDTH - PADDING * 2;

        NPCMenuScreen.drawPanel(g, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);

        // Заголовок
        g.drawCenteredString(this.font, this.title, panelX + PANEL_WIDTH / 2, panelY + 8,
                NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF);
        g.fill(panelX + 1, panelY + 22, panelX + PANEL_WIDTH - 1, panelY + 23, NPCMenuScreen.BORDER);

        // ═══ Секция: Скорость ═══
        int speedSectionY = panelY + 56;
        NPCMenuScreen.drawSection(g, this.font, contentX, speedSectionY, contentW, 56,
                Component.translatable("gui.questnpc.npc_menu.speed_section").getString());

        int speedFieldY = speedSectionY + 22;
        NPCMenuScreen.drawEditBoxBg(g, contentX + SECTION_PAD - 2, speedFieldY - 2, 64, 20, speedValid);

        Component speedHint = Component.translatable("gui.questnpc.npc_menu.speed_hint");
        g.drawString(this.font, speedHint, contentX + SECTION_PAD, speedFieldY + 20,
                NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);

        // ═══ Секция: Задержка ═══
        int delaySectionY = speedFieldY + 16 + 30;
        NPCMenuScreen.drawSection(g, this.font, contentX, delaySectionY, contentW, 68,
                Component.translatable("gui.questnpc.npc_menu.delay_section").getString());

        int delayFieldY = delaySectionY + 30;
        g.drawString(this.font, Component.translatable("gui.questnpc.npc_menu.delay_min"),
                contentX + SECTION_PAD, delaySectionY + 18, NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);
        g.drawString(this.font, Component.translatable("gui.questnpc.npc_menu.delay_max"),
                contentX + SECTION_PAD + 62, delaySectionY + 18, NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);

        boolean delayMinOk = delayMinValid && delayRangeValid;
        boolean delayMaxOk = delayMaxValid && delayRangeValid;
        NPCMenuScreen.drawEditBoxBg(g, contentX + SECTION_PAD - 2, delayFieldY - 2, 44, 20, delayMinOk);
        g.drawString(this.font, "\u2014", contentX + SECTION_PAD + 48, delayFieldY + 2,
                NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);
        NPCMenuScreen.drawEditBoxBg(g, contentX + SECTION_PAD + 60, delayFieldY - 2, 44, 20, delayMaxOk);

        Component delayHint = Component.translatable("gui.questnpc.npc_menu.delay_hint");
        g.drawString(this.font, delayHint, contentX + SECTION_PAD, delayFieldY + 20,
                NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);

        // ═══ Секция: Здоровье (v2.8.0) ═══
        NPCMenuScreen.drawSection(g, this.font, contentX, healthSectionY, contentW, 68,
                Component.translatable("gui.questnpc.attributes.health_section").getString());

        // Текущее / Макс
        String currentLabel = String.format(java.util.Locale.ROOT,
                Component.translatable("gui.questnpc.attributes.health_current").getString(),
                npc.getHealth(), npc.getMaxHealth());
        g.drawString(this.font, currentLabel, contentX + SECTION_PAD, healthSectionY + 18,
                NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);

        // Фон поля Max HP
        NPCMenuScreen.drawEditBoxBg(g, contentX + SECTION_PAD - 2, healthFieldY - 2, 64, 20, healthValid);

        Component healthHint = Component.translatable("gui.questnpc.attributes.health_hint");
        g.drawString(this.font, healthHint, contentX + SECTION_PAD, healthFieldY + 20,
                NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);

        // Футер
        Component footer = Component.translatable("gui.questnpc.menu.footer");
        g.drawCenteredString(this.font, footer, panelX + PANEL_WIDTH / 2, panelY + PANEL_HEIGHT - 12,
                NPCMenuScreen.TEXT_DARK_GRAY & 0x00FFFFFF);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
