package com.questnpc.events;

import com.questnpc.QuestNPC;
import com.questnpc.capability.PlayerQuestProgress;
import com.questnpc.capability.PlayerQuestProvider;
import com.questnpc.capability.QuestKey;
import com.questnpc.capability.QuestRegistry;
import com.questnpc.entity.quest.QuestDefinition;
import com.questnpc.entity.quest.QuestObjective;
import com.questnpc.entity.quest.objective.BreakBlockObjective;
import com.questnpc.entity.quest.objective.KillObjective;
import com.questnpc.entity.quest.objective.ReachBiomeObjective;
import com.questnpc.entity.quest.objective.ReachStructureObjective;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * Stage 5 (v2.9.4): главный FORGE-bus event handler квестовой системы.
 *
 * <p>Подписан на:
 * <ul>
 *   <li>{@link AttachCapabilitiesEvent} — attach {@link PlayerQuestProvider} к каждому Player</li>
 *   <li>{@link PlayerEvent.Clone} — копирует capability при смерти/respawn/dimension change</li>
 *   <li>{@link PlayerEvent.PlayerLoggedInEvent} — cleanupOrphaned + checkOnLogin + initial sync</li>
 *   <li>{@link LivingDeathEvent} — триггер KillObjective</li>
 *   <li>{@link BlockEvent.BreakEvent} — триггер BreakBlockObjective</li>
 *   <li>{@link TickEvent.PlayerTickEvent} — polling location-objectives каждые 20 тиков</li>
 * </ul>
 *
 * <p>Дедупликация уведомлений: chat шлётся ТОЛЬКО когда objective завершается (newVal >= max),
 * не на каждый kill/break. После завершения объекта проверяется готовность всего квеста — ещё
 * один chat «Готов к сдаче».
 */
@Mod.EventBusSubscriber(modid = QuestNPC.MOD_ID)
public final class QuestEventHandler {

    private static final ResourceLocation CAP_RL = new ResourceLocation(QuestNPC.MOD_ID, "quest_progress");

    private QuestEventHandler() {}

    // -------------------------------------------------------------------------
    // Capability lifecycle
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void attachCaps(AttachCapabilitiesEvent<net.minecraft.world.entity.Entity> e) {
        if (e.getObject() instanceof Player && !(e.getObject() instanceof FakePlayer)) {
            e.addCapability(CAP_RL, new PlayerQuestProvider());
        }
    }

    /** Сохраняет capability через смерть / respawn / dimension change. */
    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone e) {
        e.getOriginal().reviveCaps();
        try {
            e.getOriginal().getCapability(PlayerQuestProvider.CAP).ifPresent(oldCap -> {
                e.getEntity().getCapability(PlayerQuestProvider.CAP).ifPresent(newCap -> {
                    newCap.deserializeNBT(oldCap.serializeNBT());
                });
            });
        } finally {
            e.getOriginal().invalidateCaps();
        }
    }

    // -------------------------------------------------------------------------
    // Login: cleanup orphaned + checkOnLogin objectives + initial sync
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        MinecraftServer server = sp.getServer();
        if (server == null) return;

        sp.getCapability(PlayerQuestProvider.CAP).ifPresent(prog -> {
            // Silent cleanup orphaned (decision §2 от 2026-05-25)
            Set<QuestKey> valid = QuestRegistry.collectAllValidKeys(server);
            prog.cleanupOrphaned(valid);

            // Check location objectives с checkOnLogin=true
            for (QuestKey key : new HashSet<>(prog.getActiveKeys())) {
                QuestDefinition q = QuestRegistry.lookup(key, server);
                if (q == null) continue;
                for (QuestObjective obj : q.getObjectives()) {
                    if (!obj.checkOnLogin()) continue;
                    long current = prog.getProgress(key, obj.getId());
                    if (current >= obj.getMaxProgress()) continue;
                    checkAndUpdateLocationObjective(sp, prog, key, q, obj);
                }
            }

            QuestChatHelper.syncProgressToClient(sp);
        });
    }

    // -------------------------------------------------------------------------
    // Kill triggers
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onKill(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer sp)) return;
        if (e.getEntity() instanceof Player) return; // PvP не считаем
        MinecraftServer server = sp.getServer();
        if (server == null) return;

        sp.getCapability(PlayerQuestProvider.CAP).ifPresent(prog -> {
            boolean anyChanged = false;
            for (QuestKey key : new HashSet<>(prog.getActiveKeys())) {
                QuestDefinition q = QuestRegistry.lookup(key, server);
                if (q == null) continue;

                for (QuestObjective obj : q.getObjectives()) {
                    if (!(obj instanceof KillObjective ko)) continue;
                    if (!ko.matches(e.getEntity())) continue;

                    long current = prog.getProgress(key, obj.getId());
                    if (current >= obj.getMaxProgress()) continue;

                    long newVal = prog.addProgress(key, obj.getId(), 1L, obj.getMaxProgress());
                    anyChanged = true;

                    if (newVal >= obj.getMaxProgress()) {
                        QuestChatHelper.sendObjectiveComplete(sp, q, obj);
                        if (q.isObjectivesComplete(prog, key)) {
                            QuestChatHelper.sendQuestReady(sp, q);
                        }
                    }
                }
            }
            if (anyChanged) QuestChatHelper.syncProgressToClient(sp);
        });
    }

    // -------------------------------------------------------------------------
    // Break-block triggers
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent e) {
        if (e.isCanceled()) return;
        if (!(e.getPlayer() instanceof ServerPlayer sp)) return;
        MinecraftServer server = sp.getServer();
        if (server == null) return;

        sp.getCapability(PlayerQuestProvider.CAP).ifPresent(prog -> {
            boolean anyChanged = false;
            for (QuestKey key : new HashSet<>(prog.getActiveKeys())) {
                QuestDefinition q = QuestRegistry.lookup(key, server);
                if (q == null) continue;

                for (QuestObjective obj : q.getObjectives()) {
                    if (!(obj instanceof BreakBlockObjective bo)) continue;
                    if (!bo.matches(e.getState())) continue;

                    long current = prog.getProgress(key, obj.getId());
                    if (current >= obj.getMaxProgress()) continue;

                    long newVal = prog.addProgress(key, obj.getId(), 1L, obj.getMaxProgress());
                    anyChanged = true;

                    if (newVal >= obj.getMaxProgress()) {
                        QuestChatHelper.sendObjectiveComplete(sp, q, obj);
                        if (q.isObjectivesComplete(prog, key)) {
                            QuestChatHelper.sendQuestReady(sp, q);
                        }
                    }
                }
            }
            if (anyChanged) QuestChatHelper.syncProgressToClient(sp);
        });
    }

    // -------------------------------------------------------------------------
    // Location polling (PlayerTickEvent)
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!(e.player instanceof ServerPlayer sp)) return;
        if (sp.tickCount % 20 != 0) return;
        MinecraftServer server = sp.getServer();
        if (server == null) return;

        sp.getCapability(PlayerQuestProvider.CAP).ifPresent(prog -> {
            boolean anyChanged = false;
            for (QuestKey key : new HashSet<>(prog.getActiveKeys())) {
                QuestDefinition q = QuestRegistry.lookup(key, server);
                if (q == null) continue;

                for (QuestObjective obj : q.getObjectives()) {
                    if (obj.autoCheckIntervalTicks() == 0) continue;

                    long current = prog.getProgress(key, obj.getId());
                    if (current >= obj.getMaxProgress()) continue;

                    if (checkAndUpdateLocationObjective(sp, prog, key, q, obj)) {
                        anyChanged = true;
                    }
                }
            }
            if (anyChanged) QuestChatHelper.syncProgressToClient(sp);
        });
    }

    /**
     * Проверяет location-objective (Biome/Structure). Если игрок в локации — мгновенно
     * заполняет прогресс до max и шлёт chat-уведомления. Возвращает true если прогресс
     * изменился.
     */
    private static boolean checkAndUpdateLocationObjective(ServerPlayer sp, PlayerQuestProgress prog,
                                                           QuestKey key, QuestDefinition q,
                                                           QuestObjective obj) {
        boolean inLocation;
        if (obj instanceof ReachBiomeObjective rb) {
            inLocation = rb.isInBiome(sp);
        } else if (obj instanceof ReachStructureObjective rs) {
            inLocation = rs.isInStructure(sp);
        } else {
            return false;
        }
        if (!inLocation) return false;

        prog.addProgress(key, obj.getId(), obj.getMaxProgress(), obj.getMaxProgress());
        QuestChatHelper.sendObjectiveComplete(sp, q, obj);
        if (q.isObjectivesComplete(prog, key)) {
            QuestChatHelper.sendQuestReady(sp, q);
        }
        return true;
    }
}
