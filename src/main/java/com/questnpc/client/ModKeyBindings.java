package com.questnpc.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.questnpc.QuestNPCLogger;
import com.questnpc.client.debug.NPCDebugRenderer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Клиентские кейбинды мода QuestNPC.
 * Регистрация через RegisterKeyMappingsEvent (mod bus), обработка через ClientTickEvent (Forge bus).
 */
public class ModKeyBindings {

    public static final KeyMapping RECALCULATE_ZONE = new KeyMapping(
            "key.questnpc.recalculate_zone",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.questnpc"
    );

    /**
     * Регистрация кейбиндов. Вызывается из mod bus.
     */
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(RECALCULATE_ZONE);
        QuestNPCLogger.info("Кейбинды QuestNPC зарегистрированы");
    }

    /**
     * Обработка нажатий. Регистрируется на Forge EVENT_BUS.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

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
    }
}
