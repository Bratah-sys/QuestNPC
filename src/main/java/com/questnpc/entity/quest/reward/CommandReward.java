package com.questnpc.entity.quest.reward;

import com.questnpc.entity.quest.QuestReward;
import com.questnpc.entity.quest.RewardGrantContext;
import com.questnpc.entity.quest.RewardType;
import net.minecraft.nbt.CompoundTag;

/**
 * Награда «выполнить /команду от лица сервера». Этап 1: grant no-op. Реализация — этап 6.
 */
public class CommandReward extends QuestReward {

    private String command = "";

    @Override
    public RewardType getType() { return RewardType.COMMAND; }

    public String getCommand() { return command; }
    public void setCommand(String v) { this.command = v != null ? v : ""; }

    @Override
    public void grant(RewardGrantContext ctx) {
        // TODO Stage 6: server.getCommands().performPrefixedCommand(serverSource, command)
    }

    @Override
    protected void writeData(CompoundTag tag) {
        tag.putString("Command", command);
    }

    @Override
    protected void readData(CompoundTag tag) {
        this.command = tag.contains("Command") ? tag.getString("Command") : "";
    }
}
