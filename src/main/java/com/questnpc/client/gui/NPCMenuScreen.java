package com.questnpc.client.gui;

import com.questnpc.QuestNPCLogger;
import com.questnpc.client.gui.widget.DarkButton;
import com.questnpc.entity.ModEntityTypes;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.quest.QuestDefinition;
import com.questnpc.network.ChangeModelPacket;
import com.questnpc.network.CloseMenuPacket;
import com.questnpc.network.DeleteNPCPacket;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.RenameNPCPacket;
import net.minecraft.nbt.CompoundTag;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Двухпанельное меню NPC в тёмном стиле.
 * Левая панель: превью модели + инфо. Правая панель: сетка кнопок-разделов.
 */
public class NPCMenuScreen extends Screen {

    // ═══ Цветовая палитра ═══
    // v2.9.2: повышено до public — design-system reuse в client/gui/picker/ pickers
    public static final int BG_OVERLAY     = 0xC0000000;
    public static final int BG_DARK        = 0xFF1A1A2E;
    public static final int SECTION_BG     = 0xFF1F2937;
    public static final int BORDER         = 0xFF4A5568;
    public static final int TEXT_WHITE     = 0xFFE2E8F0;
    public static final int TEXT_GRAY      = 0xFF9CA3AF;
    public static final int TEXT_DARK_GRAY = 0xFF6B7280;
    public static final int TEXT_CYAN      = 0xFF2DD4BF;
    public static final int TEXT_RED       = 0xFFFF5555;
    public static final int EDIT_BG        = 0xFF111827;
    public static final int SECTION_TITLE  = 0xFF3B82F6;
    public static final int BTN_GREEN_BG   = 0xFF10B981;
    public static final int BTN_GREEN_HOVER= 0xFF22C55E;
    public static final int BTN_GRAY_BG    = 0xFF374151;
    public static final int BTN_GRAY_HOVER = 0xFF4B5563;
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
    private boolean currentTradingEnabled;
    private List<QuestNPCEntity.TradeSet> currentTradeSets;
    private List<String> currentLockedTradeSets; // v2.9.5 — снимок имён заблокированных сетов
    private boolean currentScheduleEnabled;
    private List<CompoundTag> currentSchedule;
    private net.minecraft.world.item.ItemStack[] currentEquipment; // v2.8.0 снимок брони с сервера
    private boolean currentQuestsEnabled;                          // v2.9.1 снимок toggle квестов
    private List<QuestDefinition> currentQuests;                   // v2.9.1 снимок списка квестов

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

    public NPCMenuScreen(QuestNPCEntity npc, double speed, int delayMin, int delayMax, String modelType,
                         boolean tradingEnabled, List<QuestNPCEntity.TradeSet> tradeSets,
                         List<String> lockedTradeSetNames,
                         boolean scheduleEnabled, List<CompoundTag> schedule,
                         net.minecraft.world.item.ItemStack[] equipment,
                         boolean questsEnabled, List<QuestDefinition> quests) {
        super(Component.translatable("gui.questnpc.menu.title"));
        this.npc = npc;
        this.currentSpeed = speed;
        this.currentDelayMin = delayMin;
        this.currentDelayMax = delayMax;
        this.currentModelType = modelType != null ? modelType : "";
        this.currentTradingEnabled = tradingEnabled;
        this.currentTradeSets = tradeSets != null ? tradeSets : new ArrayList<>();
        this.currentLockedTradeSets = lockedTradeSetNames != null ? new ArrayList<>(lockedTradeSetNames) : new ArrayList<>();
        this.currentScheduleEnabled = scheduleEnabled;
        this.currentSchedule = schedule != null ? schedule : new ArrayList<>();
        if (equipment != null && equipment.length == QuestNPCEntity.EQUIPMENT_SLOTS) {
            this.currentEquipment = equipment;
        } else {
            this.currentEquipment = new net.minecraft.world.item.ItemStack[]{
                    net.minecraft.world.item.ItemStack.EMPTY,
                    net.minecraft.world.item.ItemStack.EMPTY,
                    net.minecraft.world.item.ItemStack.EMPTY,
                    net.minecraft.world.item.ItemStack.EMPTY
            };
        }
        // v2.9.1: snapshot квестов с deep-copy через NBT round-trip
        this.currentQuestsEnabled = questsEnabled;
        this.currentQuests = new ArrayList<>();
        if (quests != null) {
            for (QuestDefinition q : quests) {
                if (q != null) this.currentQuests.add(QuestDefinition.load(q.save()));
            }
        }
    }

    // ═══ Выбор кастомной модели (custom:...) ═══
    @Nullable
    private String pendingCustomModelType;

    /**
     * Устанавливает выбранную модель из каталога мобов. Вызывается из ModelCatalogScreen.
     */
    public void setPendingModelType(@Nullable ResourceLocation modelType) {
        this.pendingModelType = modelType;
        this.pendingCustomModelType = null;
        // Сбрасываем кэш превью
        this.previewEntity = null;
    }

    /**
     * Устанавливает выбранную кастомную модель. Вызывается из CustomModelBrowserScreen.
     * @param customModelType полный идентификатор, напр. "custom:cool_kid"
     */
    public void setPendingCustomModel(String customModelType) {
        this.pendingCustomModelType = customModelType;
        this.pendingModelType = null;
        // Сбрасываем кэш превью
        this.previewEntity = null;
    }

    @Override
    protected void init() {
        super.init();

        // Сброс флага при возврате из подэкрана.
        // v2.5.5 (BUG-012): closeSent НЕ сбрасываем — он инициализируется один раз
        // в декларации поля, чтобы ресайз окна не приводил к повторному CloseMenuPacket.
        navigatingToSubScreen = false;

        panelX = (this.width - TOTAL_WIDTH) / 2;
        panelY = (this.height - TOTAL_HEIGHT) / 2;

        int rightX = panelX + LEFT_WIDTH + 1;
        int rightW = TOTAL_WIDTH - LEFT_WIDTH - 1;

        // ═══ Правая панель: поле имени ═══
        int nameY = panelY + 30;
        int nameFieldW = rightW / 2 - PADDING;
        nameField = new EditBox(this.font, rightX + PADDING, nameY, nameFieldW, 16,
                Component.literal("Name")) {
            @Override
            public void setFocused(boolean focused) {
                boolean wasFocused = this.isFocused();
                super.setFocused(focused);
                if (wasFocused && !focused) applyName();
            }
        };
        nameField.setMaxLength(32);
        String currentName = npc.hasCustomName() ? npc.getCustomName().getString() : npc.getName().getString();
        nameField.setValue(currentName);
        nameField.setBordered(false);
        this.addRenderableWidget(nameField);

        // ═══ Правая панель: сетка кнопок ═══
        int gridStartY = nameY + 24;
        int btnW = (rightW - PADDING * 3) / 2;
        int btnH = 20;
        int gap = 4;

        String[][] gridButtons = {
            {"gui.questnpc.menu.btn.actions",   "gui.questnpc.menu.btn.attributes"},
            {"gui.questnpc.menu.btn.dialog",    "gui.questnpc.menu.btn.equipment"},
            {"gui.questnpc.menu.btn.quests",    "gui.questnpc.menu.btn.pose"},
            {"gui.questnpc.menu.btn.position",  "gui.questnpc.menu.btn.trading"},
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
                } else if (key.equals("gui.questnpc.menu.btn.trading")) {
                    action = button -> {
                        navigatingToSubScreen = true;
                        Minecraft.getInstance().setScreen(
                            new NPCTradingScreen(npc, currentTradingEnabled, currentTradeSets,
                                    currentLockedTradeSets, this));
                    };
                } else if (key.equals("gui.questnpc.menu.btn.actions")) {
                    action = button -> {
                        navigatingToSubScreen = true;
                        Minecraft.getInstance().setScreen(new ScheduleScreen(
                                npc, currentTradeSets, currentScheduleEnabled, currentSchedule, this));
                    };
                } else if (key.equals("gui.questnpc.menu.btn.equipment")) {
                    action = button -> {
                        navigatingToSubScreen = true;
                        Minecraft.getInstance().setScreen(
                                new EquipmentScreen(npc, currentEquipment, this));
                    };
                } else if (key.equals("gui.questnpc.menu.btn.quests")) {
                    action = button -> {
                        navigatingToSubScreen = true;
                        Minecraft.getInstance().setScreen(new QuestsScreen(
                                npc, this, currentQuestsEnabled, currentQuests));
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
        int bottomBtnW = (TOTAL_WIDTH - PADDING * 3) / 2;


        // Применить модель (если выбрана)
        this.addRenderableWidget(new DarkButton(
            panelX + PADDING, bottomY, bottomBtnW, 20,
            Component.translatable("gui.questnpc.npc_menu.apply"),
            button -> applyModel(),
            BTN_GREEN_BG, BTN_GREEN_HOVER, 0xFFFFFFFF
        ));

        // Удалить
        deleteBtn = this.addRenderableWidget(new DarkButton(
            panelX + PADDING + bottomBtnW + PADDING, bottomY, bottomBtnW, 20,
            Component.translatable("gui.questnpc.menu.btn.delete"),
            button -> handleDelete(),
            BTN_RED_BG, BTN_RED_HOVER, 0xFFFFFFFF
        ));
    }

    // ═══ Логика переименования ═══
    private void applyName() {
        String name = nameField.getValue().trim();
        if (name.isEmpty() || name.length() > 32) return;
        String current = npc.hasCustomName() ? npc.getCustomName().getString() : npc.getName().getString();
        if (name.equals(current)) return;
        ModNetwork.INSTANCE.sendToServer(new RenameNPCPacket(npc.getId(), name));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameField.isFocused() && (keyCode == 257 || keyCode == 335)) { // ENTER, KP_ENTER
            applyName();
            this.setFocused(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ═══ Логика применения модели ═══
    private void applyModel() {
        if (pendingCustomModelType != null) {
            // Кастомная модель из .geo.json
            ModNetwork.INSTANCE.sendToServer(new ChangeModelPacket(npc.getId(), pendingCustomModelType));
            QuestNPCLogger.info("NPC {} — применена кастомная модель '{}' из файла",
                    npc.getId(), pendingCustomModelType);
        } else if (pendingModelType != null) {
            // Ванильный моб из каталога
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
        if (pendingCustomModelType != null) {
            // Кастомная модель: показываем имя файла
            String name = pendingCustomModelType.startsWith("custom:")
                    ? pendingCustomModelType.substring("custom:".length())
                    : pendingCustomModelType;
            g.drawCenteredString(this.font, "\u2B50 " + name, previewCenterX, labelY, TEXT_CYAN & 0x00FFFFFF);
        } else if (pendingModelType != null) {
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
     * Если pendingModelType/pendingCustomModelType задан — создаёт/кэширует фейковую entity.
     */
    @Nullable
    private LivingEntity getPreviewEntity() {
        // Кастомная модель из .geo.json — создаём фейковый QuestNPCEntity с нужным modelType
        if (pendingCustomModelType != null) {
            return getOrCreateCustomPreviewEntity(pendingCustomModelType);
        }

        if (pendingModelType != null) {
            return getOrCreatePreviewEntity(pendingModelType);
        }

        // Если у NPC уже задана модель — показываем её
        String currentModel = npc.getModelEntityType();
        if (currentModel.isEmpty()) return null; // дефолт — рендерим NPC напрямую

        if (currentModel.startsWith("custom:")) {
            return getOrCreateCustomPreviewEntity(currentModel);
        }

        ResourceLocation rl = ResourceLocation.tryParse(currentModel);
        if (rl == null) return null;
        return getOrCreatePreviewEntity(rl);
    }

    /**
     * Создаёт/кэширует фейковый QuestNPCEntity для превью кастомной модели.
     */
    @Nullable
    private LivingEntity getOrCreateCustomPreviewEntity(String customModelType) {
        // Проверяем кэш — если уже создан для этой модели, возвращаем
        if (previewEntity instanceof QuestNPCEntity fakeNpc
                && customModelType.equals(fakeNpc.getModelEntityType())) {
            return previewEntity;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        try {
            QuestNPCEntity fakeNpc = ModEntityTypes.QUEST_NPC.get().create(mc.level);
            if (fakeNpc != null) {
                fakeNpc.setModelEntityType(customModelType);
                previewEntity = fakeNpc;
                return fakeNpc;
            }
        } catch (Exception e) {
            QuestNPCLogger.warn("Не удалось создать превью для кастомной модели '{}': {}",
                    customModelType, e.getMessage());
        }
        return null;
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

    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x40000000);
        g.fill(x, y, x + w, y + h, BG_DARK);
        drawOutlineRect(g, x, y, w, h, BORDER);
    }

    public static void drawOutlineRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void drawEditBoxBg(GuiGraphics g, int x, int y, int w, int h, boolean valid) {
        g.fill(x, y, x + w, y + h, EDIT_BG);
        int borderColor = valid ? BORDER : TEXT_RED;
        drawOutlineRect(g, x, y, w, h, borderColor);
    }

    public static void drawSection(GuiGraphics g, net.minecraft.client.gui.Font font,
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

    // ═══ Обновление кэша из подэкранов (вызывается после Apply) ═══

    /**
     * Обновляет кэш торговли после применения изменений в {@link NPCTradingScreen}.
     * Делает защитную копию, чтобы последующие правки в подэкране не мутировали кэш
     * через разделяемую ссылку.
     */
    void updateTradingState(boolean enabled, List<QuestNPCEntity.TradeSet> sets) {
        updateTradingState(enabled, sets, null);
    }

    /** Stage 6 (v2.9.5) overload — также сохраняет снимок имён locked сетов для последующего reopen NPCTradingScreen. */
    void updateTradingState(boolean enabled, List<QuestNPCEntity.TradeSet> sets, List<String> lockedNames) {
        this.currentTradingEnabled = enabled;
        this.currentTradeSets = sets != null ? new ArrayList<>(sets) : new ArrayList<>();
        if (lockedNames != null) {
            this.currentLockedTradeSets = new ArrayList<>(lockedNames);
        }
    }

    /**
     * Обновляет кэш расписания после применения изменений в {@link ScheduleScreen}.
     * Делает защитную копию списка NBT-тегов.
     */
    void updateScheduleState(boolean enabled, List<CompoundTag> schedule) {
        this.currentScheduleEnabled = enabled;
        this.currentSchedule = schedule != null ? new ArrayList<>(schedule) : new ArrayList<>();
    }

    /**
     * v2.8.1: обновляет локальный снимок экипировки после Apply в {@link EquipmentScreen}.
     * Глубокая копия — изменения в одном экране не утекают в другой через ссылку.
     */
    public void setEquipmentSnapshot(net.minecraft.world.item.ItemStack[] equipment) {
        if (equipment == null || equipment.length != QuestNPCEntity.EQUIPMENT_SLOTS) return;
        for (int i = 0; i < QuestNPCEntity.EQUIPMENT_SLOTS; i++) {
            currentEquipment[i] = equipment[i] != null
                    ? equipment[i].copy()
                    : net.minecraft.world.item.ItemStack.EMPTY;
        }
    }

    /**
     * v2.9.1: обновляет локальный снимок квестов после Apply в {@link QuestsScreen}.
     * Deep-copy через NBT round-trip ({@code QuestDefinition.load(src.save())}) —
     * изменения в дочернем экране не утекают через разделённые ссылки.
     */
    public void setQuestsSnapshot(boolean enabled, List<QuestDefinition> snapshot) {
        this.currentQuestsEnabled = enabled;
        this.currentQuests = new ArrayList<>();
        if (snapshot == null) return;
        for (QuestDefinition q : snapshot) {
            if (q != null) this.currentQuests.add(QuestDefinition.load(q.save()));
        }
    }

    /** v2.9.1: для передачи в {@link QuestsScreen} (read-only view). */
    public boolean isQuestsEnabled() { return currentQuestsEnabled; }
    public List<QuestDefinition> getCurrentQuests() {
        return java.util.Collections.unmodifiableList(currentQuests);
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
