package com.questnpc.client.gui;

import com.questnpc.client.gui.widget.DarkButton;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.quest.QuestDefinition;
import com.questnpc.entity.quest.QuestObjective;
import com.questnpc.entity.quest.objective.KillObjective;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.UpdateNPCQuestsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Редактор квестов NPC (v2.9.1 MVP).
 *
 * <p>Двухпанельный layout: слева список квестов (≤20) с кнопкой «+ Добавить», справа —
 * форма редактирования выбранного квеста (title, description, список целей ≤10).
 *
 * <p>В MVP поддержан только тип objective KILL с временным text-полем для entityId
 * (без EntityCatalogScreen — pickers в Этапе 3). Другие типы целей (Bring/Break/Reach*)
 * имеют классы из Этапа 1, но без UI до Этапа 3. Tag-mode toggle — Этап 3.
 * Награды и условия — Этапы 4/6.
 *
 * <p>Все изменения локальны (мутируют {@link #pendingQuests}) до нажатия «Применить»,
 * которое шлёт {@link UpdateNPCQuestsPacket}, обновляет parent через
 * {@link NPCMenuScreen#setQuestsSnapshot} и возвращает в parent через
 * {@code setScreen(parentScreen)} (паттерн v2.8.1).
 */
public class QuestsScreen extends Screen {

    // ─── Layout ──────────────────────────────────────────────────────────
    private static final int PANEL_WIDTH    = 380;
    private static final int PANEL_HEIGHT   = 316;
    private static final int PADDING        = 10;

    // Левая колонка (список квестов)
    private static final int LIST_WIDTH     = 105;
    private static final int LIST_ROW_H     = 16;
    private static final int LIST_VISIBLE_ROWS = 11; // помещается в высоту панели
    private static final int LIST_Y_OFFSET  = 50;    // относительно panelY

    // Правая колонка (форма)
    private static final int FORM_X_OFFSET  = LIST_WIDTH + 6; // относительно panelX + PADDING
    private static final int OBJ_ROW_H      = 22;
    private static final int OBJ_VISIBLE    = 4;  // визуально помещается до 4 целей без скролла

    // Цвет выделения выбранного квеста в списке
    private static final int LIST_SELECTED_BG = 0xFF2DD4BF;
    private static final int LIST_NORMAL_BG   = 0xFF374151;
    private static final int LIST_HOVER_BG    = 0xFF4B5563;

    // Regex для валидации entityId (формат ResourceLocation: namespace:path)
    private static final Pattern ENTITY_ID_PATTERN = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_/.-]+$");

    // Лимиты счётчика убийств в objective
    private static final int COUNT_MIN = 1;
    private static final int COUNT_MAX = 9999;

    // ─── Data ────────────────────────────────────────────────────────────
    private final QuestNPCEntity npc;
    private final Screen parentScreen;

    /** Рабочая копия списка квестов (мутируется EditBox responders). Изначально — deep-copy из parent. */
    private final List<QuestDefinition> pendingQuests = new ArrayList<>();
    private boolean pendingQuestsEnabled;

    private int selectedIndex = -1;
    private int scrollOffset = 0;

    /** True если хотя бы одна попытка Apply провалилась — для подсветки невалидных полей. */
    private boolean showValidationErrors = false;

    /** Двойной клик для удаления квеста — индекс ожидающего подтверждения. -1 = нет. */
    private int deleteConfirmIndex = -1;

    private int panelX, panelY;

    // ─── Виджеты (пересоздаются при init() и при смене selectedIndex) ──
    private DarkButton enableToggleBtn;
    private DarkButton addQuestBtn;
    private DarkButton scrollUpBtn;
    private DarkButton scrollDownBtn;
    // Форма редактирования (создаётся когда selectedIndex != -1)
    @Nullable private EditBox titleBox;
    @Nullable private EditBox descBox;
    @Nullable private DarkButton deleteQuestBtn;
    @Nullable private DarkButton addObjectiveBtn;
    // Objective rows (до OBJ_VISIBLE)
    @Nullable private final EditBox[] objEntityBoxes = new EditBox[10]; // на 10 целей макс
    @Nullable private final EditBox[] objCountBoxes  = new EditBox[10];

    public QuestsScreen(QuestNPCEntity npc,
                        Screen parentScreen,
                        boolean initialEnabled,
                        List<QuestDefinition> initialQuests) {
        super(Component.translatable("gui.questnpc.quests.title",
                npc.hasCustomName() ? npc.getCustomName().getString() : npc.getName().getString()));
        this.npc = npc;
        this.parentScreen = parentScreen;
        this.pendingQuestsEnabled = initialEnabled;
        if (initialQuests != null) {
            for (QuestDefinition q : initialQuests) {
                if (q != null) pendingQuests.add(QuestDefinition.load(q.save())); // deep copy
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;

        int contentX = panelX + PADDING;
        int contentY = panelY + 28;

        // ── Top bar: toggle + counter ──
        enableToggleBtn = this.addRenderableWidget(new DarkButton(
                contentX, contentY, 140, 18,
                buildEnableLabel(),
                btn -> {
                    pendingQuestsEnabled = !pendingQuestsEnabled;
                    btn.setMessage(buildEnableLabel());
                }
        ));

        // ── Left column: список квестов ──
        int listX = contentX;
        int listY = panelY + LIST_Y_OFFSET;
        rebuildListWidgets(listX, listY);

        // ── Add quest button (под списком) ──
        int addBtnY = listY + LIST_VISIBLE_ROWS * LIST_ROW_H + 4;
        addQuestBtn = this.addRenderableWidget(new DarkButton(
                listX, addBtnY, LIST_WIDTH - 2, 16,
                Component.translatable("gui.questnpc.quests.add"),
                btn -> addNewQuest(),
                NPCMenuScreen.BTN_GREEN_BG, NPCMenuScreen.BTN_GREEN_HOVER, 0xFFFFFFFF
        ));
        addQuestBtn.active = pendingQuests.size() < QuestNPCEntity.MAX_QUESTS;

        // ── Scroll buttons (right strip of list, видимы при > LIST_VISIBLE_ROWS) ──
        int scrollX = listX + LIST_WIDTH - 14 - 2;
        scrollUpBtn = this.addRenderableWidget(new DarkButton(
                scrollX, listY + 2, 14, 14,
                Component.literal("▲"),
                btn -> { if (scrollOffset > 0) { scrollOffset--; reinit(); } }
        ));
        scrollDownBtn = this.addRenderableWidget(new DarkButton(
                scrollX, listY + LIST_VISIBLE_ROWS * LIST_ROW_H - 16, 14, 14,
                Component.literal("▼"),
                btn -> {
                    if (scrollOffset + LIST_VISIBLE_ROWS < pendingQuests.size()) {
                        scrollOffset++;
                        reinit();
                    }
                }
        ));
        boolean showScroll = pendingQuests.size() > LIST_VISIBLE_ROWS;
        scrollUpBtn.visible = showScroll;
        scrollDownBtn.visible = showScroll;
        scrollUpBtn.active = scrollOffset > 0;
        scrollDownBtn.active = scrollOffset + LIST_VISIBLE_ROWS < pendingQuests.size();

        // ── Right column: form для выбранного квеста ──
        if (selectedIndex >= 0 && selectedIndex < pendingQuests.size()) {
            buildEditorWidgets(contentX + FORM_X_OFFSET, panelY + LIST_Y_OFFSET);
        }

        // ── Bottom bar: Back / Cancel / Apply ──
        int btnY = panelY + PANEL_HEIGHT - 26;
        // Back (left)
        this.addRenderableWidget(new DarkButton(
                contentX, btnY, 60, 18,
                Component.translatable("gui.questnpc.quests.back"),
                btn -> Minecraft.getInstance().setScreen(parentScreen)
        ));
        // Apply (right, green)
        this.addRenderableWidget(new DarkButton(
                panelX + PANEL_WIDTH - PADDING - 80, btnY, 80, 18,
                Component.translatable("gui.questnpc.npc_menu.apply"),
                btn -> tryApply(),
                NPCMenuScreen.BTN_GREEN_BG, NPCMenuScreen.BTN_GREEN_HOVER, 0xFFFFFFFF
        ));
        // Cancel (between, gray)
        this.addRenderableWidget(new DarkButton(
                panelX + PANEL_WIDTH - PADDING - 80 - 60 - 4, btnY, 60, 18,
                Component.translatable("gui.questnpc.npc_menu.cancel"),
                btn -> Minecraft.getInstance().setScreen(parentScreen),
                NPCMenuScreen.BTN_GRAY_BG, NPCMenuScreen.BTN_GRAY_HOVER, NPCMenuScreen.TEXT_WHITE
        ));
    }

    private Component buildEnableLabel() {
        return Component.translatable(pendingQuestsEnabled
                ? "gui.questnpc.quests.enabled"
                : "gui.questnpc.quests.disabled");
    }

    private void rebuildListWidgets(int listX, int listY) {
        // Кнопки-строки списка — клик выбирает квест.
        int visibleEnd = Math.min(scrollOffset + LIST_VISIBLE_ROWS, pendingQuests.size());
        for (int i = scrollOffset; i < visibleEnd; i++) {
            final int idx = i;
            QuestDefinition q = pendingQuests.get(i);
            String label = q.getTitle().isEmpty()
                    ? Component.translatable("gui.questnpc.quests.untitled", i + 1).getString()
                    : truncate(q.getTitle(), 14);
            int bg = (idx == selectedIndex) ? LIST_SELECTED_BG : LIST_NORMAL_BG;
            int textColor = (idx == selectedIndex) ? 0xFF000000 : NPCMenuScreen.TEXT_WHITE;
            int rowY = listY + (i - scrollOffset) * LIST_ROW_H;
            this.addRenderableWidget(new DarkButton(
                    listX, rowY, LIST_WIDTH - 2, LIST_ROW_H - 1,
                    Component.literal(label),
                    btn -> {
                        selectedIndex = idx;
                        deleteConfirmIndex = -1; // сбрасываем confirm при смене квеста
                        reinit();
                    },
                    bg, LIST_HOVER_BG, textColor
            ));
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private void buildEditorWidgets(int formX, int formY) {
        QuestDefinition q = pendingQuests.get(selectedIndex);

        int formW = PANEL_WIDTH - LIST_WIDTH - PADDING * 2 - 6;
        int y = formY;

        // ── Секция «КВЕСТ» ──
        y += 18; // под header текст рисуется в render()
        // Title
        titleBox = new EditBox(this.font, formX + 4, y, formW - 8, 14,
                Component.translatable("gui.questnpc.quests.title_field"));
        titleBox.setMaxLength(QuestDefinition.MAX_TITLE_LENGTH);
        titleBox.setValue(q.getTitle());
        titleBox.setResponder(text -> q.setTitle(text));
        titleBox.setBordered(true);
        this.addRenderableWidget(titleBox);

        y += 20;
        // Description
        descBox = new EditBox(this.font, formX + 4, y, formW - 8, 14,
                Component.translatable("gui.questnpc.quests.desc_field"));
        descBox.setMaxLength(QuestDefinition.MAX_DESCRIPTION_LENGTH);
        descBox.setValue(q.getDescription());
        descBox.setResponder(text -> q.setDescription(text));
        descBox.setBordered(true);
        this.addRenderableWidget(descBox);

        y += 26;
        // ── Секция «ЦЕЛИ» ──
        y += 16; // под header

        List<QuestObjective> objectives = q.getObjectives();
        int objCount = Math.min(objectives.size(), OBJ_VISIBLE);
        for (int i = 0; i < objCount; i++) {
            buildObjectiveRow(formX + 4, y + i * OBJ_ROW_H, formW - 8, i, objectives.get(i));
        }

        // Кнопка «+ Добавить цель»
        addObjectiveBtn = this.addRenderableWidget(new DarkButton(
                formX + 4, y + objCount * OBJ_ROW_H + 2, formW - 8, 14,
                Component.translatable("gui.questnpc.quests.add_objective"),
                btn -> addNewObjective(),
                NPCMenuScreen.BTN_GREEN_BG, NPCMenuScreen.BTN_GREEN_HOVER, 0xFFFFFFFF
        ));
        addObjectiveBtn.active = objectives.size() < QuestDefinition.MAX_OBJECTIVES;

        // ── Кнопка «Удалить квест» внизу формы ──
        boolean isConfirm = (deleteConfirmIndex == selectedIndex);
        Component delLabel = Component.translatable(isConfirm
                ? "gui.questnpc.quests.delete_confirm"
                : "gui.questnpc.quests.delete");
        int delBtnY = panelY + PANEL_HEIGHT - 50;
        deleteQuestBtn = this.addRenderableWidget(new DarkButton(
                formX + formW - 90, delBtnY, 90, 14,
                delLabel,
                btn -> handleDeleteClick(),
                0xFF7F1D1D, 0xFFB91C1C, 0xFFFFFFFF
        ));
    }

    private void buildObjectiveRow(int x, int y, int w, int slotIdx, QuestObjective obj) {
        if (!(obj instanceof KillObjective ko)) return; // в MVP только KILL

        // Type cycler (disabled, tooltip)
        DarkButton typeBtn = this.addRenderableWidget(new DarkButton(
                x, y, 36, 18,
                Component.translatable("gui.questnpc.quests.type_kill"),
                btn -> {}, // disabled, в этапе 3 станет cycler
                NPCMenuScreen.BTN_GRAY_BG, NPCMenuScreen.BTN_GRAY_HOVER, NPCMenuScreen.TEXT_DARK_GRAY
        ));
        typeBtn.active = false; // Disabled
        typeBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.questnpc.quests.type_locked")));

        // entityId EditBox
        EditBox entityBox = new EditBox(this.font, x + 40, y + 2, w - 40 - 60 - 24, 14,
                Component.translatable("gui.questnpc.quests.entity_id"));
        entityBox.setMaxLength(64);
        entityBox.setValue(ko.getEntityType() != null ? ko.getEntityType().toString() : "");
        entityBox.setHint(Component.literal("minecraft:zombie"));
        entityBox.setResponder(text -> {
            ResourceLocation rl = ResourceLocation.tryParse(text);
            ko.setEntityType(rl);
        });
        entityBox.setBordered(true);
        this.addRenderableWidget(entityBox);
        objEntityBoxes[slotIdx] = entityBox;

        // count EditBox
        EditBox countBox = new EditBox(this.font, x + w - 80, y + 2, 56, 14,
                Component.translatable("gui.questnpc.quests.count_field"));
        countBox.setMaxLength(4);
        countBox.setValue(String.valueOf(ko.getCount()));
        countBox.setResponder(text -> {
            try {
                int v = Integer.parseInt(text.trim());
                if (v >= COUNT_MIN && v <= COUNT_MAX) ko.setCount(v);
            } catch (NumberFormatException ignored) { /* not applied */ }
        });
        countBox.setBordered(true);
        this.addRenderableWidget(countBox);
        objCountBoxes[slotIdx] = countBox;

        // Delete objective ✕
        final int slotIdxFinal = slotIdx;
        this.addRenderableWidget(new DarkButton(
                x + w - 20, y, 20, 18,
                Component.literal("✕"),
                btn -> removeObjective(slotIdxFinal),
                0xFF7F1D1D, 0xFFB91C1C, 0xFFFFFFFF
        ));
    }

    private void addNewQuest() {
        if (pendingQuests.size() >= QuestNPCEntity.MAX_QUESTS) return;
        QuestDefinition q = new QuestDefinition();
        q.addObjective(new KillObjective()); // UX: сразу видна форма objective
        pendingQuests.add(q);
        selectedIndex = pendingQuests.size() - 1;
        // Scroll to end if needed
        if (selectedIndex >= scrollOffset + LIST_VISIBLE_ROWS) {
            scrollOffset = selectedIndex - LIST_VISIBLE_ROWS + 1;
        }
        deleteConfirmIndex = -1;
        showValidationErrors = false;
        reinit();
    }

    private void addNewObjective() {
        if (selectedIndex < 0) return;
        QuestDefinition q = pendingQuests.get(selectedIndex);
        if (q.getObjectives().size() >= QuestDefinition.MAX_OBJECTIVES) return;
        q.addObjective(new KillObjective());
        reinit();
    }

    private void removeObjective(int slotIdx) {
        if (selectedIndex < 0) return;
        QuestDefinition q = pendingQuests.get(selectedIndex);
        if (slotIdx < 0 || slotIdx >= q.getObjectives().size()) return;
        q.removeObjective(slotIdx);
        reinit();
    }

    private void handleDeleteClick() {
        if (selectedIndex < 0) return;
        if (deleteConfirmIndex != selectedIndex) {
            // first click — ask confirmation
            deleteConfirmIndex = selectedIndex;
            if (deleteQuestBtn != null) {
                deleteQuestBtn.setMessage(Component.translatable("gui.questnpc.quests.delete_confirm"));
            }
        } else {
            // second click — actually delete
            pendingQuests.remove(selectedIndex);
            selectedIndex = -1;
            deleteConfirmIndex = -1;
            showValidationErrors = false;
            // Корректировка scroll
            if (scrollOffset > 0 && scrollOffset + LIST_VISIBLE_ROWS > pendingQuests.size()) {
                scrollOffset = Math.max(0, pendingQuests.size() - LIST_VISIBLE_ROWS);
            }
            reinit();
        }
    }

    /** Полный re-init виджетов (вызывается при изменении selectedIndex, добавлении/удалении). */
    private void reinit() {
        this.clearWidgets();
        this.init();
    }

    private void tryApply() {
        if (!validateAll()) {
            showValidationErrors = true;
            return;
        }
        showValidationErrors = false;
        // Отправляем пакет на сервер
        ModNetwork.INSTANCE.sendToServer(new UpdateNPCQuestsPacket(
                npc.getId(), pendingQuestsEnabled, pendingQuests));
        // v2.8.1 pattern: обновляем parent-snapshot локально, чтобы при reopen QuestsScreen
        // данные были свежими без дожидания нового OpenNPCMenuPacket с сервера.
        if (parentScreen instanceof NPCMenuScreen npcm) {
            npcm.setQuestsSnapshot(pendingQuestsEnabled, pendingQuests);
        }
        Minecraft.getInstance().setScreen(parentScreen);
    }

    /** Возвращает true если все квесты валидны. Side-effect: устанавливает selectedIndex на первый невалидный. */
    private boolean validateAll() {
        for (int i = 0; i < pendingQuests.size(); i++) {
            QuestDefinition q = pendingQuests.get(i);
            if (!isQuestValid(q)) {
                selectedIndex = i;
                scrollOffset = Math.max(0,
                        Math.min(i, pendingQuests.size() - LIST_VISIBLE_ROWS));
                return false;
            }
        }
        return true;
    }

    private boolean isQuestValid(QuestDefinition q) {
        if (q.getTitle().trim().isEmpty()) return false;
        if (q.getObjectives().isEmpty()) return false;
        for (QuestObjective obj : q.getObjectives()) {
            if (obj instanceof KillObjective ko) {
                ResourceLocation rl = ko.getEntityType();
                if (rl == null || !ENTITY_ID_PATTERN.matcher(rl.toString()).matches()) return false;
                if (ko.getCount() < COUNT_MIN || ko.getCount() > COUNT_MAX) return false;
            }
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, NPCMenuScreen.BG_OVERLAY);
        NPCMenuScreen.drawPanel(g, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);

        // Заголовок
        g.drawCenteredString(this.font, this.title, panelX + PANEL_WIDTH / 2, panelY + 8,
                NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF);
        g.fill(panelX + 1, panelY + 22, panelX + PANEL_WIDTH - 1, panelY + 23, NPCMenuScreen.BORDER);

        // Счётчик квестов справа от toggle
        String counter = Component.translatable("gui.questnpc.quests.count",
                pendingQuests.size(), QuestNPCEntity.MAX_QUESTS).getString();
        g.drawString(this.font, counter,
                panelX + PADDING + 150,
                panelY + 33,
                NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);

        // Разделитель списка / формы
        int sepX = panelX + PADDING + LIST_WIDTH + 2;
        g.fill(sepX, panelY + LIST_Y_OFFSET - 4, sepX + 1,
                panelY + PANEL_HEIGHT - 32, NPCMenuScreen.BORDER);

        super.render(g, mouseX, mouseY, partialTick);

        renderRightPane(g, mouseX, mouseY);

        // Validation status (bottom centre, выше bottom-buttons)
        if (showValidationErrors) {
            String msg = Component.translatable("gui.questnpc.quests.validation_err").getString();
            g.drawCenteredString(this.font, msg,
                    panelX + PANEL_WIDTH / 2,
                    panelY + PANEL_HEIGHT - 42,
                    NPCMenuScreen.TEXT_RED & 0x00FFFFFF);
        }

        // Футер
        Component footer = Component.translatable("gui.questnpc.menu.footer");
        g.drawCenteredString(this.font, footer, panelX + PANEL_WIDTH / 2, panelY + PANEL_HEIGHT - 9,
                NPCMenuScreen.TEXT_DARK_GRAY & 0x00FFFFFF);
    }

    private void renderRightPane(GuiGraphics g, int mouseX, int mouseY) {
        int formX = panelX + PADDING + FORM_X_OFFSET;
        int formY = panelY + LIST_Y_OFFSET;
        int formW = PANEL_WIDTH - LIST_WIDTH - PADDING * 2 - 6;

        if (selectedIndex < 0 || selectedIndex >= pendingQuests.size()) {
            // Placeholder
            Component hint = Component.translatable("gui.questnpc.quests.select_hint");
            g.drawCenteredString(this.font, hint,
                    formX + formW / 2,
                    formY + 100,
                    NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF);
            return;
        }

        QuestDefinition q = pendingQuests.get(selectedIndex);

        // Section header «КВЕСТ»
        String qSection = Component.translatable("gui.questnpc.quests.quest_section").getString();
        g.drawString(this.font, qSection, formX, formY + 4,
                NPCMenuScreen.SECTION_TITLE & 0x00FFFFFF, false);
        g.fill(formX, formY + 14, formX + formW, formY + 15, NPCMenuScreen.BORDER);

        // Подсветка невалидных полей при showValidationErrors
        if (showValidationErrors && titleBox != null && q.getTitle().trim().isEmpty()) {
            NPCMenuScreen.drawOutlineRect(g, titleBox.getX() - 1, titleBox.getY() - 1,
                    titleBox.getWidth() + 2, titleBox.getHeight() + 2, NPCMenuScreen.TEXT_RED);
        }

        // Section header «ЦЕЛИ»
        int objHeaderY = formY + 18 + 20 + 26;
        String oSection = Component.translatable("gui.questnpc.quests.objectives").getString();
        g.drawString(this.font, oSection, formX, objHeaderY,
                NPCMenuScreen.SECTION_TITLE & 0x00FFFFFF, false);
        g.fill(formX, objHeaderY + 10, formX + formW, objHeaderY + 11, NPCMenuScreen.BORDER);

        // Подсветка невалидных objective полей
        if (showValidationErrors) {
            List<QuestObjective> objs = q.getObjectives();
            for (int i = 0; i < Math.min(objs.size(), OBJ_VISIBLE); i++) {
                if (objs.get(i) instanceof KillObjective ko) {
                    ResourceLocation rl = ko.getEntityType();
                    boolean entityBad = (rl == null) || !ENTITY_ID_PATTERN.matcher(rl.toString()).matches();
                    boolean countBad = ko.getCount() < COUNT_MIN || ko.getCount() > COUNT_MAX;
                    if (entityBad && objEntityBoxes[i] != null) {
                        EditBox b = objEntityBoxes[i];
                        NPCMenuScreen.drawOutlineRect(g, b.getX() - 1, b.getY() - 1,
                                b.getWidth() + 2, b.getHeight() + 2, NPCMenuScreen.TEXT_RED);
                    }
                    if (countBad && objCountBoxes[i] != null) {
                        EditBox b = objCountBoxes[i];
                        NPCMenuScreen.drawOutlineRect(g, b.getX() - 1, b.getY() - 1,
                                b.getWidth() + 2, b.getHeight() + 2, NPCMenuScreen.TEXT_RED);
                    }
                }
            }
        }

        // Hint про rewards
        Component soonHint = Component.translatable("gui.questnpc.quests.rewards_soon");
        g.drawString(this.font, soonHint,
                formX, panelY + PANEL_HEIGHT - 70,
                NPCMenuScreen.TEXT_DARK_GRAY & 0x00FFFFFF, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
