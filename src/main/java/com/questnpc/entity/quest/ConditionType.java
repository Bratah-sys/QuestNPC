package com.questnpc.entity.quest;

public enum ConditionType {
    DISTANCE_TO_STRUCTURE,
    PLAYER_LEVEL,
    COMPLETED_QUEST;

    public static ConditionType byNameOrNull(String name) {
        if (name == null) return null;
        for (ConditionType t : values()) if (t.name().equals(name)) return t;
        return null;
    }
}
