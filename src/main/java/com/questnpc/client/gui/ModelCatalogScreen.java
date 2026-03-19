package com.questnpc.client.gui;

import com.questnpc.QuestNPCLogger;
import com.questnpc.client.gui.widget.DarkButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Полноэкранный каталог моделей мобов с 3D-превью.
 * Вкладки модов, поиск, сетка карточек 3xN с вертикальным скроллом.
 */
public class ModelCatalogScreen extends Screen {

    // ═══ Цвета (тёмная тема) ═══
    private static final int BG_FULL       = 0xFF0D1117;
    private static final int CARD_BG       = 0xFF1F2937;
    private static final int CARD_HOVER    = 0xFF2D3748;
    private static final int CARD_SELECTED = 0xFF065F46;
    private static final int CARD_BORDER   = 0xFF4A5568;
    private static final int CARD_SEL_BORDER = 0xFF2DD4BF;
    private static final int TAB_BG        = 0xFF1A1A2E;
    private static final int TAB_ACTIVE    = 0xFF2563EB;
    private static final int TAB_HOVER     = 0xFF374151;
    private static final int SCROLLBAR_BG  = 0xFF1A1A2E;
    private static final int SCROLLBAR_FG  = 0xFF4A5568;
    private static final int SCROLLBAR_HOVER = 0xFF6B7280;

    // ═══ Размеры ═══
    private static final int HEADER_HEIGHT = 32;
    private static final int FOOTER_HEIGHT = 30;
    private static final int CARD_WIDTH    = 100;
    private static final int CARD_HEIGHT   = 120;
    private static final int CARD_GAP      = 8;
    private static final int COLUMNS       = 3;
    private static final int SCROLLBAR_W   = 8;
    private static final int TAB_HEIGHT    = 20;
    private static final int SEARCH_WIDTH  = 100;

    // ═══ Данные ═══
    private final Screen parent;
    @Nullable
    private final ResourceLocation initialSelection;

    // Вкладки модов: namespace → display name
    private final List<String> modNamespaces = new ArrayList<>();
    private final Map<String, String> modDisplayNames = new HashMap<>();
    private int activeModIndex = 0;

    // Карточки текущего мода (после фильтрации)
    private List<EntityEntry> filteredEntries = new ArrayList<>();
    // Все entity по namespace
    private final Map<String, List<EntityEntry>> allEntries = new LinkedHashMap<>();

    // Кэш entity instances для 3D-превью
    private final Map<ResourceLocation, LivingEntity> entityCache = new HashMap<>();

    // Выбранная модель
    @Nullable
    private ResourceLocation selectedModel;

    // Скролл
    private double scrollOffset = 0;
    private boolean scrollbarDragging = false;
    private double scrollbarDragOffset = 0;

    // Скролл вкладок
    private int tabScrollOffset = 0;

    // Виджеты
    private EditBox searchBox;

    // Layout
    private int gridX, gridY, gridW, gridH;
    private int scrollbarX;

    /**
     * @param parent           экран-родитель (NPCMenuScreen)
     * @param initialSelection текущая выбранная модель (null = дефолт)
     */
    public ModelCatalogScreen(Screen parent, @Nullable ResourceLocation initialSelection) {
        super(Component.translatable("gui.questnpc.catalog.title"));
        this.parent = parent;
        this.initialSelection = initialSelection;
        this.selectedModel = initialSelection;
    }

    // ═══════════════════════════════════════════════
    // ИНИЦИАЛИЗАЦИЯ
    // ═══════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();

        // Собираем EntityType по модам
        if (allEntries.isEmpty()) {
            buildEntityIndex();
        }

        // Layout
        int padding = 10;
        int totalGridW = COLUMNS * CARD_WIDTH + (COLUMNS - 1) * CARD_GAP;
        gridX = (this.width - totalGridW - SCROLLBAR_W - 4) / 2;
        gridY = HEADER_HEIGHT + 4;
        gridW = totalGridW;
        gridH = this.height - HEADER_HEIGHT - FOOTER_HEIGHT - 8;
        scrollbarX = gridX + gridW + 4;

        // ═══ Поиск ═══
        searchBox = new EditBox(this.font, padding, 6, SEARCH_WIDTH, 18, Component.translatable("gui.questnpc.catalog.search"));
        searchBox.setHint(Component.translatable("gui.questnpc.catalog.search"));
        searchBox.setMaxLength(50);
        searchBox.setBordered(false);
        searchBox.setResponder(query -> {
            filterEntries();
            scrollOffset = 0;
        });
        this.addRenderableWidget(searchBox);

        // ═══ Кнопка папки (справа) ═══
        int folderBtnX = this.width - padding - 20;
        this.addRenderableWidget(new DarkButton(
                folderBtnX, 6, 20, 20,
                Component.literal("\uD83D\uDCC1"),
                button -> Minecraft.getInstance().setScreen(new WIPScreen(this))
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

        filterEntries();
    }

    // ═══════════════════════════════════════════════
    // ИНДЕКС ENTITY
    // ═══════════════════════════════════════════════

    private void buildEntityIndex() {
        Map<String, List<EntityEntry>> grouped = new TreeMap<>();

        for (var entry : ForgeRegistries.ENTITY_TYPES.getEntries()) {
            EntityType<?> type = entry.getValue();
            ResourceLocation rl = entry.getKey().location();

            // Фильтруем только LivingEntity (пропускаем PLAYER)
            if (type == EntityType.PLAYER) continue;

            // Проверяем что это LivingEntity через категорию
            if (!isLivingEntityType(type)) continue;

            String namespace = rl.getNamespace();
            String name = Component.translatable(type.getDescriptionId()).getString();

            grouped.computeIfAbsent(namespace, k -> new ArrayList<>())
                    .add(new EntityEntry(rl, type, name));
        }

        // Сортировка внутри каждого мода по имени
        for (List<EntityEntry> list : grouped.values()) {
            list.sort(Comparator.comparing(e -> e.displayName.toLowerCase()));
        }

        // Формируем вкладки: "minecraft" первый, остальные по алфавиту
        allEntries.clear();
        modNamespaces.clear();
        modDisplayNames.clear();

        if (grouped.containsKey("minecraft")) {
            modNamespaces.add("minecraft");
            modDisplayNames.put("minecraft", Component.translatable("gui.questnpc.catalog.vanilla").getString());
            allEntries.put("minecraft", grouped.get("minecraft"));
        }

        for (String ns : grouped.keySet()) {
            if (ns.equals("minecraft")) continue;
            modNamespaces.add(ns);
            String displayName = ModList.get().getModContainerById(ns)
                    .map(c -> c.getModInfo().getDisplayName())
                    .orElse(ns);
            modDisplayNames.put(ns, displayName);
            allEntries.put(ns, grouped.get(ns));
        }

        QuestNPCLogger.debug("Каталог: загружено {} модов с entity", modNamespaces.size());
    }

    /**
     * Проверяет является ли EntityType типом LivingEntity.
     * Создаём временную entity чтобы проверить — если создание падает, пропускаем.
     */
    @SuppressWarnings("unchecked")
    private boolean isLivingEntityType(EntityType<?> type) {
        try {
            // Используем category как эвристику — мобы имеют конкретные категории
            var category = type.getCategory();
            // Все LivingEntity имеют category != MISC, кроме некоторых
            // Более надёжный способ: проверить через factory
            return category != net.minecraft.world.entity.MobCategory.MISC
                    || type == EntityType.IRON_GOLEM
                    || type == EntityType.SNOW_GOLEM
                    || type == EntityType.VILLAGER
                    || type == EntityType.WANDERING_TRADER;
        } catch (Exception e) {
            return false;
        }
    }

    private void filterEntries() {
        if (modNamespaces.isEmpty()) return;

        String namespace = modNamespaces.get(activeModIndex);
        List<EntityEntry> all = allEntries.getOrDefault(namespace, Collections.emptyList());

        String query = searchBox != null ? searchBox.getValue().trim().toLowerCase() : "";

        if (query.isEmpty()) {
            filteredEntries = new ArrayList<>(all);
        } else {
            filteredEntries = all.stream()
                    .filter(e -> e.displayName.toLowerCase().contains(query)
                            || e.id.getPath().contains(query))
                    .collect(Collectors.toList());
            QuestNPCLogger.debug("Каталог: поиск '{}' -> {} результатов", query, filteredEntries.size());
        }
    }

    // ═══════════════════════════════════════════════
    // РЕНДЕРИНГ
    // ═══════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Фон на весь экран
        g.fill(0, 0, this.width, this.height, BG_FULL);

        // Хедер
        renderHeader(g, mouseX, mouseY);

        // Разделитель под хедером
        g.fill(0, HEADER_HEIGHT, this.width, HEADER_HEIGHT + 1, NPCMenuScreen.BORDER);

        // Сетка карточек (со scissor)
        renderGrid(g, mouseX, mouseY, partialTick);

        // Скроллбар
        renderScrollbar(g, mouseX, mouseY);

        // Разделитель перед футером
        g.fill(0, this.height - FOOTER_HEIGHT, this.width, this.height - FOOTER_HEIGHT + 1, NPCMenuScreen.BORDER);

        // Виджеты (кнопки, поиск)
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 0, this.width, HEADER_HEIGHT, TAB_BG);

        // Фон поиска
        NPCMenuScreen.drawEditBoxBg(g, 8, 4, SEARCH_WIDTH + 4, 22, true);

        // Вкладки модов
        renderModTabs(g, mouseX, mouseY);
    }

    private void renderModTabs(GuiGraphics g, int mouseX, int mouseY) {
        int tabStartX = SEARCH_WIDTH + 20;
        int tabEndX = this.width - 30;
        int tabY = 6;
        int tabGap = 4;

        // Стрелки влево/вправо если вкладок много
        int availableW = tabEndX - tabStartX;
        int currentX = tabStartX - tabScrollOffset;

        for (int i = 0; i < modNamespaces.size(); i++) {
            String displayName = modDisplayNames.get(modNamespaces.get(i));
            int tabW = this.font.width(displayName) + 12;

            // Пропускаем если вне видимой зоны
            if (currentX + tabW < tabStartX || currentX > tabEndX) {
                currentX += tabW + tabGap;
                continue;
            }

            // Обрезка
            int drawX = Math.max(currentX, tabStartX);
            int drawEndX = Math.min(currentX + tabW, tabEndX);
            if (drawEndX - drawX < 10) {
                currentX += tabW + tabGap;
                continue;
            }

            int bg;
            if (i == activeModIndex) {
                bg = TAB_ACTIVE;
            } else if (mouseX >= drawX && mouseX < drawEndX && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
                bg = TAB_HOVER;
            } else {
                bg = CARD_BG;
            }

            g.fill(currentX, tabY, currentX + tabW, tabY + TAB_HEIGHT, bg);
            NPCMenuScreen.drawOutlineRect(g, currentX, tabY, tabW, TAB_HEIGHT, CARD_BORDER);

            int textX = currentX + (tabW - this.font.width(displayName)) / 2;
            int textY = tabY + (TAB_HEIGHT - 8) / 2;
            g.drawString(this.font, displayName, textX, textY, NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF, false);

            currentX += tabW + tabGap;
        }

        // Стрелки прокрутки если нужно
        int totalTabsWidth = 0;
        for (String ns : modNamespaces) {
            totalTabsWidth += this.font.width(modDisplayNames.get(ns)) + 12 + tabGap;
        }
        if (totalTabsWidth > availableW) {
            // Стрелка влево
            if (tabScrollOffset > 0) {
                g.drawString(this.font, "\u25C4", tabStartX - 10, tabY + 6, NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF, false);
            }
            // Стрелка вправо
            if (tabScrollOffset < totalTabsWidth - availableW) {
                g.drawString(this.font, "\u25BA", tabEndX + 2, tabY + 6, NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF, false);
            }
        }
    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (filteredEntries.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.questnpc.catalog.no_results"),
                    this.width / 2, this.height / 2,
                    NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF);
            return;
        }

        int totalRows = (filteredEntries.size() + COLUMNS - 1) / COLUMNS;
        int rowHeight = CARD_HEIGHT + CARD_GAP;

        // Scissor для области сетки
        g.enableScissor(gridX - 2, gridY, gridX + gridW + 2, gridY + gridH);

        for (int i = 0; i < filteredEntries.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;

            int cardX = gridX + col * (CARD_WIDTH + CARD_GAP);
            int cardY = gridY + row * rowHeight - (int) scrollOffset;

            // Виртуальный скролл: пропускаем невидимые
            if (cardY + CARD_HEIGHT < gridY || cardY > gridY + gridH) continue;

            EntityEntry entry = filteredEntries.get(i);
            boolean isSelected = entry.id.equals(selectedModel);
            boolean isHovered = mouseX >= cardX && mouseX < cardX + CARD_WIDTH
                    && mouseY >= cardY && mouseY < cardY + CARD_HEIGHT
                    && mouseY >= gridY && mouseY < gridY + gridH;

            renderCard(g, entry, cardX, cardY, isSelected, isHovered, mouseX, mouseY, partialTick);
        }

        g.disableScissor();
    }

    private void renderCard(GuiGraphics g, EntityEntry entry, int x, int y,
                            boolean selected, boolean hovered, int mouseX, int mouseY, float partialTick) {
        // Фон карточки
        int bg = selected ? CARD_SELECTED : (hovered ? CARD_HOVER : CARD_BG);
        g.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, bg);

        // Рамка
        int border = selected ? CARD_SEL_BORDER : CARD_BORDER;
        NPCMenuScreen.drawOutlineRect(g, x, y, CARD_WIDTH, CARD_HEIGHT, border);

        // 3D-превью моба
        int previewAreaH = CARD_HEIGHT - 20;
        renderEntityPreview(g, entry, x, y, previewAreaH, mouseX, mouseY);

        // Имя моба
        String name = entry.displayName;
        int maxTextW = CARD_WIDTH - 4;
        if (this.font.width(name) > maxTextW) {
            name = this.font.plainSubstrByWidth(name, maxTextW - 6) + "...";
        }
        int textX = x + (CARD_WIDTH - this.font.width(name)) / 2;
        int textY = y + CARD_HEIGHT - 14;
        g.drawString(this.font, name, textX, textY, NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF, false);
    }

    private void renderEntityPreview(GuiGraphics g, EntityEntry entry, int cardX, int cardY,
                                     int previewH, int mouseX, int mouseY) {
        LivingEntity livingEntity = getOrCreateCachedEntity(entry);
        if (livingEntity == null) {
            // Fallback: серый прямоугольник с "?"
            int cx = cardX + CARD_WIDTH / 2;
            int cy = cardY + previewH / 2;
            g.drawCenteredString(this.font, "?", cx, cy, NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF);
            return;
        }

        try {
            int renderX = cardX + CARD_WIDTH / 2;
            int renderY = cardY + previewH - 2;

            // Вычисляем масштаб по размеру моба
            float entityH = livingEntity.getBbHeight();
            float entityW = livingEntity.getBbWidth();
            float maxDim = Math.max(entityH, entityW);
            int scale = (int) Math.max(8, Math.min(35, (previewH - 15) / maxDim * 0.9F));

            float relMouseX = renderX - mouseX;
            float relMouseY = renderY - (previewH * 0.6F) - mouseY;

            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g, renderX, renderY, scale, relMouseX, relMouseY, livingEntity);
        } catch (Exception e) {
            // Fallback при ошибке рендера
            int cx = cardX + CARD_WIDTH / 2;
            int cy = cardY + previewH / 2;
            g.drawCenteredString(this.font, "!", cx, cy, NPCMenuScreen.TEXT_RED & 0x00FFFFFF);
        }
    }

    @Nullable
    private LivingEntity getOrCreateCachedEntity(EntityEntry entry) {
        LivingEntity cached = entityCache.get(entry.id);
        if (cached != null) return cached;

        if (entityCache.size() >= 50) {
            // Очищаем кэш если слишком большой
            entityCache.clear();
        }

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return null;

            Entity entity = entry.type.create(mc.level);
            if (entity instanceof LivingEntity living) {
                entityCache.put(entry.id, living);
                return living;
            }
        } catch (Exception e) {
            QuestNPCLogger.warn("Не удалось создать превью для {}: {}", entry.id, e.getMessage());
        }
        return null;
    }

    private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        if (filteredEntries.isEmpty()) return;

        int totalRows = (filteredEntries.size() + COLUMNS - 1) / COLUMNS;
        int rowHeight = CARD_HEIGHT + CARD_GAP;
        int totalContentH = totalRows * rowHeight;

        if (totalContentH <= gridH) return; // Скроллбар не нужен

        // Фон скроллбара
        g.fill(scrollbarX, gridY, scrollbarX + SCROLLBAR_W, gridY + gridH, SCROLLBAR_BG);

        // Ползунок
        float visibleRatio = (float) gridH / totalContentH;
        int thumbH = Math.max(20, (int) (gridH * visibleRatio));
        int maxScroll = totalContentH - gridH;
        float scrollRatio = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
        int thumbY = gridY + (int) ((gridH - thumbH) * scrollRatio);

        boolean thumbHovered = mouseX >= scrollbarX && mouseX < scrollbarX + SCROLLBAR_W
                && mouseY >= thumbY && mouseY < thumbY + thumbH;

        int thumbColor = (thumbHovered || scrollbarDragging) ? SCROLLBAR_HOVER : SCROLLBAR_FG;
        g.fill(scrollbarX, thumbY, scrollbarX + SCROLLBAR_W, thumbY + thumbH, thumbColor);
    }

    // ═══════════════════════════════════════════════
    // ВВОД
    // ═══════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        // Клик по вкладке мода
        if (mouseY >= 6 && mouseY < 6 + TAB_HEIGHT) {
            int tabStartX = SEARCH_WIDTH + 20;
            int tabEndX = this.width - 30;
            int tabGap = 4;
            int currentX = tabStartX - tabScrollOffset;

            for (int i = 0; i < modNamespaces.size(); i++) {
                String displayName = modDisplayNames.get(modNamespaces.get(i));
                int tabW = this.font.width(displayName) + 12;

                if (mouseX >= Math.max(currentX, tabStartX) && mouseX < Math.min(currentX + tabW, tabEndX)) {
                    activeModIndex = i;
                    filterEntries();
                    scrollOffset = 0;
                    QuestNPCLogger.debug("Каталог: переключение на мод '{}', {} entity",
                            modNamespaces.get(i), filteredEntries.size());
                    return true;
                }
                currentX += tabW + tabGap;
            }
        }

        // Клик по скроллбару
        if (mouseX >= scrollbarX && mouseX < scrollbarX + SCROLLBAR_W
                && mouseY >= gridY && mouseY < gridY + gridH) {
            scrollbarDragging = true;
            scrollbarDragOffset = mouseY;
            return true;
        }

        // Клик по карточке
        if (mouseX >= gridX && mouseX < gridX + gridW && mouseY >= gridY && mouseY < gridY + gridH) {
            int col = ((int) mouseX - gridX) / (CARD_WIDTH + CARD_GAP);
            if (col >= COLUMNS) return false;
            int relX = ((int) mouseX - gridX) % (CARD_WIDTH + CARD_GAP);
            if (relX > CARD_WIDTH) return false; // Клик в промежутке

            int rowHeight = CARD_HEIGHT + CARD_GAP;
            int row = ((int) (mouseY - gridY + scrollOffset)) / rowHeight;
            int relY = ((int) (mouseY - gridY + scrollOffset)) % rowHeight;
            if (relY > CARD_HEIGHT) return false; // Клик в промежутке

            int index = row * COLUMNS + col;
            if (index >= 0 && index < filteredEntries.size()) {
                ResourceLocation clickedId = filteredEntries.get(index).id;
                if (clickedId.equals(selectedModel)) {
                    selectedModel = null; // Снять выбор
                } else {
                    selectedModel = clickedId;
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollbarDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollbarDragging) {
            int totalRows = (filteredEntries.size() + COLUMNS - 1) / COLUMNS;
            int rowHeight = CARD_HEIGHT + CARD_GAP;
            int totalContentH = totalRows * rowHeight;
            int maxScroll = Math.max(0, totalContentH - gridH);

            float ratio = (float) (mouseY - gridY) / gridH;
            scrollOffset = Math.max(0, Math.min(maxScroll, ratio * maxScroll));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // Скролл вкладок в зоне хедера
        if (mouseY < HEADER_HEIGHT && mouseX > SEARCH_WIDTH + 20 && mouseX < this.width - 30) {
            int tabGap = 4;
            int totalTabsWidth = 0;
            for (String ns : modNamespaces) {
                totalTabsWidth += this.font.width(modDisplayNames.get(ns)) + 12 + tabGap;
            }
            int availableW = this.width - 30 - SEARCH_WIDTH - 20;
            int maxTabScroll = Math.max(0, totalTabsWidth - availableW);
            tabScrollOffset = (int) Math.max(0, Math.min(maxTabScroll, tabScrollOffset - delta * 20));
            return true;
        }

        // Вертикальный скролл сетки
        int totalRows = (filteredEntries.size() + COLUMNS - 1) / COLUMNS;
        int rowHeight = CARD_HEIGHT + CARD_GAP;
        int totalContentH = totalRows * rowHeight;
        int maxScroll = Math.max(0, totalContentH - gridH);

        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta * 30));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Пропускаем Escape для searchBox чтобы он не перехватывал
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
        if (parent instanceof NPCMenuScreen menuScreen) {
            menuScreen.setPendingModelType(selectedModel);
        }
        Minecraft.getInstance().setScreen(parent);
    }

    @Nullable
    public ResourceLocation getSelectedModel() {
        return selectedModel;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        // Очищаем кэш entity при закрытии
        entityCache.clear();
    }

    // ═══════════════════════════════════════════════
    // ВСПОМОГАТЕЛЬНЫЙ КЛАСС
    // ═══════════════════════════════════════════════

    /**
     * Запись об EntityType для отображения в каталоге.
     */
    static class EntityEntry {
        final ResourceLocation id;
        final EntityType<?> type;
        final String displayName;

        EntityEntry(ResourceLocation id, EntityType<?> type, String displayName) {
            this.id = id;
            this.type = type;
            this.displayName = displayName;
        }
    }
}
