package com.questnpc.entity.quest;

public enum RewardType {
    ITEM,
    XP_POINTS,
    COMMAND,
    UNLOCK_TRADE_SET;

    public static RewardType byNameOrNull(String name) {
        if (name == null) return null;
        for (RewardType t : values()) if (t.name().equals(name)) return t;
        return null;
    }
}
