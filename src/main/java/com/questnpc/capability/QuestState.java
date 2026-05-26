package com.questnpc.capability;

public enum QuestState {
    ACTIVE,
    COMPLETED,
    FAILED;

    public static QuestState byNameOrNull(String name) {
        if (name == null) return null;
        for (QuestState t : values()) if (t.name().equals(name)) return t;
        return null;
    }
}