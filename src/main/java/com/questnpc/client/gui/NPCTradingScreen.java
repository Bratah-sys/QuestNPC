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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

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
    private static final int PANEL_HEIGHT   = 296;
    private static final int PADDING        = 10;
    private static final int VISIBLE_ROWS   = 4;
    private static final int MAX_OFFERS     = 10;
    private static final int ROW_AREA_START = 76;  // relative to panelY
    private static final int ROW_HEIGHT     = 46;

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

    // ─── Data ────────────────────────────────────────────────────────────
    private final QuestNPCEntity npc;
    private final Screen parentScreen;
    private boolean tradingEnabled;
    private final List<OfferData> offers = new ArrayList<>();
    private int scrollOffset = 0;

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

    public NPCTradingScreen(QuestNPCEntity npc, boolean tradingEnabled, ListTag tradeOffersTag,
                            Screen parentScreen) {
        super(Component.translatable("gui.questnpc.trading.title"));
        this.npc = npc;
        this.tradingEnabled = tradingEnabled;
        this.parentScreen = parentScreen;
        loadOffers(tradeOffersTag);
    }

    // ─── NBT helpers ─────────────────────────────────────────────────────

    private void loadOffers(@Nullable ListTag tag) {
        offers.clear();
        if (tag == null) return;
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
            offers.add(data);
        }
    }

    private ListTag buildOffersTag() {
        ListTag result = new ListTag();
        for (OfferData data : offers) {
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
                panelX + PANEL_WIDTH - PADDING - 110, panelY + 52, 110, 16,
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
                    refillBtns[slot].setMessage(Component.translatable(
                            offer.refilable ? "gui.questnpc.trading.refill_on" : "gui.questnpc.trading.refill_off"));
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
        ModNetwork.INSTANCE.sendToServer(
                new UpdateTradingEnabledPacket(npc.getId(), tradingEnabled));
        ModNetwork.INSTANCE.sendToServer(
                new UpdateTradeOffersPacket(npc.getId(), buildOffersTag()));
        Minecraft.getInstance().setScreen(parentScreen);
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
        g.fill(panelX + 1, panelY + 72, panelX + PANEL_WIDTH - 1, panelY + 73, NPCMenuScreen.BORDER);

        // 3. Offer count label
        String countStr = String.format(
                Component.translatable("gui.questnpc.trading.offers_count").getString(),
                offers.size());
        g.drawString(this.font, countStr, cx, panelY + 54,
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
