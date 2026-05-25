package com.questnpc.client.gui;

import com.questnpc.network.OpenPlayerQuestListPacket;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Stage 5 (v2.9.4) skeleton — наполнение в шаге 5.6.
 * Открывается S→C пакетом {@link OpenPlayerQuestListPacket}.
 */
public class PlayerQuestScreen extends Screen {

    private final OpenPlayerQuestListPacket payload;

    public PlayerQuestScreen(OpenPlayerQuestListPacket payload) {
        super(Component.literal("Quests"));
        this.payload = payload;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
