package com.questnpc.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.questnpc.QuestNPCLogger;
import com.questnpc.client.debug.NPCDebugRenderer;
import com.questnpc.client.gui.QuestBookOverlay;
import com.questnpc.item.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Клиентские кейбинды мода QuestNPC.
 * Регистрация через RegisterKeyMappingsEvent (mod bus), обработка через ClientTickEvent (Forge bus).
 */

@Mod.EventBusSubscriber(modid = "questnpc", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)

public class ModKeyBindings {

    public static final KeyMapping RECALCULATE_ZONE = new KeyMapping(
            "key.questnpc.recalculate_zone",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.questnpc"
    );

    public static final KeyMapping OPEN_QUEST_BOOK = new KeyMapping(
            "key.questnpc.open_quest_book",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.questnpc"
    );

    /**
     * Регистрация кейбиндов. Вызывается из mod bus.
     */
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(RECALCULATE_ZONE);
        event.register(OPEN_QUEST_BOOK);
        QuestNPCLogger.info("Кейбинды QuestNPC зарегистрированы");
    }


    /**
     * Обработка нажатий. Регистрируется на Forge EVENT_BUS.
     */
    private static int curiosPollCounter = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            QuestBookOverlay.hasBookEquippedCached = false;
            return;
        }

        // MED-001: опрашиваем Curios раз в секунду, не на каждом кадре рендера.
        if (++curiosPollCounter >= 20) {
            curiosPollCounter = 0;
            QuestBookOverlay.hasBookEquippedCached = CuriosApi.getCuriosInventory(mc.player).map(inv ->
                    inv.findFirstCurio(stack -> stack.is(ModItems.QUEST_BOOK.get())).isPresent()
            ).orElse(false);
        }

        while (RECALCULATE_ZONE.consumeClick()) {
            NPCDebugRenderer.clearCache();
            QuestNPCLogger.info("Игрок нажал [{}] — пересчёт зон патрулирования",
                    RECALCULATE_ZONE.getKey().getDisplayName().getString());

            // Подтверждение в чат игроку
            mc.player.displayClientMessage(
                    Component.literal("§a[QuestNPC] Зоны патрулирования пересчитаны"),
                    false
            );
        }

        while (OPEN_QUEST_BOOK.consumeClick()) {
            // Переключаем статус "Новый квест" в оверлее
            com.questnpc.client.gui.QuestBookOverlay.hasNewQuest = !com.questnpc.client.gui.QuestBookOverlay.hasNewQuest;

            QuestNPCLogger.info("Статус уведомления изменен.");

            // Тестовое сообщение
            mc.player.displayClientMessage(
                    Component.literal("§6[QuestNPC] §fСтатус уведомлений изменен!"),
                    true
            );
        }
    }
}
