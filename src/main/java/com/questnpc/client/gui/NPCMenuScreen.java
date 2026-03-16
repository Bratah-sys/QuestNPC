package com.questnpc.client.gui;

import com.questnpc.client.gui.widget.TabButton;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.RequestPatrolChangePacket;
import com.questnpc.network.UpdateNPCSettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * GUI-экран меню NPC. Открывается при ПКМ палкой по QuestNPCEntity.
 * Тёмный кастомный UI с вкладками, секциями и стильным рендерингом.
 */
public class NPCMenuScreen extends Screen {

    // ═══ Цветовая палитра ═══
    private static final int BG_OVERLAY     = 0xC0000000;  // Затемнение за панелью
    private static final int BG_DARK        = 0xFF1A1A2E;  // Основной фон панели
    private static final int SECTION_BG     = 0xFF1F2937;  // Фон секций
    private static final int BORDER         = 0xFF4A5568;  // Рамки
    private static final int BORDER_LIGHT   = 0xFF6B7280;  // Рамка при фокусе
    private static final int TEXT_WHITE     = 0xFFE2E8F0;  // Заголовки
    private static final int TEXT_GRAY      = 0xFF9CA3AF;  // Подсказки
    private static final int TEXT_DARK_GRAY = 0xFF6B7280;  // Футер
    private static final int TEXT_CYAN      = 0xFF2DD4BF;  // Координаты
    private static final int TEXT_RED       = 0xFFFF5555;  // Ошибки валидации
    private static final int BTN_GREEN_BG   = 0xFF10B981;  // Кнопка "Применить"
    private static final int BTN_GREEN_HOVER= 0xFF22C55E;  // Кнопка "Применить" hover
    private static final int BTN_GRAY_BG    = 0xFF374151;  // Кнопка "Отмена"
    private static final int BTN_GRAY_HOVER = 0xFF4B5563;  // Кнопка "Отмена" hover
    private static final int BTN_OUTLINE    = 0xFF4A5568;  // Кнопка "Сменить" рамка
    private static final int BTN_OUTLINE_HV = 0xFF6B7280;  // Кнопка "Сменить" hover
    private static final int SECTION_TITLE  = 0xFF3B82F6;  // Заголовки секций (синий)
    private static final int RESET_BTN_BG   = 0xFF2D3748;  // Кнопка сброса фон
    private static final int RESET_BTN_HV   = 0xFF4A5568;  // Кнопка сброса hover
    private static final int EDIT_BG        = 0xFF111827;  // Фон полей ввода

    // ═══ Размеры панели ═══
    private static final int PANEL_WIDTH  = 280;
    private static final int PANEL_HEIGHT = 290;
    private static final int PADDING      = 12;
    private static final int SECTION_PAD  = 8;

    // ═══ Данные ═══
    private final QuestNPCEntity npc;
    private final double currentSpeed;
    private final int currentDelayMin;
    private final int currentDelayMax;

    // ═══ Виджеты ═══
    private EditBox speedField;
    private EditBox delayMinField;
    private EditBox delayMaxField;
    private TabButton[] tabs;
    private Button patrolChangeBtn;
    private Button speedResetBtn;
    private Button delayResetBtn;
    private Button cancelBtn;
    private Button applyBtn;

    // ═══ Состояние ═══
    private int currentTab = 0;
    private boolean speedValid = true;
    private boolean delayMinValid = true;
    private boolean delayMaxValid = true;
    private boolean delayRangeValid = true;

    // Координаты панели (вычисляются в init)
    private int panelX, panelY;

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

        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;

        int contentX = panelX + PADDING;
        int contentW = PANEL_WIDTH - PADDING * 2;

        // ═══ Вкладки ═══
        int tabY = panelY + 38;
        int tabW = contentW / 3;
        tabs = new TabButton[3];
        String[] tabKeys = {
            "gui.questnpc.npc_menu.tab.movement",
            "gui.questnpc.npc_menu.tab.quest",
            "gui.questnpc.npc_menu.tab.behavior"
        };
        for (int i = 0; i < 3; i++) {
            final int tabIndex = i;
            tabs[i] = new TabButton(
                contentX + i * tabW, tabY, tabW, 20,
                Component.translatable(tabKeys[i]),
                button -> switchTab(tabIndex)
            );
            this.addRenderableWidget(tabs[i]);
        }
        tabs[0].setSelected(true);

        // ═══ Секция: Точка патруля ═══
        int sectionStartY = tabY + 28;
        int patrolBtnY = sectionStartY + 28;
        patrolChangeBtn = this.addRenderableWidget(new OutlineButton(
            contentX + contentW - 72, patrolBtnY, 62, 16,
            Component.translatable("gui.questnpc.npc_menu.patrol_change"),
            button -> {
                ModNetwork.INSTANCE.sendToServer(new RequestPatrolChangePacket(npc.getId()));
                this.onClose();
            }
        ));

        // ═══ Секция: Скорость ═══
        int speedSectionY = patrolBtnY + 30;
        int speedFieldY = speedSectionY + 22;
        int fieldHeight = 16;

        speedField = new EditBox(this.font, contentX + SECTION_PAD, speedFieldY, 60, fieldHeight,
                Component.translatable("gui.questnpc.npc_menu.speed"));
        speedField.setMaxLength(6);
        speedField.setValue(formatSpeed(currentSpeed));
        speedField.setResponder(text -> validateAll());
        speedField.setBordered(false);
        this.addRenderableWidget(speedField);

        speedResetBtn = this.addRenderableWidget(new ResetButton(
            contentX + SECTION_PAD + 66, speedFieldY, 16, fieldHeight,
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

        delayResetBtn = this.addRenderableWidget(new ResetButton(
            contentX + SECTION_PAD + 108, delayFieldY, 16, fieldHeight,
            button -> {
                delayMinField.setValue(String.valueOf(QuestNPCEntity.DEFAULT_DELAY_MIN));
                delayMaxField.setValue(String.valueOf(QuestNPCEntity.DEFAULT_DELAY_MAX));
                validateAll();
            }
        ));

        // ═══ Кнопки внизу ═══
        int btnY = panelY + PANEL_HEIGHT - 36;
        int btnW = (contentW - 8) / 2;

        cancelBtn = this.addRenderableWidget(new StyledButton(
            contentX, btnY, btnW, 20,
            Component.translatable("gui.questnpc.npc_menu.cancel"),
            button -> this.onClose(),
            BTN_GRAY_BG, BTN_GRAY_HOVER, TEXT_WHITE
        ));

        applyBtn = this.addRenderableWidget(new StyledButton(
            contentX + btnW + 8, btnY, btnW, 20,
            Component.translatable("gui.questnpc.npc_menu.apply"),
            button -> applySettings(),
            BTN_GREEN_BG, BTN_GREEN_HOVER, 0xFFFFFFFF
        ));

        validateAll();
        updateWidgetVisibility();
    }

    // ═══ Переключение вкладок ═══
    private void switchTab(int index) {
        currentTab = index;
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setSelected(i == index);
        }
        updateWidgetVisibility();
    }

    /** Скрыть/показать виджеты вкладки "Движение" в зависимости от currentTab. */
    private void updateWidgetVisibility() {
        boolean movementTab = (currentTab == 0);
        speedField.visible = movementTab;
        delayMinField.visible = movementTab;
        delayMaxField.visible = movementTab;
        patrolChangeBtn.visible = movementTab;
        speedResetBtn.visible = movementTab;
        delayResetBtn.visible = movementTab;
        applyBtn.visible = movementTab;
    }

    // ═══ Форматирование скорости без лишних нулей ═══
    private static String formatSpeed(double speed) {
        String s = String.format("%.2f", speed);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) s += "0";
        }
        return s;
    }

    // ═══ Валидация всех полей ═══
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

        speedField.setTextColor(speedValid ? 0xFFFFFF : (TEXT_RED & 0x00FFFFFF));
        delayMinField.setTextColor(delayMinValid && delayRangeValid ? 0xFFFFFF : (TEXT_RED & 0x00FFFFFF));
        delayMaxField.setTextColor(delayMaxValid && delayRangeValid ? 0xFFFFFF : (TEXT_RED & 0x00FFFFFF));
    }

    // ═══ Отправка настроек на сервер ═══
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

    // ═══════════════════════════════════════════════
    // РЕНДЕРИНГ
    // ═══════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Затемнение фона
        g.fill(0, 0, this.width, this.height, BG_OVERLAY);

        int contentX = panelX + PADDING;
        int contentW = PANEL_WIDTH - PADDING * 2;

        // ═══ Основная панель ═══
        drawPanel(g, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);

        // ═══ Заголовок ═══
        g.drawCenteredString(this.font, this.title, panelX + PANEL_WIDTH / 2, panelY + 8, TEXT_WHITE & 0x00FFFFFF);
        Component subtitle = Component.translatable("gui.questnpc.npc_menu.subtitle");
        g.drawCenteredString(this.font, subtitle, panelX + PANEL_WIDTH / 2, panelY + 22, TEXT_GRAY & 0x00FFFFFF);

        // ═══ Разделитель под заголовком ═══
        g.fill(panelX + 1, panelY + 35, panelX + PANEL_WIDTH - 1, panelY + 36, BORDER);

        // ═══ Контент вкладки ═══
        int tabY = panelY + 38;
        if (currentTab == 0) {
            renderMovementTab(g, contentX, contentW, tabY);
        } else {
            renderWipTab(g, tabY);
        }

        // ═══ Футер ═══
        Component footer = Component.translatable("gui.questnpc.npc_menu.footer");
        g.drawCenteredString(this.font, footer, panelX + PANEL_WIDTH / 2, panelY + PANEL_HEIGHT - 12, TEXT_DARK_GRAY & 0x00FFFFFF);

        // Рендер виджетов (кнопки, поля)
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Рендер вкладки "Движение" — секции с контентом. */
    private void renderMovementTab(GuiGraphics g, int contentX, int contentW, int tabY) {
        // ═══ Секция: Точка патруля ═══
        int sectionY = tabY + 28;
        drawSection(g, contentX, sectionY, contentW, 48,
                Component.translatable("gui.questnpc.npc_menu.patrol_point").getString());

        // Координаты привязки
        BlockPos bound = npc.getBoundBlockPos();
        int coordY = sectionY + 28;
        if (bound != null) {
            String coordText = "X: " + bound.getX() + "  Y: " + bound.getY() + "  Z: " + bound.getZ();
            g.drawString(this.font, coordText, contentX + SECTION_PAD, coordY, TEXT_CYAN & 0x00FFFFFF, false);
        } else {
            Component notSet = Component.translatable("gui.questnpc.npc_menu.patrol_not_set");
            g.drawString(this.font, notSet, contentX + SECTION_PAD, coordY, TEXT_GRAY & 0x00FFFFFF, false);
        }

        // ═══ Секция: Скорость ═══
        int speedSectionY = sectionY + 56;
        drawSection(g, contentX, speedSectionY, contentW, 56,
                Component.translatable("gui.questnpc.npc_menu.speed_section").getString());

        // Фон поля ввода скорости
        int speedFieldY = speedSectionY + 22;
        drawEditBoxBackground(g, contentX + SECTION_PAD - 2, speedFieldY - 2, 64, 20, speedValid);

        // Подсказка
        Component speedHint = Component.translatable("gui.questnpc.npc_menu.speed_hint");
        g.drawString(this.font, speedHint, contentX + SECTION_PAD, speedFieldY + 20, TEXT_GRAY & 0x00FFFFFF, false);

        // ═══ Секция: Задержка ═══
        int delaySectionY = speedFieldY + 16 + 30;
        drawSection(g, contentX, delaySectionY, contentW, 68,
                Component.translatable("gui.questnpc.npc_menu.delay_section").getString());

        // Лейблы МИН / МАКС
        int delayFieldY = delaySectionY + 30;
        g.drawString(this.font, Component.translatable("gui.questnpc.npc_menu.delay_min"),
                contentX + SECTION_PAD, delaySectionY + 18, TEXT_GRAY & 0x00FFFFFF, false);
        g.drawString(this.font, Component.translatable("gui.questnpc.npc_menu.delay_max"),
                contentX + SECTION_PAD + 62, delaySectionY + 18, TEXT_GRAY & 0x00FFFFFF, false);

        // Фон полей ввода задержки
        boolean delayMinOk = delayMinValid && delayRangeValid;
        boolean delayMaxOk = delayMaxValid && delayRangeValid;
        drawEditBoxBackground(g, contentX + SECTION_PAD - 2, delayFieldY - 2, 44, 20, delayMinOk);
        g.drawString(this.font, "—", contentX + SECTION_PAD + 48, delayFieldY + 2, TEXT_GRAY & 0x00FFFFFF, false);
        drawEditBoxBackground(g, contentX + SECTION_PAD + 60, delayFieldY - 2, 44, 20, delayMaxOk);

        // Подсказка
        Component delayHint = Component.translatable("gui.questnpc.npc_menu.delay_hint");
        g.drawString(this.font, delayHint, contentX + SECTION_PAD, delayFieldY + 20, TEXT_GRAY & 0x00FFFFFF, false);
    }

    /** Рендер заглушки "В разработке..." для неактивных вкладок. */
    private void renderWipTab(GuiGraphics g, int tabY) {
        Component wip = Component.translatable("gui.questnpc.npc_menu.wip");
        int centerY = tabY + (PANEL_HEIGHT - 38 - 36) / 2;
        g.drawCenteredString(this.font, wip, panelX + PANEL_WIDTH / 2, centerY, TEXT_GRAY & 0x00FFFFFF);
    }

    // ═══════════════════════════════════════════════
    // ХЕЛПЕРЫ РЕНДЕРИНГА
    // ═══════════════════════════════════════════════

    /** Рисует основную панель с рамкой. */
    private static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        // Тень (чуть больше панели)
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x40000000);
        // Фон
        g.fill(x, y, x + w, y + h, BG_DARK);
        // Рамка
        drawOutlineRect(g, x, y, w, h, BORDER);
    }

    /** Рисует секцию с заголовком. */
    private void drawSection(GuiGraphics g, int x, int y, int w, int h, String title) {
        // Фон секции
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SECTION_BG);
        // Рамка секции
        drawOutlineRect(g, x, y, w, h, BORDER);
        // Заголовок секции — маленький текст сверху
        g.drawString(this.font, title, x + SECTION_PAD, y + 5, SECTION_TITLE & 0x00FFFFFF, false);
        // Линия под заголовком
        g.fill(x + 1, y + 16, x + w - 1, y + 17, BORDER);
    }

    /** Рисует фон для EditBox (поскольку bordered=false). */
    private static void drawEditBoxBackground(GuiGraphics g, int x, int y, int w, int h, boolean valid) {
        g.fill(x, y, x + w, y + h, EDIT_BG);
        int borderColor = valid ? BORDER : TEXT_RED;
        drawOutlineRect(g, x, y, w, h, borderColor);
    }

    /** Рисует прямоугольную рамку (1px). */
    private static void drawOutlineRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        // Верх
        g.fill(x, y, x + w, y + 1, color);
        // Низ
        g.fill(x, y + h - 1, x + w, y + h, color);
        // Лево
        g.fill(x, y, x + 1, y + h, color);
        // Право
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ═══════════════════════════════════════════════
    // КАСТОМНЫЕ КНОПКИ (внутренние классы)
    // ═══════════════════════════════════════════════

    /** Кнопка со сплошной заливкой (для "Применить" и "Отмена"). */
    private static class StyledButton extends Button {
        private final int bgColor;
        private final int hoverColor;
        private final int textColor;

        StyledButton(int x, int y, int w, int h, Component text, OnPress onPress, int bg, int hover, int textColor) {
            super(x, y, w, h, text, onPress, DEFAULT_NARRATION);
            this.bgColor = bg;
            this.hoverColor = hover;
            this.textColor = textColor;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int bg = this.isHoveredOrFocused() ? hoverColor : bgColor;
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
            drawOutlineRect(g, getX(), getY(), getWidth(), getHeight(), BORDER);

            var font = net.minecraft.client.Minecraft.getInstance().font;
            int tx = getX() + (getWidth() - font.width(getMessage())) / 2;
            int ty = getY() + (getHeight() - 8) / 2;
            g.drawString(font, getMessage(), tx, ty, textColor & 0x00FFFFFF, false);
        }
    }

    /** Кнопка с рамкой без заливки (для "Сменить"). */
    private static class OutlineButton extends Button {
        OutlineButton(int x, int y, int w, int h, Component text, OnPress onPress) {
            super(x, y, w, h, text, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hovered = this.isHoveredOrFocused();
            int border = hovered ? BTN_OUTLINE_HV : BTN_OUTLINE;
            // Лёгкая заливка при hover
            if (hovered) {
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20FFFFFF);
            }
            drawOutlineRect(g, getX(), getY(), getWidth(), getHeight(), border);

            var font = net.minecraft.client.Minecraft.getInstance().font;
            int tx = getX() + (getWidth() - font.width(getMessage())) / 2;
            int ty = getY() + (getHeight() - 8) / 2;
            int textColor = hovered ? (TEXT_WHITE & 0x00FFFFFF) : (TEXT_GRAY & 0x00FFFFFF);
            g.drawString(font, getMessage(), tx, ty, textColor, false);
        }
    }

    /** Маленькая кнопка сброса [⟳]. */
    private static class ResetButton extends Button {
        ResetButton(int x, int y, int w, int h, OnPress onPress) {
            super(x, y, w, h, Component.literal("\u27F3"), onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int bg = this.isHoveredOrFocused() ? RESET_BTN_HV : RESET_BTN_BG;
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
            drawOutlineRect(g, getX(), getY(), getWidth(), getHeight(), BORDER);

            var font = net.minecraft.client.Minecraft.getInstance().font;
            int tx = getX() + (getWidth() - font.width(getMessage())) / 2;
            int ty = getY() + (getHeight() - 8) / 2;
            int textColor = this.isHoveredOrFocused() ? 0xFFFFFF : (TEXT_GRAY & 0x00FFFFFF);
            g.drawString(font, getMessage(), tx, ty, textColor, false);
        }
    }
}
