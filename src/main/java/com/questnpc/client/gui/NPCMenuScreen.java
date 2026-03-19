package com.questnpc.client.gui;

import com.questnpc.QuestNPCLogger;
import com.questnpc.client.gui.widget.DarkButton;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.network.ChangeModelPacket;
import com.questnpc.network.CloseMenuPacket;
import com.questnpc.network.DeleteNPCPacket;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.RenameNPCPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Двухпанельное меню NPC в тёмном стиле.
 * Левая панель: превью модели + инфо. Правая панель: сетка кнопок-разделов.
 */
public class NPCMenuScreen extends Screen {

    // ═══ Цветовая палитра ═══
    static final int BG_OVERLAY     = 0xC0000000;
    static final int BG_DARK        = 0xFF1A1A2E;
    static final int SECTION_BG     = 0xFF1F2937;
    static final int BORDER         = 0xFF4A5568;
    static final int TEXT_WHITE     = 0xFFE2E8F0;
    static final int TEXT_GRAY      = 0xFF9CA3AF;
    static final int TEXT_DARK_GRAY = 0xFF6B7280;
    static final int TEXT_CYAN      = 0xFF2DD4BF;
    static final int TEXT_RED       = 0xFFFF5555;
    static final int EDIT_BG        = 0xFF111827;
    static final int SECTION_TITLE  = 0xFF3B82F6;
    static final int BTN_GREEN_BG   = 0xFF10B981;
    static final int BTN_GREEN_HOVER= 0xFF22C55E;
    static final int BTN_GRAY_BG    = 0xFF374151;
    static final int BTN_GRAY_HOVER = 0xFF4B5563;
    private static final int BTN_RED_BG     = 0xFF7F1D1D;
    private static final int BTN_RED_HOVER  = 0xFFB91C1C;

    // ═══ Размеры ═══
    private static final int TOTAL_WIDTH  = 420;
    private static final int TOTAL_HEIGHT = 280;
    private static final int LEFT_WIDTH   = 150;
    private static final int PADDING      = 8;

    // ═══ Данные ═══
    private final QuestNPCEntity npc;
    private final double currentSpeed;
    private final int currentDelayMin;
    private final int currentDelayMax;
    private final String currentModelType;

    // ═══ Виджеты ═══
    private EditBox nameField;
    private DarkButton deleteBtn;

    // ═══ Состояние ═══
    private boolean deleteConfirm = false;
    private int panelX, panelY;

    /** true когда переходим на подэкран — подавляет отправку CloseMenuPacket */
    private boolean navigatingToSubScreen = false;

    // ═══ Выбор модели из каталога (ожидает "Применить") ═══
    @Nullable
    private ResourceLocation pendingModelType;
    @Nullable
    private LivingEntity previewEntity;

    public NPCMenuScreen(QuestNPCEntity npc, double speed, int delayMin, int delayMax, String modelType) {
        super(Component.translatable("gui.questnpc.menu.title"));
        this.npc = npc;
        this.currentSpeed = speed;
        this.currentDelayMin = delayMin;
        this.currentDelayMax = delayMax;
        this.currentModelType = modelType != null ? modelType : "";
    }

    /**
     * Устанавливает выбранную модель из каталога. Вызывается из ModelCatalogScreen.
     */
    public void setPendingModelType(@Nullable ResourceLocation modelType) {
        this.pendingModelType = modelType;
        // Сбрасываем кэш превью
        this.previewEntity = null;
    }

    @Override
    protected void init() {
        super.init();

        // Сброс флагов при возврате из подэкрана
        navigatingToSubScreen = false;
        closeSent = false;

        panelX = (this.width - TOTAL_WIDTH) / 2;
        panelY = (this.height - TOTAL_HEIGHT) / 2;

        int rightX = panelX + LEFT_WIDTH + 1;
        int rightW = TOTAL_WIDTH - LEFT_WIDTH - 1;

        // ═══ Правая панель: поле имени ═══
        int nameY = panelY + 30;
        int nameFieldW = rightW / 2 - PADDING;
        nameField = new EditBox(this.font, rightX + PADDING, nameY, nameFieldW, 16,
                Component.literal("Name"));
        nameField.setMaxLength(32);
        String currentName = npc.hasCustomName() ? npc.getCustomName().getString() : npc.getName().getString();
        nameField.setValue(currentName);
        nameField.setBordered(false);
        this.addRenderableWidget(nameField);

        // Кнопка подтверждения имени
        this.addRenderableWidget(new DarkButton(
            rightX + PADDING + nameFieldW + 4, nameY - 1, 18, 18,
            Component.literal("\u2713"),
            button -> applyName(),
            BTN_GREEN_BG, BTN_GREEN_HOVER, 0xFFFFFFFF
        ));

        // ═══ Правая панель: сетка кнопок ═══
        int gridStartY = nameY + 24;
        int btnW = (rightW - PADDING * 3) / 2;
        int btnH = 20;
        int gap = 4;

        String[][] gridButtons = {
            {"gui.questnpc.menu.btn.import",    "gui.questnpc.menu.btn.export"},
            {"gui.questnpc.menu.btn.actions",   "gui.questnpc.menu.btn.attributes"},
            {"gui.questnpc.menu.btn.dialog",    "gui.questnpc.menu.btn.equipment"},
            {"gui.questnpc.menu.btn.goals",     "gui.questnpc.menu.btn.pose"},
            {"gui.questnpc.menu.btn.position",  "gui.questnpc.menu.btn.rotation"},
            {"gui.questnpc.menu.btn.scale",     "gui.questnpc.menu.btn.trading"},
        };

        for (int row = 0; row < gridButtons.length; row++) {
            int rowY = gridStartY + row * (btnH + gap);
            for (int col = 0; col < 2; col++) {
                int btnX = rightX + PADDING + col * (btnW + PADDING);
                String key = gridButtons[row][col];

                // Рабочие кнопки: Атрибуты и Позиция
                Button.OnPress action;
                if (key.equals("gui.questnpc.menu.btn.attributes")) {
                    action = button -> {
                        navigatingToSubScreen = true;
                        Minecraft.getInstance().setScreen(
                            new NPCAttributesScreen(npc, currentSpeed, currentDelayMin, currentDelayMax, this));
                    };
                } else if (key.equals("gui.questnpc.menu.btn.position")) {
                    action = button -> {
                        navigatingToSubScreen = true;
                        Minecraft.getInstance().setScreen(
                            new NPCPositionScreen(npc, this));
                    };
                } else {
                    action = button -> {}; // заглушка
                }

                this.addRenderableWidget(new DarkButton(
                    btnX, rowY, btnW, btnH,
                    Component.translatable(key), action
                ));
            }
        }

        // ═══ Левая панель: кнопки ═══
        int leftBtnW = LEFT_WIDTH - PADDING * 2;
        int leftBtnY = panelY + TOTAL_HEIGHT - 60;
        this.addRenderableWidget(new DarkButton(
            panelX + PADDING, leftBtnY, leftBtnW, 16,
            Component.translatable("gui.questnpc.menu.btn.change_skin"),
            button -> {}
        ));
        this.addRenderableWidget(new DarkButton(
            panelX + PADDING, leftBtnY + 20, leftBtnW, 16,
            Component.translatable("gui.questnpc.menu.btn.change_model"),
            button -> {
                // Открываем каталог моделей
                navigatingToSubScreen = true;
                ResourceLocation current = null;
                if (pendingModelType != null) {
                    current = pendingModelType;
                } else if (!currentModelType.isEmpty()) {
                    current = ResourceLocation.tryParse(currentModelType);
                }
                Minecraft.getInstance().setScreen(new ModelCatalogScreen(this, current));
            }
        ));

        // ═══ Нижняя панель: утилиты ═══
        int bottomY = panelY + TOTAL_HEIGHT - 28;
        int bottomBtnW = (TOTAL_WIDTH - PADDING * 4) / 3;

        // Копия UUID
        this.addRenderableWidget(new DarkButton(
            panelX + PADDING, bottomY, bottomBtnW, 20,
            Component.translatable("gui.questnpc.menu.btn.copy_uuid"),
            button -> {
                String uuid = npc.getUUID().toString();
                Minecraft.getInstance().keyboardHandler.setClipboard(uuid);
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(
                        Component.literal("\u00a7a[QuestNPC] ")
                            .append(Component.translatable("gui.questnpc.menu.uuid_copied")));
                }
                QuestNPCLogger.info("Игрок скопировал UUID NPC {}", npc.getId());
            },
            BTN_GREEN_BG, BTN_GREEN_HOVER, 0xFFFFFFFF
        ));

        // Применить модель (если выбрана)
        this.addRenderableWidget(new DarkButton(
            panelX + PADDING + bottomBtnW + PADDING, bottomY, bottomBtnW, 20,
            Component.translatable("gui.questnpc.npc_menu.apply"),
            button -> applyModel(),
            BTN_GREEN_BG, BTN_GREEN_HOVER, 0xFFFFFFFF
        ));

        // Удалить
        deleteBtn = this.addRenderableWidget(new DarkButton(
            panelX + PADDING + (bottomBtnW + PADDING) * 2, bottomY, bottomBtnW, 20,
            Component.translatable("gui.questnpc.menu.btn.delete"),
            button -> handleDelete(),
            BTN_RED_BG, BTN_RED_HOVER, 0xFFFFFFFF
        ));
    }

    // ═══ Логика переименования ═══
    private void applyName() {
        String name = nameField.getValue().trim();
        if (name.isEmpty() || name.length() > 32) return;
        ModNetwork.INSTANCE.sendToServer(new RenameNPCPacket(npc.getId(), name));
    }

    // ═══ Логика применения модели ═══
    private void applyModel() {
        if (pendingModelType != null) {
            ModNetwork.INSTANCE.sendToServer(new ChangeModelPacket(npc.getId(), pendingModelType.toString()));
            QuestNPCLogger.info("Отправлен запрос смены модели NPC {} на '{}'", npc.getId(), pendingModelType);
        }
    }

    // ═══ Логика удаления (двойной клик) ═══
    private void handleDelete() {
        if (!deleteConfirm) {
            deleteConfirm = true;
            deleteBtn.setMessage(Component.translatable("gui.questnpc.menu.btn.delete_confirm"));
        } else {
            ModNetwork.INSTANCE.sendToServer(new DeleteNPCPacket(npc.getId()));
            this.onClose();
        }
    }

    // ═══════════════════════════════════════════════
    // РЕНДЕРИНГ
    // ═══════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Затемнение фона
        g.fill(0, 0, this.width, this.height, BG_OVERLAY);

        // ═══ Основная панель ═══
        drawPanel(g, panelX, panelY, TOTAL_WIDTH, TOTAL_HEIGHT);

        // ═══ Разделитель между панелями ═══
        g.fill(panelX + LEFT_WIDTH, panelY + 1, panelX + LEFT_WIDTH + 1, panelY + TOTAL_HEIGHT - 1, BORDER);

        // ═══ Заголовок ═══
        g.drawCenteredString(this.font, this.title, panelX + TOTAL_WIDTH / 2, panelY + 4, TEXT_WHITE & 0x00FFFFFF);
        // UUID мелким
        String uuidStr = "UUID: " + npc.getUUID().toString();
        g.drawString(this.font, uuidStr, panelX + LEFT_WIDTH + PADDING, panelY + 16, TEXT_DARK_GRAY & 0x00FFFFFF, false);

        // ═══ Разделитель под заголовком ═══
        g.fill(panelX + 1, panelY + 26, panelX + TOTAL_WIDTH - 1, panelY + 27, BORDER);

        // ═══ Левая панель ═══
        renderLeftPanel(g, mouseX, mouseY);

        // ═══ Правая панель: фон поля имени ═══
        int rightX = panelX + LEFT_WIDTH + 1;
        int rightW = TOTAL_WIDTH - LEFT_WIDTH - 1;
        int nameY = panelY + 30;
        int nameFieldW = rightW / 2 - PADDING;
        drawEditBoxBg(g, rightX + PADDING - 2, nameY - 2, nameFieldW + 4, 20, true);

        // Registry ID
        int regIdX = rightX + PADDING + nameFieldW + 26;
        g.drawString(this.font, "entity.questnpc.quest_npc", regIdX, nameY + 4, TEXT_DARK_GRAY & 0x00FFFFFF, false);

        // ═══ Разделитель нижней панели ═══
        int bottomSepY = panelY + TOTAL_HEIGHT - 32;
        g.fill(panelX + 1, bottomSepY, panelX + TOTAL_WIDTH - 1, bottomSepY + 1, BORDER);

        // ═══ Футер ═══
        Component footer = Component.translatable("gui.questnpc.menu.footer");
        g.drawCenteredString(this.font, footer, panelX + TOTAL_WIDTH / 2, panelY + TOTAL_HEIGHT - 10, TEXT_DARK_GRAY & 0x00FFFFFF);

        // Рендер виджетов
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Рендер левой панели: имя, инфо, превью модели, позиция. */
    private void renderLeftPanel(GuiGraphics g, int mouseX, int mouseY) {
        int lx = panelX + PADDING;
        int ly = panelY + 30;

        // Имя NPC
        String npcName = npc.hasCustomName() ? npc.getCustomName().getString() : npc.getName().getString();
        g.drawString(this.font, npcName, lx, ly, TEXT_WHITE & 0x00FFFFFF, false);

        // Инфо-блок
        int infoY = ly + 14;
        String noneTxt = Component.translatable("gui.questnpc.menu.none").getString();
        g.drawString(this.font, Component.translatable("gui.questnpc.menu.owner", noneTxt),
                lx, infoY, TEXT_GRAY & 0x00FFFFFF, false);

        infoY += 11;
        BlockPos bound = npc.getBoundBlockPos();
        String homeStr = bound != null
                ? bound.getX() + ", " + bound.getY() + ", " + bound.getZ()
                : Component.translatable("gui.questnpc.menu.not_set").getString();
        g.drawString(this.font, Component.translatable("gui.questnpc.menu.home", homeStr),
                lx, infoY, TEXT_GRAY & 0x00FFFFFF, false);

        infoY += 11;
        g.drawString(this.font, Component.translatable("gui.questnpc.menu.team", noneTxt),
                lx, infoY, TEXT_GRAY & 0x00FFFFFF, false);

        infoY += 11;
        String hp = String.format("%.1f", npc.getHealth());
        String maxHp = String.format("%.1f", npc.getMaxHealth());
        g.drawString(this.font, "HP: " + hp + "/" + maxHp,
                lx, infoY, TEXT_GRAY & 0x00FFFFFF, false);

        // ═══ Превью модели NPC ═══
        int previewCenterX = panelX + LEFT_WIDTH / 2;
        int previewLeft = previewCenterX - 35;
        int previewTop = infoY + 14;
        int previewRight = previewCenterX + 35;
        int previewBottom = panelY + TOTAL_HEIGHT - 80;
        int previewSize = 40;

        // Рамка превью
        g.fill(previewLeft, previewTop, previewRight, previewBottom, SECTION_BG);
        drawOutlineRect(g, previewLeft, previewTop, previewRight - previewLeft, previewBottom - previewTop, BORDER);

        // Определяем что рендерить в превью
        LivingEntity entityToRender = getPreviewEntity();
        int renderX = previewCenterX;
        int renderY = previewBottom - 5;
        float relMouseX = renderX - mouseX;
        float relMouseY = renderY - 30 - mouseY;

        if (entityToRender != null) {
            // Рендерим выбранную модель или дефолтный NPC
            try {
                float entityH = entityToRender.getBbHeight();
                int scale = (int) Math.max(15, Math.min(previewSize, (previewBottom - previewTop - 10) / entityH * 0.8F));
                InventoryScreen.renderEntityInInventoryFollowsMouse(
                        g, renderX, renderY, scale, relMouseX, relMouseY, entityToRender);
            } catch (Exception e) {
                // Fallback: рендерим NPC
                InventoryScreen.renderEntityInInventoryFollowsMouse(
                        g, renderX, renderY, previewSize, relMouseX, relMouseY, npc);
            }
        } else {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g, renderX, renderY, previewSize, relMouseX, relMouseY, npc);
        }

        // Подпись под превью: какая модель выбрана
        int labelY = previewBottom + 2;
        if (pendingModelType != null) {
            String modelName = pendingModelType.getPath();
            g.drawCenteredString(this.font, modelName, previewCenterX, labelY, TEXT_CYAN & 0x00FFFFFF);
        }

        // Pos: текущая позиция
        int posY = previewBottom + 14;
        String posStr = (int) npc.getX() + ", " + (int) npc.getY() + ", " + (int) npc.getZ();
        g.drawString(this.font, Component.translatable("gui.questnpc.menu.pos", posStr),
                lx, posY, TEXT_CYAN & 0x00FFFFFF, false);
    }

    /**
     * Получает entity для превью в левой панели.
     * Если pendingModelType задан — создаёт/кэширует фейковую entity.
     */
    @Nullable
    private LivingEntity getPreviewEntity() {
        if (pendingModelType == null) {
            // Если у NPC уже задана модель — показываем её
            String currentModel = npc.getModelEntityType();
            if (currentModel.isEmpty()) return null; // дефолт — рендерим NPC напрямую

            ResourceLocation rl = ResourceLocation.tryParse(currentModel);
            if (rl == null) return null;
            return getOrCreatePreviewEntity(rl);
        }
        return getOrCreatePreviewEntity(pendingModelType);
    }

    @Nullable
    private LivingEntity getOrCreatePreviewEntity(ResourceLocation rl) {
        // Проверяем кэш
        if (previewEntity != null) {
            ResourceLocation cachedType = ForgeRegistries.ENTITY_TYPES.getKey(previewEntity.getType());
            if (rl.equals(cachedType)) return previewEntity;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
        if (type == null) return null;

        try {
            Entity entity = type.create(mc.level);
            if (entity instanceof LivingEntity living) {
                previewEntity = living;
                return living;
            }
        } catch (Exception e) {
            QuestNPCLogger.warn("Не удалось создать превью entity '{}': {}", rl, e.getMessage());
        }
        return null;
    }

    // ═══════════════════════════════════════════════
    // ХЕЛПЕРЫ РЕНДЕРИНГА (package-private для подэкранов)
    // ═══════════════════════════════════════════════

    static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x40000000);
        g.fill(x, y, x + w, y + h, BG_DARK);
        drawOutlineRect(g, x, y, w, h, BORDER);
    }

    static void drawOutlineRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    static void drawEditBoxBg(GuiGraphics g, int x, int y, int w, int h, boolean valid) {
        g.fill(x, y, x + w, y + h, EDIT_BG);
        int borderColor = valid ? BORDER : TEXT_RED;
        drawOutlineRect(g, x, y, w, h, borderColor);
    }

    static void drawSection(GuiGraphics g, net.minecraft.client.gui.Font font,
                             int x, int y, int w, int h, String title) {
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SECTION_BG);
        drawOutlineRect(g, x, y, w, h, BORDER);
        g.drawString(font, title, x + 8, y + 5, SECTION_TITLE & 0x00FFFFFF, false);
        g.fill(x + 1, y + 16, x + w - 1, y + 17, BORDER);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ═══ Закрытие меню — уведомляем сервер для закрытия сессии ═══

    private boolean closeSent = false;

    @Override
    public void onClose() {
        sendClosePacket();
        super.onClose();
    }

    @Override
    public void removed() {
        sendClosePacket();
        super.removed();
    }

    private void sendClosePacket() {
        if (!closeSent && !navigatingToSubScreen) {
            closeSent = true;
            ModNetwork.INSTANCE.sendToServer(new CloseMenuPacket(npc.getId()));
        }
    }
}
