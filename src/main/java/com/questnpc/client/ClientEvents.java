package com.questnpc.client;

import com.questnpc.QuestNPCLogger;
import com.questnpc.client.gui.QuestBookOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "questnpc", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("quest_book_icon", QuestBookOverlay.HUD_QUEST_BOOK);
        QuestNPCLogger.debug("HUD quest_book зарегистрирован");
    }
}
