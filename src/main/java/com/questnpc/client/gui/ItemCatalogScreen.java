package com.questnpc.client.gui;

import com.questnpc.QuestNPCLogger;
import com.questnpc.client.gui.widget.DarkButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Полноэкранный каталог предметов с вкладками по модам и поиском.
 * Используется в {@link NPCTradingScreen} для визуального выбора предмета в слот сделки
 * (вместо текстового ввода registry ID).
 *
 * <p>Архитектурный клон {@link ModelCatalogScreen}, но:
 * <ul>
 *   <li>Источник данных — {@link ForgeRegistries#ITEMS}</li>
 *   <li>Ячейки компактные (20×20), рендер через {@code GuiGraphics.renderItem}</li>
 *   <li>Индекс строится один раз, кэшируется на первый {@code init()}</li>
 * </ul>
 */
public class ItemCatalogScreen extends Screen {

    // ═══ Цвета (повторяют тему ModelCatalogScreen) ═══
    private static final int BG_FULL         = 0xFF0D1117;
    private static final int CELL_BG         = 0xFF1F2937;
    private static final int CELL_HOVER      = 0xFF2D3748;
    private static final int CELL_SELECTED   = 0xFF065F46;
    private static final int CELL_BORDER     = 0xFF4A5568;
    private static final int CELL_SEL_BORDER = 0xFF2DD4BF;
    private static final int TAB_BG          = 0xFF1A1A2E;
    private static final int TAB_ACTIVE      = 0xFF2563EB;
    private static final int TAB_HOVER       = 0xFF374151;
    private static final int SCROLLBAR_BG    = 0xFF1A1A2E;
    private static final int SCROLLBAR_FG    = 0xFF4A5568;
    private static final int SCROLLBAR_HOVER = 0xFF6B7280;

    // ═══ Размеры ═══
    private static final int HEADER_HEIGHT = 32;
    private static final int FOOTER_HEIGHT = 46; // увеличен: строка "Выбрано" + кнопки
    private static final int CELL_SIZE     = 20;
    private static final int CELL_GAP      = 2;
    private static final int SCROLLBAR_W   = 8;
    private static final int TAB_HEIGHT    = 20;
    private static final int SEARCH_WIDTH  = 120;

    // ═══ Данные ═══
    private final net.minecraft.client.gui.screens.Screen parent;
    @Nullable private final NPCTradingScreen tradeParent; // legacy путь: setPendingItemSelection
    private final int globalSlotId;
    @Nullable
    private final Item initialSelection;
    @Nullable private final java.util.function.Predicate<Item> itemFilter;     // v2.8.0
    @Nullable private final java.util.function.Consumer<Item> onConfirm;       // v2.8.0

    // Вкладки модов: namespace → display name
    private final List<String> modNamespaces = new ArrayList<>();
    private final Map<String, String> modDisplayNames = new HashMap<>();
    private int activeModIndex = 0;

    // Полный индекс по namespace (строится один раз на класс)
    private static final Map<String, List<ItemEntry>> CACHED_ENTRIES = new LinkedHashMap<>();
    private static final List<String> CACHED_NAMESPACES = new ArrayList<>();
    private static final Map<String, String> CACHED_DISPLAY_NAMES = new HashMap<>();

    // Отфильтрованный список текущей вкладки
    private List<ItemEntry> filteredEntries = new ArrayList<>();

    // Выбранный предмет
    @Nullable
    private Item selectedItem;

    // Скролл сетки
    private double scrollOffset = 0;
    private boolean scrollbarDragging = false;

    // Скролл вкладок
    private int tabScrollOffset = 0;

    // Виджеты
    private EditBox searchBox;

    // Layout
    private int gridX, gridY, gridW, gridH;
    private int scrollbarX;
    private int columns;

    /**
     * @param parent       родительский экран торговли
     * @param globalSlotId закодированный идентификатор слота: {@code rowIndex * 3 + col}
     *                     (0=input1, 1=input2, 2=output)
     * @param currentItem  текущий выбранный предмет (null = пустой слот)
     */
    public ItemCatalogScreen(NPCTradingScreen parent, int globalSlotId, @Nullable Item currentItem) {
        super(Component.translatable("gui.questnpc.item_catalog.title"));
        this.parent = parent;
        this.tradeParent = parent;
        this.globalSlotId = globalSlotId;
        this.initialSelection = currentItem;
        this.selectedItem = currentItem;
        this.itemFilter = null;
        this.onConfirm = null;
    }

    /**
     * v2.8.0: универсальный конструктор для не-trading-вызовов (например, Экипировка).
     * @param parent     любой Screen, на который вернуть управление при Back/Esc.
     * @param current    текущий выбранный Item (null = пустой слот).
     * @param filter     фильтр предметов (null = показывать все). Применяется в дополнение к поиску.
     * @param onConfirm  колбэк выбора. Получает null если очищено.
     */
    public ItemCatalogScreen(net.minecraft.client.gui.screens.Screen parent,
                             @Nullable Item current,
                             @Nullable java.util.function.Predicate<Item> filter,
                             java.util.function.Consumer<Item> onConfirm) {
        super(Component.translatable("gui.questnpc.item_catalog.title"));
        this.parent = parent;
        this.tradeParent = null;
        this.globalSlotId = -1;
        this.initialSelection = current;
        this.selectedItem = current;
        this.itemFilter = filter;
        this.onConfirm = onConfirm;
    }

    // ═══════════════════════════════════════════════
    // ИНИЦИАЛИЗАЦИЯ
    // ═══════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();

        if (CACHED_ENTRIES.isEmpty()) {
            buildItemIndex();
        }
        // Перекладываем кэш в локальные поля (дешёвая ссылка)
        modNamespaces.clear();
        modNamespaces.addAll(CACHED_NAMESPACES);
        modDisplayNames.clear();
        modDisplayNames.putAll(CACHED_DISPLAY_NAMES);

        // Layout: динамическое число колонок под ширину окна
        int padding = 10;
        int availableW = this.width - padding * 2 - SCROLLBAR_W - 4;
        columns = Math.max(8, availableW / (CELL_SIZE + CELL_GAP));
        int totalGridW = columns * (CELL_SIZE + CELL_GAP) - CELL_GAP;

        gridX = (this.width - totalGridW - SCROLLBAR_W - 4) / 2;
        gridY = HEADER_HEIGHT + 4;
        gridW = totalGridW;
        gridH = this.height - HEADER_HEIGHT - FOOTER_HEIGHT - 8;
        scrollbarX = gridX + gridW + 4;

        // ═══ Поиск ═══
        searchBox = new EditBox(this.font, padding, 6, SEARCH_WIDTH, 18,
                Component.translatable("gui.questnpc.item_catalog.search"));
        searchBox.setHint(Component.translatable("gui.questnpc.item_catalog.search"));
        searchBox.setMaxLength(50);
        searchBox.setBordered(false);
        searchBox.setResponder(query -> {
            filterEntries();
            scrollOffset = 0;
        });
        this.addRenderableWidget(searchBox);

        // Установить вкладку так, чтобы initialSelection оказался в ней (если возможно)
        if (initialSelection != null) {
            ResourceLocation rl = ForgeRegistries.ITEMS.getKey(initialSelection);
            if (rl != null) {
                int idx = modNamespaces.indexOf(rl.getNamespace());
                if (idx >= 0) activeModIndex = idx;
            }
        }

        // ═══ Кнопки внизу ═══
        int btnW = 90;
        int btnH = 20;
        int btnY = this.height - 26;
        int centerX = this.width / 2;

        this.addRenderableWidget(new DarkButton(
                centerX - btnW - 100, btnY, btnW, btnH,
                Component.translatable("gui.questnpc.item_catalog.back"),
                button -> Minecraft.getInstance().setScreen(parent)
        ));

        this.addRenderableWidget(new DarkButton(
                centerX - btnW / 2, btnY, btnW, btnH,
                Component.translatable("gui.questnpc.item_catalog.clear"),
                button -> {
                    selectedItem = null;
                    confirmSelection();
                },
                NPCMenuScreen.BTN_GRAY_BG, 0xFF7F1D1D, NPCMenuScreen.TEXT_WHITE
        ));

        this.addRenderableWidget(new DarkButton(
                centerX + 100, btnY, btnW, btnH,
                Component.translatable("gui.questnpc.item_catalog.select"),
                button -> confirmSelection(),
                NPCMenuScreen.BTN_GREEN_BG, NPCMenuScreen.BTN_GREEN_HOVER, 0xFFFFFFFF
        ));

        filterEntries();
    }

    // ═══════════════════════════════════════════════
    // ИНДЕКС ПРЕДМЕТОВ
    // ═══════════════════════════════════════════════

    private static void buildItemIndex() {
        Map<String, List<ItemEntry>> grouped = new TreeMap<>();

        for (var entry : ForgeRegistries.ITEMS.getEntries()) {
            Item item = entry.getValue();
            if (item == null || item == Items.AIR) continue;
            ResourceLocation rl = entry.getKey().location();

            String displayName;
            try {
                displayName = new ItemStack(item).getHoverName().getString();
            } catch (Exception e) {
                displayName = rl.getPath();
            }
            grouped.computeIfAbsent(rl.getNamespace(), k -> new ArrayList<>())
                    .add(new ItemEntry(rl, item, displayName));
        }

        // Сортировка внутри каждого мода по имени
        for (List<ItemEntry> list : grouped.values()) {
            list.sort(Comparator.comparing(e -> e.displayName.toLowerCase()));
        }

        CACHED_ENTRIES.clear();
        CACHED_NAMESPACES.clear();
        CACHED_DISPLAY_NAMES.clear();

        // "minecraft" всегда первым
        if (grouped.containsKey("minecraft")) {
            CACHED_NAMESPACES.add("minecraft");
            CACHED_DISPLAY_NAMES.put("minecraft",
                    Component.translatable("gui.questnpc.item_catalog.vanilla").getString());
            CACHED_ENTRIES.put("minecraft", grouped.get("minecraft"));
        }

        for (String ns : grouped.keySet()) {
            if (ns.equals("minecraft")) continue;
            CACHED_NAMESPACES.add(ns);
            String displayName = ModList.get().getModContainerById(ns)
                    .map(c -> c.getModInfo().getDisplayName())
                    .orElse(ns);
            CACHED_DISPLAY_NAMES.put(ns, displayName);
            CACHED_ENTRIES.put(ns, grouped.get(ns));
        }

        int total = grouped.values().stream().mapToInt(List::size).sum();
        QuestNPCLogger.debug("Каталог предметов: {} модов, {} записей",
                CACHED_NAMESPACES.size(), total);
    }

    /**
     * v2.5.5 (BUG-009): инвалидация статического кэша. Вызывается
     * {@code ResourceManagerReloadListener} при F3+T — следующий заход в
     * каталог пересоберёт индекс через {@link #buildItemIndex()}.
     */
    public static void invalidateCache() {
        CACHED_ENTRIES.clear();
        CACHED_NAMESPACES.clear();
        CACHED_DISPLAY_NAMES.clear();
    }

    private void filterEntries() {
        if (modNamespaces.isEmpty()) return;

        String namespace = modNamespaces.get(activeModIndex);
        List<ItemEntry> all = CACHED_ENTRIES.getOrDefault(namespace, Collections.emptyList());

        String query = searchBox != null ? searchBox.getValue().trim().toLowerCase() : "";
        java.util.stream.Stream<ItemEntry> stream = all.stream();
        if (itemFilter != null) {
            stream = stream.filter(e -> itemFilter.test(e.item));
        }
        if (!query.isEmpty()) {
            stream = stream.filter(e -> e.displayName.toLowerCase().contains(query)
                    || e.id.getPath().contains(query));
        }
        filteredEntries = stream.collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════
    // РЕНДЕРИНГ
    // ═══════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, BG_FULL);

        renderHeader(g, mouseX, mouseY);
        g.fill(0, HEADER_HEIGHT, this.width, HEADER_HEIGHT + 1, NPCMenuScreen.BORDER);

        renderGrid(g, mouseX, mouseY);
        renderScrollbar(g, mouseX, mouseY);

        // Футер: разделитель + строка "Выбрано"
        int footerY = this.height - FOOTER_HEIGHT;
        g.fill(0, footerY, this.width, footerY + 1, NPCMenuScreen.BORDER);
        renderFooterSelection(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);

        // Тултип для наведённой ячейки — поверх всего, после super.render
        renderHoveredTooltip(g, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 0, this.width, HEADER_HEIGHT, TAB_BG);
        NPCMenuScreen.drawEditBoxBg(g, 8, 4, SEARCH_WIDTH + 4, 22, true);
        renderModTabs(g, mouseX, mouseY);
    }

    private void renderModTabs(GuiGraphics g, int mouseX, int mouseY) {
        int tabStartX = SEARCH_WIDTH + 20;
        int tabEndX = this.width - 30;
        int tabY = 6;
        int tabGap = 4;

        int currentX = tabStartX - tabScrollOffset;

        for (int i = 0; i < modNamespaces.size(); i++) {
            String displayName = modDisplayNames.get(modNamespaces.get(i));
            int tabW = this.font.width(displayName) + 12;

            if (currentX + tabW < tabStartX || currentX > tabEndX) {
                currentX += tabW + tabGap;
                continue;
            }

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
                bg = CELL_BG;
            }

            g.fill(currentX, tabY, currentX + tabW, tabY + TAB_HEIGHT, bg);
            NPCMenuScreen.drawOutlineRect(g, currentX, tabY, tabW, TAB_HEIGHT, CELL_BORDER);

            int textX = currentX + (tabW - this.font.width(displayName)) / 2;
            int textY = tabY + (TAB_HEIGHT - 8) / 2;
            g.drawString(this.font, displayName, textX, textY,
                    NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF, false);

            currentX += tabW + tabGap;
        }

        // Индикаторы скролла вкладок
        int availableW = tabEndX - tabStartX;
        int totalTabsWidth = 0;
        for (String ns : modNamespaces) {
            totalTabsWidth += this.font.width(modDisplayNames.get(ns)) + 12 + tabGap;
        }
        if (totalTabsWidth > availableW) {
            if (tabScrollOffset > 0) {
                g.drawString(this.font, "\u25C4", tabStartX - 10, tabY + 6,
                        NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF, false);
            }
            if (tabScrollOffset < totalTabsWidth - availableW) {
                g.drawString(this.font, "\u25BA", tabEndX + 2, tabY + 6,
                        NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF, false);
            }
        }
    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        if (filteredEntries.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.questnpc.item_catalog.no_results"),
                    this.width / 2, this.height / 2,
                    NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF);
            return;
        }

        int stride = CELL_SIZE + CELL_GAP;
        g.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);

        for (int i = 0; i < filteredEntries.size(); i++) {
            int col = i % columns;
            int row = i / columns;

            int cellX = gridX + col * stride;
            int cellY = gridY + row * stride - (int) scrollOffset;

            if (cellY + CELL_SIZE < gridY || cellY > gridY + gridH) continue;

            ItemEntry entry = filteredEntries.get(i);
            boolean isSelected = entry.item == selectedItem;
            boolean isHovered = mouseX >= cellX && mouseX < cellX + CELL_SIZE
                    && mouseY >= cellY && mouseY < cellY + CELL_SIZE
                    && mouseY >= gridY && mouseY < gridY + gridH;

            int bg = isSelected ? CELL_SELECTED : (isHovered ? CELL_HOVER : CELL_BG);
            g.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, bg);
            int border = isSelected ? CELL_SEL_BORDER : CELL_BORDER;
            NPCMenuScreen.drawOutlineRect(g, cellX, cellY, CELL_SIZE, CELL_SIZE, border);

            // Иконка предмета
            ItemStack stack = new ItemStack(entry.item);
            int iconX = cellX + 2;
            int iconY = cellY + 2;
            try {
                g.renderItem(stack, iconX, iconY);
                g.renderItemDecorations(this.font, stack, iconX, iconY);
            } catch (Exception e) {
                g.drawCenteredString(this.font, "?",
                        cellX + CELL_SIZE / 2, cellY + CELL_SIZE / 2 - 4,
                        NPCMenuScreen.TEXT_RED & 0x00FFFFFF);
            }
        }

        g.disableScissor();
    }

    private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        if (filteredEntries.isEmpty()) return;

        int totalRows = (filteredEntries.size() + columns - 1) / columns;
        int stride = CELL_SIZE + CELL_GAP;
        int totalContentH = totalRows * stride;
        if (totalContentH <= gridH) return;

        g.fill(scrollbarX, gridY, scrollbarX + SCROLLBAR_W, gridY + gridH, SCROLLBAR_BG);

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

    private void renderFooterSelection(GuiGraphics g, int mouseX, int mouseY) {
        int footerY = this.height - FOOTER_HEIGHT;
        int lineY = footerY + 4;
        int iconX = 10;

        String label;
        if (selectedItem == null || selectedItem == Items.AIR) {
            label = Component.translatable("gui.questnpc.item_catalog.none_selected").getString();
            g.drawString(this.font, label, iconX, lineY + 4,
                    NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);
        } else {
            ItemStack stack = new ItemStack(selectedItem);
            try {
                g.renderItem(stack, iconX, lineY);
            } catch (Exception ignored) {}

            String name = stack.getHoverName().getString();
            ResourceLocation rl = ForgeRegistries.ITEMS.getKey(selectedItem);
            String idStr = rl != null ? rl.toString() : "?";
            String prefix = Component.translatable("gui.questnpc.item_catalog.selected", name).getString();
            g.drawString(this.font, prefix, iconX + 22, lineY,
                    NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF, false);
            g.drawString(this.font, idStr, iconX + 22, lineY + 10,
                    NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);
        }
    }

    private void renderHoveredTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (filteredEntries.isEmpty()) return;
        if (mouseX < gridX || mouseX >= gridX + gridW
                || mouseY < gridY || mouseY >= gridY + gridH) return;

        int stride = CELL_SIZE + CELL_GAP;
        int relX = mouseX - gridX;
        int relY = (int) (mouseY - gridY + scrollOffset);
        int col = relX / stride;
        int row = relY / stride;
        if (col >= columns) return;
        if (relX % stride >= CELL_SIZE) return;
        if (relY % stride >= CELL_SIZE) return;

        int index = row * columns + col;
        if (index < 0 || index >= filteredEntries.size()) return;

        ItemEntry entry = filteredEntries.get(index);
        ItemStack stack = new ItemStack(entry.item);
        try {
            g.renderTooltip(this.font, stack, mouseX, mouseY);
        } catch (Exception ignored) {
            // Некоторые моды бросают при построении тултипа — игнорируем
        }
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
                if (mouseX >= Math.max(currentX, tabStartX)
                        && mouseX < Math.min(currentX + tabW, tabEndX)) {
                    activeModIndex = i;
                    filterEntries();
                    scrollOffset = 0;
                    return true;
                }
                currentX += tabW + tabGap;
            }
        }

        // Клик по скроллбару
        if (mouseX >= scrollbarX && mouseX < scrollbarX + SCROLLBAR_W
                && mouseY >= gridY && mouseY < gridY + gridH) {
            scrollbarDragging = true;
            return true;
        }

        // Клик по ячейке сетки
        if (mouseX >= gridX && mouseX < gridX + gridW && mouseY >= gridY && mouseY < gridY + gridH) {
            int stride = CELL_SIZE + CELL_GAP;
            int relX = (int) mouseX - gridX;
            int relY = (int) (mouseY - gridY + scrollOffset);
            int col = relX / stride;
            int row = relY / stride;
            if (col >= columns) return false;
            if (relX % stride >= CELL_SIZE) return false;
            if (relY % stride >= CELL_SIZE) return false;

            int index = row * columns + col;
            if (index >= 0 && index < filteredEntries.size()) {
                ItemEntry clicked = filteredEntries.get(index);
                if (clicked.item == selectedItem) {
                    // Двойной клик = подтверждение
                    confirmSelection();
                } else {
                    selectedItem = clicked.item;
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
            int totalRows = (filteredEntries.size() + columns - 1) / columns;
            int stride = CELL_SIZE + CELL_GAP;
            int totalContentH = totalRows * stride;
            int maxScroll = Math.max(0, totalContentH - gridH);
            float ratio = (float) (mouseY - gridY) / gridH;
            scrollOffset = Math.max(0, Math.min(maxScroll, ratio * maxScroll));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // Скролл вкладок
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
        int totalRows = (filteredEntries.size() + columns - 1) / columns;
        int stride = CELL_SIZE + CELL_GAP;
        int totalContentH = totalRows * stride;
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
        if (onConfirm != null) {
            // v2.8.0: новый путь (Экипировка и др.) — отдаём выбор через callback.
            onConfirm.accept(selectedItem);
        } else if (tradeParent != null) {
            // Legacy путь — NPCTradingScreen.
            tradeParent.setPendingItemSelection(globalSlotId, selectedItem);
        }
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ═══════════════════════════════════════════════
    // ВСПОМОГАТЕЛЬНЫЙ КЛАСС
    // ═══════════════════════════════════════════════

    static class ItemEntry {
        final ResourceLocation id;
        final Item item;
        final String displayName;

        ItemEntry(ResourceLocation id, Item item, String displayName) {
            this.id = id;
            this.item = item;
            this.displayName = displayName;
        }
    }
}
