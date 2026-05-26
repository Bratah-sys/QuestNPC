package com.questnpc.client.gui;

import com.questnpc.QuestNPCLogger;
import com.questnpc.client.gui.widget.DarkButton;
import com.questnpc.client.model.CustomModelManager;
import com.questnpc.client.model.CustomModelManager.CustomModelInfo;
import com.questnpc.entity.ModEntityTypes;
import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Экран выбора кастомных .geo.json моделей с 3D-превью.
 * Отображает сетку карточек моделей из config/questnpc/custom_models/.
 */
public class CustomModelBrowserScreen extends Screen {

    // ═══ Цвета ═══
    private static final int BG_FULL       = 0xFF0D1117;
    private static final int CARD_BG       = 0xFF1F2937;
    private static final int CARD_HOVER    = 0xFF2D3748;
    private static final int CARD_SELECTED = 0xFF065F46;
    private static final int CARD_BORDER   = 0xFF4A5568;
    private static final int CARD_SEL_BORDER = 0xFF2DD4BF;
    private static final int HEADER_BG     = 0xFF1A1A2E;

    // ═══ Размеры ═══
    private static final int HEADER_HEIGHT = 32;
    private static final int FOOTER_HEIGHT = 30;
    private static final int CARD_WIDTH    = 100;
    private static final int CARD_HEIGHT   = 120;
    private static final int CARD_GAP      = 8;
    private static final int COLUMNS       = 3;

    // ═══ Данные ═══
    private final Screen parent;
    private final List<CustomModelInfo> models = new ArrayList<>();

    @Nullable
    private String selectedModelName;

    // Кэш фейковых QuestNPCEntity для 3D-превью
    private final Map<String, QuestNPCEntity> previewCache = new HashMap<>();

    // Скролл
    private double scrollOffset = 0;
    private int gridX, gridY, gridW, gridH;

    /**
     * @param parent экран-родитель (ModelCatalogScreen)
     */
    public CustomModelBrowserScreen(Screen parent) {
        super(Component.translatable("gui.questnpc.model_catalog.custom_models"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        // Сканируем папку моделей
        CustomModelManager.getInstance().scanModels();
        models.clear();
        models.addAll(CustomModelManager.getInstance().getModels());

        // Layout — сетка по центру
        int totalGridW = COLUMNS * CARD_WIDTH + (COLUMNS - 1) * CARD_GAP;
        gridX = (this.width - totalGridW) / 2;
        gridY = HEADER_HEIGHT + 8;
        gridW = totalGridW;
        gridH = this.height - HEADER_HEIGHT - FOOTER_HEIGHT - 16;

        // ═══ Кнопка "Открыть папку" в хедере ═══
        int padding = 10;
        int openFolderW = 100;
        this.addRenderableWidget(new DarkButton(
                this.width - padding - openFolderW, 6, openFolderW, 20,
                Component.translatable("gui.questnpc.model_catalog.open_folder"),
                button -> {
                    CustomModelManager.getInstance().ensureDirectoryExists();
                    Util.getPlatform().openUri(
                            CustomModelManager.getInstance().getModelsDirectory().toUri());
                }
        ));

        // ═══ Кнопки внизу ═══
        int btnW = 90;
        int btnH = 20;
        int btnY = this.height - FOOTER_HEIGHT + 5;
        int centerX = this.width / 2;

        this.addRenderableWidget(new DarkButton(
                centerX - btnW - 10, btnY, btnW, btnH,
                Component.translatable("gui.questnpc.catalog.back"),
                button -> Minecraft.getInstance().setScreen(parent)
        ));

        this.addRenderableWidget(new DarkButton(
                centerX + 10, btnY, btnW, btnH,
                Component.translatable("gui.questnpc.catalog.select"),
                button -> confirmSelection(),
                NPCMenuScreen.BTN_GREEN_BG, NPCMenuScreen.BTN_GREEN_HOVER, 0xFFFFFFFF
        ));

        // Кнопка "Обновить"
        this.addRenderableWidget(new DarkButton(
                centerX + btnW + 20, btnY, 60, btnH,
                Component.translatable("gui.questnpc.model_catalog.refresh"),
                button -> {
                    CustomModelManager.getInstance().scanModels();
                    models.clear();
                    models.addAll(CustomModelManager.getInstance().getModels());
                    previewCache.clear();
                    scrollOffset = 0;
                }
        ));
    }

    // ═══════════════════════════════════════════════
    // РЕНДЕРИНГ
    // ═══════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Фон
        g.fill(0, 0, this.width, this.height, BG_FULL);

        // Хедер
        g.fill(0, 0, this.width, HEADER_HEIGHT, HEADER_BG);
        g.drawCenteredString(this.font, this.title,
                this.width / 2, 12, NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF);
        g.fill(0, HEADER_HEIGHT, this.width, HEADER_HEIGHT + 1, NPCMenuScreen.BORDER);

        // Сетка моделей
        if (models.isEmpty()) {
            String path = CustomModelManager.getInstance().getModelsDirectory().toString();
            Component hint = Component.translatable("gui.questnpc.model_catalog.no_custom_models", path);
            List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(hint, this.width - 40);
            int textY = this.height / 2 - lines.size() * 5;
            for (var line : lines) {
                g.drawString(this.font, line,
                        (this.width - this.font.width(line)) / 2, textY,
                        NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);
                textY += 11;
            }
        } else {
            renderGrid(g, mouseX, mouseY, partialTick);
        }

        // Разделитель футера
        g.fill(0, this.height - FOOTER_HEIGHT, this.width,
                this.height - FOOTER_HEIGHT + 1, NPCMenuScreen.BORDER);

        // Виджеты
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int rowHeight = CARD_HEIGHT + CARD_GAP;

        g.enableScissor(gridX - 2, gridY, gridX + gridW + 2, gridY + gridH);

        for (int i = 0; i < models.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;

            int cardX = gridX + col * (CARD_WIDTH + CARD_GAP);
            int cardY = gridY + row * rowHeight - (int) scrollOffset;

            // Виртуальный скролл
            if (cardY + CARD_HEIGHT < gridY || cardY > gridY + gridH) continue;

            CustomModelInfo model = models.get(i);
            boolean isSelected = model.name().equals(selectedModelName);
            boolean isHovered = mouseX >= cardX && mouseX < cardX + CARD_WIDTH
                    && mouseY >= cardY && mouseY < cardY + CARD_HEIGHT
                    && mouseY >= gridY && mouseY < gridY + gridH;

            renderCard(g, model, cardX, cardY, isSelected, isHovered, mouseX, mouseY, partialTick);
        }

        g.disableScissor();
    }

    private void renderCard(GuiGraphics g, CustomModelInfo model, int x, int y,
                            boolean selected, boolean hovered, int mouseX, int mouseY,
                            float partialTick) {
        // Фон карточки
        int bg = selected ? CARD_SELECTED : (hovered ? CARD_HOVER : CARD_BG);
        g.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, bg);

        // Рамка
        int border = selected ? CARD_SEL_BORDER : CARD_BORDER;
        NPCMenuScreen.drawOutlineRect(g, x, y, CARD_WIDTH, CARD_HEIGHT, border);

        // 3D-превью модели
        int previewAreaH = CARD_HEIGHT - 24;
        renderModelPreview(g, model, x, y, previewAreaH, mouseX, mouseY);

        // Имя модели
        String name = model.name();
        int maxTextW = CARD_WIDTH - 4;
        if (this.font.width(name) > maxTextW) {
            name = this.font.plainSubstrByWidth(name, maxTextW - 6) + "...";
        }
        int textX = x + (CARD_WIDTH - this.font.width(name)) / 2;
        int textY = y + CARD_HEIGHT - 18;
        g.drawString(this.font, name, textX, textY, NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF, false);

        // Инфо: кости
        String info = model.boneCount() + " bones";
        int infoX = x + (CARD_WIDTH - this.font.width(info)) / 2;
        g.drawString(this.font, info, infoX, textY + 10,
                NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);
    }

    private void renderModelPreview(GuiGraphics g, CustomModelInfo model, int cardX, int cardY,
                                    int previewH, int mouseX, int mouseY) {
        QuestNPCEntity fakeNpc = getOrCreatePreviewNpc(model.name());
        if (fakeNpc == null) {
            // Fallback: "?"
            int cx = cardX + CARD_WIDTH / 2;
            int cy = cardY + previewH / 2;
            g.drawCenteredString(this.font, "?", cx, cy, NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF);
            return;
        }

        try {
            int renderX = cardX + CARD_WIDTH / 2;
            int renderY = cardY + previewH - 2;

            // Масштаб
            float entityH = fakeNpc.getBbHeight();
            float entityW = fakeNpc.getBbWidth();
            float maxDim = Math.max(entityH, entityW);
            int scale = (int) Math.max(8, Math.min(35, (previewH - 15) / maxDim * 0.9F));

            float relMouseX = renderX - mouseX;
            float relMouseY = renderY - (previewH * 0.6F) - mouseY;

            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g, renderX, renderY, scale, relMouseX, relMouseY, fakeNpc);
        } catch (Exception e) {
            // Fallback при ошибке рендера
            int cx = cardX + CARD_WIDTH / 2;
            int cy = cardY + previewH / 2;
            g.drawCenteredString(this.font, "!", cx, cy, NPCMenuScreen.TEXT_RED & 0x00FFFFFF);
        }
    }

    /**
     * Получает или создаёт фейковый QuestNPCEntity для 3D-превью кастомной модели.
     */
    @Nullable
    private QuestNPCEntity getOrCreatePreviewNpc(String modelName) {
        QuestNPCEntity cached = previewCache.get(modelName);
        if (cached != null) return cached;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        try {
            QuestNPCEntity fakeNpc = ModEntityTypes.QUEST_NPC.get().create(mc.level);
            if (fakeNpc != null) {
                fakeNpc.setModelEntityType(CustomModelManager.CUSTOM_PREFIX + modelName);
                previewCache.put(modelName, fakeNpc);
                return fakeNpc;
            }
        } catch (Exception e) {
            QuestNPCLogger.warn("Не удалось создать превью для модели '{}': {}", modelName, e.getMessage());
        }
        return null;
    }

    // ═══════════════════════════════════════════════
    // ВВОД
    // ═══════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        // Клик по карточке в сетке
        if (mouseX >= gridX && mouseX < gridX + gridW && mouseY >= gridY && mouseY < gridY + gridH) {
            int col = ((int) mouseX - gridX) / (CARD_WIDTH + CARD_GAP);
            if (col >= COLUMNS) return false;
            int relX = ((int) mouseX - gridX) % (CARD_WIDTH + CARD_GAP);
            if (relX > CARD_WIDTH) return false;

            int rowHeight = CARD_HEIGHT + CARD_GAP;
            int row = ((int) (mouseY - gridY + scrollOffset)) / rowHeight;
            int relY = ((int) (mouseY - gridY + scrollOffset)) % rowHeight;
            if (relY > CARD_HEIGHT) return false;

            int index = row * COLUMNS + col;
            if (index >= 0 && index < models.size()) {
                String clicked = models.get(index).name();
                if (clicked.equals(selectedModelName)) {
                    selectedModelName = null;
                } else {
                    selectedModelName = clicked;
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int totalRows = (models.size() + COLUMNS - 1) / COLUMNS;
        int rowHeight = CARD_HEIGHT + CARD_GAP;
        int totalContentH = totalRows * rowHeight;
        int maxScroll = Math.max(0, totalContentH - gridH);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta * 30));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ═══════════════════════════════════════════════
    // ДЕЙСТВИЯ
    // ═══════════════════════════════════════════════

    private void confirmSelection() {
        if (selectedModelName == null) {
            Minecraft.getInstance().setScreen(parent);
            return;
        }

        String customModelType = CustomModelManager.CUSTOM_PREFIX + selectedModelName;

        if (parent instanceof ModelCatalogScreen catalogScreen) {
            Screen catalogParent = catalogScreen.getParent();
            if (catalogParent instanceof NPCMenuScreen menuScreen) {
                menuScreen.setPendingCustomModel(customModelType);
                QuestNPCLogger.info("Выбрана кастомная модель '{}' для применения", selectedModelName);
            }
        }

        // Возвращаемся в NPCMenuScreen (пропуская каталог)
        if (parent instanceof ModelCatalogScreen catalogScreen) {
            Minecraft.getInstance().setScreen(catalogScreen.getParent());
        } else {
            Minecraft.getInstance().setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        previewCache.clear();
    }
}
