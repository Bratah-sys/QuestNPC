package com.questnpc.entity.quest;

/**
 * Тип цели квеста. Enum используется для тэга-дискриминатора в NBT и для фабрики {@link QuestObjective#createEmpty}.
 */
public enum ObjectiveType {
    KILL,
    BRING,
    BREAK_BLOCK,
    REACH_BIOME,
    REACH_STRUCTURE;

    public static ObjectiveType byNameOrNull(String name) {
        if (name == null) return null;
        for (ObjectiveType t : values()) if (t.name().equals(name)) return t;
        return null;
    }
}
