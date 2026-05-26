package com.questnpc.client.gui;

import com.questnpc.client.ClientQuestCache;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

public class PlayerQuestScreen extends Screen {

    private final int imageWidth = 256;
    private final int imageHeight = 180;
    private int leftPos;
    private int topPos;

    public PlayerQuestScreen() {
        super(Component.literal("Журнал заданий"));
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        // В будущем здесь мы добавим кнопки для прокрутки списка квестов
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // 1. Отрисовка подложки (пока используем заливку цветом)
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xFF333333);
        graphics.fill(this.leftPos + 2, this.topPos + 2, this.leftPos + this.imageWidth - 2, this.topPos + this.imageHeight - 2, 0xFFC6C6C6);

        // 2. Заголовок
        graphics.drawString(this.font, this.title, this.leftPos + 10, this.topPos + 10, 0x333333, false);

        // 3. Чтение данных из кэша
        CompoundTag data = ClientQuestCache.playerData;
        int yOffset = 30;

        if (data.contains("Quests", Tag.TAG_LIST)) {
            ListTag questsTag = data.getList("Quests", Tag.TAG_COMPOUND);
            if (questsTag.isEmpty()) {
                graphics.drawString(this.font, "У вас пока нет активных заданий.", this.leftPos + 10, this.topPos + yOffset, 0x555555, false);
            } else {
                graphics.drawString(this.font, "Ваши задания:", this.leftPos + 10, this.topPos + yOffset, 0x000000, false);
                yOffset += 15;

                // Перебираем квесты из NBT и выводим их статусы
                for (int i = 0; i < questsTag.size(); i++) {
                    CompoundTag qTag = questsTag.getCompound(i);
                    String qId = qTag.getString("QuestId");
                    String state = qTag.getString("State");

                    // Обрезаем ID для визуального дебага (позже заменим на реальное название из ServerQuestCache)
                    String shortId = qId.length() > 8 ? qId.substring(0, 8) + "..." : qId;

                    int color = state.equals("COMPLETED") ? 0x00AA00 : (state.equals("ACTIVE") ? 0xAA0000 : 0x555555);
                    graphics.drawString(this.font, "- Квест: " + shortId + " [" + state + "]", this.leftPos + 10, this.topPos + yOffset, color, false);
                    yOffset += 12;
                }
            }
        } else {
            graphics.drawString(this.font, "Данные квестов не загружены.", this.leftPos + 10, this.topPos + yOffset, 0x555555, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}