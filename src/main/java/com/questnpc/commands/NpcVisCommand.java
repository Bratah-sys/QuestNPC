package com.questnpc.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.questnpc.QuestNPCLogger;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.ToggleVisualizationPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Команда /npc_vis — клиентский переключатель дебаг-визуализации патруля NPC.
 * Доступна всем игрокам (permissionLevel = 0).
 */
public final class NpcVisCommand {

    private static final Set<UUID> ENABLED_PLAYERS = new HashSet<>();

    private NpcVisCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("npc_vis")
                        .requires(source -> source.hasPermission(0))
                        .executes(context -> execute(context.getSource()))
        );
        QuestNPCLogger.info("Команда /npc_vis зарегистрирована");
    }

    private static int execute(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("Эта команда доступна только игрокам"));
            return 0;
        }

        UUID uuid = player.getUUID();
        final boolean enabled;

        if (ENABLED_PLAYERS.contains(uuid)) {
            ENABLED_PLAYERS.remove(uuid);
            enabled = false;
            QuestNPCLogger.info("NPC визуализация ВЫКЛ для {}", player.getName().getString());
        } else {
            ENABLED_PLAYERS.add(uuid);
            enabled = true;
            QuestNPCLogger.info("NPC визуализация ВКЛ для {}", player.getName().getString());
        }

        ModNetwork.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ToggleVisualizationPacket(enabled)
        );

        String key = enabled
                ? "commands.questnpc.npc_vis.enabled"
                : "commands.questnpc.npc_vis.disabled";
        source.sendSuccess(() -> Component.translatable(key), false);

        return Command.SINGLE_SUCCESS;
    }
}
