package com.questnpc.events;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.schedule.ScheduleEntry;
import com.questnpc.item.PatrolBrushItem;
import com.questnpc.network.FinishPatrolPaintPacket;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.NPCMenuSessionManager;
import com.questnpc.network.OpenNPCMenuPacket;
import com.questnpc.network.ScheduleSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

/**
 * Обработчик взаимодействий игрока с NPC: открытие меню (палка) и назначение точки патруля (спец-палка).
 */
public class NPCInteractionHandler {

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        // Только серверная сторона, основная рука
        if (event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getTarget() instanceof QuestNPCEntity npc)) return;

        ItemStack held = event.getEntity().getMainHandItem();
        // Должна быть обычная палка (не спец-палка смены точки)
        if (!held.is(Items.STICK)) return;
        CompoundTag questTag = held.getTagElement("QuestNPC");
        if (questTag != null && questTag.getBoolean("PatrolChange")) return; // спец-палка — игнор

        ServerPlayer player = (ServerPlayer) event.getEntity();
        QuestNPCLogger.info("Игрок {} открыл меню NPC {}", player.getName().getString(), npc.getId());

        // Открываем серверную сессию для валидации C2S-пакетов
        NPCMenuSessionManager.getInstance().openSession(
                player.getUUID(), npc.getId(), player.getName().getString());

        // Упаковываем расписание в список NBT-тегов
        List<CompoundTag> scheduleTags = new ArrayList<>();
        for (ScheduleEntry e : npc.getSchedule()) {
            scheduleTags.add(e.save());
        }

        // === УПАКОВКА ДИАЛОГОВ ДЛЯ ОТПРАВКИ НА КЛИЕНТ (MagmaIce12) ===
        List<CompoundTag> serializedDialogues = new ArrayList<>();
        if (npc.getDialogues() != null) {
            for (com.questnpc.entity.dialogue.DialogueNode node : npc.getDialogues().values()) {
                CompoundTag nodeTag = new CompoundTag();
                nodeTag.putString("Id", node.getId());
                nodeTag.putString("NpcText", node.getNpcText());

                if (node.getOptions() != null && !node.getOptions().isEmpty()) {
                    ListTag optionList = new ListTag();
                    for (com.questnpc.entity.dialogue.DialogueOption opt : node.getOptions()) {
                        CompoundTag optTag = new CompoundTag();
                        optTag.putString("Text", opt.getText());
                        optTag.putString("NextNodeId", opt.getNextNodeId());
                        optTag.putString("Action", opt.getAction());
                        optionList.add(optTag);
                    }
                    nodeTag.put("Options", optionList);
                }
                serializedDialogues.add(nodeTag);
            }
        }
        // =============================================================

        // Отправляем S2C-пакет для открытия меню с текущими настройками NPC
        ModNetwork.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new OpenNPCMenuPacket(
                        npc.getId(),
                        npc.getPatrolSpeed(),
                        npc.getPatrolDelayMin(),
                        npc.getPatrolDelayMax(),
                        npc.getModelEntityType(),
                        npc.getTradingEnabled(),
                        npc.getTradeSets(),
                        new java.util.ArrayList<>(npc.getLockedTradeSets()), // v2.9.5
                        npc.isScheduleEnabled(),
                        scheduleTags,
                        npc.copyEquipmentSnapshot(),
                        npc.isQuestsEnabled(),
                        npc.getQuests(),
                        // Вставляем 3 новых параметра диалогов в самый конец
                        npc.isDialoguesEnabled(),
                        npc.getStartNodeId(),
                        serializedDialogues
                )
        );
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ItemStack held = event.getEntity().getMainHandItem();
        if (!held.is(Items.STICK)) return;
        CompoundTag questTag = held.getTagElement("QuestNPC");
        if (questTag == null || !questTag.getBoolean("PatrolChange")) return;
        if (!questTag.hasUUID("TargetNPC")) return;

        ServerPlayer player = (ServerPlayer) event.getEntity();
        UUID npcUuid = questTag.getUUID("TargetNPC");
        BlockPos clickedPos = event.getPos();
        Direction face = event.getFace();

        // Определение центра патруля
        BlockPos center;
        if (face == Direction.UP) {
            center = clickedPos.above();
        } else {
            center = clickedPos;
        }

        QuestNPCLogger.debug("Спец-палка клик по блоку [{},{},{}], грань {} → центр [{},{},{}]",
                clickedPos.getX(), clickedPos.getY(), clickedPos.getZ(), face,
                center.getX(), center.getY(), center.getZ());

        // Поиск NPC по UUID в мире
        ServerLevel serverLevel = (ServerLevel) event.getLevel();
        Entity entity = serverLevel.getEntity(npcUuid);

        if (!(entity instanceof QuestNPCEntity npc)) {
            player.sendSystemMessage(Component.translatable("message.questnpc.npc_not_found"));
            QuestNPCLogger.warn("NPC {} не найден в мире — смена точки отменена", npcUuid);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        // Привязка
        npc.setBoundBlockPos(center);
        npc.activatePatrol();

        QuestNPCLogger.info("Игрок {} установил точку патруля NPC {} на [{}, {}, {}] (грань: {})",
                player.getName().getString(), npc.getId(),
                center.getX(), center.getY(), center.getZ(), face);

        // Спец-палка превращается обратно в чистую обычную палку
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));

        // Сообщение
        String coords = center.getX() + ", " + center.getY() + ", " + center.getZ();
        player.sendSystemMessage(Component.translatable("message.questnpc.patrol_set", coords));

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack held = event.getEntity().getMainHandItem();
        if (!(held.getItem() instanceof PatrolBrushItem)) return;

        // Не ломать блок — только финализация
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);

        if (event.getLevel().isClientSide()) {
            ModNetwork.INSTANCE.sendToServer(new FinishPatrolPaintPacket());
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() == null) return;
        ItemStack held = event.getPlayer().getMainHandItem();
        if (held.getItem() instanceof PatrolBrushItem) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof QuestNPCEntity npc)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        List<CompoundTag> serialized = new ArrayList<>();
        for (ScheduleEntry e : npc.getSchedule()) serialized.add(e.save());
        ModNetwork.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ScheduleSyncPacket(npc.getId(), npc.isScheduleEnabled(), serialized)
        );
    }
}