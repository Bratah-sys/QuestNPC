package com.questnpc.entity.dialogue;

/**
 * Модель одного варианта ответа игрока в диалоге.
 * v1.0.0 (Ветка feature/menu-rework-magma)
 */
public class DialogueOption {
    private String text = "";
    private String nextNodeId = ""; // ID узла, к которому переходим. Если "", диалог закрывается.
    private String action = "";     // Заглушка под будущие экшены (например, "give_quest:UUID")

    public DialogueOption() {}

    public DialogueOption(String text, String nextNodeId, String action) {
        this.text = text != null ? text : "";
        this.nextNodeId = nextNodeId != null ? nextNodeId : "";
        this.action = action != null ? action : "";
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
    }

    public String getNextNodeId() {
        return nextNodeId;
    }

    public void setNextNodeId(String nextNodeId) {
        this.nextNodeId = nextNodeId != null ? nextNodeId : "";
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action != null ? action : "";
    }

    /**
     * Проверяет, закрывает ли данный выбор диалоговое окно.
     */
    public boolean endsDialogue() {
        return nextNodeId.isEmpty();
    }
}