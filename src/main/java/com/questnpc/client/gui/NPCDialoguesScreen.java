package com.questnpc.client.gui;

import com.questnpc.client.gui.widget.DarkButton;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.UpdateNPCDialoguesPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Административный экран для создания и редактирования диалоговых древ NPC.
 */
public class NPCDialoguesScreen extends Screen {

    private final NPCMenuScreen parent;
    private boolean dialoguesEnabled;
    private String startNodeId;
    private final List<CompoundTag> dialogues;

    // Элементы управления
    private EditBox startNodeIdField;
    private EditBox npcTextField;
    private int selectedNodeIndex = -1;

    // Поля для редактирования вариантов ответов (DialogueOption) для выбранного узла
    private final EditBox[] optionTextFields = new EditBox[3];
    private final EditBox[] optionNextNodeFields = new EditBox[3];
    private final EditBox[] optionActionFields = new EditBox[3];

    public NPCDialoguesScreen(NPCMenuScreen parent, boolean dialoguesEnabled, String startNodeId, List<CompoundTag> dialogues) {
        super(Component.literal("Редактор диалогов NPC"));
        this.parent = parent;
        this.dialoguesEnabled = dialoguesEnabled;
        this.startNodeId = startNodeId;
        this.dialogues = new ArrayList<>();

        // Глубокое копирование, чтобы изменения не применились при простой отмене
        for (CompoundTag tag : dialogues) {
            this.dialogues.add(tag.copy());
        }
    }

    @Override
    protected void init() {
        this.clearWidgets();

        // Кнопка Вкл/Выкл систему диалогов
        String toggleText = this.dialoguesEnabled ? "Диалоги: ВКЛ" : "Диалоги: ВЫКЛ";
        this.addRenderableWidget(new DarkButton(20, 20, 120, 20, Component.literal(toggleText), b -> {
            this.dialoguesEnabled = !this.dialoguesEnabled;
            this.init();
        }));

        // Текстовое поле для ID стартовой реплики
        this.startNodeIdField = new EditBox(this.font, 245, 20, 80, 20, Component.literal("Start ID"));
        this.startNodeIdField.setValue(this.startNodeId != null ? this.startNodeId : "start");
        this.addRenderableWidget(this.startNodeIdField);

        // --- СПИСОК РЕПЛИК (СЛЕВА) ---
        int listY = 50;
        for (int i = 0; i < dialogues.size(); i++) {
            final int index = i;
            CompoundTag node = dialogues.get(index);
            String nodeId = node.getString("Id");

            this.addRenderableWidget(new DarkButton(20, listY, 120, 18, Component.literal(nodeId), b -> {
                saveCurrentNodeState();
                this.selectedNodeIndex = index;
                this.init();
            }));
            listY += 22;
        }

        // Кнопки добавления/удаления узлов диалога
        this.addRenderableWidget(new DarkButton(20, this.height - 50, 58, 20, Component.literal("+ Добавить"), b -> {
            saveCurrentNodeState();
            CompoundTag newNode = new CompoundTag();
            newNode.putString("Id", "node_" + (dialogues.size() + 1));
            newNode.putString("NpcText", "Текст NPC...");
            newNode.put("Options", new ListTag());
            dialogues.add(newNode);
            selectedNodeIndex = dialogues.size() - 1;
            this.init();
        }));

        this.addRenderableWidget(new DarkButton(82, this.height - 50, 58, 20, Component.literal("- Удалить"), b -> {
            if (selectedNodeIndex >= 0 && selectedNodeIndex < dialogues.size()) {
                dialogues.remove(selectedNodeIndex);
                selectedNodeIndex = -1;
                this.init();
            }
        }));

        // --- РЕДАКТОР ВЫБРАННОЙ РЕПЛИКИ (ПО ЦЕНТРУ И СПРАВА) ---
        if (selectedNodeIndex >= 0 && selectedNodeIndex < dialogues.size()) {
            CompoundTag activeNode = dialogues.get(selectedNodeIndex);

            // Поле ID реплики
            EditBox idField = new EditBox(this.font, 220, 50, 120, 20, Component.literal("Node ID"));
            idField.setValue(activeNode.getString("Id"));
            idField.setResponder(val -> activeNode.putString("Id", val));
            this.addRenderableWidget(idField);

            // Поле Текста NPC
            this.npcTextField = new EditBox(this.font, 155, 92, this.width - 180, 20, Component.literal("NPC Text"));
            this.npcTextField.setValue(activeNode.getString("NpcText"));
            this.npcTextField.setMaxLength(256);
            this.addRenderableWidget(this.npcTextField);

            // Варианты ответов игрока (3 слота)
            ListTag optionsList = activeNode.getList("Options", 10);
            int optY = 135;

            for (int i = 0; i < 3; i++) {
                CompoundTag optTag = i < optionsList.size() ? optionsList.getCompound(i) : new CompoundTag();

                // Текст кнопки ответа
                this.optionTextFields[i] = new EditBox(this.font, 155, optY, 140, 18, Component.literal("Текст"));
                this.optionTextFields[i].setValue(optTag.getString("Text"));
                this.addRenderableWidget(this.optionTextFields[i]);

                // ID следующей ноды перехода
                this.optionNextNodeFields[i] = new EditBox(this.font, 300, optY, 80, 18, Component.literal("Переход"));
                this.optionNextNodeFields[i].setValue(optTag.getString("NextNodeId"));
                this.addRenderableWidget(this.optionNextNodeFields[i]);

                // Экшен-команда (опционально)
                this.optionActionFields[i] = new EditBox(this.font, 385, optY, this.width - 400, 18, Component.literal("Экшен"));
                this.optionActionFields[i].setValue(optTag.getString("Action"));
                this.addRenderableWidget(this.optionActionFields[i]);

                optY += 22;
            }
        }

        // --- НИЖНИЕ КНОПКИ УПРАВЛЕНИЯ ЭКРАНОМ ---
        // Кнопка Применить и Сохранить по Сети
        this.addRenderableWidget(new DarkButton(this.width - 230, this.height - 30, 105, 20, Component.literal("Save & Apply"), b -> {
            saveCurrentNodeState();
            this.startNodeId = this.startNodeIdField.getValue();

            // 1. Обновляем локальный snapshot в основном меню GUI
            this.parent.setDialoguesSnapshot(this.dialoguesEnabled, this.startNodeId, this.dialogues);

            // 2. Отправляем C2S-пакет изменений на сервер (ИСПРАВЛЕНО: получаем ID через геттер)
            ModNetwork.INSTANCE.sendToServer(new UpdateNPCDialoguesPacket(
                    this.parent.getNpc().getId(),
                    this.dialoguesEnabled,
                    this.startNodeId,
                    this.dialogues
            ));

            // Возвращаемся в главное меню
            this.minecraft.setScreen(this.parent);
        }));

        // Кнопка Отмена
        this.addRenderableWidget(new DarkButton(this.width - 115, this.height - 30, 100, 20, Component.literal("Cancel"), b -> {
            this.minecraft.setScreen(this.parent);
        }));
    }

    private void saveCurrentNodeState() {
        if (selectedNodeIndex >= 0 && selectedNodeIndex < dialogues.size()) {
            CompoundTag node = dialogues.get(selectedNodeIndex);
            if (this.npcTextField != null) {
                node.putString("NpcText", this.npcTextField.getValue());
            }

            ListTag newOptionsList = new ListTag();
            for (int i = 0; i < 3; i++) {
                if (this.optionTextFields[i] != null && !this.optionTextFields[i].getValue().isEmpty()) {
                    CompoundTag opt = new CompoundTag();
                    opt.putString("Text", this.optionTextFields[i].getValue());
                    opt.putString("NextNodeId", this.optionNextNodeFields[i].getValue());
                    opt.putString("Action", this.optionActionFields[i].getValue());
                    newOptionsList.add(opt);
                }
            }
            node.put("Options", newOptionsList);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTicks);

        // Заголовок экрана
        g.drawString(this.font, this.title, 20, 5, 0x00FF00);

        // Отрисовка текстовых меток
        g.drawString(this.font, "Стартовый узел:", 155, 25, 0xFFFFFF);

        // Подсвечиваем выбранный узел в списке зелёным прямоугольником за текстом
        if (selectedNodeIndex >= 0 && selectedNodeIndex < dialogues.size()) {
            g.drawString(this.font, "ID реплики:", 155, 55, 0xAAAAAA);
            g.drawString(this.font, "Текст NPC:", 155, 80, 0xAAAAAA);
            g.drawString(this.font, "Варианты ответов игрока (Текст кнопки | Куда ведет ID | Экшен команда):", 155, 122, 0xFFAA00);

            // Рисуем рамку вокруг выбранного элемента в списке
            int rectY = 50 + (selectedNodeIndex * 22);
            g.renderOutline(20, rectY, 120, 18, 0x55FF55FF);
        } else {
            g.drawString(this.font, "Выберите или создайте узел диалога слева.", 160, 80, 0x777777);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}