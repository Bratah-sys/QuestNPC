package com.questnpc.client.gui;

import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.OpenNPCQuestsPacket;
import com.questnpc.network.OpenPlayerQuestListPacket;
import com.questnpc.network.QuestSnapshots.ObjectiveProgressSnapshot;
import com.questnpc.network.QuestSnapshots.QuestProgressSnapshot;
import com.questnpc.network.QuestSnapshots.QuestSnapshot;
import com.questnpc.network.RequestQuestAbandonPacket;
import com.questnpc.network.RequestQuestAcceptPacket;
import com.questnpc.network.RequestQuestTurnInPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stage 5 (v2.9.4): player-side экран квестов NPC.
 * Открывается S→C пакетом {@link OpenPlayerQuestListPacket} после клика «Квесты» в
 * хабе {@link NPCInteractionScreen}.
 *
 * <p>3 вкладки: Доступные / Активные / Сдать. Действия:
 * <ul>
 *   <li>Принять → {@link RequestQuestAcceptPacket}, экран закрывается (re-open чтобы увидеть актуальный список)</li>
 *   <li>Сдать → {@link RequestQuestTurnInPacket}, экран закрывается</li>
 *   <li>Отказаться → double-click confirm → {@link RequestQuestAbandonPacket} → re-request snapshot</li>
 *   <li>← Назад → возврат в {@link NPCInteractionScreen}</li>
 * </ul>
 */
public class PlayerQuestScreen extends Screen {

    // Цветовая палитра — заимствована из NPCMenuScreen для консистентности.
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

    private static final int PANEL_WIDTH  = 380;
    private static final int PANEL_HEIGHT = 316;
    private static final int PADDING      = 8;
    private static final int TAB_HEIGHT   = 22;
    private static final int CARD_HEIGHT  = 86;
    private static final int CARD_SPACING = 6;
    private static final int VISIBLE_CARDS = 3; // ≤3 карточек влезает без скролла

    // Tab indices.
    private static final int TAB_OFFERABLE = 0;
    private static final int TAB_ACTIVE = 1;
    private static final int TAB_TURN_IN = 2;

    // ═══ Снимок данных ═══
    private final int npcEntityId;
    private final String npcDisplayName;
    private final List<QuestSnapshot> offerable;
    private final List<QuestProgressSnapshot> active;
    private final List<QuestSnapshot> turnInReady;

    // ═══ Состояние ═══
    private int activeTab = TAB_OFFERABLE;
    /** Индекс карточки в текущем списке, ожидающей подтверждения отказа. -1 = нет. */
    private int abandonConfirmIdx = -1;
    private int scrollOffset = 0;
    private int panelX, panelY;

    public PlayerQuestScreen(OpenPlayerQuestListPacket payload) {
        super(Component.translatable("gui.questnpc.player_quest.title", payload.getNpcDisplayName()));
        this.npcEntityId = payload.getNpcEntityId();
        this.npcDisplayName = payload.getNpcDisplayName();
        this.offerable = payload.getOfferable() != null ? payload.getOfferable() : Collections.emptyList();
        this.active = payload.getActive() != null ? payload.getActive() : Collections.emptyList();
        this.turnInReady = payload.getTurnInReady() != null ? payload.getTurnInReady() : Collections.emptyList();
        // Авто-старт на вкладке «Сдать», если есть готовые — самый частый UX-кейс.
        if (!this.turnInReady.isEmpty()) this.activeTab = TAB_TURN_IN;
        else if (this.offerable.isEmpty() && !this.active.isEmpty()) this.activeTab = TAB_ACTIVE;
    }

    @Override
    protected void init() {
        super.init();
        this.panelX = (this.width - PANEL_WIDTH) / 2;
        this.panelY = (this.height - PANEL_HEIGHT) / 2;
        this.abandonConfirmIdx = -1;
        this.scrollOffset = 0;

        // ─── Tabs (3 кнопки в ряд) ───
        int tabsY = panelY + PADDING + 14; // ниже header
        int tabW = (PANEL_WIDTH - PADDING * 2) / 3;
        addTabButton(panelX + PADDING, tabsY, tabW - 2, TAB_OFFERABLE,
                Component.translatable("gui.questnpc.player_quest.tab.offerable", offerable.size()), false);
        addTabButton(panelX + PADDING + tabW, tabsY, tabW - 2, TAB_ACTIVE,
                Component.translatable("gui.questnpc.player_quest.tab.active", active.size()), false);
        addTabButton(panelX + PADDING + tabW * 2, tabsY, tabW - 2, TAB_TURN_IN,
                Component.translatable("gui.questnpc.player_quest.tab.turn_in", turnInReady.size()),
                !turnInReady.isEmpty()); // gold highlight

        // ─── Карточки контента ───
        rebuildCardButtons();

        // ─── Низ: «← Назад» ───
        Button backBtn = Button.builder(
                Component.translatable("gui.questnpc.player_quest.back"),
                b -> Minecraft.getInstance().setScreen(buildBackScreen()))
                .bounds(panelX + PADDING, panelY + PANEL_HEIGHT - PADDING - 20,
                        80, 20)
                .build();
        addRenderableWidget(backBtn);
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
        if (highlight) {
            b.setMessage(label.copy().withStyle(ChatFormatting.GOLD));
        } else if (this.activeTab == tabIdx) {
            b.setMessage(label.copy().withStyle(ChatFormatting.AQUA));
        }
        addRenderableWidget(b);
    }

    private void rebuild() {
        this.clearWidgets();
        init();
    }

    private void rebuildCardButtons() {
        int cardsTop = panelY + PADDING + 14 + TAB_HEIGHT + 6;

        List<?> currentList = getCurrentList();
        if (currentList == null || currentList.isEmpty()) return;

        int visibleCount = Math.min(VISIBLE_CARDS, currentList.size() - scrollOffset);
        for (int i = 0; i < visibleCount; i++) {
            int dataIdx = scrollOffset + i;
            int cardY = cardsTop + i * (CARD_HEIGHT + CARD_SPACING);

            switch (activeTab) {
                case TAB_OFFERABLE -> {
                    QuestSnapshot q = offerable.get(dataIdx);
                    addAcceptButton(panelX + PANEL_WIDTH - PADDING - 90, cardY + CARD_HEIGHT - 22, q);
                }
                case TAB_ACTIVE -> {
                    QuestProgressSnapshot q = active.get(dataIdx);
                    addAbandonButton(panelX + PANEL_WIDTH - PADDING - 90, cardY + CARD_HEIGHT - 22, q, dataIdx);
                }
                case TAB_TURN_IN -> {
                    QuestSnapshot q = turnInReady.get(dataIdx);
                    addTurnInButton(panelX + PANEL_WIDTH - PADDING - 90, cardY + CARD_HEIGHT - 22, q);
                }
            }
        }
    }

    private List<?> getCurrentList() {
        return switch (activeTab) {
            case TAB_OFFERABLE -> offerable;
            case TAB_ACTIVE -> active;
            case TAB_TURN_IN -> turnInReady;
            default -> Collections.emptyList();
        };
    }

    private void addAcceptButton(int x, int y, QuestSnapshot q) {
        Button b = Button.builder(
                Component.translatable("gui.questnpc.player_quest.accept"),
                btn -> {
                    ModNetwork.INSTANCE.sendToServer(new RequestQuestAcceptPacket(npcEntityId, q.questId()));
                    this.onClose();
                })
                .bounds(x, y, 84, 18).build();
        addRenderableWidget(b);
    }

    private void addTurnInButton(int x, int y, QuestSnapshot q) {
        Button b = Button.builder(
                Component.translatable("gui.questnpc.player_quest.turn_in")
                        .copy().withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                btn -> {
                    ModNetwork.INSTANCE.sendToServer(new RequestQuestTurnInPacket(npcEntityId, q.questId()));
                    this.onClose();
                })
                .bounds(x, y, 84, 18).build();
        addRenderableWidget(b);
    }

    private void addAbandonButton(int x, int y, QuestProgressSnapshot q, int dataIdx) {
        Component label = (abandonConfirmIdx == dataIdx)
                ? Component.translatable("gui.questnpc.player_quest.abandon.confirm")
                        .copy().withStyle(ChatFormatting.RED)
                : Component.translatable("gui.questnpc.player_quest.abandon");
        Button b = Button.builder(label, btn -> {
            if (abandonConfirmIdx == dataIdx) {
                ModNetwork.INSTANCE.sendToServer(new RequestQuestAbandonPacket(npcEntityId, q.questId()));
                // После отказа — попросим сервер прислать свежий список
                ModNetwork.INSTANCE.sendToServer(new OpenNPCQuestsPacket(npcEntityId));
                this.onClose();
            } else {
                this.abandonConfirmIdx = dataIdx;
                this.rebuild();
            }
        }).bounds(x, y, 84, 18).build();
        addRenderableWidget(b);
    }

    /** Возвращает в хаб Данила. Если NPC уже невалиден (despawned) — просто закрывает экран. */
    private Screen buildBackScreen() {
        if (Minecraft.getInstance().level == null) return null;
        Entity e = Minecraft.getInstance().level.getEntity(npcEntityId);
        if (e instanceof QuestNPCEntity npc) {
            return new NPCInteractionScreen(npc);
        }
        return null; // null закрывает screen
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Затемнение фона.
        g.fill(0, 0, this.width, this.height, BG_OVERLAY);

        // Панель.
        g.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, BG_DARK);
        NPCMenuScreen.drawOutlineRect(g, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, BORDER);

        // Header (по центру).
        Component title = Component.translatable("gui.questnpc.player_quest.title", npcDisplayName);
        g.drawCenteredString(this.font, title, panelX + PANEL_WIDTH / 2, panelY + PADDING, TEXT_CYAN);

        // Карточки контента.
        renderCards(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderCards(GuiGraphics g, int mouseX, int mouseY) {
        int cardsTop = panelY + PADDING + 14 + TAB_HEIGHT + 6;

        List<?> currentList = getCurrentList();
        if (currentList == null || currentList.isEmpty()) {
            Component empty = Component.translatable(switch (activeTab) {
                case TAB_OFFERABLE -> "gui.questnpc.player_quest.no_offerable";
                case TAB_ACTIVE -> "gui.questnpc.player_quest.no_active";
                case TAB_TURN_IN -> "gui.questnpc.player_quest.no_turn_in";
                default -> "gui.questnpc.player_quest.no_offerable";
            });
            g.drawCenteredString(this.font, empty,
                    panelX + PANEL_WIDTH / 2, cardsTop + 40, TEXT_GRAY);
            return;
        }

        int visibleCount = Math.min(VISIBLE_CARDS, currentList.size() - scrollOffset);
        for (int i = 0; i < visibleCount; i++) {
            int dataIdx = scrollOffset + i;
            int cardY = cardsTop + i * (CARD_HEIGHT + CARD_SPACING);
            int cardX = panelX + PADDING;
            int cardW = PANEL_WIDTH - PADDING * 2;

            // Фон карточки.
            g.fill(cardX, cardY, cardX + cardW, cardY + CARD_HEIGHT, SECTION_BG);
            NPCMenuScreen.drawOutlineRect(g, cardX, cardY, cardW, CARD_HEIGHT, BORDER);

            switch (activeTab) {
                case TAB_OFFERABLE -> renderOfferableCard(g, cardX, cardY, cardW, offerable.get(dataIdx));
                case TAB_ACTIVE -> renderActiveCard(g, cardX, cardY, cardW, active.get(dataIdx));
                case TAB_TURN_IN -> renderTurnInCard(g, cardX, cardY, cardW, turnInReady.get(dataIdx));
            }
        }

        // Indicator if скролл нужен.
        if (currentList.size() > VISIBLE_CARDS) {
            String more = "▼ ещё " + (currentList.size() - scrollOffset - VISIBLE_CARDS);
            if (currentList.size() - scrollOffset - VISIBLE_CARDS > 0) {
                g.drawString(this.font, more, panelX + PANEL_WIDTH / 2 - 30,
                        cardsTop + VISIBLE_CARDS * (CARD_HEIGHT + CARD_SPACING) - 2, TEXT_GRAY);
            }
        }
    }

    private void renderOfferableCard(GuiGraphics g, int x, int y, int w, QuestSnapshot q) {
        int textX = x + 4;
        g.drawString(this.font, q.title(), textX, y + 3, TEXT_WHITE);
        if (!q.description().isEmpty()) {
            String desc = q.description();
            if (desc.length() > 50) desc = desc.substring(0, 50) + "…";
            g.drawString(this.font, desc, textX, y + 14, TEXT_GRAY);
        }

        // Objectives (до 3 строк).
        int oy = y + 26;
        g.drawString(this.font, Component.translatable("gui.questnpc.player_quest.objectives_label"),
                textX, oy, SECTION_TITLE);
        oy += 10;
        int shown = 0;
        for (String s : q.objectiveDescriptions()) {
            if (shown >= 2) { g.drawString(this.font, "…", textX, oy, TEXT_GRAY); break; }
            g.drawString(this.font, "• " + s, textX, oy, TEXT_WHITE);
            oy += 10;
            shown++;
        }

        // Rewards (мелким шрифтом справа).
        if (!q.rewardDescriptions().isEmpty()) {
            int rx = x + w / 2;
            g.drawString(this.font, Component.translatable("gui.questnpc.player_quest.rewards_label"),
                    rx, y + 26, TEXT_GOLD);
            int ry = y + 36;
            int shownR = 0;
            for (String r : q.rewardDescriptions()) {
                if (shownR >= 3) { g.drawString(this.font, "…", rx, ry, TEXT_GRAY); break; }
                g.drawString(this.font, "+ " + r, rx, ry, TEXT_GOLD);
                ry += 10;
                shownR++;
            }
        }
    }

    private void renderActiveCard(GuiGraphics g, int x, int y, int w, QuestProgressSnapshot q) {
        int textX = x + 4;
        g.drawString(this.font, q.title(), textX, y + 3, TEXT_WHITE);
        if (!q.description().isEmpty()) {
            String desc = q.description();
            if (desc.length() > 50) desc = desc.substring(0, 50) + "…";
            g.drawString(this.font, desc, textX, y + 14, TEXT_GRAY);
        }

        // Objectives с прогрессом.
        int oy = y + 26;
        g.drawString(this.font, Component.translatable("gui.questnpc.player_quest.objectives_label"),
                textX, oy, SECTION_TITLE);
        oy += 10;
        int shown = 0;
        for (ObjectiveProgressSnapshot obj : q.objectives()) {
            if (shown >= 3) { g.drawString(this.font, "…", textX, oy, TEXT_GRAY); break; }
            String line = "• " + obj.description() + " " + obj.current() + "/" + obj.max();
            int color = (obj.current() >= obj.max()) ? TEXT_GREEN : TEXT_WHITE;
            g.drawString(this.font, line, textX, oy, color);
            oy += 10;
            shown++;
        }
    }

    private void renderTurnInCard(GuiGraphics g, int x, int y, int w, QuestSnapshot q) {
        int textX = x + 4;
        g.drawString(this.font, q.title(), textX, y + 3, TEXT_GOLD);
        if (!q.description().isEmpty()) {
            String desc = q.description();
            if (desc.length() > 50) desc = desc.substring(0, 50) + "…";
            g.drawString(this.font, desc, textX, y + 14, TEXT_GRAY);
        }

        int oy = y + 26;
        g.drawString(this.font, Component.translatable("gui.questnpc.player_quest.rewards_label"),
                textX, oy, TEXT_GOLD);
        oy += 10;
        int shown = 0;
        List<String> rewards = q.rewardDescriptions();
        if (rewards.isEmpty()) rewards = new ArrayList<>(); // безопасность
        for (String r : rewards) {
            if (shown >= 4) { g.drawString(this.font, "…", textX, oy, TEXT_GRAY); break; }
            g.drawString(this.font, "+ " + r, textX, oy, TEXT_GOLD);
            oy += 10;
            shown++;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        List<?> currentList = getCurrentList();
        int max = (currentList != null) ? Math.max(0, currentList.size() - VISIBLE_CARDS) : 0;
        if (delta < 0 && scrollOffset < max) {
            scrollOffset++;
            this.rebuild();
            return true;
        }
        if (delta > 0 && scrollOffset > 0) {
            scrollOffset--;
            this.rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
