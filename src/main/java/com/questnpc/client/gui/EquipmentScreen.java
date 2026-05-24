package com.questnpc.client.gui;

import com.questnpc.client.gui.widget.DarkButton;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.UpdateEquipmentPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Подменю «Экипировка» (v2.8.0).
 * 4 слота брони (HEAD/CHEST/LEGS/FEET) — клик по слоту открывает {@link ItemCatalogScreen}
 * с фильтром по нужному типу брони. Реальное хранилище — в кастомном NBT-ключе Equipment
 * сущности (см. {@link QuestNPCEntity#setQuestNPCEquipment}). Vanilla armor рендер не задействован.
 */
public class EquipmentScreen extends Screen {

    private static final int PANEL_WIDTH  = 320;
    private static final int PANEL_HEIGHT = 260;
    private static final int PADDING      = 12;

    private static final String[] SLOT_KEYS = {
            "gui.questnpc.equipment.slot.head",
            "gui.questnpc.equipment.slot.chest",
            "gui.questnpc.equipment.slot.legs",
            "gui.questnpc.equipment.slot.feet"
    };
    private static final EquipmentSlot[] VANILLA_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final QuestNPCEntity npc;
    private final Screen parentScreen;
    private final ItemStack[] pending;

    private int panelX, panelY;

    public EquipmentScreen(QuestNPCEntity npc, ItemStack[] initial, Screen parent) {
        super(Component.translatable("gui.questnpc.equipment.title"));
        this.npc = npc;
        this.parentScreen = parent;
        // Защитная копия — не мутируем массив из NPCMenuScreen напрямую.
        this.pending = new ItemStack[QuestNPCEntity.EQUIPMENT_SLOTS];
        for (int i = 0; i < QuestNPCEntity.EQUIPMENT_SLOTS; i++) {
            this.pending[i] = (initial != null && i < initial.length && initial[i] != null)
                    ? initial[i].copy() : ItemStack.EMPTY;
        }
    }

    @Override
    protected void init() {
        super.init();

        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;

        int contentX = panelX + PADDING;
        int contentW = PANEL_WIDTH - PADDING * 2;

        // ── Кнопка «Назад» ─────────────────────────────
        this.addRenderableWidget(new DarkButton(
                contentX, panelY + 30, 60, 18,
                Component.translatable("gui.questnpc.menu.btn.back"),
                button -> Minecraft.getInstance().setScreen(parentScreen)
        ));

        // ── 4 слотовых строки ──────────────────────────
        int rowH = 32;
        int rowsStartY = panelY + 56;
        int clearBtnW = 56;
        int slotBtnW = contentW - clearBtnW - 4;

        for (int i = 0; i < QuestNPCEntity.EQUIPMENT_SLOTS; i++) {
            final int slotIdx = i;
            int rowY = rowsStartY + i * (rowH + 4);

            // Большая кнопка на ширину строки минус Clear — открывает каталог.
            this.addRenderableWidget(new DarkButton(
                    contentX, rowY, slotBtnW, rowH,
                    Component.literal(""),
                    button -> openCatalog(slotIdx)
            ));

            // Маленькая «Очистить»
            this.addRenderableWidget(new DarkButton(
                    contentX + slotBtnW + 4, rowY, clearBtnW, rowH,
                    Component.translatable("gui.questnpc.equipment.clear"),
                    button -> pending[slotIdx] = ItemStack.EMPTY,
                    NPCMenuScreen.BTN_GRAY_BG, 0xFF7F1D1D, NPCMenuScreen.TEXT_WHITE
            ));
        }

        // ── Кнопки внизу: Cancel + Apply ───────────────
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
                button -> applyEquipment(),
                NPCMenuScreen.BTN_GREEN_BG, NPCMenuScreen.BTN_GREEN_HOVER, 0xFFFFFFFF
        ));
    }

    private void openCatalog(int slotIdx) {
        EquipmentSlot vanillaSlot = VANILLA_SLOTS[slotIdx];
        Item current = pending[slotIdx].isEmpty() ? null : pending[slotIdx].getItem();
        java.util.function.Predicate<Item> filter = item ->
                item instanceof ArmorItem a && a.getEquipmentSlot() == vanillaSlot;

        Minecraft.getInstance().setScreen(new ItemCatalogScreen(
                this, current, filter, selected -> {
                    pending[slotIdx] = (selected == null) ? ItemStack.EMPTY : new ItemStack(selected);
                }
        ));
    }

    private void applyEquipment() {
        ModNetwork.INSTANCE.sendToServer(new UpdateEquipmentPacket(npc.getId(), pending));
        Minecraft.getInstance().setScreen(parentScreen);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, NPCMenuScreen.BG_OVERLAY);
        NPCMenuScreen.drawPanel(g, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);

        // Заголовок
        g.drawCenteredString(this.font, this.title, panelX + PANEL_WIDTH / 2, panelY + 8,
                NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF);
        g.fill(panelX + 1, panelY + 22, panelX + PANEL_WIDTH - 1, panelY + 23, NPCMenuScreen.BORDER);

        super.render(g, mouseX, mouseY, partialTick);

        // ── Рисуем содержимое слотов поверх кнопок ─────
        int contentX = panelX + PADDING;
        int contentW = PANEL_WIDTH - PADDING * 2;
        int rowH = 32;
        int rowsStartY = panelY + 56;
        int clearBtnW = 56;
        int slotBtnW = contentW - clearBtnW - 4;

        for (int i = 0; i < QuestNPCEntity.EQUIPMENT_SLOTS; i++) {
            int rowY = rowsStartY + i * (rowH + 4);
            ItemStack stack = pending[i];

            // Иконка (или плейсхолдер)
            int iconX = contentX + 6;
            int iconY = rowY + (rowH - 16) / 2;
            if (!stack.isEmpty()) {
                g.renderItem(stack, iconX, iconY);
            } else {
                NPCMenuScreen.drawOutlineRect(g, iconX, iconY, 16, 16, NPCMenuScreen.BORDER);
            }

            // Название слота
            String slotName = Component.translatable(SLOT_KEYS[i]).getString();
            g.drawString(this.font, slotName, iconX + 22, rowY + 6,
                    NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF, false);

            // Имя предмета или плейсхолдер «— пусто —»
            String itemLabel = stack.isEmpty()
                    ? Component.translatable("gui.questnpc.equipment.slot.empty").getString()
                    : stack.getHoverName().getString();
            g.drawString(this.font, itemLabel, iconX + 22, rowY + rowH - 12,
                    stack.isEmpty()
                            ? NPCMenuScreen.TEXT_DARK_GRAY & 0x00FFFFFF
                            : NPCMenuScreen.TEXT_CYAN & 0x00FFFFFF,
                    false);
        }

        // Hint
        Component hint = Component.translatable("gui.questnpc.equipment.hint");
        g.drawString(this.font, hint, contentX, panelY + PANEL_HEIGHT - 54,
                NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);

        // Футер
        Component footer = Component.translatable("gui.questnpc.menu.footer");
        g.drawCenteredString(this.font, footer, panelX + PANEL_WIDTH / 2, panelY + PANEL_HEIGHT - 12,
                NPCMenuScreen.TEXT_DARK_GRAY & 0x00FFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
