package com.questnpc.capability;

import com.questnpc.QuestNPC;
import com.questnpc.QuestNPCLogger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = QuestNPC.MOD_ID)
public class QuestCapabilityEvents {

    // Уникальный ID для прикрепления к игроку
    public static final ResourceLocation QUEST_DATA_ID = new ResourceLocation(QuestNPC.MOD_ID, "quest_data");

    /**
     * Шаг 1: Прикрепляем Capability ко всем сущностям типа Player.
     */
    @SubscribeEvent
    public static void onAttachCapabilitiesPlayer(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            if (!event.getObject().getCapability(PlayerQuestProvider.PLAYER_QUEST_DATA).isPresent()) {
                event.addCapability(QUEST_DATA_ID, new PlayerQuestProvider());
            }
        }
    }

    /**
     * Шаг 2: При смерти/возрождении (или возвращении из Энда) игрок технически пересоздается.
     * Копируем квесты из старого тела в новое.
     */
    @SubscribeEvent
    public static void onPlayerCloned(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player clone = event.getEntity();

        original.getCapability(PlayerQuestProvider.PLAYER_QUEST_DATA).ifPresent(oldData -> {
            clone.getCapability(PlayerQuestProvider.PLAYER_QUEST_DATA).ifPresent(newData -> {
                newData.copyFrom(oldData);
                QuestNPCLogger.debug("Данные квестов успешно скопированы для игрока " + clone.getName().getString());
            });
        });
    }

    /**
     * Шаг 3: Регистрация самой Capability в Forge (нужно вызывать из шины мода)
     */
    @Mod.EventBusSubscriber(modid = QuestNPC.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            event.register(PlayerQuestData.class);
        }
    }
}