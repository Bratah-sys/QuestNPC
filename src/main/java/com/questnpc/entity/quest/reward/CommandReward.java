package com.questnpc.entity.quest.reward;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.quest.QuestReward;
import com.questnpc.entity.quest.RewardGrantContext;
import com.questnpc.entity.quest.RewardType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Награда «выполнить /команду от лица сервера». Этап 1: grant no-op. Реализация — этап 6.
 */
public class CommandReward extends QuestReward {

    private String command = "";

    @Override
    public RewardType getType() { return RewardType.COMMAND; }

    public String getCommand() { return command; }
    public void setCommand(String v) { this.command = v != null ? v : ""; }

    /**
     * Stage 6 (v2.9.5): выполняет команду от имени сервера с правами OP4.
     *
     * <p>Поддерживаемые placeholder'ы:
     * <ul>
     *   <li>{@code @p} — имя игрока (vanilla-style)</li>
     *   <li>{@code {player}} — имя игрока</li>
     *   <li>{@code {npc}} — отображаемое имя NPC</li>
     *   <li>{@code {quest_id}} — UUID квеста</li>
     * </ul>
     *
     * <p>Безопасность: команда задаётся ТОЛЬКО админом через QuestsScreen — игрок сам
     * команды не вводит. Permission 4 нужен для give/effect/summon и пр.
     * {@code withSuppressedOutput} — чтобы команда не спамила chat (chat «Награда: X»
     * шлётся отдельно через QuestChatHelper).
     */
    @Override
    public void grant(RewardGrantContext ctx) {
        ServerPlayer player = ctx.player();
        if (player == null || command == null || command.isBlank()) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        String resolved = command
                .replace("@p", player.getName().getString())
                .replace("{player}", player.getName().getString())
                .replace("{npc}", ctx.npc() != null ? ctx.npc().getDisplayName().getString() : "")
                .replace("{quest_id}", ctx.questId() != null ? ctx.questId() : "");
        if (resolved.startsWith("/")) resolved = resolved.substring(1);

        CommandSourceStack source = server.createCommandSourceStack()
                .withPermission(4)
                .withSuppressedOutput()
                .withPosition(player.position())
                .withRotation(player.getRotationVector())
                .withLevel((ServerLevel) player.level());

        try {
            server.getCommands().performPrefixedCommand(source, resolved);
        } catch (Exception ex) {
            QuestNPCLogger.warn("CommandReward execution failed: player={}, command='{}', error={}",
                    player.getName().getString(), resolved, ex.getMessage());
        }
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
