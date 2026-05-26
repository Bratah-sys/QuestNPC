package com.questnpc.entity.dialogue;

import java.util.ArrayList;
import java.util.List;

/**
 * Модель одного узла (реплики NPC) в системе диалогов.
 * v1.0.0 (Ветка feature/menu-rework-magma)
 */
public class DialogueNode {
    private String id = "";
    private String npcText = "";
    private final List<DialogueOption> options = new ArrayList<>();

    public DialogueNode() {}

    public DialogueNode(String id, String npcText) {
        this.id = id != null ? id : "";
        this.npcText = npcText != null ? npcText : "";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id != null ? id : "";
    }

    public String getNpcText() {
        return npcText;
    }

    public void setNpcText(String npcText) {
        this.npcText = npcText != null ? npcText : "";
    }

    /**
     * Возвращает изменяемый список вариантов ответа.
     */
    public List<DialogueOption> getOptions() {
        return options;
    }

    /**
     * Безопасно перезаписывает список опций (исключает утечку ссылок).
     */
    public void setOptions(List<DialogueOption> newOptions) {
        this.options.clear();
        if (newOptions != null) {
            this.options.addAll(newOptions);
        }
    }
}