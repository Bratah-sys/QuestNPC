package com.questnpc.client.gui;

import com.questnpc.client.gui.widget.DarkButton;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.UpdateTradeOffersPacket;
import com.questnpc.network.UpdateTradingEnabledPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Административный экран настройки торговли NPC.
 * Позволяет включить/выключить торговлю и настроить список сделок (до 10).
 */
public class NPCTradingScreen extends Screen {

    // ─── Layout constants ────────────────────────────────────────────────
    private static final int PANEL_WIDTH    = 380;
    private static final int PANEL_HEIGHT   = 284;
    private static final int PADDING        = 10;
    private static final int VISIBLE_ROWS   = 4;
    private static final int MAX_OFFERS     = 10;
    private static final int ROW_AREA_START = 76;  // relative to panelY
    private static final int ROW_HEIGHT     = 40;

    // Column layout (relative to contentX)
    private static final int EDIT_W   = 86; // width of each item EditBox
    private static final int EDIT_GAP = 12; // gap between columns (for separator chars)
    private static final int COL2_OFF = EDIT_W + EDIT_GAP;           // = 98
    private static final int COL3_OFF = (EDIT_W + EDIT_GAP) * 2;     // = 196
    private static final int DEL_OFF  = (EDIT_W + EDIT_GAP) * 3;     // = 294
    private static final int LIMIT_W  = 38;

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

    private final EditBox[]    input1Boxes = new EditBox[VISIBLE_ROWS];
    private final EditBox[]    input2Boxes = new EditBox[VISIBLE_ROWS];
    private final EditBox[]    outputBoxes = new EditBox[VISIBLE_ROWS];
    private final EditBox[]    limitBoxes  = new EditBox[VISIBLE_ROWS];
    private final DarkButton[] refillBtns  = new DarkButton[VISIBLE_ROWS];
    private final DarkButton[] deleteBtns  = new DarkButton[VISIBLE_ROWS];

    // ─── Inner data class ────────────────────────────────────────────────

    private static class OfferData {
        String  input1Text = "";
        String  input2Text = "";
        String  outputText = "";
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
                data.input1Text = itemToText(data.input1);
            }
            if (offerTag.contains("input2")) {
                data.input2 = ItemStack.of(offerTag.getCompound("input2"));
                data.input2Text = itemToText(data.input2);
            }
            if (offerTag.contains("output")) {
                data.output = ItemStack.of(offerTag.getCompound("output"));
                data.outputText = itemToText(data.output);
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

    // ─── Item parsing ────────────────────────────────────────────────────

    @Nullable
    private static ItemStack parseItemStack(String text) {
        if (text == null) return null;
        text = text.trim();
        if (text.isEmpty()) return null;
        String[] parts = text.split("\\s+", 2);
        ResourceLocation rl = ResourceLocation.tryParse(parts[0]);
        if (rl == null) return null;
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return null;
        int count = 1;
        if (parts.length > 1) {
            try { count = Math.max(1, Math.min(64, Integer.parseInt(parts[1]))); }
            catch (NumberFormatException ignored) {}
        }
        return new ItemStack(item, count);
    }

    private static String itemToText(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) return "";
        return stack.getCount() == 1 ? key.toString() : key + " " + stack.getCount();
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

            // Input 1
            input1Boxes[slot] = new EditBox(this.font,
                    cx, ry, EDIT_W, 16, Component.literal(""));
            input1Boxes[slot].setMaxLength(64);
            input1Boxes[slot].setBordered(false);
            input1Boxes[slot].setHint(Component.translatable("gui.questnpc.trading.input1_hint"));
            input1Boxes[slot].setResponder(text -> {
                if (refreshing) return;
                int idx = scrollOffset + s;
                if (idx >= offers.size()) return;
                offers.get(idx).input1Text = text;
                ItemStack parsed = parseItemStack(text);
                offers.get(idx).input1 = parsed != null ? parsed : ItemStack.EMPTY;
                updateRowTextColor(s);
            });
            this.addRenderableWidget(input1Boxes[slot]);

            // Input 2
            input2Boxes[slot] = new EditBox(this.font,
                    cx + COL2_OFF, ry, EDIT_W, 16, Component.literal(""));
            input2Boxes[slot].setMaxLength(64);
            input2Boxes[slot].setBordered(false);
            input2Boxes[slot].setHint(Component.translatable("gui.questnpc.trading.input2_hint"));
            input2Boxes[slot].setResponder(text -> {
                if (refreshing) return;
                int idx = scrollOffset + s;
                if (idx >= offers.size()) return;
                offers.get(idx).input2Text = text;
                ItemStack parsed = parseItemStack(text);
                offers.get(idx).input2 = parsed != null ? parsed : ItemStack.EMPTY;
            });
            this.addRenderableWidget(input2Boxes[slot]);

            // Output
            outputBoxes[slot] = new EditBox(this.font,
                    cx + COL3_OFF, ry, EDIT_W, 16, Component.literal(""));
            outputBoxes[slot].setMaxLength(64);
            outputBoxes[slot].setBordered(false);
            outputBoxes[slot].setHint(Component.translatable("gui.questnpc.trading.output_hint"));
            outputBoxes[slot].setResponder(text -> {
                if (refreshing) return;
                int idx = scrollOffset + s;
                if (idx >= offers.size()) return;
                offers.get(idx).outputText = text;
                ItemStack parsed = parseItemStack(text);
                offers.get(idx).output = parsed != null ? parsed : ItemStack.EMPTY;
                updateRowTextColor(s);
            });
            this.addRenderableWidget(outputBoxes[slot]);

            // Limit EditBox
            limitBoxes[slot] = new EditBox(this.font,
                    cx + 44, ry + 22, LIMIT_W, 14, Component.literal(""));
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
                    cx + 90, ry + 22, 80, 14,
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

    // ─── Logic ───────────────────────────────────────────────────────────

    private Component toggleComponent() {
        return Component.translatable(tradingEnabled
                ? "gui.questnpc.trading.enabled"
                : "gui.questnpc.trading.disabled");
    }

    private void addOffer() {
        if (offers.size() >= MAX_OFFERS) return;
        offers.add(new OfferData());
        // Scroll to last page if new offer is beyond visible area
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

    /** Синхронизирует содержимое виджетов строк с данными из списка сделок. */
    private void refreshRows() {
        refreshing = true;
        try {
            for (int slot = 0; slot < VISIBLE_ROWS; slot++) {
                int offerIdx = scrollOffset + slot;
                boolean hasOffer = offerIdx < offers.size();

                input1Boxes[slot].visible = hasOffer;
                input2Boxes[slot].visible = hasOffer;
                outputBoxes[slot].visible = hasOffer;
                limitBoxes[slot].visible  = hasOffer;
                refillBtns[slot].visible  = hasOffer;
                deleteBtns[slot].visible  = hasOffer;

                input1Boxes[slot].active = hasOffer;
                input2Boxes[slot].active = hasOffer;
                outputBoxes[slot].active = hasOffer;
                limitBoxes[slot].active  = hasOffer;
                refillBtns[slot].active  = hasOffer;
                deleteBtns[slot].active  = hasOffer;

                if (hasOffer) {
                    OfferData offer = offers.get(offerIdx);
                    input1Boxes[slot].setValue(offer.input1Text);
                    input2Boxes[slot].setValue(offer.input2Text);
                    outputBoxes[slot].setValue(offer.outputText);
                    limitBoxes[slot].setValue(offer.limitText.equals("0") ? "0" : offer.limitText);
                    refillBtns[slot].setMessage(Component.translatable(
                            offer.refilable ? "gui.questnpc.trading.refill_on" : "gui.questnpc.trading.refill_off"));
                    updateRowTextColor(slot);
                }
            }
        } finally {
            refreshing = false;
        }
    }

    private void updateRowTextColor(int slot) {
        int offerIdx = scrollOffset + slot;
        if (offerIdx >= offers.size()) return;
        OfferData offer = offers.get(offerIdx);

        // Input1: red = text entered but unparseable; white = valid; default = empty hint
        if (offer.input1Text.isEmpty()) {
            input1Boxes[slot].setTextColor(0xAAAAAA);
        } else {
            input1Boxes[slot].setTextColor(offer.input1Valid()
                    ? 0xFFFFFF : (NPCMenuScreen.TEXT_RED & 0x00FFFFFF));
        }

        // Input2: optional — red only if text is non-empty and invalid
        if (offer.input2Text.isEmpty()) {
            input2Boxes[slot].setTextColor(0xAAAAAA);
        } else {
            input2Boxes[slot].setTextColor(
                    !offer.input2.isEmpty() ? 0xFFFFFF : (NPCMenuScreen.TEXT_RED & 0x00FFFFFF));
        }

        // Output: required
        if (offer.outputText.isEmpty()) {
            outputBoxes[slot].setTextColor(0xAAAAAA);
        } else {
            outputBoxes[slot].setTextColor(offer.outputValid()
                    ? 0xFFFFFF : (NPCMenuScreen.TEXT_RED & 0x00FFFFFF));
        }
    }

    private void applySettings() {
        // Отправляем два пакета: сначала тумблер, потом список сделок
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

        // 4. Row backgrounds + separators + limit labels
        for (int slot = 0; slot < VISIBLE_ROWS; slot++) {
            int offerIdx = scrollOffset + slot;
            if (offerIdx >= offers.size()) break;
            OfferData offer = offers.get(offerIdx);
            int ry = panelY + ROW_AREA_START + slot * ROW_HEIGHT;

            // EditBox backgrounds
            boolean i1ok = offer.input1Text.isEmpty() || offer.input1Valid();
            boolean outOk = offer.outputText.isEmpty() || offer.outputValid();
            NPCMenuScreen.drawEditBoxBg(g, cx - 2,             ry - 2, EDIT_W + 4, 20, i1ok);
            NPCMenuScreen.drawEditBoxBg(g, cx + COL2_OFF - 2,  ry - 2, EDIT_W + 4, 20, true);
            NPCMenuScreen.drawEditBoxBg(g, cx + COL3_OFF - 2,  ry - 2, EDIT_W + 4, 20, outOk);

            // Separator chars
            g.drawString(this.font, "+",
                    cx + EDIT_W + 3, ry + 4, NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);
            g.drawString(this.font, "\u2192",
                    cx + COL3_OFF - 9, ry + 4, NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);

            // Line 2: "Лимит:" label + limit EditBox bg
            int ry2 = ry + 22;
            String limitLabel = Component.translatable("gui.questnpc.trading.limit").getString() + ":";
            g.drawString(this.font, limitLabel, cx, ry2 + 1,
                    NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);
            NPCMenuScreen.drawEditBoxBg(g, cx + 42, ry2 - 2, LIMIT_W + 4, 18, true);

            // "0=∞" hint to right of limit box
            if (offer.getMaxUses() == 0) {
                g.drawString(this.font,
                        Component.translatable("gui.questnpc.trading.unlimited").getString(),
                        cx + 42 + LIMIT_W + 6, ry2 + 1,
                        NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);
            }

            // Row divider (except after last visible offer)
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
            String scrollInfo = (scrollOffset + 1) + "–" + end + " / " + offers.size();
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

        // 7. Widgets on top
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
