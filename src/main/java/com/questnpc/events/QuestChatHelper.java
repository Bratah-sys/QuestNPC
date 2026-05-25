package com.questnpc.events;

import com.questnpc.capability.PlayerQuestProvider;
import com.questnpc.entity.quest.QuestDefinition;
import com.questnpc.entity.quest.QuestObjective;
import com.questnpc.entity.quest.QuestReward;
import com.questnpc.entity.quest.objective.BreakBlockObjective;
import com.questnpc.entity.quest.objective.BringObjective;
import com.questnpc.entity.quest.objective.KillObjective;
import com.questnpc.entity.quest.objective.ReachBiomeObjective;
import com.questnpc.entity.quest.objective.ReachStructureObjective;
import com.questnpc.entity.quest.reward.CommandReward;
import com.questnpc.entity.quest.reward.ItemReward;
import com.questnpc.entity.quest.reward.UnlockTradeSetReward;
import com.questnpc.entity.quest.reward.XPPointsReward;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.SyncPlayerQuestProgressPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * Stage 5 (v2.9.4): centralized helper для quest-related chat-уведомлений.
 * Шаблоны через {@code Component.translatable("chat.questnpc.X", ...)} —
 * локализация в lang JSON'ах.
 *
 * <p>Также содержит {@link #syncProgressToClient(ServerPlayer)} — utility
 * для рассылки {@link SyncPlayerQuestProgressPacket} после мутаций.
 */
public final class QuestChatHelper {

    private QuestChatHelper() {}

    public static void sendQuestAccepted(ServerPlayer p, QuestDefinition q) {
        p.sendSystemMessage(Component.translatable("chat.questnpc.accepted", q.getTitle())
                .withStyle(ChatFormatting.AQUA));
    }

    public static void sendObjectiveComplete(ServerPlayer p, QuestDefinition q, QuestObjective obj) {
        p.sendSystemMessage(Component.translatable("chat.questnpc.objective_complete",
                q.getTitle(), describeObjective(obj)).withStyle(ChatFormatting.GRAY));
    }

    public static void sendQuestReady(ServerPlayer p, QuestDefinition q) {
        p.sendSystemMessage(Component.translatable("chat.questnpc.ready", q.getTitle())
                .withStyle(ChatFormatting.GOLD));
    }

    public static void sendQuestCompleted(ServerPlayer p, QuestDefinition q) {
        p.sendSystemMessage(Component.translatable("chat.questnpc.completed", q.getTitle())
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
    }

    public static void sendRewardGranted(ServerPlayer p, QuestReward reward) {
        // Stage 5 stub: реальная выдача — Этап 6. Сейчас только chat.
        p.sendSystemMessage(Component.translatable("chat.questnpc.reward", describeReward(reward))
                .withStyle(ChatFormatting.YELLOW));
    }

    public static void sendQuestAbandoned(ServerPlayer p, QuestDefinition q) {
        p.sendSystemMessage(Component.translatable("chat.questnpc.abandoned", q.getTitle())
                .withStyle(ChatFormatting.RED));
    }

    public static void sendBringFailed(ServerPlayer p) {
        p.sendSystemMessage(Component.translatable("chat.questnpc.bring_failed")
                .withStyle(ChatFormatting.RED));
    }

    /** Полный snapshot capability шлётся клиенту после любой мутации. */
    public static void syncProgressToClient(ServerPlayer sp) {
        sp.getCapability(PlayerQuestProvider.CAP).ifPresent(prog -> {
            CompoundTag nbt = prog.serializeNBT();
            ModNetwork.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp),
                    new SyncPlayerQuestProgressPacket(nbt));
        });
    }

    // -------------------------------------------------------------------------
    // Human-readable descriptions (для chat и UI snapshots)
    // -------------------------------------------------------------------------

    public static String describeObjective(QuestObjective obj) {
        if (obj instanceof KillObjective ko) {
            String target = ko.isTagMode()
                    ? (ko.getEntityTypeTag() != null ? "#" + ko.getEntityTypeTag().location() : "?")
                    : (ko.getEntityType() != null ? ko.getEntityType().toString() : "?");
            return "Убить " + target + " ×" + ko.getCount();
        }
        if (obj instanceof BringObjective bo) {
            String item = bo.getStack().isEmpty()
                    ? "?"
                    : bo.getStack().getHoverName().getString();
            return "Принести " + item + " ×" + bo.getCount();
        }
        if (obj instanceof BreakBlockObjective bbo) {
            String target = bbo.isTagMode()
                    ? (bbo.getBlockTag() != null ? "#" + bbo.getBlockTag().location() : "?")
                    : (bbo.getBlockId() != null ? bbo.getBlockId().toString() : "?");
            return "Сломать " + target + " ×" + bbo.getCount();
        }
        if (obj instanceof ReachBiomeObjective rbo) {
            return "Дойти до биома " + (rbo.getBiomeId() != null ? rbo.getBiomeId() : "?");
        }
        if (obj instanceof ReachStructureObjective rso) {
            return "Дойти до структуры " + (rso.getStructureId() != null ? rso.getStructureId() : "?");
        }
        return "Цель";
    }

    public static String describeReward(QuestReward reward) {
        if (reward instanceof ItemReward ir) {
            return ir.getStack().getCount() + "× " + ir.getStack().getHoverName().getString();
        }
        if (reward instanceof XPPointsReward xr) return xr.getAmount() + " XP";
        if (reward instanceof CommandReward cr) {
            String cmd = cr.getCommand();
            return "/" + (cmd != null && cmd.length() > 32 ? cmd.substring(0, 32) + "…" : cmd);
        }
        if (reward instanceof UnlockTradeSetReward ur) {
            return "Разблокирован набор сделок «" + ur.getTradeSetName() + "»";
        }
        return "награда";
    }
}
