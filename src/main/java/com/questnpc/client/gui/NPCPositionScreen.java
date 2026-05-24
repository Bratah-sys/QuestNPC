package com.questnpc.client.gui;

import com.questnpc.client.gui.widget.DarkButton;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.RequestPatrolChangePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Подменю "Позиция" — отображение координат точки патруля и кнопка смены.
 * Тёмная тема, кнопка "Назад" возвращает к главному меню.
 */
public class NPCPositionScreen extends Screen {

    private static final int PANEL_WIDTH  = 280;
    private static final int PANEL_HEIGHT = 180;
    private static final int PADDING      = 12;
    private static final int SECTION_PAD  = 8;

    private final QuestNPCEntity npc;
    private final Screen parentScreen;

    private int panelX, panelY;

    public NPCPositionScreen(QuestNPCEntity npc, Screen parent) {
        super(Component.translatable("gui.questnpc.menu.btn.position"));
        this.npc = npc;
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();

        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;

        int contentX = panelX + PADDING;
        int contentW = PANEL_WIDTH - PADDING * 2;

        // ═══ Кнопка "Назад" ═══
        this.addRenderableWidget(new DarkButton(
            contentX, panelY + 30, 60, 18,
            Component.translatable("gui.questnpc.menu.btn.back"),
            button -> Minecraft.getInstance().setScreen(parentScreen)
        ));

        // ═══ Кнопка "Сменить точку патруля" ═══
        // v2.8.0: было +110 — налезало на posInfoY (sectionY+52 = panelY+108) с overflow ~7px.
        int changeBtnY = panelY + 128;
        this.addRenderableWidget(new DarkButton(
            contentX, changeBtnY, contentW, 20,
            Component.translatable("gui.questnpc.npc_menu.change_patrol"),
            button -> {
                ModNetwork.INSTANCE.sendToServer(new RequestPatrolChangePacket(npc.getId()));
                this.onClose();
            },
            NPCMenuScreen.BTN_GREEN_BG, NPCMenuScreen.BTN_GREEN_HOVER, 0xFFFFFFFF
        ));

        // ═══ Кнопка "Отмена" ═══
        this.addRenderableWidget(new DarkButton(
            contentX, panelY + PANEL_HEIGHT - 36, contentW, 20,
            Component.translatable("gui.questnpc.npc_menu.cancel"),
            button -> Minecraft.getInstance().setScreen(parentScreen),
            NPCMenuScreen.BTN_GRAY_BG, NPCMenuScreen.BTN_GRAY_HOVER, NPCMenuScreen.TEXT_WHITE
        ));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, NPCMenuScreen.BG_OVERLAY);

        int contentX = panelX + PADDING;
        int contentW = PANEL_WIDTH - PADDING * 2;

        NPCMenuScreen.drawPanel(g, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);

        // Заголовок
        g.drawCenteredString(this.font, this.title, panelX + PANEL_WIDTH / 2, panelY + 8,
                NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF);
        g.fill(panelX + 1, panelY + 22, panelX + PANEL_WIDTH - 1, panelY + 23, NPCMenuScreen.BORDER);

        // ═══ Секция: Точка патруля ═══
        int sectionY = panelY + 56;
        NPCMenuScreen.drawSection(g, this.font, contentX, sectionY, contentW, 48,
                Component.translatable("gui.questnpc.npc_menu.patrol_point").getString());

        // Координаты
        BlockPos bound = npc.getBoundBlockPos();
        int coordY = sectionY + 28;
        if (bound != null) {
            String coordText = "X: " + bound.getX() + "  Y: " + bound.getY() + "  Z: " + bound.getZ();
            g.drawString(this.font, coordText, contentX + SECTION_PAD, coordY,
                    NPCMenuScreen.TEXT_CYAN & 0x00FFFFFF, false);
        } else {
            Component notSet = Component.translatable("gui.questnpc.npc_menu.patrol_not_set");
            g.drawString(this.font, notSet, contentX + SECTION_PAD, coordY,
                    NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);
        }

        // Текущая позиция NPC
        int posInfoY = sectionY + 52;
        String posStr = String.format("NPC Pos: %d, %d, %d", (int) npc.getX(), (int) npc.getY(), (int) npc.getZ());
        g.drawString(this.font, posStr, contentX + SECTION_PAD, posInfoY,
                NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);

        // Футер
        Component footer = Component.translatable("gui.questnpc.menu.footer");
        g.drawCenteredString(this.font, footer, panelX + PANEL_WIDTH / 2, panelY + PANEL_HEIGHT - 12,
                NPCMenuScreen.TEXT_DARK_GRAY & 0x00FFFFFF);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
