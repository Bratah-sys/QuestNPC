package com.questnpc.client;

import com.questnpc.QuestNPC; // Замени на путь к твоему главному классу мода
import com.questnpc.client.gui.QuestBookOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// ВАЖНО: bus = Mod.EventBusSubscriber.Bus.MOD и value = Dist.CLIENT
@Mod.EventBusSubscriber(modid = "questnpc", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        System.out.println("[QuestNPC] Попытка регистрации оверлея...");

        // Регистрируем наш HUD
        event.registerAboveAll("quest_book_icon", QuestBookOverlay.HUD_QUEST_BOOK);

        System.out.println("[QuestNPC] Оверлей успешно зарегистрирован!");
    }
}