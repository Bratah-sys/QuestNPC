package com.questnpc.client.gui;

import com.questnpc.client.gui.widget.DarkButton;
import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Skeleton экрана управления квестами NPC (v2.9.0 foundation).
 *
 * <p>Этап 1 (foundation): только заголовок + кнопка «← Назад» + placeholder.
 * Реальный редактор (список квестов, форма редактирования, сетевой пакет) — этап 2.
 *
 * <p>Стиль и layout — аналогичны {@link EquipmentScreen}.
 */
public class QuestsScreen extends Screen {

    private static final int PANEL_WIDTH  = 380;
    private static final int PANEL_HEIGHT = 260;
    private static final int PADDING      = 12;

    private final QuestNPCEntity npc;
    private final Screen parentScreen;

    private int panelX, panelY;

    public QuestsScreen(QuestNPCEntity npc, Screen parent) {
        super(Component.translatable("gui.questnpc.quests.title",
                npc.hasCustomName() ? npc.getCustomName().getString() : npc.getName().getString()));
        this.npc = npc;
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;

        // ── Кнопка «Назад» в верхнем левом углу ──
        this.addRenderableWidget(new DarkButton(
                panelX + PADDING, panelY + 30, 60, 18,
                Component.translatable("gui.questnpc.quests.back"),
                button -> Minecraft.getInstance().setScreen(parentScreen)
        ));
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

        // Placeholder в центре
        Component placeholder = Component.translatable("gui.questnpc.quests.placeholder");
        g.drawCenteredString(this.font, placeholder,
                panelX + PANEL_WIDTH / 2, panelY + PANEL_HEIGHT / 2 - 4,
                NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF);

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
