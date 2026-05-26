package com.questnpc.events;

import com.questnpc.capability.PlayerQuestProgress;
import com.questnpc.capability.PlayerQuestProvider;
import com.questnpc.capability.QuestKey;
import com.questnpc.capability.QuestRegistry;
import com.questnpc.entity.QuestNPCEntity;
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
import com.questnpc.network.QuestJournalEntry;
import com.questnpc.network.QuestSnapshots;
import com.questnpc.network.SyncPlayerQuestProgressPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * Полный snapshot capability шлётся клиенту после любой мутации.
     *
     * <p>Stage 8 (v2.9.8): дополнительно собирает {@link QuestJournalEntry} для всех
     * active+completed квестов — необходимо для {@link com.questnpc.client.gui.PlayerQuestJournalScreen}
     * (на клиенте нет доступа к NPC во всех мирах, поэтому descriptions формируем server-side).
     */
    public static void syncProgressToClient(ServerPlayer sp) {
        sp.getCapability(PlayerQuestProvider.CAP).ifPresent(prog -> {
            CompoundTag nbt = prog.serializeNBT();
            List<QuestJournalEntry> entries = buildJournalEntries(sp, prog);
            ModNetwork.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp),
                    new SyncPlayerQuestProgressPacket(nbt, entries));
        });
    }

    /**
     * Stage 8 (v2.9.8): собрать journal entries для всех active+completed квестов игрока.
     * Используется в {@link #syncProgressToClient(ServerPlayer)} и
     * {@link com.questnpc.network.RequestJournalRefreshPacket}.
     *
     * <p>Если NPC не найден (despawned/удалён) — entry для этого QuestKey пропускается
     * (для active это будет cleanup'нуто на login через {@link PlayerQuestProgress#cleanupOrphaned}).
     */
    public static List<QuestJournalEntry> buildJournalEntries(ServerPlayer sp, PlayerQuestProgress prog) {
        List<QuestJournalEntry> entries = new ArrayList<>();
        MinecraftServer server = sp.getServer();
        if (server == null) return entries;

        // Active
        for (QuestKey key : new HashSet<>(prog.getActiveKeys())) {
            QuestDefinition q = QuestRegistry.lookup(key, server);
            if (q == null) continue;
            QuestNPCEntity npc = QuestRegistry.lookupNpc(key.npcUuid(), server);
            String npcName = npc != null ? npc.getDisplayName().getString() : "?";
            entries.add(buildEntry(key, q, npcName, prog, false, 0L));
        }
        // Completed
        for (QuestKey key : new HashSet<>(prog.getCompletedKeys())) {
            QuestDefinition q = QuestRegistry.lookup(key, server);
            if (q == null) continue;
            QuestNPCEntity npc = QuestRegistry.lookupNpc(key.npcUuid(), server);
            String npcName = npc != null ? npc.getDisplayName().getString() : "?";
            // completedAt timestamp хранится в PlayerQuestProgress.completed map — добавим getter
            long completedAt = prog.getCompletedAt(key);
            entries.add(buildEntry(key, q, npcName, prog, true, completedAt));
        }
        return entries;
    }

    private static QuestJournalEntry buildEntry(QuestKey key, QuestDefinition q, String npcName,
                                                PlayerQuestProgress prog, boolean completed, long completedAt) {
        List<QuestSnapshots.ObjectiveProgressSnapshot> objs = new ArrayList<>();
        if (!completed) {
            // Для активных — собираем прогресс по каждому objective.
            for (QuestObjective o : q.getObjectives()) {
                long current;
                if (o instanceof BringObjective) {
                    current = 0L; // Bring не отслеживается через progress map (проверяется при сдаче)
                } else {
                    current = prog.getProgress(key, o.getId());
                }
                objs.add(new QuestSnapshots.ObjectiveProgressSnapshot(
                        describeObjective(o), current, o.getMaxProgress()));
            }
        }
        List<String> rewards = new ArrayList<>();
        if (!completed) {
            for (QuestReward r : q.getRewards()) rewards.add(describeReward(r));
        }
        return new QuestJournalEntry(key, npcName, q.getTitle(), q.getDescription(),
                objs, rewards, completedAt, completed);
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
