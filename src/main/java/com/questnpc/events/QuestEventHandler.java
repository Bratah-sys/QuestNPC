package com.questnpc.events;

import com.questnpc.QuestNPC;
import com.questnpc.QuestNPCLogger;
import com.questnpc.capability.PlayerQuestProvider;
import com.questnpc.capability.QuestState;
import com.questnpc.entity.quest.ObjectiveType;
import com.questnpc.entity.quest.QuestDefinition;
import com.questnpc.entity.quest.QuestObjective;
import com.questnpc.entity.quest.ServerQuestCache;
import com.questnpc.entity.quest.objective.BreakBlockObjective;
import com.questnpc.entity.quest.objective.KillObjective;
import com.questnpc.entity.quest.objective.ReachBiomeObjective;

import com.questnpc.entity.quest.objective.ReachStructureObjective;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = QuestNPC.MOD_ID)
public class QuestEventHandler {

    // 1. Игрок ломает блок
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && !event.getLevel().isClientSide()) {
            processProgress(player, ObjectiveType.BREAK_BLOCK, event);
        }
    }

    // 2. Игрок убивает моба
    @SubscribeEvent
    public static void onEntityKill(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && !player.level().isClientSide()) {
            processProgress(player, ObjectiveType.KILL, event);
        }
    }

    // 3. Поллинг (Проверка биомов и структур каждые 20 тиков = 1 сек)
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) return;

        ServerPlayer player = (ServerPlayer) event.player;
        if (player.tickCount % 20 == 0) {
            processProgress(player, ObjectiveType.REACH_BIOME, null);
            processProgress(player, ObjectiveType.REACH_STRUCTURE, null); // РАСКОММЕНТИРОВАНО
        }
    }

    /**
     * Универсальный метод проверки и обновления прогресса.
     */
    private static void processProgress(ServerPlayer player, ObjectiveType type, Object eventContext) {
        player.getCapability(PlayerQuestProvider.PLAYER_QUEST_DATA).ifPresent(data -> {

            boolean anyUpdated = false;

            // Пробегаемся по всем квестам, которые хранятся у игрока
            for (String questId : data.getActiveQuests()) {

                // Достаем квест из глобального кэша сервера
                QuestDefinition quest = ServerQuestCache.getQuest(questId);
                if (quest == null) continue;

                for (QuestObjective obj : quest.getObjectives()) {
                    if (obj.getType() != type) continue;

                    long currentProgress = data.getObjectiveProgress(obj.getId());
                    if (obj.isComplete(currentProgress)) continue; // Уже выполнено

                    boolean matched = false;

                    // Делегируем проверку конкретному классу Objective
                    if (type == ObjectiveType.BREAK_BLOCK && eventContext instanceof BlockEvent.BreakEvent breakEvent) {
                        matched = ((BreakBlockObjective) obj).matches(breakEvent.getState());
                    } else if (type == ObjectiveType.KILL && eventContext instanceof LivingDeathEvent killEvent) {
                        matched = ((KillObjective) obj).matches(killEvent.getEntity());
                    } else if (type == ObjectiveType.REACH_BIOME) {
                        matched = ((ReachBiomeObjective) obj).matches(player);
                    } else if (type == ObjectiveType.REACH_STRUCTURE) {
                        // НАЧАЛО ВСТАВКИ: Проверка для достижения структур
                        matched = ((ReachStructureObjective) obj).matches(player);
                        // КОНЕЦ ВСТАВКИ
                    }

                    if (matched) {
                        // Для биомов и структур прогресс сразу становится максимальным
                        long amountToAdd = (type == ObjectiveType.REACH_BIOME || type == ObjectiveType.REACH_STRUCTURE)
                                ? obj.getMaxProgress() : 1;

                        data.addObjectiveProgress(obj.getId(), amountToAdd);
                        anyUpdated = true;

                        // Проверяем, завершилась ли именно эта цель только что
                        if (obj.isComplete(currentProgress + amountToAdd)) {
                            player.sendSystemMessage(Component.literal("§aЦель выполнена: §f" + type.name()));
                            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);
                        }
                    }
                }
            }
        });
    }
}