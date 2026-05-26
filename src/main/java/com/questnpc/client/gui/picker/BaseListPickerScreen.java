package com.questnpc.client.gui.picker;

import com.questnpc.client.gui.NPCMenuScreen;
import com.questnpc.client.gui.widget.DarkButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Абстрактный full-screen picker со списком текстовых строк (без иконок и mod-вкладок).
 * Базовый класс для {@link BiomePickerScreen}, {@link StructurePickerScreen},
 * {@link EntityTagPickerScreen}, {@link BlockTagPickerScreen}.
 *
 * <p>Layout: header с поиском, вертикальный список с скроллом, footer с
 * Back / Clear / Select. Подклассы реализуют:
 * <ul>
 *   <li>{@link #buildEntries()} — источник данных</li>
 *   <li>{@link #displayName(Object)} — основная строка</li>
 *   <li>{@link #subText(Object)} — вспомогательная строка серым (опционально)</li>
 *   <li>{@link #idOf(Object)} — конвертация {@code T} → {@code String}-key для callback</li>
 *   <li>{@link #screenTitleKey()} — lang-ключ заголовка</li>
 * </ul>
 *
 * <p>Колбэк {@link #onConfirm} получает выбранный {@code T} (или null если очищено).
 *
 * @param <T> тип элемента (например, {@code ResourceLocation} для biome/structure,
 *            {@code TagKey<X>} для тегов).
 */
public abstract class BaseListPickerScreen<T> extends Screen {

    protected static final int BG_FULL         = 0xFF0D1117;
    protected static final int ROW_BG          = 0xFF1F2937;
    protected static final int ROW_HOVER       = 0xFF2D3748;
    protected static final int ROW_SELECTED    = 0xFF065F46;
    protected static final int ROW_BORDER      = 0xFF4A5568;
    protected static final int ROW_SEL_BORDER  = 0xFF2DD4BF;
    protected static final int SCROLLBAR_BG    = 0xFF1A1A2E;
    protected static final int SCROLLBAR_FG    = 0xFF4A5568;
    protected static final int SCROLLBAR_HOVER = 0xFF6B7280;
    protected static final int HEADER_BG       = 0xFF1A1A2E;

    protected static final int HEADER_HEIGHT = 32;
    protected static final int FOOTER_HEIGHT = 46;
    protected static final int ROW_HEIGHT    = 22;
    protected static final int ROW_GAP       = 2;
    protected static final int SCROLLBAR_W   = 8;
    protected static final int SEARCH_WIDTH  = 200;

    protected final Screen parent;
    protected final Consumer<T> onConfirm;
    @Nullable protected T selected;

    protected EditBox searchBox;
    protected List<T> allEntries = new ArrayList<>();
    protected List<T> filteredEntries = new ArrayList<>();

    protected double scrollOffset = 0;
    protected boolean scrollbarDragging = false;

    protected int listX, listY, listW, listH;
    protected int scrollbarX;

    protected BaseListPickerScreen(Screen parent, @Nullable T initialSelection,
                                   Consumer<T> onConfirm) {
        super(Component.empty());
        this.parent = parent;
        this.selected = initialSelection;
        this.onConfirm = onConfirm;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Абстрактные методы
    // ═══════════════════════════════════════════════════════════════════

    /** Источник данных (вызывается один раз в init()). Может вернуть пустой список — тогда
     *  picker покажет empty-placeholder. */
    protected abstract List<T> buildEntries();

    /** Основная строка (slug или translated name). */
    protected abstract String displayName(T entry);

    /** Опциональная вспомогательная строка (например, namespace). null = не показывать. */
    @Nullable protected String subText(T entry) { return null; }

    /** Lang-ключ заголовка picker'а. */
    protected abstract String screenTitleKey();

    // ═══════════════════════════════════════════════════════════════════
    // Init
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();

        if (allEntries.isEmpty()) {
            allEntries = buildEntries();
            if (allEntries == null) allEntries = Collections.emptyList();
        }

        int padding = 10;
        int listMaxW = 500;
        listW = Math.min(listMaxW, this.width - padding * 2 - SCROLLBAR_W - 4);
        listX = (this.width - listW - SCROLLBAR_W - 4) / 2;
        listY = HEADER_HEIGHT + 4;
        listH = this.height - HEADER_HEIGHT - FOOTER_HEIGHT - 8;
        scrollbarX = listX + listW + 4;

        searchBox = new EditBox(this.font, padding, 6, SEARCH_WIDTH, 18,
                Component.translatable("gui.questnpc.pickers.search.placeholder"));
        searchBox.setHint(Component.translatable("gui.questnpc.pickers.search.placeholder"));
        searchBox.setMaxLength(80);
        searchBox.setBordered(false);
        searchBox.setResponder(q -> { filterEntries(); scrollOffset = 0; });
        this.addRenderableWidget(searchBox);

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
                btn -> { selected = null; confirmSelection(); },
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

    protected void filterEntries() {
        String q = searchBox != null ? searchBox.getValue().trim().toLowerCase() : "";
        if (q.isEmpty()) {
            filteredEntries = new ArrayList<>(allEntries);
        } else {
            filteredEntries = allEntries.stream()
                    .filter(e -> displayName(e).toLowerCase().contains(q)
                            || (subText(e) != null && subText(e).toLowerCase().contains(q)))
                    .collect(Collectors.toList());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Render
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, BG_FULL);

        g.fill(0, 0, this.width, HEADER_HEIGHT, HEADER_BG);
        NPCMenuScreen.drawEditBoxBg(g, 8, 4, SEARCH_WIDTH + 4, 22, true);
        g.drawString(this.font,
                Component.translatable(screenTitleKey()),
                SEARCH_WIDTH + 24, 12,
                NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF, false);
        g.fill(0, HEADER_HEIGHT, this.width, HEADER_HEIGHT + 1, NPCMenuScreen.BORDER);

        renderList(g, mouseX, mouseY);
        renderScrollbar(g, mouseX, mouseY);

        int footerY = this.height - FOOTER_HEIGHT;
        g.fill(0, footerY, this.width, footerY + 1, NPCMenuScreen.BORDER);
        renderFooter(g);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        if (allEntries.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.questnpc.pickers.empty"),
                    this.width / 2, this.height / 2,
                    NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF);
            return;
        }
        if (filteredEntries.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.questnpc.item_catalog.no_results"),
                    this.width / 2, this.height / 2,
                    NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF);
            return;
        }
        int stride = ROW_HEIGHT + ROW_GAP;
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < filteredEntries.size(); i++) {
            int rowY = listY + i * stride - (int) scrollOffset;
            if (rowY + ROW_HEIGHT < listY || rowY > listY + listH) continue;

            T entry = filteredEntries.get(i);
            boolean isSelected = entry.equals(selected);
            boolean isHovered = mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
                    && mouseY >= listY && mouseY < listY + listH;
            int bg = isSelected ? ROW_SELECTED : (isHovered ? ROW_HOVER : ROW_BG);
            g.fill(listX, rowY, listX + listW, rowY + ROW_HEIGHT, bg);
            int border = isSelected ? ROW_SEL_BORDER : ROW_BORDER;
            NPCMenuScreen.drawOutlineRect(g, listX, rowY, listW, ROW_HEIGHT, border);

            String name = displayName(entry);
            g.drawString(this.font, name, listX + 6, rowY + 4,
                    NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF, false);
            String sub = subText(entry);
            if (sub != null) {
                g.drawString(this.font, sub, listX + 6, rowY + 13,
                        NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);
            }
        }
        g.disableScissor();
    }

    private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        int stride = ROW_HEIGHT + ROW_GAP;
        int totalContentH = filteredEntries.size() * stride;
        if (totalContentH <= listH) return;

        g.fill(scrollbarX, listY, scrollbarX + SCROLLBAR_W, listY + listH, SCROLLBAR_BG);
        float visibleRatio = (float) listH / totalContentH;
        int thumbH = Math.max(20, (int) (listH * visibleRatio));
        int maxScroll = totalContentH - listH;
        float scrollRatio = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
        int thumbY = listY + (int) ((listH - thumbH) * scrollRatio);
        boolean thumbHovered = mouseX >= scrollbarX && mouseX < scrollbarX + SCROLLBAR_W
                && mouseY >= thumbY && mouseY < thumbY + thumbH;
        int thumbColor = (thumbHovered || scrollbarDragging) ? SCROLLBAR_HOVER : SCROLLBAR_FG;
        g.fill(scrollbarX, thumbY, scrollbarX + SCROLLBAR_W, thumbY + thumbH, thumbColor);
    }

    private void renderFooter(GuiGraphics g) {
        int footerY = this.height - FOOTER_HEIGHT;
        if (selected == null) {
            g.drawString(this.font,
                    Component.translatable("gui.questnpc.quests.objective.not_selected"),
                    10, footerY + 8, NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);
        } else {
            String name = displayName(selected);
            g.drawString(this.font, name, 10, footerY + 4,
                    NPCMenuScreen.TEXT_WHITE & 0x00FFFFFF, false);
            String sub = subText(selected);
            if (sub != null) {
                g.drawString(this.font, sub, 10, footerY + 18,
                        NPCMenuScreen.TEXT_GRAY & 0x00FFFFFF, false);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Input
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        if (mouseX >= scrollbarX && mouseX < scrollbarX + SCROLLBAR_W
                && mouseY >= listY && mouseY < listY + listH) {
            scrollbarDragging = true;
            return true;
        }
        if (mouseX >= listX && mouseX < listX + listW
                && mouseY >= listY && mouseY < listY + listH) {
            int stride = ROW_HEIGHT + ROW_GAP;
            int relY = (int) (mouseY - listY + scrollOffset);
            int index = relY / stride;
            if (relY % stride >= ROW_HEIGHT) return false;
            if (index >= 0 && index < filteredEntries.size()) {
                T clicked = filteredEntries.get(index);
                if (clicked.equals(selected)) {
                    confirmSelection();
                } else {
                    selected = clicked;
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
            int stride = ROW_HEIGHT + ROW_GAP;
            int totalContentH = filteredEntries.size() * stride;
            int maxScroll = Math.max(0, totalContentH - listH);
            float ratio = (float) (mouseY - listY) / listH;
            scrollOffset = Math.max(0, Math.min(maxScroll, ratio * maxScroll));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int stride = ROW_HEIGHT + ROW_GAP;
        int totalContentH = filteredEntries.size() * stride;
        int maxScroll = Math.max(0, totalContentH - listH);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta * 30));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    protected void confirmSelection() {
        onConfirm.accept(selected);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
