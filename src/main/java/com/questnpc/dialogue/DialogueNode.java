package com.questnpc.dialogue;

import net.minecraft.network.FriendlyByteBuf;
import java.util.ArrayList;
import java.util.List;

public class DialogueNode {
    private final String id;                      // Уникальный ID узла (например, "start")
    private final String text;                    // Текст, который говорит NPC
    private final List<DialogueOption> options;   // Список доступных ответов для игрока

    public DialogueNode(String id, String text, List<DialogueOption> options) {
        this.id = id;
        this.text = text;
        this.options = options != null ? options : new ArrayList<>();
    }

    public String getId() { return id; }
    public String getText() { return text; }
    public List<DialogueOption> getOptions() { return options; }

    // =========================================
    // СЕТЕВАЯ СИНХРОНИЗАЦИЯ (Для пакетов)
    // =========================================
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeUtf(this.id);
        buf.writeUtf(this.text);

        // Сначала записываем количество ответов, затем сами ответы
        buf.writeInt(this.options.size());
        for (DialogueOption option : this.options) {
            option.toNetwork(buf);
        }
    }

    public static DialogueNode fromNetwork(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        String text = buf.readUtf();

        // Читаем количество ответов и собираем их в список
        int size = buf.readInt();
        List<DialogueOption> options = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            options.add(DialogueOption.fromNetwork(buf));
        }

        return new DialogueNode(id, text, options);
    }
}