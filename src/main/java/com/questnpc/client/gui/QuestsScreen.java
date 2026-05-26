package com.questnpc.client.gui;

import com.questnpc.client.gui.picker.BiomePickerScreen;
import com.questnpc.client.gui.picker.BlockCatalogScreen;
import com.questnpc.client.gui.picker.BlockTagPickerScreen;
import com.questnpc.client.gui.picker.EntityCatalogScreen;
import com.questnpc.client.gui.picker.EntityTagPickerScreen;
import com.questnpc.client.gui.picker.StructurePickerScreen;
import com.questnpc.client.gui.widget.DarkButton;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.quest.ConditionType;
import com.questnpc.entity.quest.ObjectiveType;
import com.questnpc.entity.quest.QuestCondition;
import com.questnpc.entity.quest.QuestDefinition;
import com.questnpc.entity.quest.QuestObjective;
import com.questnpc.entity.quest.QuestReward;
import com.questnpc.entity.quest.RewardType;
import com.questnpc.entity.quest.reward.CommandReward;
import com.questnpc.entity.quest.reward.ItemReward;
import com.questnpc.entity.quest.reward.UnlockTradeSetReward;
import com.questnpc.entity.quest.reward.XPPointsReward;
import com.questnpc.entity.quest.condition.DistanceToStructureCondition;
import com.questnpc.entity.quest.objective.BreakBlockObjective;
import com.questnpc.entity.quest.objective.BringObjective;
import com.questnpc.entity.quest.objective.KillObjective;
import com.questnpc.entity.quest.objective.ReachBiomeObjective;
import com.questnpc.entity.quest.objective.ReachStructureObjective;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.UpdateNPCQuestsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Редактор квестов NPC (v2.9.2 — Stage 3 «pickers + все типы objectives»).
 *
 * <p>Двухпанельный layout: слева список квестов (≤20) с кнопкой «+ Добавить», справа —
 * форма редактирования (title, description, список целей ≤10).
 *
 * <p>v2.9.2 расширение:
 * <ul>
 *   <li>Cycler типа objective активен — переключает {@link ObjectiveType} по кругу.
 *       При смене типа objective пересоздаётся через {@link QuestObjective#createEmpty}
 *       (параметры теряются — UX-простота, как Schedule).</li>
 *   <li>UI для всех 5 типов: KILL, BRING, BREAK_BLOCK, REACH_BIOME, REACH_STRUCTURE.</li>
 *   <li>Tag-mode toggle для KILL/BREAK_BLOCK — переключает между «конкретный» (Entity/BlockCatalogScreen)
 *       и «по тегу» (EntityTag/BlockTagPickerScreen). Смена режима очищает противоположное поле.</li>
 *   <li>Pickers — full-screen (EntityCatalogScreen, BlockCatalogScreen, BiomePickerScreen,
 *       StructurePickerScreen, EntityTagPickerScreen, BlockTagPickerScreen + переиспользуемый
 *       {@link ItemCatalogScreen} для BringObjective).</li>
 *   <li>BreakBlockObjective.dropRequired — checkbox с tooltip «Stage 5» (логика drop-tracking
 *       вне scope этого этапа).</li>
 * </ul>
 *
 * <p>Все изменения локальны до Apply ({@link UpdateNPCQuestsPacket}). Реальная проверка
 * прогресса (isComplete/matches) — Этап 5.
 */
public class QuestsScreen extends Screen {

    // ─── Layout ──────────────────────────────────────────────────────────
    private static final int PANEL_WIDTH    = 380;
    private static final int PANEL_HEIGHT   = 316;
    private static final int PADDING        = 10;

    // Левая колонка (список квестов)
    private static final int LIST_WIDTH     = 105;
    private static final int LIST_ROW_H     = 16;
    private static final int LIST_VISIBLE_ROWS = 11;
    private static final int LIST_Y_OFFSET  = 50;

    // Правая колонка
    private static final int FORM_X_OFFSET  = LIST_WIDTH + 6;
    private static final int OBJ_ROW_H      = 24; // v2.9.2 чуть выше — нужен второй ряд для tag-mode
    private static final int OBJ_VISIBLE    = 3;  // 3 цели влезает с увеличенной высотой строки

    // Prerequisites (v2.9.3) — секция «УСЛОВИЯ ВЫДАЧИ» с collapse-toggle
    private static final int PREREQ_ROW_H   = 22;
    private static final int PREREQ_VISIBLE = 3;  // больше требует скролла; документировано в plan

    private static final int LIST_SELECTED_BG = 0xFF2DD4BF;
    private static final int LIST_NORMAL_BG   = 0xFF374151;
    private static final int LIST_HOVER_BG    = 0xFF4B5563;

    private static final int COUNT_MIN = 1;
    private static final int COUNT_MAX = 9999;

    // ─── Data ────────────────────────────────────────────────────────────
    private final QuestNPCEntity npc;
    private final Screen parentScreen;

    private final List<QuestDefinition> pendingQuests = new ArrayList<>();
    private boolean pendingQuestsEnabled;

    private int selectedIndex = -1;
    private int scrollOffset = 0;

    private boolean showValidationErrors = false;
    private int deleteConfirmIndex = -1;

    private int panelX, panelY;

    // ─── Виджеты ─────────────────────────────────────────────────────────
    private DarkButton enableToggleBtn;
    private DarkButton addQuestBtn;
    private DarkButton scrollUpBtn;
    private DarkButton scrollDownBtn;
    @Nullable private EditBox titleBox;
    @Nullable private EditBox descBox;
    @Nullable private DarkButton deleteQuestBtn;
    @Nullable private DarkButton addObjectiveBtn;

    /** Геометрия EditBox count'а каждой видимой objective — для красной подсветки. */
    @Nullable private final EditBox[] objCountBoxes = new EditBox[QuestDefinition.MAX_OBJECTIVES];
    /** Геометрия «pick»-плитки каждой видимой objective — для красной подсветки невалидных. */
    @Nullable private final int[][] objPickBounds = new int[QuestDefinition.MAX_OBJECTIVES][4]; // [x,y,w,h]

    // Prerequisites widget cache (v2.9.3)
    private boolean prerequisitesExpanded = false;
    @Nullable private final EditBox[] prereqMinDistanceBoxes = new EditBox[QuestDefinition.MAX_PREREQUISITES];
    @Nullable private final int[][] prereqPickBounds = new int[QuestDefinition.MAX_PREREQUISITES][4];

    // Rewards widget cache (Stage 7 v2.9.6)
    private boolean rewardsExpanded = false;
    private static final int REWARDS_VISIBLE = 3;
    private static final int REWARD_ROW_H = 22;
    @Nullable private final EditBox[] rewardEditBoxes = new EditBox[QuestDefinition.MAX_REWARDS];
    @Nullable private final int[][] rewardPickBounds = new int[QuestDefinition.MAX_REWARDS][4];

    // Form scroll state (Stage 7.5 v2.9.7) — offset-based, требует reinit при изменении
    private int formScrollOffset = 0;
    private static final int FORM_SCROLL_STEP = 20;
    /** Высчитывается после buildEditorWidgets — максимальная absolute Y нижнего widget'а. */
    private int formContentMaxY = 0;

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
                if (q != null) pendingQuests.add(QuestDefinition.load(q.save()));
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

        enableToggleBtn = this.addRenderableWidget(new DarkButton(
                contentX, contentY, 140, 18, buildEnableLabel(),
                btn -> { pendingQuestsEnabled = !pendingQuestsEnabled; btn.setMessage(buildEnableLabel()); }
        ));

        int listX = contentX;
        int listY = panelY + LIST_Y_OFFSET;
        rebuildListWidgets(listX, listY);

        int addBtnY = listY + LIST_VISIBLE_ROWS * LIST_ROW_H + 4;
        addQuestBtn = this.addRenderableWidget(new DarkButton(
                listX, addBtnY, LIST_WIDTH - 2, 16,
                Component.translatable("gui.questnpc.quests.add"),
                btn -> addNewQuest(),
                NPCMenuScreen.BTN_GREEN_BG, NPCMenuScreen.BTN_GREEN_HOVER, 0xFFFFFFFF
        ));
        addQuestBtn.active = pendingQuests.size() < QuestNPCEntity.MAX_QUESTS;

        int scrollX = listX + LIST_WIDTH - 14 - 2;
        scrollUpBtn = this.addRenderableWidget(new DarkButton(
                scrollX, listY + 2, 14, 14, Component.literal("▲"),
                btn -> { if (scrollOffset > 0) { scrollOffset--; reinit(); } }
        ));
        scrollDownBtn = this.addRenderableWidget(new DarkButton(
                scrollX, listY + LIST_VISIBLE_ROWS * LIST_ROW_H - 16, 14, 14,
                Component.literal("▼"),
                btn -> {
                    if (scrollOffset + LIST_VISIBLE_ROWS < pendingQuests.size()) {
                        scrollOffset++; reinit();
                    }
                }
        ));
        boolean showScroll = pendingQuests.size() > LIST_VISIBLE_ROWS;
        scrollUpBtn.visible = showScroll;
        scrollDownBtn.visible = showScroll;
        scrollUpBtn.active = scrollOffset > 0;
        scrollDownBtn.active = scrollOffset + LIST_VISIBLE_ROWS < pendingQuests.size();

        if (selectedIndex >= 0 && selectedIndex < pendingQuests.size()) {
            buildEditorWidgets(contentX + FORM_X_OFFSET, panelY + LIST_Y_OFFSET);
        }

        int btnY = panelY + PANEL_HEIGHT - 26;
        this.addRenderableWidget(new DarkButton(
                contentX, btnY, 60, 18,
                Component.translatable("gui.questnpc.quests.back"),
                btn -> Minecraft.getInstance().setScreen(parentScreen)
        ));
        this.addRenderableWidget(new DarkButton(
                panelX + PANEL_WIDTH - PADDING - 80, btnY, 80, 18,
                Component.translatable("gui.questnpc.npc_menu.apply"),
                btn -> tryApply(),
                NPCMenuScreen.BTN_GREEN_BG, NPCMenuScreen.BTN_GREEN_HOVER, 0xFFFFFFFF
        ));
        this.addRenderableWidget(new DarkButton(
                panelX + PANEL_WIDTH - PADDING - 80 - 60 - 4, btnY, 60, 18,
                Component.translatable("gui.questnpc.npc_menu.cancel"),
                btn -> Minecraft.getInstance().setScreen(parentScreen),
                NPCMenuScreen.BTN_GRAY_BG, NPCMenuScreen.BTN_GRAY_HOVER, NPCMenuScreen.TEXT_WHITE
        ));
    }

    private Component buildEnableLabel() {
        return Component.translatable(pendingQuestsEnabled
                ? "gui.questnpc.quests.enabled" : "gui.questnpc.quests.disabled");
    }

    private void rebuildListWidgets(int listX, int listY) {
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
                    btn -> { selectedIndex = idx; deleteConfirmIndex = -1; reinit(); },
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
        // Stage 7.5 (v2.9.7): scroll — смещаем y на -formScrollOffset. Все widgets ниже
        // используют этот y, поэтому смещение применяется ко всему form-контенту автоматически.
        // deleteQuestBtn рендерится по абсолютному formY (всегда в верхнем правом углу).
        int y = formY - formScrollOffset;

        // ── «Удалить квест» — v2.9.3: переехала в верхний-правый угол формы ──
        boolean isConfirm = (deleteConfirmIndex == selectedIndex);
        Component delLabel = Component.translatable(isConfirm
                ? "gui.questnpc.quests.delete_confirm" : "gui.questnpc.quests.delete");
        deleteQuestBtn = this.addRenderableWidget(new DarkButton(
                formX + formW - 70, formY, 70, 14,
                delLabel, btn -> handleDeleteClick(),
                0xFF7F1D1D, 0xFFB91C1C, 0xFFFFFFFF
        ));

        // Секция «КВЕСТ»
        y += 18;
        titleBox = new EditBox(this.font, formX + 4, y, formW - 8 - 74, 14,
                Component.translatable("gui.questnpc.quests.title_field"));
        titleBox.setMaxLength(QuestDefinition.MAX_TITLE_LENGTH);
        titleBox.setValue(q.getTitle());
        titleBox.setResponder(q::setTitle);
        titleBox.setBordered(true);
        this.addRenderableWidget(titleBox);

        y += 20;
        descBox = new EditBox(this.font, formX + 4, y, formW - 8, 14,
                Component.translatable("gui.questnpc.quests.desc_field"));
        descBox.setMaxLength(QuestDefinition.MAX_DESCRIPTION_LENGTH);
        descBox.setValue(q.getDescription());
        descBox.setResponder(q::setDescription);
        descBox.setBordered(true);
        this.addRenderableWidget(descBox);

        // Секция «ЦЕЛИ»
        y += 26 + 16;

        List<QuestObjective> objectives = q.getObjectives();
        int objCount = Math.min(objectives.size(), OBJ_VISIBLE);
        for (int i = 0; i < QuestDefinition.MAX_OBJECTIVES; i++) {
            objCountBoxes[i] = null;
            objPickBounds[i] = new int[]{-1, -1, 0, 0};
        }
        // Stage 7 (v2.9.6): KILL row выше на 20 px для lootDrop UI второй строкой
        int rowY = y;
        for (int i = 0; i < objCount; i++) {
            buildObjectiveRow(formX + 4, rowY, formW - 8, i, objectives.get(i));
            rowY += rowHeightForObjective(objectives.get(i));
        }

        addObjectiveBtn = this.addRenderableWidget(new DarkButton(
                formX + 4, rowY + 2, formW - 8, 14,
                Component.translatable("gui.questnpc.quests.add_objective"),
                btn -> addNewObjective(),
                NPCMenuScreen.BTN_GREEN_BG, NPCMenuScreen.BTN_GREEN_HOVER, 0xFFFFFFFF
        ));
        addObjectiveBtn.active = objectives.size() < QuestDefinition.MAX_OBJECTIVES;

        // Секция «НАГРАДЫ» (Stage 7 v2.9.6) — между «ЦЕЛИ» и «УСЛОВИЯ»
        int rewardsY = rowY + 22;
        int afterRewardsY = buildRewardsSection(formX + 4, rewardsY, formW - 8, q);

        // Секция «УСЛОВИЯ ВЫДАЧИ» (v2.9.3) — collapse-toggle + строки
        int prereqY = afterRewardsY + 6;
        buildPrerequisitesSection(formX + 4, prereqY, formW - 8, q);

        // Stage 7.5 (v2.9.7): пересчёт высоты form-контента для clamp scroll'а.
        // Итерируем все widgets и находим максимальный bottom (y + height).
        int maxBottom = formY;
        for (var ch : this.children()) {
            if (ch instanceof net.minecraft.client.gui.components.AbstractWidget aw) {
                int bottom = aw.getY() + aw.getHeight();
                if (bottom > maxBottom) maxBottom = bottom;
            }
        }
        // Конвертируем в absolute Y (без offset) — для maxScroll расчёта в mouseScrolled
        this.formContentMaxY = maxBottom + formScrollOffset;
    }

    /**
     * Stage 7.5 (v2.9.7): вертикальный scroll правой панели через колесо мыши.
     * Активен только когда курсор в form area. Использует {@link #formScrollOffset} +
     * {@link #reinit()} (offset-based pattern из NPCTradingScreen).
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int formLeft = panelX + PADDING + FORM_X_OFFSET;
        int formRight = panelX + PANEL_WIDTH - PADDING;
        int formTop = panelY + LIST_Y_OFFSET;
        int formBottom = panelY + PANEL_HEIGHT - 26;
        if (mouseX >= formLeft && mouseX < formRight && mouseY >= formTop && mouseY < formBottom) {
            int visibleH = formBottom - formTop;
            int maxScroll = Math.max(0, formContentMaxY - formTop - visibleH);
            int newOffset = formScrollOffset - (int)(delta * FORM_SCROLL_STEP);
            newOffset = Math.max(0, Math.min(newOffset, maxScroll));
            if (newOffset != formScrollOffset) {
                formScrollOffset = newOffset;
                reinit();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** Stage 7: KILL row требует доп. высоту для lootDrop второй строкой. */
    private static int rowHeightForObjective(QuestObjective obj) {
        if (obj instanceof KillObjective) return OBJ_ROW_H + 20;
        if (obj instanceof BringObjective) return OBJ_ROW_H + 20; // Stage 7.5: вторая строка для dropSource
        return OBJ_ROW_H;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Rewards section (Stage 7 v2.9.6) — заглушка, наполнение далее в файле
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Stage 7 (v2.9.6): секция «НАГРАДЫ» — collapse-toggle, до {@link #REWARDS_VISIBLE}
     * видимых строк, «+ Добавить» в конце. Возвращает Y после секции.
     * Pattern полностью аналогичен {@link #buildPrerequisitesSection}.
     */
    private int buildRewardsSection(int x, int y, int w, QuestDefinition q) {
        // Сброс кэша геометрии reward-строк
        for (int i = 0; i < QuestDefinition.MAX_REWARDS; i++) {
            rewardEditBoxes[i] = null;
            rewardPickBounds[i] = new int[]{-1, -1, 0, 0};
        }

        // Collapse-toggle header
        String headerLabel = (rewardsExpanded ? "▼ " : "▶ ")
                + Component.translatable("gui.questnpc.quests.rewards.section").getString()
                + " (" + q.getRewards().size() + "/" + QuestDefinition.MAX_REWARDS + ")";
        this.addRenderableWidget(new DarkButton(
                x, y, w, 14, Component.literal(headerLabel),
                btn -> { rewardsExpanded = !rewardsExpanded; reinit(); }
        ));

        if (!rewardsExpanded) return y + 14;

        // Rows (до REWARDS_VISIBLE)
        int rowY = y + 16;
        List<QuestReward> rewards = q.getRewards();
        int visible = Math.min(rewards.size(), REWARDS_VISIBLE);
        for (int i = 0; i < visible; i++) {
            buildRewardRow(x, rowY + i * REWARD_ROW_H, w, i, rewards.get(i));
        }

        // «+ Добавить награду»
        int addBtnY = rowY + visible * REWARD_ROW_H + 2;
        DarkButton addBtn = this.addRenderableWidget(new DarkButton(
                x, addBtnY, w, 12,
                Component.translatable("gui.questnpc.quests.rewards.add"),
                btn -> addNewReward(),
                NPCMenuScreen.BTN_GRAY_BG, NPCMenuScreen.BTN_GRAY_HOVER, NPCMenuScreen.TEXT_WHITE
        ));
        addBtn.active = rewards.size() < QuestDefinition.MAX_REWARDS;

        return addBtnY + 14;
    }

    private void buildRewardRow(int x, int y, int w, int slotIdx, QuestReward r) {
        // Type cycler — active, переключает по 4 типам
        DarkButton typeBtn = this.addRenderableWidget(new DarkButton(
                x, y, 90, 18,
                Component.translatable("gui.questnpc.quests.rewards.type." + r.getType().name()),
                btn -> cycleRewardType(slotIdx),
                NPCMenuScreen.BTN_GRAY_BG, NPCMenuScreen.BTN_GRAY_HOVER, NPCMenuScreen.TEXT_WHITE
        ));

        // Delete ✕
        final int slotIdxFinal = slotIdx;
        this.addRenderableWidget(new DarkButton(
                x + w - 20, y, 20, 18, Component.literal("✕"),
                btn -> removeReward(slotIdxFinal),
                0xFF7F1D1D, 0xFFB91C1C, 0xFFFFFFFF
        ));

        // Type-specific fields
        int fieldsX = x + 94;
        int fieldsW = w - 90 - 20 - 8;

        if (r instanceof ItemReward ir) {
            renderItemRewardFields(ir, slotIdx, fieldsX, y, fieldsW);
        } else if (r instanceof XPPointsReward xr) {
            renderXpRewardFields(xr, slotIdx, fieldsX, y, fieldsW);
        } else if (r instanceof CommandReward cr) {
            renderCommandRewardFields(cr, slotIdx, fieldsX, y, fieldsW);
        } else if (r instanceof UnlockTradeSetReward ur) {
            renderUnlockTradeSetRewardFields(ur, slotIdx, fieldsX, y, fieldsW);
        }
    }

    private void renderItemRewardFields(ItemReward ir, int slotIdx, int x, int y, int w) {
        // [pick tile: 70%] [count: 40px]
        int countW = 40;
        int pickW = w - countW - 4;

        // Pick item tile
        ItemStack stack = ir.getStack();
        Component pickLabel = stack.isEmpty()
                ? Component.translatable("gui.questnpc.quests.objective.kill.loot_drop.empty")
                : stack.getHoverName();
        int pickX = x;
        DarkButton pickBtn = this.addRenderableWidget(new DarkButton(
                pickX, y, pickW, 18, pickLabel,
                btn -> {
                    net.minecraft.world.item.Item current = stack.isEmpty() ? null : stack.getItem();
                    int currentCount = stack.isEmpty() ? 1 : stack.getCount();
                    Minecraft.getInstance().setScreen(new ItemCatalogScreen(
                            this, current, null,
                            picked -> { if (picked != null) ir.setStack(new ItemStack(picked, currentCount)); }
                    ));
                }
        ));
        pickBtn.setTooltip(Tooltip.create(Component.translatable("gui.questnpc.quests.rewards.item.tooltip")));
        rewardPickBounds[slotIdx] = new int[]{pickX, y, pickW, 18};

        // Count EditBox
        EditBox countBox = new EditBox(this.font, x + pickW + 4, y + 2, countW, 14,
                Component.translatable("gui.questnpc.quests.count_field"));
        countBox.setMaxLength(3);
        countBox.setValue(stack.isEmpty() ? "1" : String.valueOf(stack.getCount()));
        countBox.setResponder(text -> {
            try {
                int v = Integer.parseInt(text.trim());
                if (v >= 1 && v <= 999 && !ir.getStack().isEmpty()) {
                    ItemStack copy = ir.getStack().copy();
                    copy.setCount(v);
                    ir.setStack(copy);
                }
            } catch (NumberFormatException ignored) {}
        });
        countBox.setBordered(true);
        this.addRenderableWidget(countBox);
        rewardEditBoxes[slotIdx] = countBox;
    }

    private void renderXpRewardFields(XPPointsReward xr, int slotIdx, int x, int y, int w) {
        // [amount: 80px] [label "XP"]
        int amountW = 80;
        EditBox box = new EditBox(this.font, x, y + 2, amountW, 14,
                Component.translatable("gui.questnpc.quests.rewards.xp.label"));
        box.setMaxLength(6);
        box.setValue(String.valueOf(xr.getAmount()));
        box.setResponder(text -> {
            try {
                int v = Integer.parseInt(text.trim());
                if (v >= 0 && v <= 100000) xr.setAmount(v);
            } catch (NumberFormatException ignored) {}
        });
        box.setBordered(true);
        this.addRenderableWidget(box);
        rewardEditBoxes[slotIdx] = box;
    }

    private void renderCommandRewardFields(CommandReward cr, int slotIdx, int x, int y, int w) {
        // Wide EditBox для команды
        EditBox box = new EditBox(this.font, x, y + 2, w, 14,
                Component.translatable("gui.questnpc.quests.rewards.command.placeholder"));
        box.setMaxLength(200);
        box.setValue(cr.getCommand());
        box.setResponder(text -> cr.setCommand(text));
        box.setBordered(true);
        box.setHint(Component.translatable("gui.questnpc.quests.rewards.command.placeholder"));
        box.setTooltip(Tooltip.create(Component.translatable("gui.questnpc.quests.rewards.command.tooltip")));
        this.addRenderableWidget(box);
        rewardEditBoxes[slotIdx] = box;
    }

    private void renderUnlockTradeSetRewardFields(UnlockTradeSetReward ur, int slotIdx, int x, int y, int w) {
        EditBox box = new EditBox(this.font, x, y + 2, w, 14,
                Component.translatable("gui.questnpc.quests.rewards.unlock.tooltip"));
        box.setMaxLength(50);
        box.setValue(ur.getTradeSetName());
        box.setResponder(text -> ur.setTradeSetName(text));
        box.setBordered(true);
        box.setTooltip(Tooltip.create(Component.translatable("gui.questnpc.quests.rewards.unlock.tooltip")));
        this.addRenderableWidget(box);
        rewardEditBoxes[slotIdx] = box;
    }

    private void cycleRewardType(int slotIdx) {
        if (selectedIndex < 0) return;
        QuestDefinition q = pendingQuests.get(selectedIndex);
        List<QuestReward> rewards = new ArrayList<>(q.getRewards());
        if (slotIdx < 0 || slotIdx >= rewards.size()) return;
        RewardType cur = rewards.get(slotIdx).getType();
        RewardType[] all = RewardType.values();
        RewardType next = all[(cur.ordinal() + 1) % all.length];
        rewards.set(slotIdx, QuestReward.createEmpty(next));
        q.clearRewards();
        for (QuestReward r : rewards) q.addReward(r);
        reinit();
    }

    private void addNewReward() {
        if (selectedIndex < 0) return;
        QuestDefinition q = pendingQuests.get(selectedIndex);
        if (q.getRewards().size() >= QuestDefinition.MAX_REWARDS) return;
        q.addReward(QuestReward.createEmpty(RewardType.ITEM));
        reinit();
    }

    private void removeReward(int slotIdx) {
        if (selectedIndex < 0) return;
        QuestDefinition q = pendingQuests.get(selectedIndex);
        if (slotIdx < 0 || slotIdx >= q.getRewards().size()) return;
        q.removeReward(slotIdx);
        reinit();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Prerequisites section (v2.9.3)
    // ═══════════════════════════════════════════════════════════════════

    private void buildPrerequisitesSection(int x, int y, int w, QuestDefinition q) {
        // Сброс кэша геометрии prereq-строк
        for (int i = 0; i < QuestDefinition.MAX_PREREQUISITES; i++) {
            prereqMinDistanceBoxes[i] = null;
            prereqPickBounds[i] = new int[]{-1, -1, 0, 0};
        }

        // Collapse-toggle header — кликабельная строка с символом и счётчиком
        String headerLabel = (prerequisitesExpanded ? "▼ " : "▶ ")
                + Component.translatable("gui.questnpc.quests.prerequisites.section").getString()
                + " (" + q.getPrerequisites().size() + "/" + QuestDefinition.MAX_PREREQUISITES + ")";
        this.addRenderableWidget(new DarkButton(
                x, y, w, 14, Component.literal(headerLabel),
                btn -> { prerequisitesExpanded = !prerequisitesExpanded; reinit(); }
        ));

        if (!prerequisitesExpanded) return;

        // Rows (до PREREQ_VISIBLE)
        int rowY = y + 16;
        List<QuestCondition> prereqs = q.getPrerequisites();
        int visible = Math.min(prereqs.size(), PREREQ_VISIBLE);
        for (int i = 0; i < visible; i++) {
            buildPrerequisiteRow(x, rowY + i * PREREQ_ROW_H, w, i, prereqs.get(i));
        }

        // «+ Добавить условие»
        int addBtnY = rowY + visible * PREREQ_ROW_H + 2;
        DarkButton addBtn = this.addRenderableWidget(new DarkButton(
                x, addBtnY, w, 12,
                Component.translatable("gui.questnpc.quests.prerequisites.add"),
                btn -> addNewPrerequisite(),
                NPCMenuScreen.BTN_GRAY_BG, NPCMenuScreen.BTN_GRAY_HOVER, NPCMenuScreen.TEXT_WHITE
        ));
        addBtn.active = prereqs.size() < QuestDefinition.MAX_PREREQUISITES;
    }

    private void buildPrerequisiteRow(int x, int y, int w, int slotIdx, QuestCondition cond) {
        // Type cycler — disabled, отображает имя типа. Только DISTANCE_TO_STRUCTURE реализован.
        DarkButton typeBtn = this.addRenderableWidget(new DarkButton(
                x, y, 110, 18,
                Component.translatable("gui.questnpc.quests.prerequisites.type." + cond.getType().name()),
                btn -> {},
                NPCMenuScreen.BTN_GRAY_BG, NPCMenuScreen.BTN_GRAY_HOVER, NPCMenuScreen.TEXT_DARK_GRAY
        ));
        typeBtn.active = false;
        typeBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.questnpc.quests.prerequisites.future_type_tooltip")));

        // Delete ✕ — общая для всех типов
        final int slotIdxFinal = slotIdx;
        this.addRenderableWidget(new DarkButton(
                x + w - 20, y, 20, 18, Component.literal("✕"),
                btn -> removePrerequisite(slotIdxFinal),
                0xFF7F1D1D, 0xFFB91C1C, 0xFFFFFFFF
        ));

        // Поля по типу
        int fieldsX = x + 114;
        int fieldsW = w - 110 - 20 - 8;

        if (cond.getType() == ConditionType.DISTANCE_TO_STRUCTURE) {
            renderDistanceToStructureFields((DistanceToStructureCondition) cond,
                    slotIdx, fieldsX, y, fieldsW);
        }
        // PLAYER_LEVEL / COMPLETED_QUEST — пока не отображаются (cycler disabled)
    }

    private void renderDistanceToStructureFields(DistanceToStructureCondition cond,
                                                  int slotIdx, int x, int y, int w) {
        // [pickStructure: ~50%] [distance: 40px] [invert: 18px]
        int invertW = 22;
        int distW = 50;
        int pickW = w - distW - invertW - 8;

        // Pick tile — структура
        net.minecraft.resources.ResourceLocation sid = cond.getStructureId();
        String pickLabel = sid != null ? sid.toString()
                : Component.translatable("gui.questnpc.quests.prerequisites.structure.not_selected").getString();
        int pickX = x;
        this.addRenderableWidget(new DarkButton(
                pickX, y, pickW, 18, Component.literal(pickLabel),
                btn -> Minecraft.getInstance().setScreen(new StructurePickerScreen(
                        this, cond.getStructureId(), picked -> cond.setStructureId(picked)))
        ));
        prereqPickBounds[slotIdx] = new int[]{pickX, y, pickW, 18};

        // Min distance EditBox
        EditBox distBox = new EditBox(this.font, x + pickW + 4, y + 2, distW, 14,
                Component.translatable("gui.questnpc.quests.prerequisites.min_distance"));
        distBox.setMaxLength(5);
        distBox.setValue(String.valueOf(cond.getMinDistance()));
        distBox.setResponder(text -> {
            try {
                int v = Integer.parseInt(text.trim());
                if (v >= 0 && v <= 10000) cond.setMinDistance(v);
            } catch (NumberFormatException ignored) {}
        });
        distBox.setBordered(true);
        this.addRenderableWidget(distBox);
        prereqMinDistanceBoxes[slotIdx] = distBox;

        // Invert mini-toggle
        DarkButton invBtn = this.addRenderableWidget(new DarkButton(
                x + pickW + 4 + distW + 4, y, invertW, 18,
                Component.literal(cond.isInverted() ? "✓" : "✕"),
                btn -> { cond.setInverted(!cond.isInverted()); reinit(); }
        ));
        invBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.questnpc.quests.prerequisites.invert")));
    }

    private void addNewPrerequisite() {
        if (selectedIndex < 0) return;
        QuestDefinition q = pendingQuests.get(selectedIndex);
        if (q.getPrerequisites().size() >= QuestDefinition.MAX_PREREQUISITES) return;
        try {
            QuestCondition c = QuestCondition.createEmpty(ConditionType.DISTANCE_TO_STRUCTURE);
            q.addPrerequisite(c);
            reinit();
        } catch (UnsupportedOperationException ex) {
            // не должно случиться для DISTANCE_TO_STRUCTURE
        }
    }

    private void removePrerequisite(int slotIdx) {
        if (selectedIndex < 0) return;
        QuestDefinition q = pendingQuests.get(selectedIndex);
        if (slotIdx < 0 || slotIdx >= q.getPrerequisites().size()) return;
        q.removePrerequisite(slotIdx);
        reinit();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Objective row — type cycler + dispatch по типу
    // ═══════════════════════════════════════════════════════════════════

    private void buildObjectiveRow(int x, int y, int w, int slotIdx, QuestObjective obj) {
        // Type cycler (v2.9.2: активен — переключает по 5 типам)
        DarkButton typeBtn = this.addRenderableWidget(new DarkButton(
                x, y, 50, 18,
                Component.translatable(typeKey(obj.getType())),
                btn -> cycleObjectiveType(slotIdx),
                NPCMenuScreen.BTN_GRAY_BG, NPCMenuScreen.BTN_GRAY_HOVER, NPCMenuScreen.TEXT_WHITE
        ));

        // Кнопка ✕ — общая для всех типов
        final int slotIdxFinal = slotIdx;
        this.addRenderableWidget(new DarkButton(
                x + w - 20, y, 20, 18, Component.literal("✕"),
                btn -> removeObjective(slotIdxFinal),
                0xFF7F1D1D, 0xFFB91C1C, 0xFFFFFFFF
        ));

        // Dispatch по типу — оставшаяся область шириной (w - 50 - 20 - 4) = w - 74
        int fieldsX = x + 52;
        int fieldsW = w - 50 - 20 - 4;

        switch (obj.getType()) {
            case KILL -> renderKillFields((KillObjective) obj, slotIdx, fieldsX, y, fieldsW);
            case BRING -> renderBringFields((BringObjective) obj, slotIdx, fieldsX, y, fieldsW);
            case BREAK_BLOCK -> renderBreakBlockFields((BreakBlockObjective) obj, slotIdx, fieldsX, y, fieldsW);
            case REACH_BIOME -> renderReachBiomeFields((ReachBiomeObjective) obj, slotIdx, fieldsX, y, fieldsW);
            case REACH_STRUCTURE -> renderReachStructureFields((ReachStructureObjective) obj, slotIdx, fieldsX, y, fieldsW);
        }
    }

    private void cycleObjectiveType(int slotIdx) {
        if (selectedIndex < 0) return;
        QuestDefinition q = pendingQuests.get(selectedIndex);
        if (slotIdx < 0 || slotIdx >= q.getObjectives().size()) return;
        ObjectiveType current = q.getObjectives().get(slotIdx).getType();
        ObjectiveType[] all = ObjectiveType.values();
        ObjectiveType next = all[(current.ordinal() + 1) % all.length];
        // Замена objective — старые поля теряются
        q.removeObjective(slotIdx);
        QuestObjective replacement = QuestObjective.createEmpty(next);
        // Вставка в ту же позицию через clear/re-add (QuestDefinition не имеет insertAt)
        List<QuestObjective> snapshot = new ArrayList<>(q.getObjectives());
        snapshot.add(slotIdx, replacement);
        q.clearObjectives();
        for (QuestObjective o : snapshot) q.addObjective(o);
        reinit();
    }

    private static String typeKey(ObjectiveType type) {
        return "gui.questnpc.quests.objective.type." + type.name();
    }

    // ───────────────────────────────────────────
    // KILL fields
    // ───────────────────────────────────────────
    private void renderKillFields(KillObjective ko, int slotIdx, int x, int y, int w) {
        // Tag-mode toggle (60px) + pick tile (variable) + count (50px)
        int toggleW = 70;
        int countW = 50;
        int pickW = w - toggleW - countW - 8;

        DarkButton toggleBtn = this.addRenderableWidget(new DarkButton(
                x, y, toggleW, 18,
                Component.translatable(ko.isTagMode()
                        ? "gui.questnpc.quests.objective.tag_mode.tag"
                        : "gui.questnpc.quests.objective.tag_mode.specific"),
                btn -> {
                    ko.setTagMode(!ko.isTagMode());
                    // Очистка противоположного поля при смене режима (destructive — MVP)
                    if (ko.isTagMode()) { ko.setEntityType(null); }
                    else { ko.setEntityTypeTag(null); }
                    reinit();
                }
        ));

        // Pick tile
        Component pickLabel;
        if (ko.isTagMode()) {
            TagKey<EntityType<?>> tag = ko.getEntityTypeTag();
            pickLabel = Component.literal(tag != null ? "#" + tag.location().toString()
                    : notSelected());
        } else {
            ResourceLocation rl = ko.getEntityType();
            pickLabel = Component.literal(rl != null ? rl.toString() : notSelected());
        }
        int pickX = x + toggleW + 4;
        int pickY = y;
        this.addRenderableWidget(new DarkButton(
                pickX, pickY, pickW, 18, pickLabel,
                btn -> {
                    if (ko.isTagMode()) {
                        Minecraft.getInstance().setScreen(new EntityTagPickerScreen(
                                this, ko.getEntityTypeTag(),
                                picked -> ko.setEntityTypeTag(picked)));
                    } else {
                        Minecraft.getInstance().setScreen(new EntityCatalogScreen(
                                this, ko.getEntityType(),
                                picked -> ko.setEntityType(picked)));
                    }
                }
        ));
        objPickBounds[slotIdx] = new int[]{pickX, pickY, pickW, 18};

        // Count
        EditBox countBox = new EditBox(this.font, x + toggleW + 4 + pickW + 4, y + 2, countW, 14,
                Component.translatable("gui.questnpc.quests.count_field"));
        countBox.setMaxLength(4);
        countBox.setValue(String.valueOf(ko.getCount()));
        countBox.setResponder(text -> {
            try {
                int v = Integer.parseInt(text.trim());
                if (v >= COUNT_MIN && v <= COUNT_MAX) ko.setCount(v);
            } catch (NumberFormatException ignored) {}
        });
        countBox.setBordered(true);
        this.addRenderableWidget(countBox);
        objCountBoxes[slotIdx] = countBox;

        // Stage 7 (v2.9.6): lootDrop UI — вторая строка под основной
        renderLootDropFields(ko, slotIdx, x, y + 20, w);
    }

    /**
     * Stage 7 (v2.9.6): UI для KillObjective.lootDrop — quest-locked drop через GLM.
     * Layout: одна строка ~16 px высоты — toggle «Quest-drop» (on/off) + picker tile.
     * Toggle on с пустым lootDrop → автоматически открываем picker.
     * Toggle off → setLootDrop(EMPTY).
     */
    private void renderLootDropFields(KillObjective ko, int slotIdx, int x, int y, int w) {
        boolean active = !ko.getLootDrop().isEmpty();
        int toggleW = 80;
        int gap = 4;
        int pickW = w - toggleW - gap;

        // Toggle Quest-drop
        DarkButton toggleBtn = this.addRenderableWidget(new DarkButton(
                x, y, toggleW, 16,
                Component.translatable("gui.questnpc.quests.objective.kill.loot_drop.toggle"),
                btn -> {
                    if (active) {
                        // Сейчас active → выключаем
                        ko.setLootDrop(ItemStack.EMPTY);
                        reinit();
                    } else {
                        // Включаем → открываем picker сразу
                        Minecraft.getInstance().setScreen(new ItemCatalogScreen(
                                this, null, null,
                                picked -> {
                                    if (picked != null) ko.setLootDrop(new ItemStack(picked, 1));
                                }
                        ));
                    }
                },
                active ? NPCMenuScreen.BTN_GREEN_BG : NPCMenuScreen.BTN_GRAY_BG,
                active ? NPCMenuScreen.BTN_GREEN_HOVER : NPCMenuScreen.BTN_GRAY_HOVER,
                NPCMenuScreen.TEXT_WHITE
        ));
        toggleBtn.setTooltip(Tooltip.create(
                Component.translatable("gui.questnpc.quests.objective.kill.loot_drop.tooltip")));

        // Pick tile — отображает выбранный предмет
        Component pickLabel = active
                ? ko.getLootDrop().getHoverName()
                : Component.translatable("gui.questnpc.quests.objective.kill.loot_drop.empty");
        DarkButton pickBtn = this.addRenderableWidget(new DarkButton(
                x + toggleW + gap, y, pickW, 16, pickLabel,
                btn -> {
                    net.minecraft.world.item.Item current = active ? ko.getLootDrop().getItem() : null;
                    Minecraft.getInstance().setScreen(new ItemCatalogScreen(
                            this, current, null,
                            picked -> {
                                if (picked != null) ko.setLootDrop(new ItemStack(picked, 1));
                            }
                    ));
                }
        ));
        pickBtn.active = active; // disabled пока quest-drop выключен
    }

    // ───────────────────────────────────────────
    // BRING fields — uses existing ItemCatalogScreen
    // ───────────────────────────────────────────
    private void renderBringFields(BringObjective bo, int slotIdx, int x, int y, int w) {
        int countW = 50;
        int consumeW = 70;
        int pickW = w - countW - consumeW - 8;

        ItemStack stack = bo.getStack();
        String label = stack.isEmpty() ? notSelected() : stack.getHoverName().getString();
        int pickX = x;
        int pickY = y;
        this.addRenderableWidget(new DarkButton(
                pickX, pickY, pickW, 18, Component.literal(label),
                btn -> {
                    // Переиспользуем универсальный ctor ItemCatalogScreen (v2.8.0)
                    Minecraft.getInstance().setScreen(new ItemCatalogScreen(
                            this,
                            stack.isEmpty() ? null : stack.getItem(),
                            null, // без фильтра
                            picked -> bo.setStack(picked == null ? ItemStack.EMPTY : new ItemStack(picked))
                    ));
                }
        ));
        objPickBounds[slotIdx] = new int[]{pickX, pickY, pickW, 18};

        EditBox countBox = new EditBox(this.font, x + pickW + 4, y + 2, countW, 14,
                Component.translatable("gui.questnpc.quests.count_field"));
        countBox.setMaxLength(4);
        countBox.setValue(String.valueOf(bo.getCount()));
        countBox.setResponder(text -> {
            try {
                int v = Integer.parseInt(text.trim());
                if (v >= COUNT_MIN && v <= COUNT_MAX) bo.setCount(v);
            } catch (NumberFormatException ignored) {}
        });
        countBox.setBordered(true);
        this.addRenderableWidget(countBox);
        objCountBoxes[slotIdx] = countBox;

        // Consume toggle (compact)
        this.addRenderableWidget(new DarkButton(
                x + pickW + 4 + countW + 4, y, consumeW, 18,
                Component.literal(bo.isConsumeOnTurnIn() ? "✓" : "✕"),
                btn -> { bo.setConsumeOnTurnIn(!bo.isConsumeOnTurnIn()); reinit(); }
        )).setTooltip(Tooltip.create(
                Component.translatable("gui.questnpc.quests.objective.consume")));

        // Stage 7.5 (v2.9.7): вторая строка — toggle «Источник» + entity picker
        renderBringSourceFields(bo, slotIdx, x, y + 20, w);
    }

    /**
     * Stage 7.5 (v2.9.7): UI для BringObjective.dropSourceEntityType — quest-locked
     * source-drop через GLM. Аналог {@link #renderLootDropFields} для KillObjective.
     */
    private void renderBringSourceFields(BringObjective bo, int slotIdx, int x, int y, int w) {
        boolean active = bo.getDropSourceEntityType() != null;
        int toggleW = 80;
        int gap = 4;
        int pickW = w - toggleW - gap;

        DarkButton toggleBtn = this.addRenderableWidget(new DarkButton(
                x, y, toggleW, 16,
                Component.translatable("gui.questnpc.quests.objective.bring.drop_source.toggle"),
                btn -> {
                    if (active) {
                        bo.setDropSourceEntityType(null);
                        reinit();
                    } else {
                        Minecraft.getInstance().setScreen(new EntityCatalogScreen(
                                this, null,
                                picked -> { if (picked != null) bo.setDropSourceEntityType(picked); }
                        ));
                    }
                },
                active ? NPCMenuScreen.BTN_GREEN_BG : NPCMenuScreen.BTN_GRAY_BG,
                active ? NPCMenuScreen.BTN_GREEN_HOVER : NPCMenuScreen.BTN_GRAY_HOVER,
                NPCMenuScreen.TEXT_WHITE
        ));
        toggleBtn.setTooltip(Tooltip.create(
                Component.translatable("gui.questnpc.quests.objective.bring.drop_source.tooltip")));

        Component pickLabel = active
                ? Component.literal(bo.getDropSourceEntityType().toString())
                : Component.translatable("gui.questnpc.quests.objective.kill.loot_drop.empty");
        DarkButton pickBtn = this.addRenderableWidget(new DarkButton(
                x + toggleW + gap, y, pickW, 16, pickLabel,
                btn -> Minecraft.getInstance().setScreen(new EntityCatalogScreen(
                        this, bo.getDropSourceEntityType(),
                        picked -> { if (picked != null) bo.setDropSourceEntityType(picked); }
                ))
        ));
        pickBtn.active = active;
    }

    // ───────────────────────────────────────────
    // BREAK_BLOCK fields
    // ───────────────────────────────────────────
    private void renderBreakBlockFields(BreakBlockObjective bb, int slotIdx, int x, int y, int w) {
        int toggleW = 70;
        int countW = 50;
        int dropW = 24;
        int pickW = w - toggleW - countW - dropW - 12;

        DarkButton toggleBtn = this.addRenderableWidget(new DarkButton(
                x, y, toggleW, 18,
                Component.translatable(bb.isTagMode()
                        ? "gui.questnpc.quests.objective.tag_mode.tag"
                        : "gui.questnpc.quests.objective.tag_mode.specific"),
                btn -> {
                    bb.setTagMode(!bb.isTagMode());
                    if (bb.isTagMode()) bb.setBlockId(null);
                    else bb.setBlockTag(null);
                    reinit();
                }
        ));

        Component pickLabel;
        if (bb.isTagMode()) {
            TagKey<Block> tag = bb.getBlockTag();
            pickLabel = Component.literal(tag != null ? "#" + tag.location().toString()
                    : notSelected());
        } else {
            ResourceLocation rl = bb.getBlockId();
            pickLabel = Component.literal(rl != null ? rl.toString() : notSelected());
        }
        int pickX = x + toggleW + 4;
        this.addRenderableWidget(new DarkButton(
                pickX, y, pickW, 18, pickLabel,
                btn -> {
                    if (bb.isTagMode()) {
                        Minecraft.getInstance().setScreen(new BlockTagPickerScreen(
                                this, bb.getBlockTag(), picked -> bb.setBlockTag(picked)));
                    } else {
                        Minecraft.getInstance().setScreen(new BlockCatalogScreen(
                                this, bb.getBlockId(), picked -> bb.setBlockId(picked)));
                    }
                }
        ));
        objPickBounds[slotIdx] = new int[]{pickX, y, pickW, 18};

        EditBox countBox = new EditBox(this.font, x + toggleW + 4 + pickW + 4, y + 2, countW, 14,
                Component.translatable("gui.questnpc.quests.count_field"));
        countBox.setMaxLength(4);
        countBox.setValue(String.valueOf(bb.getCount()));
        countBox.setResponder(text -> {
            try {
                int v = Integer.parseInt(text.trim());
                if (v >= COUNT_MIN && v <= COUNT_MAX) bb.setCount(v);
            } catch (NumberFormatException ignored) {}
        });
        countBox.setBordered(true);
        this.addRenderableWidget(countBox);
        objCountBoxes[slotIdx] = countBox;

        // dropRequired — disabled checkbox (Stage 5)
        DarkButton dropBtn = this.addRenderableWidget(new DarkButton(
                x + toggleW + 4 + pickW + 4 + countW + 4, y, dropW, 18,
                Component.literal(bb.isDropRequired() ? "D✓" : "D"),
                btn -> {}
        ));
        dropBtn.active = false;
        dropBtn.setTooltip(Tooltip.create(
                Component.translatable("gui.questnpc.quests.objective.drop_required_locked")));
    }

    // ───────────────────────────────────────────
    // REACH_BIOME fields
    // ───────────────────────────────────────────
    private void renderReachBiomeFields(ReachBiomeObjective rb, int slotIdx, int x, int y, int w) {
        ResourceLocation rl = rb.getBiomeId();
        String label = rl != null ? rl.toString() : notSelected();
        int pickX = x;
        this.addRenderableWidget(new DarkButton(
                pickX, y, w, 18, Component.literal(label),
                btn -> Minecraft.getInstance().setScreen(new BiomePickerScreen(
                        this, rb.getBiomeId(), picked -> rb.setBiomeId(picked)))
        ));
        objPickBounds[slotIdx] = new int[]{pickX, y, w, 18};
    }

    // ───────────────────────────────────────────
    // REACH_STRUCTURE fields
    // ───────────────────────────────────────────
    private void renderReachStructureFields(ReachStructureObjective rs, int slotIdx, int x, int y, int w) {
        ResourceLocation rl = rs.getStructureId();
        String label = rl != null ? rl.toString() : notSelected();
        int pickX = x;
        this.addRenderableWidget(new DarkButton(
                pickX, y, w, 18, Component.literal(label),
                btn -> Minecraft.getInstance().setScreen(new StructurePickerScreen(
                        this, rs.getStructureId(), picked -> rs.setStructureId(picked)))
        ));
        objPickBounds[slotIdx] = new int[]{pickX, y, w, 18};
    }

    private static String notSelected() {
        return Component.translatable("gui.questnpc.quests.objective.not_selected").getString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Actions
    // ═══════════════════════════════════════════════════════════════════

    private void addNewQuest() {
        if (pendingQuests.size() >= QuestNPCEntity.MAX_QUESTS) return;
        QuestDefinition q = new QuestDefinition();
        q.addObjective(new KillObjective());
        pendingQuests.add(q);
        selectedIndex = pendingQuests.size() - 1;
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
        q.addObjective(new KillObjective()); // дефолт — KILL, можно сразу циклить
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
            deleteConfirmIndex = selectedIndex;
            if (deleteQuestBtn != null) {
                deleteQuestBtn.setMessage(Component.translatable("gui.questnpc.quests.delete_confirm"));
            }
        } else {
            pendingQuests.remove(selectedIndex);
            selectedIndex = -1;
            deleteConfirmIndex = -1;
            showValidationErrors = false;
            if (scrollOffset > 0 && scrollOffset + LIST_VISIBLE_ROWS > pendingQuests.size()) {
                scrollOffset = Math.max(0, pendingQuests.size() - LIST_VISIBLE_ROWS);
            }
            reinit();
        }
    }

    private void reinit() { this.clearWidgets(); this.init(); }

    private void tryApply() {
        if (!validateAll()) { showValidationErrors = true; return; }
        showValidationErrors = false;
        ModNetwork.INSTANCE.sendToServer(new UpdateNPCQuestsPacket(
                npc.getId(), pendingQuestsEnabled, pendingQuests));
        if (parentScreen instanceof NPCMenuScreen npcm) {
            npcm.setQuestsSnapshot(pendingQuestsEnabled, pendingQuests);
        }
        Minecraft.getInstance().setScreen(parentScreen);
    }

    private boolean validateAll() {
        for (int i = 0; i < pendingQuests.size(); i++) {
            if (!isQuestValid(pendingQuests.get(i))) {
                selectedIndex = i;
                scrollOffset = Math.max(0, Math.min(i, pendingQuests.size() - LIST_VISIBLE_ROWS));
                return false;
            }
        }
        return true;
    }

    private boolean isQuestValid(QuestDefinition q) {
        if (q.getTitle().trim().isEmpty()) return false;
        if (q.getObjectives().isEmpty()) return false;
        for (QuestObjective obj : q.getObjectives()) {
            if (!isObjectiveValid(obj)) return false;
        }
        // v2.9.3: prerequisites валидация
        for (QuestCondition c : q.getPrerequisites()) {
            if (!isConditionValid(c)) return false;
        }
        // Stage 7 (v2.9.6): rewards валидация. Пустой список наград разрешён —
        // отвергаем только заполненные но некорректные награды.
        for (QuestReward r : q.getRewards()) {
            if (!isRewardValid(r)) return false;
        }
        return true;
    }

    /** Stage 7 (v2.9.6): валидация одной награды по типу. */
    private boolean isRewardValid(QuestReward r) {
        if (r instanceof ItemReward ir) return !ir.getStack().isEmpty();
        if (r instanceof XPPointsReward xr) return xr.getAmount() > 0;
        if (r instanceof CommandReward cr) return !cr.getCommand().isBlank();
        if (r instanceof UnlockTradeSetReward ur) return !ur.getTradeSetName().isBlank();
        return true;
    }

    private boolean isConditionValid(QuestCondition c) {
        if (c instanceof DistanceToStructureCondition d) {
            return d.getStructureId() != null && d.getMinDistance() >= 0;
        }
        // Прочие типы пока не имплементированы — считаем валидными
        return true;
    }

    private boolean isObjectiveValid(QuestObjective obj) {
        switch (obj.getType()) {
            case KILL -> {
                KillObjective ko = (KillObjective) obj;
                if (ko.isTagMode()) {
                    if (ko.getEntityTypeTag() == null) return false;
                } else {
                    if (ko.getEntityType() == null) return false;
                }
                return ko.getCount() >= COUNT_MIN && ko.getCount() <= COUNT_MAX;
            }
            case BRING -> {
                BringObjective bo = (BringObjective) obj;
                if (bo.getStack().isEmpty()) return false;
                return bo.getCount() >= COUNT_MIN && bo.getCount() <= COUNT_MAX;
            }
            case BREAK_BLOCK -> {
                BreakBlockObjective bb = (BreakBlockObjective) obj;
                if (bb.isTagMode()) {
                    if (bb.getBlockTag() == null) return false;
                } else {
                    if (bb.getBlockId() == null) return false;
                }
                return bb.getCount() >= COUNT_MIN && bb.getCount() <= COUNT_MAX;
            }
            case REACH_BIOME -> {
                return ((ReachBiomeObjective) obj).getBiomeId() != null;
            }
            case REACH_STRUCTURE -> {
                return ((ReachStructureObjective) obj).getStructureId() != null;
            }
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Render
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, NPCMenuScreen.BG_OVERLAY);
        NPCMenuScreen.drawPanel(g, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);

        g.drawCenteredString(this.font, this.title, panelX + PANEL_WIDTH / 2, panelY + 8,
                NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF);
        g.fill(panelX + 1, panelY + 22, panelX + PANEL_WIDTH - 1, panelY + 23, NPCMenuScreen.BORDER);

        String counter = Component.translatable("gui.questnpc.quests.count",
                pendingQuests.size(), QuestNPCEntity.MAX_QUESTS).getString();
        g.drawString(this.font, counter,
                panelX + PADDING + 150, panelY + 33,
                NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);

        int sepX = panelX + PADDING + LIST_WIDTH + 2;
        g.fill(sepX, panelY + LIST_Y_OFFSET - 4, sepX + 1,
                panelY + PANEL_HEIGHT - 32, NPCMenuScreen.BORDER);

        super.render(g, mouseX, mouseY, partialTick);

        renderRightPane(g, mouseX, mouseY);

        if (showValidationErrors) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.questnpc.quests.validation_err"),
                    panelX + PANEL_WIDTH / 2,
                    panelY + PANEL_HEIGHT - 42,
                    NPCMenuScreen.TEXT_RED & 0x00FFFFFF);
        }

        g.drawCenteredString(this.font,
                Component.translatable("gui.questnpc.menu.footer"),
                panelX + PANEL_WIDTH / 2, panelY + PANEL_HEIGHT - 9,
                NPCMenuScreen.TEXT_DARK_GRAY & 0x00FFFFFF);
    }

    private void renderRightPane(GuiGraphics g, int mouseX, int mouseY) {
        int formX = panelX + PADDING + FORM_X_OFFSET;
        int formY = panelY + LIST_Y_OFFSET;
        int formW = PANEL_WIDTH - LIST_WIDTH - PADDING * 2 - 6;

        if (selectedIndex < 0 || selectedIndex >= pendingQuests.size()) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.questnpc.quests.select_hint"),
                    formX + formW / 2, formY + 100,
                    NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF);
            return;
        }

        QuestDefinition q = pendingQuests.get(selectedIndex);

        // Section header «КВЕСТ»
        g.drawString(this.font, Component.translatable("gui.questnpc.quests.quest_section"),
                formX, formY + 4, NPCMenuScreen.SECTION_TITLE & 0x00FFFFFF, false);
        g.fill(formX, formY + 14, formX + formW, formY + 15, NPCMenuScreen.BORDER);

        // Подсветка title если пуст
        if (showValidationErrors && titleBox != null && q.getTitle().trim().isEmpty()) {
            NPCMenuScreen.drawOutlineRect(g, titleBox.getX() - 1, titleBox.getY() - 1,
                    titleBox.getWidth() + 2, titleBox.getHeight() + 2, NPCMenuScreen.TEXT_RED);
        }

        // Section header «ЦЕЛИ»
        int objHeaderY = formY + 18 + 20 + 26;
        g.drawString(this.font, Component.translatable("gui.questnpc.quests.objectives"),
                formX, objHeaderY, NPCMenuScreen.SECTION_TITLE & 0x00FFFFFF, false);
        g.fill(formX, objHeaderY + 10, formX + formW, objHeaderY + 11, NPCMenuScreen.BORDER);

        // Подсветка невалидных полей objectives + prerequisites (v2.9.3)
        if (showValidationErrors) {
            List<QuestObjective> objs = q.getObjectives();
            for (int i = 0; i < Math.min(objs.size(), OBJ_VISIBLE); i++) {
                if (!isObjectiveValid(objs.get(i))) {
                    int[] pb = objPickBounds[i];
                    if (pb != null && pb[0] >= 0) {
                        NPCMenuScreen.drawOutlineRect(g, pb[0] - 1, pb[1] - 1, pb[2] + 2, pb[3] + 2,
                                NPCMenuScreen.TEXT_RED);
                    }
                    EditBox cb = objCountBoxes[i];
                    if (cb != null) {
                        NPCMenuScreen.drawOutlineRect(g, cb.getX() - 1, cb.getY() - 1,
                                cb.getWidth() + 2, cb.getHeight() + 2, NPCMenuScreen.TEXT_RED);
                    }
                }
            }
            if (prerequisitesExpanded) {
                List<QuestCondition> prereqs = q.getPrerequisites();
                for (int i = 0; i < Math.min(prereqs.size(), PREREQ_VISIBLE); i++) {
                    if (!isConditionValid(prereqs.get(i))) {
                        int[] pb = prereqPickBounds[i];
                        if (pb != null && pb[0] >= 0) {
                            NPCMenuScreen.drawOutlineRect(g, pb[0] - 1, pb[1] - 1, pb[2] + 2, pb[3] + 2,
                                    NPCMenuScreen.TEXT_RED);
                        }
                        EditBox db = prereqMinDistanceBoxes[i];
                        if (db != null) {
                            NPCMenuScreen.drawOutlineRect(g, db.getX() - 1, db.getY() - 1,
                                    db.getWidth() + 2, db.getHeight() + 2, NPCMenuScreen.TEXT_RED);
                        }
                    }
                }
            }
        }

        // Hint про rewards (теперь после prerequisites сдвинут чуть вниз)
        g.drawString(this.font, Component.translatable("gui.questnpc.quests.rewards_soon"),
                formX, panelY + PANEL_HEIGHT - 50,
                NPCMenuScreen.TEXT_DARK_GRAY & 0x00FFFFFF, false);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
