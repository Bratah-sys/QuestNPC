package com.questnpc.client.gui;

import com.questnpc.client.gui.widget.DarkButton;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.schedule.ScheduleEntry;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.RequestPatrolBrushPacket;
import com.questnpc.network.UpdateSchedulePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Административный экран расписания NPC — настройка слотов активности по игровому времени суток.
 *
 * <p>Каждый слот привязан к диапазону [fromTick, toTick) на 24000-тиковом круге.
 * Поддерживает три типа: ACTIVITY, TRADE, ANIMATION.
 *
 * <p>WIP: движение «патруль», взаимодействия «диалог»/«квест», триггер GeckoLib-анимаций
 * пока заглушки — помечены подсказками в UI.
 */
public class ScheduleScreen extends Screen {

    // ─── Layout ──────────────────────────────────────────────────────────
    private static final int PANEL_WIDTH  = 420;
    private static final int PANEL_HEIGHT = 320;
    private static final int PADDING      = 10;

    private static final int TIMELINE_Y_OFF = 32;
    private static final int TIMELINE_H     = 16;
    private static final int LEGEND_Y_OFF   = 56;
    private static final int CARDS_Y_OFF    = 70;
    private static final int CARD_H         = 20;
    private static final int VISIBLE_CARDS  = 4;
    private static final int EDIT_Y_OFF     = 160;

    private static final int COLOR_ACTIVITY    = 0xFFA855F7;
    private static final int COLOR_TRADE       = 0xFFEAB308;
    private static final int COLOR_ANIMATION   = 0xFFFF6B6B;
    private static final int COLOR_TIME_CURSOR = 0xFFFBBF24;

    private static final String[] ANIMATIONS = {"idle", "sit", "wave", "smoke", "work", "sleep"};

    // ─── Data ────────────────────────────────────────────────────────────
    private final QuestNPCEntity npc;
    private final Screen parentScreen;
    private final List<QuestNPCEntity.TradeSet> tradeSets;

    private final List<ScheduleEntry> entries = new ArrayList<>();
    private boolean scheduleEnabled;
    private int selectedSlotIndex = -1;
    private int scrollOffset = 0;
    private boolean suppressResponders = false;

    private int panelX, panelY;

    // ─── Widgets ─────────────────────────────────────────────────────────
    private DarkButton toggleBtn;
    private DarkButton scrollUpBtn;
    private DarkButton scrollDownBtn;
    private DarkButton addBtn;

    private EditBox nameField;
    private DarkButton typeActivityBtn;
    private DarkButton typeTradeBtn;
    private DarkButton typeAnimationBtn;
    private EditBox fromField;
    private EditBox toField;
    private DarkButton movementPointBtn;
    private DarkButton movementPatrolBtn;
    private DarkButton movementBrushBtn;
    private EditBox posXField;
    private EditBox posYField;
    private EditBox posZField;
    private DarkButton useMyPosBtn;
    private DarkButton tradeSetCyclerBtn;
    private DarkButton animationCyclerBtn;
    private DarkButton interactTradeBtn;
    private DarkButton interactTradeSetBtn;
    private DarkButton interactDialogBtn;
    private DarkButton interactQuestBtn;

    // Card hit boxes (rebuilt every render)
    private int[] cardX = new int[0];
    private int[] cardY = new int[0];
    private int[] cardW = new int[0];
    private int[] cardDelX = new int[0];

    public ScheduleScreen(QuestNPCEntity npc,
                          List<QuestNPCEntity.TradeSet> tradeSets,
                          boolean scheduleEnabled,
                          List<CompoundTag> schedule,
                          Screen parent) {
        super(Component.translatable("gui.questnpc.schedule.title"));
        this.npc = npc;
        this.parentScreen = parent;
        this.tradeSets = tradeSets != null ? tradeSets : new ArrayList<>();
        this.scheduleEnabled = scheduleEnabled;
        if (schedule != null) {
            for (CompoundTag tag : schedule) {
                if (tag != null) this.entries.add(ScheduleEntry.load(tag));
            }
        }
    }

    @Override
    protected void init() {
        super.init();

        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;

        int contentX = panelX + PADDING;
        int contentW = PANEL_WIDTH - PADDING * 2;

        // Back
        this.addRenderableWidget(new DarkButton(
                contentX, panelY + 8, 60, 16,
                Component.translatable("gui.questnpc.menu.btn.back"),
                b -> Minecraft.getInstance().setScreen(parentScreen)
        ));

        // Enable toggle (top-right)
        toggleBtn = this.addRenderableWidget(new DarkButton(
                panelX + PANEL_WIDTH - PADDING - 140, panelY + 8, 140, 16,
                enableLabel(),
                b -> {
                    scheduleEnabled = !scheduleEnabled;
                    toggleBtn.setMessage(enableLabel());
                }
        ));

        // Cards scroll column
        int cardsAreaY = panelY + CARDS_Y_OFF;
        int cardsAreaW = contentW - 26;
        scrollUpBtn = this.addRenderableWidget(new DarkButton(
                contentX + cardsAreaW + 4, cardsAreaY, 20, 18,
                Component.literal("\u25B2"),
                b -> { if (scrollOffset > 0) scrollOffset--; }
        ));
        scrollDownBtn = this.addRenderableWidget(new DarkButton(
                contentX + cardsAreaW + 4, cardsAreaY + 22, 20, 18,
                Component.literal("\u25BC"),
                b -> {
                    int max = Math.max(0, entries.size() - VISIBLE_CARDS);
                    if (scrollOffset < max) scrollOffset++;
                }
        ));
        addBtn = this.addRenderableWidget(new DarkButton(
                contentX + cardsAreaW + 4, cardsAreaY + 44, 20, 18,
                Component.literal("+"),
                b -> addEntry()
        ));

        // Edit panel
        int editY = panelY + EDIT_Y_OFF;
        initEditPanel(contentX, editY);

        // Cancel / Apply at bottom
        int btnY = panelY + PANEL_HEIGHT - 26;
        int btnW = (contentW - 8) / 2;
        this.addRenderableWidget(new DarkButton(
                contentX, btnY, btnW, 20,
                Component.translatable("gui.questnpc.npc_menu.cancel"),
                b -> Minecraft.getInstance().setScreen(parentScreen),
                NPCMenuScreen.BTN_GRAY_BG, NPCMenuScreen.BTN_GRAY_HOVER, NPCMenuScreen.TEXT_WHITE
        ));
        this.addRenderableWidget(new DarkButton(
                contentX + btnW + 8, btnY, btnW, 20,
                Component.translatable("gui.questnpc.npc_menu.apply"),
                b -> applySettings(),
                NPCMenuScreen.BTN_GREEN_BG, NPCMenuScreen.BTN_GREEN_HOVER, 0xFFFFFFFF
        ));

        updateEditPanelFromSelected();
    }

    private void initEditPanel(int x, int y) {
        // Row 1: Name + Type segmented
        nameField = new EditBox(this.font, x + 32, y, 120, 14, Component.literal(""));
        nameField.setMaxLength(32);
        nameField.setBordered(false);
        nameField.setResponder(s -> {
            if (suppressResponders) return;
            ScheduleEntry e = currentEntry();
            if (e != null) e.name = s;
        });
        this.addRenderableWidget(nameField);

        int typeX = x + 160;
        int typeW = 66;
        typeActivityBtn = this.addRenderableWidget(new DarkButton(
                typeX, y, typeW, 14,
                Component.translatable("gui.questnpc.schedule.type.activity"),
                b -> setType(ScheduleEntry.Type.ACTIVITY)
        ));
        typeTradeBtn = this.addRenderableWidget(new DarkButton(
                typeX + typeW + 2, y, typeW, 14,
                Component.translatable("gui.questnpc.schedule.type.trade"),
                b -> setType(ScheduleEntry.Type.TRADE)
        ));
        typeAnimationBtn = this.addRenderableWidget(new DarkButton(
                typeX + (typeW + 2) * 2, y, typeW, 14,
                Component.translatable("gui.questnpc.schedule.type.animation"),
                b -> setType(ScheduleEntry.Type.ANIMATION)
        ));

        // Row 2: Time
        int timeY = y + 20;
        fromField = new EditBox(this.font, x + 32, timeY, 40, 14, Component.literal(""));
        fromField.setMaxLength(5);
        fromField.setBordered(false);
        fromField.setResponder(s -> {
            if (suppressResponders) return;
            ScheduleEntry e = currentEntry();
            if (e != null) { Integer t = parseTime(s); if (t != null) e.fromTick = t; }
        });
        this.addRenderableWidget(fromField);
        toField = new EditBox(this.font, x + 96, timeY, 40, 14, Component.literal(""));
        toField.setMaxLength(5);
        toField.setBordered(false);
        toField.setResponder(s -> {
            if (suppressResponders) return;
            ScheduleEntry e = currentEntry();
            if (e != null) { Integer t = parseTime(s); if (t != null) e.toTick = t; }
        });
        this.addRenderableWidget(toField);

        // Row 3: Movement
        int moveY = y + 40;
        movementPointBtn = this.addRenderableWidget(new DarkButton(
                x + 90, moveY, 70, 14,
                Component.translatable("gui.questnpc.schedule.movement.point"),
                b -> setMovement(ScheduleEntry.Movement.POINT)
        ));
        movementPatrolBtn = this.addRenderableWidget(new DarkButton(
                x + 166, moveY, 86, 14,
                Component.translatable("gui.questnpc.schedule.movement.patrol"),
                b -> setMovement(ScheduleEntry.Movement.PATROL)
        ));
        // v2.6.0: кнопка кисти для рисования зоны (видна только при movement=PATROL)
        movementBrushBtn = this.addRenderableWidget(new DarkButton(
                x + 254, moveY, 14, 14,
                Component.literal("\u270E"), // pencil glyph ✎
                b -> openPatrolBrush()
        ));

        // Row 4: Position
        int posY = y + 60;
        posXField = new EditBox(this.font, x + 20, posY, 36, 14, Component.literal(""));
        posXField.setMaxLength(7);
        posXField.setBordered(false);
        posXField.setResponder(s -> updatePosFromFields());
        this.addRenderableWidget(posXField);
        posYField = new EditBox(this.font, x + 66, posY, 36, 14, Component.literal(""));
        posYField.setMaxLength(7);
        posYField.setBordered(false);
        posYField.setResponder(s -> updatePosFromFields());
        this.addRenderableWidget(posYField);
        posZField = new EditBox(this.font, x + 112, posY, 36, 14, Component.literal(""));
        posZField.setMaxLength(7);
        posZField.setBordered(false);
        posZField.setResponder(s -> updatePosFromFields());
        this.addRenderableWidget(posZField);
        useMyPosBtn = this.addRenderableWidget(new DarkButton(
                x + 156, posY, 110, 14,
                Component.translatable("gui.questnpc.schedule.use_my_pos"),
                b -> useMyPosition()
        ));

        // Row 5: Type-dependent widgets
        int cyclerY = y + 80;
        tradeSetCyclerBtn = this.addRenderableWidget(new DarkButton(
                x, cyclerY, 140, 14,
                Component.literal(""),
                b -> cycleTradeSet()
        ));
        animationCyclerBtn = this.addRenderableWidget(new DarkButton(
                x, cyclerY, 140, 14,
                Component.literal(""),
                b -> cycleAnimation()
        ));

        interactTradeBtn = this.addRenderableWidget(new DarkButton(
                x, cyclerY, 100, 14,
                Component.literal(""),
                b -> toggleInteractTrade()
        ));
        interactTradeSetBtn = this.addRenderableWidget(new DarkButton(
                x + 104, cyclerY, 90, 14,
                Component.literal(""),
                b -> cycleInteractTradeSet()
        ));
        interactDialogBtn = this.addRenderableWidget(new DarkButton(
                x + 198, cyclerY, 90, 14,
                Component.translatable("gui.questnpc.schedule.interact.dialog_wip"),
                b -> {}
        ));
        interactDialogBtn.active = false;
        interactQuestBtn = this.addRenderableWidget(new DarkButton(
                x + 292, cyclerY, 90, 14,
                Component.translatable("gui.questnpc.schedule.interact.quest_wip"),
                b -> {}
        ));
        interactQuestBtn.active = false;
    }

    // ─── State helpers ───────────────────────────────────────────────────

    @Nullable
    private ScheduleEntry currentEntry() {
        if (selectedSlotIndex < 0 || selectedSlotIndex >= entries.size()) return null;
        return entries.get(selectedSlotIndex);
    }

    private Component enableLabel() {
        return Component.translatable(scheduleEnabled
                ? "gui.questnpc.schedule.enabled"
                : "gui.questnpc.schedule.disabled");
    }

    private void addEntry() {
        if (entries.size() >= QuestNPCEntity.MAX_SCHEDULE_ENTRIES) return;
        ScheduleEntry e = new ScheduleEntry();
        e.name = "Slot " + (entries.size() + 1);
        entries.add(e);
        selectedSlotIndex = entries.size() - 1;
        int max = Math.max(0, entries.size() - VISIBLE_CARDS);
        if (scrollOffset < max) scrollOffset = max;
        updateEditPanelFromSelected();
    }

    private void deleteEntry(int index) {
        if (index < 0 || index >= entries.size()) return;
        entries.remove(index);
        if (selectedSlotIndex == index) selectedSlotIndex = -1;
        else if (selectedSlotIndex > index) selectedSlotIndex--;
        int max = Math.max(0, entries.size() - VISIBLE_CARDS);
        if (scrollOffset > max) scrollOffset = max;
        updateEditPanelFromSelected();
    }

    private void setType(ScheduleEntry.Type type) {
        ScheduleEntry e = currentEntry();
        if (e == null) return;
        e.type = type;
        if (type == ScheduleEntry.Type.TRADE && (e.tradeSet == null || e.tradeSet.isEmpty()) && !tradeSets.isEmpty()) {
            e.tradeSet = tradeSets.get(0).name;
        }
        if (type == ScheduleEntry.Type.ANIMATION && (e.animation == null || e.animation.isEmpty())) {
            e.animation = ANIMATIONS[0];
        }
        updateEditPanelFromSelected();
    }

    private void setMovement(ScheduleEntry.Movement m) {
        ScheduleEntry e = currentEntry();
        if (e == null) return;
        e.movement = m;
        updateEditPanelFromSelected();
    }

    /**
     * v2.6.0: запрос кисти патруля для текущего слота.
     * Если у слота уже есть зона — показываем ConfirmScreen (Edit / Start new).
     * Иначе — сразу запрос.
     *
     * <p>Важно: Apply должен быть сделан ДО кисти, чтобы слот существовал на сервере.
     * Мы делаем applySettings() здесь же — синхронизирует текущее состояние и сохраняет сессию.
     */
    private void openPatrolBrush() {
        ScheduleEntry e = currentEntry();
        if (e == null) return;
        final int slot = selectedSlotIndex;

        // Сначала применяем текущее состояние расписания, чтобы сервер имел актуальный snapshot
        List<CompoundTag> payload = new ArrayList<>();
        for (ScheduleEntry en : entries) payload.add(en.save());
        ModNetwork.INSTANCE.sendToServer(new UpdateSchedulePacket(npc.getId(), scheduleEnabled, payload));
        if (parentScreen instanceof NPCMenuScreen parentMenu) {
            parentMenu.updateScheduleState(scheduleEnabled, payload);
        }

        if (e.patrolZone.isEmpty()) {
            sendBrushRequest(slot, true);
            return;
        }

        int existingCount = e.patrolZone.size();
        Minecraft.getInstance().setScreen(new ConfirmScreen(
                result -> {
                    // true = Edit existing (startFresh=false); false = Start new (startFresh=true)
                    sendBrushRequest(slot, !result);
                },
                Component.translatable("gui.questnpc.schedule.patrol.prompt.title"),
                Component.translatable("gui.questnpc.schedule.patrol.prompt.message", existingCount),
                Component.translatable("gui.questnpc.schedule.patrol.prompt.edit"),
                Component.translatable("gui.questnpc.schedule.patrol.prompt.new")
        ));
    }

    private void sendBrushRequest(int slot, boolean startFresh) {
        ModNetwork.INSTANCE.sendToServer(new RequestPatrolBrushPacket(npc.getId(), slot, startFresh));
        // Закрываем все GUI — выходим в мир
        Minecraft.getInstance().setScreen(null);
    }

    private void updatePosFromFields() {
        if (suppressResponders) return;
        ScheduleEntry e = currentEntry();
        if (e == null) return;
        String xs = posXField.getValue().trim();
        String ys = posYField.getValue().trim();
        String zs = posZField.getValue().trim();
        if (xs.isEmpty() && ys.isEmpty() && zs.isEmpty()) {
            e.position = null;
            return;
        }
        try {
            int xi = Integer.parseInt(xs);
            int yi = Integer.parseInt(ys);
            int zi = Integer.parseInt(zs);
            e.position = new BlockPos(xi, yi, zi);
        } catch (NumberFormatException ignored) { /* retain previous value */ }
    }

    private void useMyPosition() {
        ScheduleEntry e = currentEntry();
        if (e == null) return;
        if (Minecraft.getInstance().player == null) return;
        e.position = Minecraft.getInstance().player.blockPosition();
        updateEditPanelFromSelected();
    }

    private void cycleTradeSet() {
        ScheduleEntry e = currentEntry();
        if (e == null || tradeSets.isEmpty()) return;
        int idx = indexOfTradeSet(e.tradeSet);
        idx = (idx + 1) % tradeSets.size();
        e.tradeSet = tradeSets.get(idx).name;
        updateEditPanelFromSelected();
    }

    private void cycleInteractTradeSet() {
        ScheduleEntry e = currentEntry();
        if (e == null || tradeSets.isEmpty()) return;
        int idx = indexOfTradeSet(e.interactTradeSet);
        idx = (idx + 1) % tradeSets.size();
        e.interactTradeSet = tradeSets.get(idx).name;
        updateEditPanelFromSelected();
    }

    private int indexOfTradeSet(String name) {
        if (name == null) return -1;
        for (int i = 0; i < tradeSets.size(); i++) {
            if (name.equals(tradeSets.get(i).name)) return i;
        }
        return -1;
    }

    private void cycleAnimation() {
        ScheduleEntry e = currentEntry();
        if (e == null) return;
        int idx = -1;
        for (int i = 0; i < ANIMATIONS.length; i++) {
            if (ANIMATIONS[i].equals(e.animation)) { idx = i; break; }
        }
        idx = (idx + 1) % ANIMATIONS.length;
        e.animation = ANIMATIONS[idx];
        updateEditPanelFromSelected();
    }

    private void toggleInteractTrade() {
        ScheduleEntry e = currentEntry();
        if (e == null) return;
        e.interactTrade = !e.interactTrade;
        if (e.interactTrade && (e.interactTradeSet == null || e.interactTradeSet.isEmpty()) && !tradeSets.isEmpty()) {
            e.interactTradeSet = tradeSets.get(0).name;
        }
        updateEditPanelFromSelected();
    }

    private void updateEditPanelFromSelected() {
        ScheduleEntry e = currentEntry();
        boolean has = e != null;

        // Global visibility
        nameField.visible = has;
        typeActivityBtn.visible = has;
        typeTradeBtn.visible = has;
        typeAnimationBtn.visible = has;
        fromField.visible = has;
        toField.visible = has;
        movementPointBtn.visible = has;
        movementPatrolBtn.visible = has;
        movementBrushBtn.visible = has && currentEntry() != null
                && currentEntry().movement == ScheduleEntry.Movement.PATROL;
        posXField.visible = has;
        posYField.visible = has;
        posZField.visible = has;
        useMyPosBtn.visible = has;

        if (!has) {
            tradeSetCyclerBtn.visible = false;
            animationCyclerBtn.visible = false;
            interactTradeBtn.visible = false;
            interactTradeSetBtn.visible = false;
            interactDialogBtn.visible = false;
            interactQuestBtn.visible = false;
            return;
        }

        suppressResponders = true;
        try {
            nameField.setValue(e.name == null ? "" : e.name);
            fromField.setValue(formatTime(e.fromTick));
            toField.setValue(formatTime(e.toTick));
            posXField.setValue(e.position == null ? "" : String.valueOf(e.position.getX()));
            posYField.setValue(e.position == null ? "" : String.valueOf(e.position.getY()));
            posZField.setValue(e.position == null ? "" : String.valueOf(e.position.getZ()));

            // Type segmented: ● active, ○ inactive
            typeActivityBtn.setMessage(Component.literal(
                    (e.type == ScheduleEntry.Type.ACTIVITY ? "\u25CF " : "\u25CB ")
                            + Component.translatable("gui.questnpc.schedule.type.activity").getString()));
            typeTradeBtn.setMessage(Component.literal(
                    (e.type == ScheduleEntry.Type.TRADE ? "\u25CF " : "\u25CB ")
                            + Component.translatable("gui.questnpc.schedule.type.trade").getString()));
            typeAnimationBtn.setMessage(Component.literal(
                    (e.type == ScheduleEntry.Type.ANIMATION ? "\u25CF " : "\u25CB ")
                            + Component.translatable("gui.questnpc.schedule.type.animation").getString()));

            // Movement segmented
            movementPointBtn.setMessage(Component.literal(
                    (e.movement == ScheduleEntry.Movement.POINT ? "\u25CF " : "\u25CB ")
                            + Component.translatable("gui.questnpc.schedule.movement.point").getString()));
            movementPatrolBtn.setMessage(Component.literal(
                    (e.movement == ScheduleEntry.Movement.PATROL ? "\u25CF " : "\u25CB ")
                            + Component.translatable("gui.questnpc.schedule.movement.patrol").getString()));

            // Cyclers
            String tsLabel = Component.translatable("gui.questnpc.schedule.trade_set").getString()
                    + ": " + (e.tradeSet == null || e.tradeSet.isEmpty() ? "\u2014" : e.tradeSet) + " \u25B6";
            tradeSetCyclerBtn.setMessage(Component.literal(tsLabel));
            String animLabel = Component.translatable("gui.questnpc.schedule.animation").getString()
                    + ": " + (e.animation == null || e.animation.isEmpty() ? ANIMATIONS[0] : e.animation) + " \u25B6";
            animationCyclerBtn.setMessage(Component.literal(animLabel));

            String interactTradeLabel = (e.interactTrade ? "\u2611 " : "\u2610 ")
                    + Component.translatable("gui.questnpc.schedule.interact.trade").getString();
            interactTradeBtn.setMessage(Component.literal(interactTradeLabel));
            String interactSetLabel = (e.interactTradeSet == null || e.interactTradeSet.isEmpty()
                    ? "\u2014" : e.interactTradeSet) + " \u25B6";
            interactTradeSetBtn.setMessage(Component.literal(interactSetLabel));

            // Per-type visibility
            boolean isTrade = e.type == ScheduleEntry.Type.TRADE;
            boolean isAnim = e.type == ScheduleEntry.Type.ANIMATION;
            boolean isActivity = e.type == ScheduleEntry.Type.ACTIVITY;
            tradeSetCyclerBtn.visible = isTrade;
            animationCyclerBtn.visible = isAnim;
            interactTradeBtn.visible = isActivity;
            interactTradeSetBtn.visible = isActivity && e.interactTrade;
            interactDialogBtn.visible = isActivity;
            interactQuestBtn.visible = isActivity;
        } finally {
            suppressResponders = false;
        }
    }

    // ─── Time helpers ────────────────────────────────────────────────────

    /**
     * Преобразует игровой тик суток в строку "HH:MM" по шкале настенных часов.
     * В MC тик 0 == 06:00, 6000 == 12:00, 12000 == 18:00, 18000 == 00:00.
     */
    private static String formatTime(int tick) {
        int t = ((tick % ScheduleEntry.DAY_TICKS) + ScheduleEntry.DAY_TICKS) % ScheduleEntry.DAY_TICKS;
        double totalMinutes = (t / 1000.0 + 6.0) * 60.0;
        int total = ((int) Math.round(totalMinutes)) % (24 * 60);
        if (total < 0) total += 24 * 60;
        int hours = total / 60;
        int minutes = total % 60;
        return String.format("%02d:%02d", hours, minutes);
    }

    @Nullable
    private static Integer parseTime(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        int colon = s.indexOf(':');
        int h, m = 0;
        try {
            if (colon < 0) {
                h = Integer.parseInt(s);
            } else {
                h = Integer.parseInt(s.substring(0, colon));
                String mStr = s.substring(colon + 1);
                if (!mStr.isEmpty()) m = Integer.parseInt(mStr);
            }
        } catch (NumberFormatException ex) { return null; }
        if (h < 0 || h > 24 || m < 0 || m >= 60) return null;
        double hours = h + m / 60.0;
        // Wall clock → game tick: 06:00 = tick 0
        double shifted = ((hours - 6.0) % 24.0 + 24.0) % 24.0;
        int tick = (int) Math.round(shifted * 1000.0);
        if (tick >= ScheduleEntry.DAY_TICKS) tick = ScheduleEntry.DAY_TICKS - 1;
        return tick;
    }

    private boolean validTime(EditBox field) {
        return parseTime(field.getValue()) != null;
    }

    // ─── Apply ───────────────────────────────────────────────────────────

    private void applySettings() {
        List<CompoundTag> payload = new ArrayList<>();
        for (ScheduleEntry e : entries) payload.add(e.save());
        ModNetwork.INSTANCE.sendToServer(new UpdateSchedulePacket(npc.getId(), scheduleEnabled, payload));

        // Обновляем кэш родительского экрана — иначе повторный вход в Schedule
        // покажет устаревший снапшот из OpenNPCMenuPacket (баг v2.5.0).
        if (parentScreen instanceof NPCMenuScreen parentMenu) {
            parentMenu.updateScheduleState(scheduleEnabled, payload);
        }

        Minecraft.getInstance().setScreen(parentScreen);
    }

    // ─── Rendering ───────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, NPCMenuScreen.BG_OVERLAY);
        NPCMenuScreen.drawPanel(g, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);

        int contentX = panelX + PADDING;
        int contentW = PANEL_WIDTH - PADDING * 2;

        // Title
        g.drawCenteredString(this.font, this.title, panelX + PANEL_WIDTH / 2, panelY + 12,
                NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF);

        // Timeline
        drawTimeline(g, contentX, panelY + TIMELINE_Y_OFF, contentW, TIMELINE_H);

        // Legend
        drawLegend(g, contentX, panelY + LEGEND_Y_OFF);

        // Cards
        int cardsAreaY = panelY + CARDS_Y_OFF;
        int cardsAreaW = contentW - 26;
        drawCards(g, contentX, cardsAreaY, cardsAreaW);

        // Edit panel background/labels
        int editY = panelY + EDIT_Y_OFF;
        if (currentEntry() != null) {
            drawEditLabels(g, contentX, editY);
        } else {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.questnpc.schedule.select_hint"),
                    panelX + PANEL_WIDTH / 2, editY + 30,
                    NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF);
        }

        // Overlap warning
        if (hasAnyOverlap()) {
            g.drawString(this.font,
                    Component.translatable("gui.questnpc.schedule.overlap_warning"),
                    contentX, panelY + PANEL_HEIGHT - 44,
                    NPCMenuScreen.TEXT_RED & 0x00FFFFFF, false);
        }

        super.render(g, mouseX, mouseY, partialTick);

        // v2.6.0: tooltip для кнопки кисти (показываем количество блоков)
        if (movementBrushBtn != null && movementBrushBtn.visible && movementBrushBtn.isHoveredOrFocused()) {
            ScheduleEntry e = currentEntry();
            int count = e != null ? e.patrolZone.size() : 0;
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.questnpc.schedule.patrol.brush.tooltip"));
            lines.add(Component.translatable("gui.questnpc.schedule.patrol.blocks_count", count));
            g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        }

        // Tooltips for WIP buttons
        if (interactDialogBtn.visible && interactDialogBtn.isHoveredOrFocused()) {
            g.renderTooltip(this.font, Component.translatable("gui.questnpc.schedule.dialog_soon"), mouseX, mouseY);
        }
        if (interactQuestBtn.visible && interactQuestBtn.isHoveredOrFocused()) {
            g.renderTooltip(this.font, Component.translatable("gui.questnpc.schedule.quest_soon"), mouseX, mouseY);
        }
    }

    private void drawTimeline(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, NPCMenuScreen.EDIT_BG);
        NPCMenuScreen.drawOutlineRect(g, x, y, w, h, NPCMenuScreen.BORDER);

        for (ScheduleEntry e : entries) {
            if (e.fromTick == e.toTick) continue;
            drawSegment(g, x, y, w, h, e.fromTick, e.toTick, typeColor(e.type));
        }

        // Current time cursor
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            long dt = mc.level.getDayTime() % ScheduleEntry.DAY_TICKS;
            if (dt < 0) dt += ScheduleEntry.DAY_TICKS;
            int cx = x + (int) ((long) w * dt / ScheduleEntry.DAY_TICKS);
            g.fill(cx, y - 2, cx + 1, y + h + 2, COLOR_TIME_CURSOR);
        }

        // Hour marks (wall-clock 06, 12, 18, 00, 06)
        for (int i = 0; i <= 4; i++) {
            int tx = x + (w * i) / 4;
            int mark = (6 + i * 6) % 24;
            String label = String.format("%02d:00", mark);
            int labelW = this.font.width(label);
            g.drawString(this.font, label, tx - labelW / 2, y + h + 2,
                    NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);
        }
    }

    private void drawSegment(GuiGraphics g, int x, int y, int w, int h, int from, int to, int color) {
        int f = ((from % ScheduleEntry.DAY_TICKS) + ScheduleEntry.DAY_TICKS) % ScheduleEntry.DAY_TICKS;
        int t = ((to % ScheduleEntry.DAY_TICKS) + ScheduleEntry.DAY_TICKS) % ScheduleEntry.DAY_TICKS;
        if (f < t) {
            int x0 = x + (int) ((long) w * f / ScheduleEntry.DAY_TICKS);
            int x1 = x + (int) ((long) w * t / ScheduleEntry.DAY_TICKS);
            g.fill(x0, y + 1, x1, y + h - 1, color);
        } else {
            int x0 = x + (int) ((long) w * f / ScheduleEntry.DAY_TICKS);
            g.fill(x0, y + 1, x + w, y + h - 1, color);
            int x1 = x + (int) ((long) w * t / ScheduleEntry.DAY_TICKS);
            g.fill(x, y + 1, x1, y + h - 1, color);
        }
    }

    private void drawLegend(GuiGraphics g, int x, int y) {
        int gray = NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF;
        int white = NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF;
        String legend = Component.translatable("gui.questnpc.schedule.legend").getString() + ":";
        g.drawString(this.font, legend, x, y, gray, false);
        int cx = x + this.font.width(legend) + 6;

        int[] colors = { COLOR_ACTIVITY, COLOR_TRADE, COLOR_ANIMATION };
        String[] keys = {
                "gui.questnpc.schedule.legend.activity",
                "gui.questnpc.schedule.legend.trade",
                "gui.questnpc.schedule.legend.animation"
        };
        for (int i = 0; i < 3; i++) {
            g.fill(cx, y + 1, cx + 8, y + 9, colors[i]);
            cx += 10;
            String lbl = Component.translatable(keys[i]).getString();
            g.drawString(this.font, lbl, cx, y, white, false);
            cx += this.font.width(lbl) + 12;
        }
    }

    private void drawCards(GuiGraphics g, int x, int y, int w) {
        cardX = new int[VISIBLE_CARDS];
        cardY = new int[VISIBLE_CARDS];
        cardW = new int[VISIBLE_CARDS];
        cardDelX = new int[VISIBLE_CARDS];
        for (int i = 0; i < VISIBLE_CARDS; i++) { cardX[i] = -1; cardY[i] = -1; }

        if (entries.isEmpty()) {
            g.drawString(this.font,
                    Component.translatable("gui.questnpc.schedule.no_entries"),
                    x, y + 4, NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);
            return;
        }

        int shown = Math.min(VISIBLE_CARDS, Math.max(0, entries.size() - scrollOffset));
        for (int i = 0; i < shown; i++) {
            int idx = i + scrollOffset;
            ScheduleEntry e = entries.get(idx);
            int cy = y + i * (CARD_H + 2);
            boolean selected = (idx == selectedSlotIndex);
            boolean overlap = hasOverlap(idx);

            int bg = selected ? NPCMenuScreen.BTN_GREEN_BG : NPCMenuScreen.BTN_GRAY_BG;
            g.fill(x, cy, x + w, cy + CARD_H, bg);
            int border = overlap ? NPCMenuScreen.TEXT_RED : NPCMenuScreen.BORDER;
            NPCMenuScreen.drawOutlineRect(g, x, cy, w, CARD_H, border);

            // Color dot
            g.fill(x + 4, cy + 6, x + 12, cy + 14, typeColor(e.type));

            String timeStr = formatTime(e.fromTick) + "-" + formatTime(e.toTick);
            String nameStr = (e.name == null || e.name.isEmpty()) ? "\u2014" : e.name;
            String typeStr = "[" + typeShortName(e.type) + "]";
            String metaStr = typeMeta(e);
            String text = timeStr + "  " + nameStr + "  " + typeStr + "  " + metaStr;
            if (this.font.width(text) > w - 32) {
                text = this.font.plainSubstrByWidth(text, w - 36) + "\u2026";
            }
            g.drawString(this.font, text, x + 16, cy + 6, NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF, false);

            int delW = 12;
            int delX = x + w - delW - 2;
            cardDelX[i] = delX;
            g.drawString(this.font, "\u2715", delX + 2, cy + 6, NPCMenuScreen.TEXT_RED & 0x00FFFFFF, false);

            cardX[i] = x;
            cardY[i] = cy;
            cardW[i] = w;
        }
    }

    private void drawEditLabels(GuiGraphics g, int x, int y) {
        int gray = NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF;

        // Row 1: Name
        g.drawString(this.font, Component.translatable("gui.questnpc.schedule.name"), x, y + 3, gray, false);
        NPCMenuScreen.drawEditBoxBg(g, x + 30, y - 2, 124, 18, true);

        // Row 2: Time range
        int timeY = y + 20;
        g.drawString(this.font, Component.translatable("gui.questnpc.schedule.from"), x, timeY + 3, gray, false);
        NPCMenuScreen.drawEditBoxBg(g, x + 30, timeY - 2, 44, 18, validTime(fromField));
        g.drawString(this.font, Component.translatable("gui.questnpc.schedule.to"), x + 78, timeY + 3, gray, false);
        NPCMenuScreen.drawEditBoxBg(g, x + 94, timeY - 2, 44, 18, validTime(toField));
        g.drawString(this.font, Component.translatable("gui.questnpc.schedule.time_hint"),
                x + 144, timeY + 3, gray, false);

        // Row 3: Movement label
        int moveY = y + 40;
        g.drawString(this.font, Component.translatable("gui.questnpc.schedule.movement"),
                x, moveY + 3, gray, false);

        // Row 4: Position
        int posY = y + 60;
        g.drawString(this.font, Component.translatable("gui.questnpc.schedule.position"),
                x, posY + 3, gray, false);
        NPCMenuScreen.drawEditBoxBg(g, x + 18, posY - 2, 40, 18, true);
        NPCMenuScreen.drawEditBoxBg(g, x + 64, posY - 2, 40, 18, true);
        NPCMenuScreen.drawEditBoxBg(g, x + 110, posY - 2, 40, 18, true);
    }

    // ─── Overlap / meta ──────────────────────────────────────────────────

    private boolean hasAnyOverlap() {
        for (int i = 0; i < entries.size(); i++) {
            if (hasOverlap(i)) return true;
        }
        return false;
    }

    private boolean hasOverlap(int index) {
        ScheduleEntry e = entries.get(index);
        for (int i = 0; i < entries.size(); i++) {
            if (i == index) continue;
            if (ScheduleEntry.overlaps(e, entries.get(i))) return true;
        }
        return false;
    }

    private static int typeColor(ScheduleEntry.Type t) {
        switch (t) {
            case TRADE: return COLOR_TRADE;
            case ANIMATION: return COLOR_ANIMATION;
            case ACTIVITY:
            default: return COLOR_ACTIVITY;
        }
    }

    private static String typeShortName(ScheduleEntry.Type t) {
        switch (t) {
            case TRADE: return Component.translatable("gui.questnpc.schedule.type.trade").getString();
            case ANIMATION: return Component.translatable("gui.questnpc.schedule.type.animation").getString();
            case ACTIVITY:
            default: return Component.translatable("gui.questnpc.schedule.type.activity").getString();
        }
    }

    private String typeMeta(ScheduleEntry e) {
        switch (e.type) {
            case TRADE:
                return e.tradeSet == null || e.tradeSet.isEmpty() ? "\u2014" : e.tradeSet;
            case ANIMATION:
                return e.animation == null || e.animation.isEmpty() ? ANIMATIONS[0] : e.animation;
            case ACTIVITY:
            default:
                if (e.position == null) return "\u2014";
                return "(" + e.position.getX() + "," + e.position.getY() + "," + e.position.getZ() + ")";
        }
    }

    // ─── Mouse handling ──────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Card click handling (before super so delete-× takes precedence)
        for (int i = 0; i < cardX.length; i++) {
            if (cardX[i] < 0) continue;
            int x = cardX[i];
            int y = cardY[i];
            int w = cardW[i];
            if (mx >= x && mx < x + w && my >= y && my < y + CARD_H) {
                int idx = i + scrollOffset;
                if (mx >= cardDelX[i] && mx < cardDelX[i] + 14) {
                    deleteEntry(idx);
                    return true;
                }
                selectedSlotIndex = idx;
                updateEditPanelFromSelected();
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
