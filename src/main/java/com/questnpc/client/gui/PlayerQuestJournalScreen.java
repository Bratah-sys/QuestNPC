package com.questnpc.client.gui;

import com.questnpc.client.ClientJournalCache;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.QuestJournalEntry;
import com.questnpc.network.QuestSnapshots.ObjectiveProgressSnapshot;
import com.questnpc.network.RequestJournalAbandonPacket;
import com.questnpc.network.RequestJournalRefreshPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Stage 8 (v2.9.8): standalone Player Quest Journal экран.
 *
 * <p>Открывается клавишей {@code L} (см. {@link com.questnpc.client.ModKeyBindings#OPEN_JOURNAL}),
 * показывает все active+completed квесты игрока со всех NPC. Данные читает из
 * client-side {@link ClientJournalCache} (обновляется через
 * {@link com.questnpc.network.SyncPlayerQuestProgressPacket} + manual refresh через
 * {@link RequestJournalRefreshPacket}).
 *
 * <p>Layout: 2 вкладки (Активные / Завершённые), scroll-list карточек. Кнопка «Отказаться»
 * на активных карточках — double-click confirm pattern. Кнопка «Обновить» внизу
 * (disabled на 2 сек после клика). Кнопка «Закрыть».
 *
 * <p>{@code isPauseScreen() = true} — игра паузится в single-player при открытии journal'а.
 */
public class PlayerQuestJournalScreen extends Screen {

    // ═══ Палитра (как PlayerQuestScreen) ═══
    private static final int BG_OVERLAY    = NPCMenuScreen.BG_OVERLAY;
    private static final int BG_DARK       = NPCMenuScreen.BG_DARK;
    private static final int SECTION_BG    = NPCMenuScreen.SECTION_BG;
    private static final int BORDER        = NPCMenuScreen.BORDER;
    private static final int TEXT_WHITE    = NPCMenuScreen.TEXT_WHITE;
    private static final int TEXT_GRAY     = NPCMenuScreen.TEXT_GRAY;
    private static final int TEXT_CYAN     = NPCMenuScreen.TEXT_CYAN;
    private static final int SECTION_TITLE = NPCMenuScreen.SECTION_TITLE;
    private static final int TEXT_GOLD     = 0xFFFCD34D;
    private static final int TEXT_GREEN    = 0xFF10B981;
    private static final int TEXT_FADED    = 0xFF6B7280;

    // ═══ Геометрия ═══
    private static final int PANEL_WIDTH  = 380;
    private static final int PANEL_HEIGHT = 316;
    private static final int PADDING      = 8;
    private static final int TAB_HEIGHT   = 22;
    private static final int CARD_HEIGHT  = 90;
    private static final int CARD_SPACING = 6;
    private static final int VISIBLE_CARDS = 3;

    // ═══ Tabs ═══
    private static final int TAB_ACTIVE = 0;
    private static final int TAB_COMPLETED = 1;

    // ═══ Cooldown'ы (ticks @ 20 tps) ═══
    private static final int REFRESH_COOLDOWN_TICKS = 40; // 2 сек
    private static final int ABANDON_COOLDOWN_TICKS = 20; // 1 сек

    // ═══ Format ═══
    private static final SimpleDateFormat COMPLETED_AT_FMT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    // ═══ State ═══
    private int activeTab = TAB_ACTIVE;
    private int scrollOffset = 0;
    private int abandonConfirmIdx = -1; // индекс в текущем active-листе
    private int abandonCooldownTicks = 0;
    private int refreshCooldownTicks = 0;
    private int panelX, panelY;

    public PlayerQuestJournalScreen() {
        super(Component.translatable("gui.questnpc.journal.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.panelX = (this.width - PANEL_WIDTH) / 2;
        this.panelY = (this.height - PANEL_HEIGHT) / 2;

        // ─── Tabs (2 в ряд) ───
        int tabsY = panelY + PADDING + 14;
        int tabW = (PANEL_WIDTH - PADDING * 2) / 2;
        addTabButton(panelX + PADDING, tabsY, tabW - 2, TAB_ACTIVE,
                Component.translatable("gui.questnpc.journal.tab.active",
                        ClientJournalCache.get().activeCount()), false);
        int completedCount = ClientJournalCache.get().completedCount();
        addTabButton(panelX + PADDING + tabW, tabsY, tabW - 2, TAB_COMPLETED,
                Component.translatable("gui.questnpc.journal.tab.completed", completedCount),
                completedCount > 0);

        // ─── Карточки ───
        rebuildCardButtons();

        // ─── Bottom: «Обновить», «Закрыть» ───
        int bottomY = panelY + PANEL_HEIGHT - PADDING - 20;
        Component refreshLbl = (refreshCooldownTicks > 0)
                ? Component.translatable("gui.questnpc.journal.refresh").copy().withStyle(ChatFormatting.DARK_GRAY)
                : Component.translatable("gui.questnpc.journal.refresh");
        Button refreshBtn = Button.builder(refreshLbl, btn -> {
            if (refreshCooldownTicks > 0) return;
            ModNetwork.INSTANCE.sendToServer(new RequestJournalRefreshPacket());
            refreshCooldownTicks = REFRESH_COOLDOWN_TICKS;
            // Reinit чтобы кнопка задизаблилась визуально; данные обновятся через server roundtrip.
            this.rebuild();
        }).bounds(panelX + PADDING, bottomY, 100, 20).build();
        refreshBtn.active = (refreshCooldownTicks == 0);
        addRenderableWidget(refreshBtn);

        Button closeBtn = Button.builder(
                Component.translatable("gui.questnpc.journal.close"),
                btn -> this.onClose())
                .bounds(panelX + PANEL_WIDTH - PADDING - 100, bottomY, 100, 20)
                .build();
        addRenderableWidget(closeBtn);
    }

    private void addTabButton(int x, int y, int w, int tabIdx, Component label, boolean highlight) {
        Button b = Button.builder(label, btn -> {
            if (this.activeTab != tabIdx) {
                this.activeTab = tabIdx;
                this.abandonConfirmIdx = -1;
                this.scrollOffset = 0;
                this.rebuild();
            }
        }).bounds(x, y, w, TAB_HEIGHT - 2).build();
        if (this.activeTab == tabIdx) {
            b.setMessage(label.copy().withStyle(ChatFormatting.AQUA));
        } else if (highlight) {
            b.setMessage(label.copy().withStyle(ChatFormatting.GREEN));
        }
        addRenderableWidget(b);
    }

    private void rebuild() {
        this.clearWidgets();
        init();
    }

    private void rebuildCardButtons() {
        int cardsTop = panelY + PADDING + 14 + TAB_HEIGHT + 6;
        List<QuestJournalEntry> list = getCurrentList();
        if (list.isEmpty()) return;

        int visibleCount = Math.min(VISIBLE_CARDS, list.size() - scrollOffset);
        for (int i = 0; i < visibleCount; i++) {
            int dataIdx = scrollOffset + i;
            int cardY = cardsTop + i * (CARD_HEIGHT + CARD_SPACING);
            QuestJournalEntry entry = list.get(dataIdx);

            if (activeTab == TAB_ACTIVE) {
                addAbandonButton(panelX + PANEL_WIDTH - PADDING - 90, cardY + CARD_HEIGHT - 22, entry, dataIdx);
            }
            // Completed cards — без кнопок.
        }
    }

    private List<QuestJournalEntry> getCurrentList() {
        return switch (activeTab) {
            case TAB_ACTIVE -> ClientJournalCache.get().getActive();
            case TAB_COMPLETED -> ClientJournalCache.get().getCompleted();
            default -> List.of();
        };
    }

    private void addAbandonButton(int x, int y, QuestJournalEntry entry, int dataIdx) {
        boolean inConfirm = (abandonConfirmIdx == dataIdx);
        Component label = inConfirm
                ? Component.translatable("gui.questnpc.journal.abandon.confirm")
                        .copy().withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                : Component.translatable("gui.questnpc.journal.abandon");
        Button b = Button.builder(label, btn -> {
            if (abandonCooldownTicks > 0) return;
            if (inConfirm) {
                ModNetwork.INSTANCE.sendToServer(new RequestJournalAbandonPacket(
                        entry.questKey().npcUuid(), entry.questKey().questId()));
                this.abandonConfirmIdx = -1;
                // Кэш обновится через server-side SyncPlayerQuestProgressPacket; пока reinit'нём,
                // чтобы убрать карточку оптимистично.
                this.abandonCooldownTicks = ABANDON_COOLDOWN_TICKS;
                this.rebuild();
            } else {
                this.abandonConfirmIdx = dataIdx;
                this.abandonCooldownTicks = ABANDON_COOLDOWN_TICKS;
                this.rebuild();
            }
        }).bounds(x, y, 84, 18).build();
        b.active = (abandonCooldownTicks == 0);
        addRenderableWidget(b);
    }

    @Override
    public void tick() {
        super.tick();
        boolean needRebuild = false;
        if (abandonCooldownTicks > 0) {
            abandonCooldownTicks--;
            if (abandonCooldownTicks == 0) needRebuild = true;
        }
        if (refreshCooldownTicks > 0) {
            refreshCooldownTicks--;
            if (refreshCooldownTicks == 0) needRebuild = true;
        }
        if (needRebuild) this.rebuild();
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Затемнение.
        g.fill(0, 0, this.width, this.height, BG_OVERLAY);
        // Панель.
        g.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, BG_DARK);
        NPCMenuScreen.drawOutlineRect(g, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, BORDER);

        // Header.
        g.drawCenteredString(this.font, this.title,
                panelX + PANEL_WIDTH / 2, panelY + PADDING, TEXT_CYAN);

        // Карточки или placeholder.
        if (!ClientJournalCache.get().isInitialised()) {
            renderPlaceholder(g, "gui.questnpc.journal.loading");
        } else {
            renderCards(g, mouseX, mouseY);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderPlaceholder(GuiGraphics g, String langKey) {
        int cardsTop = panelY + PADDING + 14 + TAB_HEIGHT + 6;
        g.drawCenteredString(this.font, Component.translatable(langKey),
                panelX + PANEL_WIDTH / 2, cardsTop + 60, TEXT_GRAY);
    }

    private void renderCards(GuiGraphics g, int mouseX, int mouseY) {
        int cardsTop = panelY + PADDING + 14 + TAB_HEIGHT + 6;
        List<QuestJournalEntry> list = getCurrentList();

        if (list.isEmpty()) {
            String emptyKey = (activeTab == TAB_ACTIVE)
                    ? "gui.questnpc.journal.no_active"
                    : "gui.questnpc.journal.no_completed";
            g.drawCenteredString(this.font, Component.translatable(emptyKey),
                    panelX + PANEL_WIDTH / 2, cardsTop + 60, TEXT_GRAY);
            return;
        }

        int visibleCount = Math.min(VISIBLE_CARDS, list.size() - scrollOffset);
        for (int i = 0; i < visibleCount; i++) {
            int dataIdx = scrollOffset + i;
            int cardY = cardsTop + i * (CARD_HEIGHT + CARD_SPACING);
            int cardX = panelX + PADDING;
            int cardW = PANEL_WIDTH - PADDING * 2;

            g.fill(cardX, cardY, cardX + cardW, cardY + CARD_HEIGHT, SECTION_BG);
            NPCMenuScreen.drawOutlineRect(g, cardX, cardY, cardW, CARD_HEIGHT, BORDER);

            QuestJournalEntry entry = list.get(dataIdx);
            if (entry.completed()) {
                renderCompletedCard(g, cardX, cardY, cardW, entry);
            } else {
                renderActiveCard(g, cardX, cardY, cardW, entry);
            }
        }

        // Scroll-indicator (если есть ещё карточки ниже).
        int hidden = list.size() - scrollOffset - visibleCount;
        if (hidden > 0) {
            String more = "▼ ещё " + hidden;
            g.drawCenteredString(this.font, Component.literal(more),
                    panelX + PANEL_WIDTH / 2,
                    cardsTop + VISIBLE_CARDS * (CARD_HEIGHT + CARD_SPACING) - 4, TEXT_GRAY);
        }
        if (scrollOffset > 0) {
            String above = "▲ ещё " + scrollOffset;
            g.drawCenteredString(this.font, Component.literal(above),
                    panelX + PANEL_WIDTH / 2, cardsTop - 4, TEXT_GRAY);
        }
    }

    private void renderActiveCard(GuiGraphics g, int x, int y, int w, QuestJournalEntry entry) {
        int textX = x + 4;
        // Title.
        g.drawString(this.font, entry.title(), textX, y + 3, TEXT_WHITE);
        // Description (truncated).
        if (!entry.description().isEmpty()) {
            String desc = entry.description();
            if (desc.length() > 56) desc = desc.substring(0, 56) + "…";
            g.drawString(this.font, desc, textX, y + 14, TEXT_GRAY);
        }
        // Source NPC.
        g.drawString(this.font,
                Component.translatable("gui.questnpc.journal.source", entry.npcDisplayName()),
                textX, y + 25, TEXT_FADED);

        // Objectives (с прогрессом, до 3 строк).
        int oy = y + 38;
        g.drawString(this.font, Component.translatable("gui.questnpc.journal.objectives"),
                textX, oy, SECTION_TITLE);
        oy += 10;
        int shown = 0;
        for (ObjectiveProgressSnapshot obj : entry.objectives()) {
            if (shown >= 2) { g.drawString(this.font, "…", textX, oy, TEXT_GRAY); break; }
            String line = "• " + obj.description() + " " + obj.current() + "/" + obj.max();
            int color = (obj.current() >= obj.max()) ? TEXT_GREEN : TEXT_WHITE;
            g.drawString(this.font, line, textX, oy, color);
            oy += 10;
            shown++;
        }

        // Rewards (компактным шрифтом справа).
        if (!entry.rewardDescriptions().isEmpty()) {
            int rx = x + w / 2 + 30;
            g.drawString(this.font, Component.translatable("gui.questnpc.journal.rewards"),
                    rx, y + 38, TEXT_GOLD);
            int ry = y + 48;
            int shownR = 0;
            for (String r : entry.rewardDescriptions()) {
                if (shownR >= 2) { g.drawString(this.font, "…", rx, ry, TEXT_GRAY); break; }
                String line = "+ " + (r.length() > 18 ? r.substring(0, 18) + "…" : r);
                g.drawString(this.font, line, rx, ry, TEXT_GOLD);
                ry += 10;
                shownR++;
            }
        }
    }

    private void renderCompletedCard(GuiGraphics g, int x, int y, int w, QuestJournalEntry entry) {
        int textX = x + 4;
        // Faded title.
        g.drawString(this.font, entry.title(), textX, y + 3, TEXT_FADED);
        // Description (truncated, faded).
        if (!entry.description().isEmpty()) {
            String desc = entry.description();
            if (desc.length() > 56) desc = desc.substring(0, 56) + "…";
            g.drawString(this.font, desc, textX, y + 14, TEXT_GRAY);
        }
        // Source NPC.
        g.drawString(this.font,
                Component.translatable("gui.questnpc.journal.source", entry.npcDisplayName()),
                textX, y + 25, TEXT_FADED);
        // Completed timestamp.
        String dateStr = entry.completedAt() > 0
                ? COMPLETED_AT_FMT.format(new Date(entry.completedAt()))
                : "?";
        g.drawString(this.font,
                Component.translatable("gui.questnpc.journal.completed_at", dateStr),
                textX, y + 38, TEXT_GREEN);
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        List<QuestJournalEntry> list = getCurrentList();
        int max = Math.max(0, list.size() - VISIBLE_CARDS);
        if (delta < 0 && scrollOffset < max) {
            scrollOffset++;
            this.abandonConfirmIdx = -1;
            this.rebuild();
            return true;
        }
        if (delta > 0 && scrollOffset > 0) {
            scrollOffset--;
            this.abandonConfirmIdx = -1;
            this.rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // L снова → закрыть (UX: жмёшь снова — закрывается).
        if (com.questnpc.client.ModKeyBindings.OPEN_JOURNAL.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        // Pause в single-player — как inventory.
        return true;
    }
}
