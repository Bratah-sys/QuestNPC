package com.questnpc.events;

import com.questnpc.QuestNPC;
import com.questnpc.QuestNPCLogger;
import com.questnpc.capability.PlayerQuestProgress;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Регистрация Forge Capabilities мода (MOD-bus).
 * Stage 5 (v2.9.4): {@link PlayerQuestProgress}.
 */
@Mod.EventBusSubscriber(modid = QuestNPC.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModCapabilities {

    private ModCapabilities() {}

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.register(PlayerQuestProgress.class);
        QuestNPCLogger.info("RegisterCapabilitiesEvent: PlayerQuestProgress зарегистрирован");
    }
}
