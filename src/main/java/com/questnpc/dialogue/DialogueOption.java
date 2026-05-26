package com.questnpc.dialogue;

import net.minecraft.network.FriendlyByteBuf;

public class DialogueOption {
    private final String text;       // Текст, который увидит игрок на кнопке
    private final String nextNodeId; // ID следующего узла (например, "node_2" или "close")
    private final String action;     // Действие для сервера (например, "give_sword" или пусто "")

    public DialogueOption(String text, String nextNodeId, String action) {
        this.text = text;
        this.nextNodeId = nextNodeId;
        this.action = action != null ? action : ""; // Защита от null
    }

    public String getText() { return text; }
    public String getNextNodeId() { return nextNodeId; }
    public String getAction() { return action; }

    // =========================================
    // СЕТЕВАЯ СИНХРОНИЗАЦИЯ (Для пакетов)
    // =========================================
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeUtf(this.text);
        buf.writeUtf(this.nextNodeId);
        buf.writeUtf(this.action);
    }

    public static DialogueOption fromNetwork(FriendlyByteBuf buf) {
        return new DialogueOption(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf()
        );
    }
}