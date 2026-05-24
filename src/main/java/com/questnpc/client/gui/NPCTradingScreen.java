package com.questnpc.client.gui;

import com.questnpc.client.gui.widget.DarkButton;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.UpdateTradeOffersPacket;
import com.questnpc.network.UpdateTradingEnabledPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Административный экран настройки торговли NPC.
 * Позволяет включить/выключить торговлю и настроить список сделок (до 10).
 *
 * <p>Слоты предметов (input1, input2, output) выбираются визуально через
 * {@link ItemCatalogScreen} — клик по слоту открывает полноэкранный каталог
 * предметов с вкладками по модам и поиском. Количество задаётся в микро-поле
 * справа от иконки (1–64).
 */
public class NPCTradingScreen extends Screen {

    // ─── Layout constants ────────────────────────────────────────────────
    private static final int PANEL_WIDTH    = 380;
    private static final int PANEL_HEIGHT   = 316;
    private static final int PADDING        = 10;
    private static final int VISIBLE_ROWS   = 4;
    private static final int MAX_OFFERS     = 10;
    private static final int ROW_AREA_START = 96;  // relative to panelY
    private static final int ROW_HEIGHT     = 46;
    private static final int ADD_BTN_Y_OFF  = 72;  // relative to panelY
    private static final int COUNT_LABEL_Y_OFF = 74; // relative to panelY
    private static final int DIVIDER_Y_OFF  = 92;  // relative to panelY

    // Column layout (relative to contentX)
    private static final int SLOT_SIZE    = 20;  // icon cell size
    private static final int COUNT_W      = 22;  // count EditBox width
    private static final int COUNT_H      = 14;
    private static final int SLOT_GAP     = 2;   // gap between icon and count box
    private static final int SEP_W        = 14;  // separator "+" / "→"
    private static final int COL_STRIDE   = SLOT_SIZE + SLOT_GAP + COUNT_W + SEP_W; // = 58
    private static final int COL2_OFF     = COL_STRIDE;        // = 58
    private static final int COL3_OFF     = COL_STRIDE * 2;    // = 116
    private static final int DEL_OFF      = COL_STRIDE * 3 + 8;
    private static final int LIMIT_W      = 38;

    // ─── Tab strip layout ────────────────────────────────────────────────
    private static final int TAB_STRIP_Y_OFF   = 52; // относительно panelY
    private static final int TAB_HEIGHT        = 16;
    private static final int TAB_PAD_X         = 6;
    private static final int TAB_ICON_W        = 10;
    private static final int TAB_GAP           = 4;

    // ─── Data ────────────────────────────────────────────────────────────
    private final QuestNPCEntity npc;
    private final Screen parentScreen;
    private boolean tradingEnabled;

    /**
     * Имя каждого набора сделок (изменяется при переименовании).
     * Паралельный массив к {@link #setDrafts}.
     */
    private final List<String> setNames = new ArrayList<>();

    /** Буферы сделок по каждому набору. Индексы совпадают с {@link #setNames}. */
    private final List<List<OfferData>> setDrafts = new ArrayList<>();

    private int activeSetIndex = 0;
    private int editingNameSetIndex = -1;
    @Nullable
    private EditBox nameEditBox;
    private int deleteConfirmSetIndex = -1;
    private int scrollOffset = 0;

    /**
     * Активный список сделок — указывает на {@code setDrafts.get(activeSetIndex)}.
     * Re-pointed в {@link #setActiveSetIndex(int)}; весь существующий код работает с этим полем.
     */
    private List<OfferData> offers;

    /** Кэш геометрии вкладок, пересчитывается в render() — используется в mouseClicked(). */
    private int[] tabX = new int[0];
    private int[] tabW = new int[0];
    private int addTabX = -1;
    private int addTabW = 0;

    /** Флаг: refreshRows() в процессе — подавляет обратные вызовы responder */
    private boolean refreshing = false;

    // ─── Layout ──────────────────────────────────────────────────────────
    private int panelX, panelY;
    private int cx; // contentX = panelX + PADDING

    // ─── Named widgets ───────────────────────────────────────────────────
    private DarkButton toggleBtn;
    private DarkButton addBtn;
    private DarkButton scrollUpBtn;
    private DarkButton scrollDownBtn;

    private final SlotWidget[] input1Slots = new SlotWidget[VISIBLE_ROWS];
    private final SlotWidget[] input2Slots = new SlotWidget[VISIBLE_ROWS];
    private final SlotWidget[] outputSlots = new SlotWidget[VISIBLE_ROWS];
    private final EditBox[]    count1Boxes = new EditBox[VISIBLE_ROWS];
    private final EditBox[]    count2Boxes = new EditBox[VISIBLE_ROWS];
    private final EditBox[]    countOutBoxes = new EditBox[VISIBLE_ROWS];
    private final EditBox[]    limitBoxes  = new EditBox[VISIBLE_ROWS];
    private final DarkButton[] refillBtns  = new DarkButton[VISIBLE_ROWS];
    private final DarkButton[] deleteBtns  = new DarkButton[VISIBLE_ROWS];

    // ─── Inner data class ────────────────────────────────────────────────

    private static class OfferData {
        String  limitText  = "0";
        boolean refilable  = true;
        int     uses       = 0;

        ItemStack input1 = ItemStack.EMPTY;
        ItemStack input2 = ItemStack.EMPTY;
        ItemStack output = ItemStack.EMPTY;

        boolean input1Valid() { return !input1.isEmpty(); }
        boolean outputValid() { return !output.isEmpty(); }

        int getMaxUses() {
            try { return Math.max(0, Integer.parseInt(limitText)); }
            catch (NumberFormatException e) { return 0; }
        }
    }

    // ─── Constructor ─────────────────────────────────────────────────────

    public NPCTradingScreen(QuestNPCEntity npc, boolean tradingEnabled,
                            List<QuestNPCEntity.TradeSet> tradeSets,
                            Screen parentScreen) {
        super(Component.translatable("gui.questnpc.trading.title"));
        this.npc = npc;
        this.tradingEnabled = tradingEnabled;
        this.parentScreen = parentScreen;
        loadSets(tradeSets);
    }

    // ─── NBT helpers ─────────────────────────────────────────────────────

    private void loadSets(@Nullable List<QuestNPCEntity.TradeSet> sets) {
        setNames.clear();
        setDrafts.clear();
        if (sets != null) {
            for (QuestNPCEntity.TradeSet s : sets) {
                if (s == null) continue;
                setNames.add(s.name != null ? s.name : QuestNPCEntity.DEFAULT_TRADE_SET_NAME);
                setDrafts.add(loadOffers(s.offers));
                if (setNames.size() >= QuestNPCEntity.MAX_TRADE_SETS) break;
            }
        }
        if (setNames.isEmpty()) {
            setNames.add(QuestNPCEntity.DEFAULT_TRADE_SET_NAME);
            setDrafts.add(new ArrayList<>());
        }
        activeSetIndex = 0;
        offers = setDrafts.get(0);
    }

    private void setActiveSetIndex(int idx) {
        if (idx < 0 || idx >= setDrafts.size()) return;
        activeSetIndex = idx;
        offers = setDrafts.get(idx);
        scrollOffset = 0;
        deleteConfirmSetIndex = -1;
        refreshRows();
        updateScrollButtons();
    }

    private List<OfferData> loadOffers(@Nullable ListTag tag) {
        List<OfferData> list = new ArrayList<>();
        if (tag == null) return list;
        for (int i = 0; i < tag.size() && i < MAX_OFFERS; i++) {
            CompoundTag offerTag = tag.getCompound(i);
            OfferData data = new OfferData();
            if (offerTag.contains("input1")) {
                data.input1 = ItemStack.of(offerTag.getCompound("input1"));
            }
            if (offerTag.contains("input2")) {
                data.input2 = ItemStack.of(offerTag.getCompound("input2"));
            }
            if (offerTag.contains("output")) {
                data.output = ItemStack.of(offerTag.getCompound("output"));
            }
            data.limitText = String.valueOf(offerTag.getInt("maxUses"));
            data.refilable = !offerTag.contains("refilable") || offerTag.getBoolean("refilable");
            data.uses = offerTag.getInt("uses");
            list.add(data);
        }
        return list;
    }

    private ListTag buildOffersTag(List<OfferData> offersList) {
        ListTag result = new ListTag();
        for (OfferData data : offersList) {
            if (!data.input1Valid() || !data.outputValid()) continue;
            CompoundTag tag = new CompoundTag();
            tag.put("input1", data.input1.serializeNBT());
            tag.put("input2", data.input2.serializeNBT());
            tag.put("output", data.output.serializeNBT());
            tag.putInt("maxUses", data.getMaxUses());
            tag.putBoolean("refilable", data.refilable);
            tag.putInt("uses", data.uses);
            result.add(tag);
        }
        return result;
    }


    // ─── Slot accessors ──────────────────────────────────────────────────

    private ItemStack getStackForCol(OfferData offer, int col) {
        return switch (col) {
            case 0 -> offer.input1;
            case 1 -> offer.input2;
            case 2 -> offer.output;
            default -> ItemStack.EMPTY;
        };
    }

    private void setStackForCol(OfferData offer, int col, ItemStack stack) {
        switch (col) {
            case 0 -> offer.input1 = stack;
            case 1 -> offer.input2 = stack;
            case 2 -> offer.output = stack;
        }
    }

    // ─── Screen init ─────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        panelX = (this.width  - PANEL_WIDTH)  / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;
        cx = panelX + PADDING;

        int contentW = PANEL_WIDTH - PADDING * 2;

        // ── Back button ───────────────────────────────────────────────
        this.addRenderableWidget(new DarkButton(
                cx, panelY + 26, 60, 18,
                Component.translatable("gui.questnpc.menu.btn.back"),
                button -> Minecraft.getInstance().setScreen(parentScreen)
        ));

        // ── Trading toggle ────────────────────────────────────────────
        toggleBtn = this.addRenderableWidget(new DarkButton(
                cx + 66, panelY + 26, 190, 18,
                toggleComponent(),
                button -> {
                    tradingEnabled = !tradingEnabled;
                    toggleBtn.setMessage(toggleComponent());
                }
        ));

        // ── Add offer button ──────────────────────────────────────────
        addBtn = this.addRenderableWidget(new DarkButton(
                panelX + PANEL_WIDTH - PADDING - 110, panelY + ADD_BTN_Y_OFF, 110, 16,
                Component.translatable("gui.questnpc.trading.add_offer"),
                button -> addOffer(),
                NPCMenuScreen.BTN_GREEN_BG, NPCMenuScreen.BTN_GREEN_HOVER, 0xFFFFFFFF
        ));

        // ── Row widgets ───────────────────────────────────────────────
        for (int slot = 0; slot < VISIBLE_ROWS; slot++) {
            int ry = panelY + ROW_AREA_START + slot * ROW_HEIGHT;
            final int s = slot;

            // Input 1 — slot + count box
            input1Slots[slot] = this.addRenderableWidget(
                    new SlotWidget(s, 0, cx, ry));
            count1Boxes[slot] = createCountBox(cx + SLOT_SIZE + SLOT_GAP, ry + 3, s, 0);
            this.addRenderableWidget(count1Boxes[slot]);

            // Input 2 — slot + count box
            input2Slots[slot] = this.addRenderableWidget(
                    new SlotWidget(s, 1, cx + COL2_OFF, ry));
            count2Boxes[slot] = createCountBox(cx + COL2_OFF + SLOT_SIZE + SLOT_GAP, ry + 3, s, 1);
            this.addRenderableWidget(count2Boxes[slot]);

            // Output — slot + count box
            outputSlots[slot] = this.addRenderableWidget(
                    new SlotWidget(s, 2, cx + COL3_OFF, ry));
            countOutBoxes[slot] = createCountBox(cx + COL3_OFF + SLOT_SIZE + SLOT_GAP, ry + 3, s, 2);
            this.addRenderableWidget(countOutBoxes[slot]);

            // Limit EditBox
            limitBoxes[slot] = new EditBox(this.font,
                    cx + 44, ry + 26, LIMIT_W, 14, Component.literal(""));
            limitBoxes[slot].setMaxLength(6);
            limitBoxes[slot].setBordered(false);
            limitBoxes[slot].setFilter(str -> str.isEmpty() || str.matches("\\d{1,6}"));
            limitBoxes[slot].setResponder(text -> {
                if (refreshing) return;
                int idx = scrollOffset + s;
                if (idx >= offers.size()) return;
                offers.get(idx).limitText = text.isEmpty() ? "0" : text;
            });
            this.addRenderableWidget(limitBoxes[slot]);

            // Refill toggle
            refillBtns[slot] = this.addRenderableWidget(new DarkButton(
                    cx + 90, ry + 26, 80, 14,
                    Component.translatable("gui.questnpc.trading.refill_on"),
                    button -> {
                        int idx = scrollOffset + s;
                        if (idx >= offers.size()) return;
                        offers.get(idx).refilable = !offers.get(idx).refilable;
                        button.setMessage(Component.translatable(
                                offers.get(idx).refilable
                                        ? "gui.questnpc.trading.refill_on"
                                        : "gui.questnpc.trading.refill_off"));
                    }
            ));

            // Delete button
            deleteBtns[slot] = this.addRenderableWidget(new DarkButton(
                    cx + DEL_OFF, ry, 16, 16,
                    Component.literal("\u00D7"),
                    button -> {
                        int idx = scrollOffset + s;
                        if (idx >= offers.size()) return;
                        offers.remove(idx);
                        if (scrollOffset > 0 && scrollOffset > offers.size() - VISIBLE_ROWS) {
                            scrollOffset = Math.max(0, offers.size() - VISIBLE_ROWS);
                        }
                        refreshRows();
                    },
                    NPCMenuScreen.BTN_GRAY_BG, 0xFF7F1D1D, NPCMenuScreen.TEXT_WHITE
            ));
        }

        // ── Scroll buttons ────────────────────────────────────────────
        int scrollY = panelY + ROW_AREA_START + VISIBLE_ROWS * ROW_HEIGHT + 4;
        scrollUpBtn = this.addRenderableWidget(new DarkButton(
                panelX + PANEL_WIDTH - PADDING - 42, scrollY, 18, 14,
                Component.literal("\u25B2"),
                button -> scroll(-1)
        ));
        scrollDownBtn = this.addRenderableWidget(new DarkButton(
                panelX + PANEL_WIDTH - PADDING - 20, scrollY, 18, 14,
                Component.literal("\u25BC"),
                button -> scroll(1)
        ));

        // ── Cancel / Apply ────────────────────────────────────────────
        int footerY = panelY + PANEL_HEIGHT - 30;
        int btnW    = (contentW - 8) / 2;

        this.addRenderableWidget(new DarkButton(
                cx, footerY, btnW, 20,
                Component.translatable("gui.questnpc.npc_menu.cancel"),
                button -> Minecraft.getInstance().setScreen(parentScreen),
                NPCMenuScreen.BTN_GRAY_BG, NPCMenuScreen.BTN_GRAY_HOVER, NPCMenuScreen.TEXT_WHITE
        ));

        this.addRenderableWidget(new DarkButton(
                cx + btnW + 8, footerY, btnW, 20,
                Component.translatable("gui.questnpc.npc_menu.apply"),
                button -> applySettings(),
                NPCMenuScreen.BTN_GREEN_BG, NPCMenuScreen.BTN_GREEN_HOVER, 0xFFFFFFFF
        ));

        refreshRows();
        updateScrollButtons();
    }

    private EditBox createCountBox(int x, int y, int visibleRow, int col) {
        EditBox box = new EditBox(this.font, x, y, COUNT_W, COUNT_H, Component.literal(""));
        box.setMaxLength(2);
        box.setBordered(false);
        box.setHint(Component.translatable("gui.questnpc.trading.count_hint"));
        box.setFilter(str -> str.isEmpty() || str.matches("\\d{1,2}"));
        box.setResponder(text -> {
            if (refreshing) return;
            int idx = scrollOffset + visibleRow;
            if (idx >= offers.size()) return;
            OfferData offer = offers.get(idx);
            ItemStack stack = getStackForCol(offer, col);
            if (stack.isEmpty()) return;
            int c;
            try { c = text.isEmpty() ? 1 : Integer.parseInt(text); }
            catch (NumberFormatException e) { c = 1; }
            c = Math.max(1, Math.min(64, c));
            stack.setCount(c);
        });
        return box;
    }

    // ─── Logic ───────────────────────────────────────────────────────────

    private Component toggleComponent() {
        return Component.translatable(tradingEnabled
                ? "gui.questnpc.trading.enabled"
                : "gui.questnpc.trading.disabled");
    }

    private void addOffer() {
        if (offers.size() >= MAX_OFFERS) return;
        offers.add(new OfferData());
        if (offers.size() > scrollOffset + VISIBLE_ROWS) {
            scrollOffset = offers.size() - VISIBLE_ROWS;
        }
        refreshRows();
        updateScrollButtons();
    }

    private void scroll(int delta) {
        int maxOffset = Math.max(0, offers.size() - VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(scrollOffset + delta, maxOffset));
        refreshRows();
        updateScrollButtons();
    }

    private void updateScrollButtons() {
        scrollUpBtn.active   = scrollOffset > 0;
        scrollDownBtn.active = scrollOffset < offers.size() - VISIBLE_ROWS;
        addBtn.active = offers.size() < MAX_OFFERS;
    }

    /**
     * Вызывается из {@link ItemCatalogScreen} при возврате с выбранным предметом.
     *
     * @param globalSlotId {@code visibleRow * 3 + col}
     * @param selected     выбранный предмет (null = очистить слот)
     */
    public void setPendingItemSelection(int globalSlotId, @Nullable Item selected) {
        int visibleRow = globalSlotId / 3;
        int col = globalSlotId % 3;
        if (visibleRow < 0 || visibleRow >= VISIBLE_ROWS) return;
        int offerIdx = scrollOffset + visibleRow;
        if (offerIdx >= offers.size()) return;

        OfferData offer = offers.get(offerIdx);
        ItemStack oldStack = getStackForCol(offer, col);
        int keepCount = oldStack.isEmpty() ? 1 : oldStack.getCount();
        if (keepCount < 1) keepCount = 1;

        ItemStack newStack;
        if (selected == null || selected == Items.AIR) {
            newStack = ItemStack.EMPTY;
        } else {
            newStack = new ItemStack(selected, keepCount);
        }
        setStackForCol(offer, col, newStack);
        refreshRows();
    }

    /** Синхронизирует содержимое виджетов строк с данными из списка сделок. */
    private void refreshRows() {
        refreshing = true;
        try {
            for (int slot = 0; slot < VISIBLE_ROWS; slot++) {
                int offerIdx = scrollOffset + slot;
                boolean hasOffer = offerIdx < offers.size();

                input1Slots[slot].visible = hasOffer;
                input2Slots[slot].visible = hasOffer;
                outputSlots[slot].visible = hasOffer;
                count1Boxes[slot].visible = hasOffer;
                count2Boxes[slot].visible = hasOffer;
                countOutBoxes[slot].visible = hasOffer;
                limitBoxes[slot].visible  = hasOffer;
                refillBtns[slot].visible  = hasOffer;
                deleteBtns[slot].visible  = hasOffer;

                input1Slots[slot].active = hasOffer;
                input2Slots[slot].active = hasOffer;
                outputSlots[slot].active = hasOffer;
                count1Boxes[slot].active = hasOffer;
                count2Boxes[slot].active = hasOffer;
                countOutBoxes[slot].active = hasOffer;
                limitBoxes[slot].active  = hasOffer;
                refillBtns[slot].active  = hasOffer;
                deleteBtns[slot].active  = hasOffer;

                if (hasOffer) {
                    OfferData offer = offers.get(offerIdx);
                    count1Boxes[slot].setValue(countText(offer.input1));
                    count2Boxes[slot].setValue(countText(offer.input2));
                    countOutBoxes[slot].setValue(countText(offer.output));
                    limitBoxes[slot].setValue(offer.limitText.equals("0") ? "0" : offer.limitText);
                    refillBtns[slot].setMessage(Component.translatable(offer.refilable ? "gui.questnpc.trading.refill_on" : "gui.questnpc.trading.refill_off"));
                }
            }
        } finally {
            refreshing = false;
        }
    }

    private static String countText(ItemStack stack) {
        return stack.isEmpty() ? "" : String.valueOf(stack.getCount());
    }

    private void applySettings() {
        commitNameEdit();

        List<QuestNPCEntity.TradeSet> payload = new ArrayList<>();
        for (int i = 0; i < setNames.size() && i < QuestNPCEntity.MAX_TRADE_SETS; i++) {
            String name = setNames.get(i);
            if (name == null || name.isEmpty()) name = QuestNPCEntity.DEFAULT_TRADE_SET_NAME;
            ListTag listTag = buildOffersTag(setDrafts.get(i));
            payload.add(new QuestNPCEntity.TradeSet(name, listTag));
        }

        ModNetwork.INSTANCE.sendToServer(
                new UpdateTradingEnabledPacket(npc.getId(), tradingEnabled));
        ModNetwork.INSTANCE.sendToServer(
                new UpdateTradeOffersPacket(npc.getId(), payload));

        // Обновляем кэш родительского экрана — иначе повторный вход в Trading
        // покажет устаревший снапшот из OpenNPCMenuPacket (баг v2.5.0).
        if (parentScreen instanceof NPCMenuScreen parentMenu) {
            parentMenu.updateTradingState(tradingEnabled, payload);
        }

        Minecraft.getInstance().setScreen(parentScreen);
    }

    // ─── Tab operations ──────────────────────────────────────────────────

    private void addSet() {
        if (setNames.size() >= QuestNPCEntity.MAX_TRADE_SETS) return;
        // Сгенерировать уникальное имя: "Default N"
        String base = QuestNPCEntity.DEFAULT_TRADE_SET_NAME;
        String candidate = base;
        int n = 2;
        while (setNames.contains(candidate)) {
            candidate = base + " " + n++;
        }
        setNames.add(candidate);
        setDrafts.add(new ArrayList<>());
        setActiveSetIndex(setNames.size() - 1);
    }

    private void deleteSet(int index) {
        if (setNames.size() <= 1) return;
        if (index < 0 || index >= setNames.size()) return;
        setNames.remove(index);
        setDrafts.remove(index);
        deleteConfirmSetIndex = -1;
        int newActive = Math.max(0, Math.min(activeSetIndex, setNames.size() - 1));
        setActiveSetIndex(newActive);
    }

    private void beginRename(int index) {
        if (index < 0 || index >= setNames.size()) return;
        commitNameEdit();
        editingNameSetIndex = index;
        // Создаём EditBox над вкладкой — позиция будет обновлена в render()/init()
        int boxX = panelX + PADDING;
        int boxY = panelY + TAB_STRIP_Y_OFF;
        nameEditBox = new EditBox(this.font, boxX, boxY, 90, TAB_HEIGHT - 2,
                Component.literal(""));
        nameEditBox.setMaxLength(32);
        nameEditBox.setValue(setNames.get(index));
        nameEditBox.setBordered(true);
        nameEditBox.setResponder(text -> { /* no-op */ });
        this.addRenderableWidget(nameEditBox);
        this.setFocused(nameEditBox);
        nameEditBox.setFocused(true);
    }

    private void commitNameEdit() {
        if (editingNameSetIndex < 0 || nameEditBox == null) return;
        String val = nameEditBox.getValue().trim();
        if (!val.isEmpty() && editingNameSetIndex < setNames.size()) {
            if (val.length() > 32) val = val.substring(0, 32);
            // v2.5.5 (BUG-013): проверка уникальности имени набора.
            boolean collision = false;
            for (int i = 0; i < setNames.size(); i++) {
                if (i != editingNameSetIndex && setNames.get(i).equals(val)) {
                    collision = true;
                    break;
                }
            }
            if (!collision) {
                setNames.set(editingNameSetIndex, val);
            } else if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(
                        Component.translatable("message.questnpc.trade_set_name_taken"));
            }
        }
        removeWidget(nameEditBox);
        nameEditBox = null;
        editingNameSetIndex = -1;
    }

    private void cancelNameEdit() {
        if (nameEditBox != null) {
            removeWidget(nameEditBox);
            nameEditBox = null;
        }
        editingNameSetIndex = -1;
    }

    // ─── Rendering ───────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 1. Overlay + panel
        g.fill(0, 0, this.width, this.height, NPCMenuScreen.BG_OVERLAY);
        NPCMenuScreen.drawPanel(g, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);

        // 2. Title + dividers
        g.drawCenteredString(this.font, this.title,
                panelX + PANEL_WIDTH / 2, panelY + 6, NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF);
        g.fill(panelX + 1, panelY + 22, panelX + PANEL_WIDTH - 1, panelY + 23, NPCMenuScreen.BORDER);
        g.fill(panelX + 1, panelY + 48, panelX + PANEL_WIDTH - 1, panelY + 49, NPCMenuScreen.BORDER);
        g.fill(panelX + 1, panelY + DIVIDER_Y_OFF, panelX + PANEL_WIDTH - 1, panelY + DIVIDER_Y_OFF + 1, NPCMenuScreen.BORDER);

        // 3. Tab strip for trade sets
        renderTabStrip(g, mouseX, mouseY);

        // 4. Offer count label
        String countStr = String.format(
                Component.translatable("gui.questnpc.trading.offers_count").getString(),
                offers.size());
        g.drawString(this.font, countStr, cx, panelY + COUNT_LABEL_Y_OFF,
                NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF, false);

        // 4. Row separators + limit labels
        for (int slot = 0; slot < VISIBLE_ROWS; slot++) {
            int offerIdx = scrollOffset + slot;
            if (offerIdx >= offers.size()) break;
            OfferData offer = offers.get(offerIdx);
            int ry = panelY + ROW_AREA_START + slot * ROW_HEIGHT;

            // Фон под count-box'ами
            NPCMenuScreen.drawEditBoxBg(g,
                    cx + SLOT_SIZE + SLOT_GAP - 2, ry + 1, COUNT_W + 4, COUNT_H + 4, true);
            NPCMenuScreen.drawEditBoxBg(g,
                    cx + COL2_OFF + SLOT_SIZE + SLOT_GAP - 2, ry + 1, COUNT_W + 4, COUNT_H + 4, true);
            NPCMenuScreen.drawEditBoxBg(g,
                    cx + COL3_OFF + SLOT_SIZE + SLOT_GAP - 2, ry + 1, COUNT_W + 4, COUNT_H + 4, true);

            // Separator chars "+" and "→" в разрывах между слотами
            int plusX = cx + SLOT_SIZE + SLOT_GAP + COUNT_W + 2;
            int arrowX = cx + COL2_OFF + SLOT_SIZE + SLOT_GAP + COUNT_W + 2;
            g.drawString(this.font, "+",
                    plusX, ry + 6, NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);
            g.drawString(this.font, "\u2192",
                    arrowX, ry + 6, NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);

            // Line 2: "Лимит:" label + limit EditBox bg
            int ry2 = ry + 26;
            String limitLabel = Component.translatable("gui.questnpc.trading.limit").getString() + ":";
            g.drawString(this.font, limitLabel, cx, ry2 + 1,
                    NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);
            NPCMenuScreen.drawEditBoxBg(g, cx + 42, ry2 - 2, LIMIT_W + 4, 18, true);

            if (offer.getMaxUses() == 0) {
                g.drawString(this.font,
                        Component.translatable("gui.questnpc.trading.unlimited").getString(),
                        cx + 42 + LIMIT_W + 6, ry2 + 1,
                        NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);
            }

            // Row divider
            if (slot < VISIBLE_ROWS - 1 && offerIdx < offers.size() - 1) {
                g.fill(panelX + 1, ry + ROW_HEIGHT - 1,
                        panelX + PANEL_WIDTH - 1, ry + ROW_HEIGHT,
                        NPCMenuScreen.BORDER);
            }
        }

        // 5. Scroll indicator
        int scrollAreaY = panelY + ROW_AREA_START + VISIBLE_ROWS * ROW_HEIGHT + 4;
        if (offers.size() > VISIBLE_ROWS) {
            int end = Math.min(scrollOffset + VISIBLE_ROWS, offers.size());
            String scrollInfo = (scrollOffset + 1) + "\u2013" + end + " / " + offers.size();
            g.drawCenteredString(this.font, scrollInfo,
                    panelX + PANEL_WIDTH / 2 - 20, scrollAreaY + 3,
                    NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF);
        }

        // 6. Footer divider + text
        g.fill(panelX + 1, panelY + PANEL_HEIGHT - 34,
                panelX + PANEL_WIDTH - 1, panelY + PANEL_HEIGHT - 33, NPCMenuScreen.BORDER);
        g.drawCenteredString(this.font,
                Component.translatable("gui.questnpc.menu.footer"),
                panelX + PANEL_WIDTH / 2, panelY + PANEL_HEIGHT - 10,
                NPCMenuScreen.TEXT_DARK_GRAY & 0x00FFFFFF);

        // 7. Widgets on top (вкл. SlotWidget с иконками)
        super.render(g, mouseX, mouseY, partialTick);

        // 8. Тултипы для слотов — поверх super.render
        renderSlotTooltips(g, mouseX, mouseY);
    }

    private void renderSlotTooltips(GuiGraphics g, int mouseX, int mouseY) {
        for (int slot = 0; slot < VISIBLE_ROWS; slot++) {
            int offerIdx = scrollOffset + slot;
            if (offerIdx >= offers.size()) break;
            renderTooltipIfHovered(g, input1Slots[slot], offers.get(offerIdx).input1, mouseX, mouseY);
            renderTooltipIfHovered(g, input2Slots[slot], offers.get(offerIdx).input2, mouseX, mouseY);
            renderTooltipIfHovered(g, outputSlots[slot], offers.get(offerIdx).output, mouseX, mouseY);
        }
    }

    private void renderTooltipIfHovered(GuiGraphics g, SlotWidget slot, ItemStack stack,
                                        int mouseX, int mouseY) {
        if (!slot.visible) return;
        if (mouseX < slot.getX() || mouseX >= slot.getX() + SLOT_SIZE) return;
        if (mouseY < slot.getY() || mouseY >= slot.getY() + SLOT_SIZE) return;
        if (stack.isEmpty()) {
            g.renderTooltip(this.font,
                    Component.translatable("gui.questnpc.trading.slot_empty"),
                    mouseX, mouseY);
        } else {
            try {
                g.renderTooltip(this.font, stack, mouseX, mouseY);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ─── Tab strip rendering / input ─────────────────────────────────────

    private void renderTabStrip(GuiGraphics g, int mouseX, int mouseY) {
        int stripY = panelY + TAB_STRIP_Y_OFF;
        int x = panelX + PADDING;

        tabX = new int[setNames.size()];
        tabW = new int[setNames.size()];

        for (int i = 0; i < setNames.size(); i++) {
            String name = setNames.get(i);
            int textW = this.font.width(name);
            int iconsW = TAB_ICON_W * 2 + 2; // ✎ + ×
            int w = TAB_PAD_X * 2 + textW + iconsW + 4;
            tabX[i] = x;
            tabW[i] = w;

            boolean active = (i == activeSetIndex);
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= stripY && mouseY < stripY + TAB_HEIGHT;

            int bg;
            if (active) {
                bg = NPCMenuScreen.BTN_GREEN_BG;
            } else {
                bg = hovered ? NPCMenuScreen.BTN_GRAY_HOVER : NPCMenuScreen.BTN_GRAY_BG;
            }
            g.fill(x, stripY, x + w, stripY + TAB_HEIGHT, bg);
            NPCMenuScreen.drawOutlineRect(g, x, stripY, w, TAB_HEIGHT, NPCMenuScreen.BORDER);

            // Имя набора (или скрываем, если редактируется — EditBox показан поверх)
            if (i != editingNameSetIndex) {
                int textColor = active ? 0xFFFFFFFF : (NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF);
                g.drawString(this.font, name, x + TAB_PAD_X, stripY + 4, textColor, false);
            } else if (nameEditBox != null) {
                nameEditBox.setX(x + 1);
                nameEditBox.setY(stripY + 1);
                nameEditBox.setWidth(Math.max(60, textW + 20));
            }

            // ✎ и × иконки
            int pencilX = x + w - TAB_PAD_X - TAB_ICON_W * 2 - 2;
            int closeX  = x + w - TAB_PAD_X - TAB_ICON_W;

            int pencilColor = (NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF);
            g.drawString(this.font, "\u270E", pencilX, stripY + 4, pencilColor, false);

            boolean canDelete = setNames.size() > 1;
            int closeColor;
            if (!canDelete) {
                closeColor = (NPCMenuScreen.TEXT_DARK_GRAY & 0x00FFFFFF);
            } else if (i == deleteConfirmSetIndex) {
                closeColor = (NPCMenuScreen.TEXT_RED & 0x00FFFFFF);
            } else {
                closeColor = (NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF);
            }
            g.drawString(this.font, "\u00D7", closeX, stripY + 4, closeColor, false);

            x += w + TAB_GAP;
        }

        // "+" кнопка добавления набора
        if (setNames.size() < QuestNPCEntity.MAX_TRADE_SETS) {
            int addW = TAB_HEIGHT + 4;
            addTabX = x;
            addTabW = addW;
            boolean hovered = mouseX >= x && mouseX < x + addW && mouseY >= stripY && mouseY < stripY + TAB_HEIGHT;
            int bg = hovered ? NPCMenuScreen.BTN_GREEN_HOVER : NPCMenuScreen.BTN_GREEN_BG;
            g.fill(x, stripY, x + addW, stripY + TAB_HEIGHT, bg);
            NPCMenuScreen.drawOutlineRect(g, x, stripY, addW, TAB_HEIGHT, NPCMenuScreen.BORDER);
            g.drawCenteredString(this.font, "+", x + addW / 2, stripY + 4, 0xFFFFFF);
        } else {
            addTabX = -1;
            addTabW = 0;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int stripY = panelY + TAB_STRIP_Y_OFF;
        if (mouseY >= stripY && mouseY < stripY + TAB_HEIGHT) {
            // Клик по вкладке
            for (int i = 0; i < tabX.length; i++) {
                int x = tabX[i];
                int w = tabW[i];
                if (mouseX < x || mouseX >= x + w) continue;

                int pencilLeft = x + w - TAB_PAD_X - TAB_ICON_W * 2 - 2;
                int closeLeft  = x + w - TAB_PAD_X - TAB_ICON_W;

                if (mouseX >= closeLeft) {
                    if (setNames.size() <= 1) return true;
                    if (deleteConfirmSetIndex == i) {
                        deleteSet(i);
                    } else {
                        deleteConfirmSetIndex = i;
                    }
                    return true;
                }
                if (mouseX >= pencilLeft) {
                    beginRename(i);
                    return true;
                }
                // Клик на имя → переключение
                if (i != activeSetIndex) {
                    commitNameEdit();
                    deleteConfirmSetIndex = -1;
                    setActiveSetIndex(i);
                }
                return true;
            }
            // Клик по "+"
            if (addTabX >= 0 && mouseX >= addTabX && mouseX < addTabX + addTabW) {
                commitNameEdit();
                addSet();
                return true;
            }
        }

        // Если редактируем имя и клик не по editBox — коммитим
        if (nameEditBox != null && editingNameSetIndex >= 0) {
            boolean hitBox = mouseX >= nameEditBox.getX()
                    && mouseX < nameEditBox.getX() + nameEditBox.getWidth()
                    && mouseY >= nameEditBox.getY()
                    && mouseY < nameEditBox.getY() + nameEditBox.getHeight();
            if (!hitBox) {
                commitNameEdit();
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingNameSetIndex >= 0 && nameEditBox != null) {
            if (keyCode == 257 || keyCode == 335) { // Enter / KP Enter
                commitNameEdit();
                return true;
            }
            if (keyCode == 256) { // Esc
                cancelNameEdit();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ─── SlotWidget inner class ──────────────────────────────────────────

    /**
     * Клик-ячейка 20×20 для выбора предмета. Рендерит иконку текущего предмета
     * через {@link GuiGraphics#renderItem(ItemStack, int, int)} или "+" если пусто.
     * По клику открывает {@link ItemCatalogScreen}.
     */
    private class SlotWidget extends AbstractWidget {
        private final int visibleRow;
        private final int col; // 0=input1, 1=input2, 2=output

        SlotWidget(int visibleRow, int col, int x, int y) {
            super(x, y, SLOT_SIZE, SLOT_SIZE, Component.empty());
            this.visibleRow = visibleRow;
            this.col = col;
        }

        @Nullable
        private ItemStack currentStack() {
            int offerIdx = scrollOffset + visibleRow;
            if (offerIdx >= offers.size()) return null;
            return getStackForCol(offers.get(offerIdx), col);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (!this.visible) return;
            int x = this.getX();
            int y = this.getY();

            ItemStack stack = currentStack();
            boolean hovered = this.isHovered();
            boolean empty = stack == null || stack.isEmpty();

            // Фон ячейки: валидность зависит от колонки (input1/output обязательны)
            boolean required = (col != 1);
            boolean valid = !empty;
            int borderColor;
            int bgColor;
            if (empty && required) {
                borderColor = NPCMenuScreen.TEXT_RED;
                bgColor = 0xFF2B1515;
            } else if (empty) {
                borderColor = NPCMenuScreen.BORDER;
                bgColor = 0xFF1F2937;
            } else {
                borderColor = 0xFF2DD4BF;
                bgColor = 0xFF0F2E24;
            }
            if (hovered) {
                bgColor = 0xFF2D3748;
            }
            g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, bgColor);
            NPCMenuScreen.drawOutlineRect(g, x, y, SLOT_SIZE, SLOT_SIZE, borderColor);

            if (!empty) {
                try {
                    g.renderItem(stack, x + 2, y + 2);
                    // Декорации не нужны — количество редактируется рядом, и штатный
                    // рендер количества мешал бы восприятию
                } catch (Exception ignored) {}
            } else {
                g.drawCenteredString(
                        Minecraft.getInstance().font, "+",
                        x + SLOT_SIZE / 2, y + SLOT_SIZE / 2 - 4,
                        valid ? (NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF)
                              : (NPCMenuScreen.TEXT_RED & 0x00FFFFFF));
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            int offerIdx = scrollOffset + visibleRow;
            if (offerIdx >= offers.size()) return;
            ItemStack current = getStackForCol(offers.get(offerIdx), col);
            Item currentItem = current.isEmpty() ? null : current.getItem();
            int globalSlotId = visibleRow * 3 + col;
            Minecraft.getInstance().setScreen(
                    new ItemCatalogScreen(NPCTradingScreen.this, globalSlotId, currentItem));
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {
            narration.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
                    Component.translatable("gui.questnpc.trading.slot_empty"));
        }
    }
}
