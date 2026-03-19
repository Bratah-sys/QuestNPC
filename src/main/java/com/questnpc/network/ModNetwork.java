package com.questnpc.network;

import com.questnpc.QuestNPC;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Фабрика сетевого канала мода и точка регистрации пакетов.
 */
public final class ModNetwork {

    private static final String PROTOCOL_VERSION = "3";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(QuestNPC.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    /**
     * Регистрирует все пакеты канала.
     * Вызывать из {@code FMLCommonSetupEvent.enqueueWork()}.
     */
    public static void register() {
        INSTANCE.registerMessage(
                0,
                ToggleVisualizationPacket.class,
                ToggleVisualizationPacket::encode,
                ToggleVisualizationPacket::decode,
                ToggleVisualizationPacket::handle
        );
        INSTANCE.registerMessage(
                1,
                PathSyncPacket.class,
                PathSyncPacket::encode,
                PathSyncPacket::decode,
                PathSyncPacket::handle
        );
        INSTANCE.registerMessage(
                2,
                OpenNPCMenuPacket.class,
                OpenNPCMenuPacket::encode,
                OpenNPCMenuPacket::decode,
                OpenNPCMenuPacket::handle
        );
        INSTANCE.registerMessage(
                3,
                RequestPatrolChangePacket.class,
                RequestPatrolChangePacket::encode,
                RequestPatrolChangePacket::decode,
                RequestPatrolChangePacket::handle
        );
        INSTANCE.registerMessage(
                4,
                UpdateNPCSettingsPacket.class,
                UpdateNPCSettingsPacket::encode,
                UpdateNPCSettingsPacket::decode,
                UpdateNPCSettingsPacket::handle
        );
        INSTANCE.registerMessage(
                5,
                RenameNPCPacket.class,
                RenameNPCPacket::encode,
                RenameNPCPacket::decode,
                RenameNPCPacket::handle
        );
        INSTANCE.registerMessage(
                6,
                DeleteNPCPacket.class,
                DeleteNPCPacket::encode,
                DeleteNPCPacket::decode,
                DeleteNPCPacket::handle
        );
        INSTANCE.registerMessage(
                7,
                ChangeModelPacket.class,
                ChangeModelPacket::encode,
                ChangeModelPacket::decode,
                ChangeModelPacket::handle
        );
        INSTANCE.registerMessage(
                8,
                CloseMenuPacket.class,
                CloseMenuPacket::encode,
                CloseMenuPacket::decode,
                CloseMenuPacket::handle
        );
    }

    private ModNetwork() {}
}
