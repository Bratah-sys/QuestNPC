package com.questnpc.client.gui.picker;

import com.questnpc.client.gui.NPCMenuScreen;
import com.questnpc.client.gui.widget.DarkButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Абстрактный full-screen picker с сеткой иконок, mod-вкладками и поиском.
 * Базовый класс для {@link EntityCatalogScreen}, {@link BlockCatalogScreen}.
 *
 * <p>Дистиллят паттернов из {@link com.questnpc.client.gui.ItemCatalogScreen} и
 * {@link com.questnpc.client.gui.ModelCatalogScreen}: header с поиском, вкладки модов
 * (vanilla + alphabetical), сетка ячеек с виртуальным скроллом, scrollbar.
 *
 * <p>Подклассы реализуют 3 метода: {@link #buildEntries()}, {@link #renderIcon},
 * {@link #screenTitle()}. Колбэк выбора передаётся в ctor.
 *
 * @param <T> тип элемента, который picker возвращает (например, {@code EntityType<?>}
 *            или {@code Block}). Колбэк {@link #onConfirm} получает {@link ResourceLocation}
 *            выбранного элемента — конвертация T → ResourceLocation в {@link Entry}.
 */
public abstract class BaseGridPickerScreen<T> extends Screen {

    // ═══ Цвета (повторяют ItemCatalogScreen/ModelCatalogScreen) ═══
    protected static final int BG_FULL         = 0xFF0D1117;
    protected static final int CELL_BG         = 0xFF1F2937;
    protected static final int CELL_HOVER      = 0xFF2D3748;
    protected static final int CELL_SELECTED   = 0xFF065F46;
    protected static final int CELL_BORDER     = 0xFF4A5568;
    protected static final int CELL_SEL_BORDER = 0xFF2DD4BF;
    protected static final int TAB_BG          = 0xFF1A1A2E;
    protected static final int TAB_ACTIVE      = 0xFF2563EB;
    protected static final int TAB_HOVER       = 0xFF374151;
    protected static final int SCROLLBAR_BG    = 0xFF1A1A2E;
    protected static final int SCROLLBAR_FG    = 0xFF4A5568;
    protected static final int SCROLLBAR_HOVER = 0xFF6B7280;

    // ═══ Размеры ═══
    protected static final int HEADER_HEIGHT = 32;
    protected static final int FOOTER_HEIGHT = 46;
    protected static final int CELL_SIZE     = 20;
    protected static final int CELL_GAP      = 2;
    protected static final int SCROLLBAR_W   = 8;
    protected static final int TAB_HEIGHT    = 20;
    protected static final int SEARCH_WIDTH  = 120;

    /** Имена вкладок (namespace) — vanilla сначала, остальные алфавитно. */
    protected final List<String> modNamespaces = new ArrayList<>();
    protected final Map<String, String> modDisplayNames = new HashMap<>();
    protected int activeModIndex = 0;

    /** Полный индекс по namespace, строится в init() через {@link #buildEntries()}. */
    protected final Map<String, List<Entry<T>>> allEntries = new LinkedHashMap<>();
    /** Отфильтрованный список текущей вкладки. */
    protected List<Entry<T>> filteredEntries = new ArrayList<>();

    protected final Screen parent;
    protected final Consumer<ResourceLocation> onConfirm;
    @Nullable protected final ResourceLocation initialSelection;
    @Nullable protected ResourceLocation selectedId;

    protected EditBox searchBox;
    protected double scrollOffset = 0;
    protected boolean scrollbarDragging = false;
    protected int tabScrollOffset = 0;
    protected int gridX, gridY, gridW, gridH;
    protected int scrollbarX;
    protected int columns;

    protected BaseGridPickerScreen(Screen parent,
                                   @Nullable ResourceLocation initialSelection,
                                   Consumer<ResourceLocation> onConfirm) {
        super(Component.empty()); // подкласс задаёт через screenTitle()
        this.parent = parent;
        this.initialSelection = initialSelection;
        this.selectedId = initialSelection;
        this.onConfirm = onConfirm;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Абстрактные методы для подкласса
    // ═══════════════════════════════════════════════════════════════════

    /** Строит полный список entries (вызывается один раз в init() при первом открытии). */
    protected abstract List<Entry<T>> buildEntries();

    /** Рендерит иконку 16×16 для одной entry в ячейке (x,y — верхний левый угол иконки). */
    protected abstract void renderIcon(GuiGraphics g, Entry<T> entry, int x, int y);

    /** Опциональный тултип при наведении (по умолчанию — displayName + id). */
    protected void renderHoverTooltip(GuiGraphics g, Entry<T> entry, int mouseX, int mouseY) {
        g.renderTooltip(this.font,
                List.of(Component.literal(entry.displayName),
                        Component.literal(entry.id.toString()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY)),
                java.util.Optional.empty(),
                mouseX, mouseY);
    }

    /** Lang-ключ заголовка picker'а (показывается в шапке). */
    protected abstract String screenTitleKey();

    // ═══════════════════════════════════════════════════════════════════
    // Инициализация
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();

        if (allEntries.isEmpty()) {
            indexEntries();
        }

        // Layout
        int padding = 10;
        int availableW = this.width - padding * 2 - SCROLLBAR_W - 4;
        columns = Math.max(8, availableW / (CELL_SIZE + CELL_GAP));
        int totalGridW = columns * (CELL_SIZE + CELL_GAP) - CELL_GAP;

        gridX = (this.width - totalGridW - SCROLLBAR_W - 4) / 2;
        gridY = HEADER_HEIGHT + 4;
        gridW = totalGridW;
        gridH = this.height - HEADER_HEIGHT - FOOTER_HEIGHT - 8;
        scrollbarX = gridX + gridW + 4;

        // Search box
        searchBox = new EditBox(this.font, padding, 6, SEARCH_WIDTH, 18,
                Component.translatable("gui.questnpc.pickers.search.placeholder"));
        searchBox.setHint(Component.translatable("gui.questnpc.pickers.search.placeholder"));
        searchBox.setMaxLength(50);
        searchBox.setBordered(false);
        searchBox.setResponder(q -> { filterEntries(); scrollOffset = 0; });
        this.addRenderableWidget(searchBox);

        // Set active tab to namespace of initial selection (if any)
        if (initialSelection != null) {
            int idx = modNamespaces.indexOf(initialSelection.getNamespace());
            if (idx >= 0) activeModIndex = idx;
        }

        // Footer buttons: Back / Clear / Select
        int btnW = 90;
        int btnH = 20;
        int btnY = this.height - 26;
        int centerX = this.width / 2;

        this.addRenderableWidget(new DarkButton(
                centerX - btnW - 100, btnY, btnW, btnH,
                Component.translatable("gui.questnpc.pickers.back"),
                btn -> Minecraft.getInstance().setScreen(parent)
        ));
        this.addRenderableWidget(new DarkButton(
                centerX - btnW / 2, btnY, btnW, btnH,
                Component.translatable("gui.questnpc.item_catalog.clear"),
                btn -> { selectedId = null; confirmSelection(); },
                NPCMenuScreen.BTN_GRAY_BG, 0xFF7F1D1D, NPCMenuScreen.TEXT_WHITE
        ));
        this.addRenderableWidget(new DarkButton(
                centerX + 100, btnY, btnW, btnH,
                Component.translatable("gui.questnpc.item_catalog.select"),
                btn -> confirmSelection(),
                NPCMenuScreen.BTN_GREEN_BG, NPCMenuScreen.BTN_GREEN_HOVER, 0xFFFFFFFF
        ));

        filterEntries();
    }

    private void indexEntries() {
        List<Entry<T>> all = buildEntries();
        Map<String, List<Entry<T>>> grouped = new TreeMap<>();
        for (Entry<T> e : all) {
            grouped.computeIfAbsent(e.id.getNamespace(), k -> new ArrayList<>()).add(e);
        }
        for (List<Entry<T>> list : grouped.values()) {
            list.sort(Comparator.comparing(e -> e.displayName.toLowerCase()));
        }

        allEntries.clear();
        modNamespaces.clear();
        modDisplayNames.clear();

        if (grouped.containsKey("minecraft")) {
            modNamespaces.add("minecraft");
            modDisplayNames.put("minecraft",
                    Component.translatable("gui.questnpc.item_catalog.vanilla").getString());
            allEntries.put("minecraft", grouped.get("minecraft"));
        }
        for (String ns : grouped.keySet()) {
            if (ns.equals("minecraft")) continue;
            modNamespaces.add(ns);
            String displayName = ModList.get().getModContainerById(ns)
                    .map(c -> c.getModInfo().getDisplayName()).orElse(ns);
            modDisplayNames.put(ns, displayName);
            allEntries.put(ns, grouped.get(ns));
        }
    }

    protected void filterEntries() {
        if (modNamespaces.isEmpty()) {
            filteredEntries = Collections.emptyList();
            return;
        }
        String namespace = modNamespaces.get(activeModIndex);
        List<Entry<T>> all = allEntries.getOrDefault(namespace, Collections.emptyList());
        String query = searchBox != null ? searchBox.getValue().trim().toLowerCase() : "";
        if (query.isEmpty()) {
            filteredEntries = new ArrayList<>(all);
        } else {
            filteredEntries = all.stream()
                    .filter(e -> e.displayName.toLowerCase().contains(query)
                            || e.id.getPath().contains(query))
                    .collect(Collectors.toList());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Render
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, BG_FULL);
        renderHeader(g, mouseX, mouseY);
        g.fill(0, HEADER_HEIGHT, this.width, HEADER_HEIGHT + 1, NPCMenuScreen.BORDER);

        renderGrid(g, mouseX, mouseY);
        renderScrollbar(g, mouseX, mouseY);

        int footerY = this.height - FOOTER_HEIGHT;
        g.fill(0, footerY, this.width, footerY + 1, NPCMenuScreen.BORDER);
        renderFooter(g);

        super.render(g, mouseX, mouseY, partialTick);
        renderHoveredCellTooltip(g, mouseX, mouseY);
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
            if (i == activeModIndex) bg = TAB_ACTIVE;
            else if (mouseX >= drawX && mouseX < drawEndX && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) bg = TAB_HOVER;
            else bg = CELL_BG;
            g.fill(currentX, tabY, currentX + tabW, tabY + TAB_HEIGHT, bg);
            NPCMenuScreen.drawOutlineRect(g, currentX, tabY, tabW, TAB_HEIGHT, CELL_BORDER);
            int textX = currentX + (tabW - this.font.width(displayName)) / 2;
            int textY = tabY + (TAB_HEIGHT - 8) / 2;
            g.drawString(this.font, displayName, textX, textY,
                    NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF, false);
            currentX += tabW + tabGap;
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

            Entry<T> entry = filteredEntries.get(i);
            boolean isSelected = entry.id.equals(selectedId);
            boolean isHovered = mouseX >= cellX && mouseX < cellX + CELL_SIZE
                    && mouseY >= cellY && mouseY < cellY + CELL_SIZE
                    && mouseY >= gridY && mouseY < gridY + gridH;
            int bg = isSelected ? CELL_SELECTED : (isHovered ? CELL_HOVER : CELL_BG);
            g.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, bg);
            int border = isSelected ? CELL_SEL_BORDER : CELL_BORDER;
            NPCMenuScreen.drawOutlineRect(g, cellX, cellY, CELL_SIZE, CELL_SIZE, border);

            try { renderIcon(g, entry, cellX + 2, cellY + 2); }
            catch (Exception e) {
                g.drawCenteredString(this.font, "?", cellX + CELL_SIZE / 2, cellY + CELL_SIZE / 2 - 4,
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

    private void renderFooter(GuiGraphics g) {
        int footerY = this.height - FOOTER_HEIGHT;
        String title = Component.translatable(screenTitleKey()).getString();
        g.drawString(this.font, title, 10, footerY + 4, NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF, false);

        if (selectedId == null) {
            g.drawString(this.font,
                    Component.translatable("gui.questnpc.quests.objective.not_selected"),
                    10, footerY + 18, NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);
        } else {
            g.drawString(this.font, selectedId.toString(),
                    10, footerY + 18, NPCMenuScreen.TEXT_CYAN & 0x00FFFFFF, false);
        }
    }

    private void renderHoveredCellTooltip(GuiGraphics g, int mouseX, int mouseY) {
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
        try { renderHoverTooltip(g, filteredEntries.get(index), mouseX, mouseY); }
        catch (Exception ignored) {}
    }

    // ═══════════════════════════════════════════════════════════════════
    // Input
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        // Tab click
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
        // Scrollbar drag start
        if (mouseX >= scrollbarX && mouseX < scrollbarX + SCROLLBAR_W
                && mouseY >= gridY && mouseY < gridY + gridH) {
            scrollbarDragging = true;
            return true;
        }
        // Cell click
        if (mouseX >= gridX && mouseX < gridX + gridW
                && mouseY >= gridY && mouseY < gridY + gridH) {
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
                Entry<T> clicked = filteredEntries.get(index);
                if (clicked.id.equals(selectedId)) {
                    confirmSelection(); // double-click = confirm
                } else {
                    selectedId = clicked.id;
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
        if (mouseY < HEADER_HEIGHT && mouseX > SEARCH_WIDTH + 20 && mouseX < this.width - 30) {
            int tabGap = 4;
            int totalTabsWidth = 0;
            for (String ns : modNamespaces) totalTabsWidth += this.font.width(modDisplayNames.get(ns)) + 12 + tabGap;
            int availableW = this.width - 30 - SEARCH_WIDTH - 20;
            int maxTabScroll = Math.max(0, totalTabsWidth - availableW);
            tabScrollOffset = (int) Math.max(0, Math.min(maxTabScroll, tabScrollOffset - delta * 20));
            return true;
        }
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

    protected void confirmSelection() {
        onConfirm.accept(selectedId);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ═══════════════════════════════════════════════════════════════════
    // Entry — wrapper для отображения T в гриде
    // ═══════════════════════════════════════════════════════════════════
    public static final class Entry<T> {
        public final ResourceLocation id;
        public final T value;
        public final String displayName;
        public Entry(ResourceLocation id, T value, String displayName) {
            this.id = id;
            this.value = value;
            this.displayName = displayName;
        }
    }
}
