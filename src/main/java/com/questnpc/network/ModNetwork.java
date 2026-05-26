package com.questnpc.network;

import com.questnpc.QuestNPC;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Фабрика сетевого канала мода и точка регистрации пакетов.
 */
public final class ModNetwork {

    private static final String PROTOCOL_VERSION = "12";

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
        INSTANCE.registerMessage(
                9,
                UpdateTradingEnabledPacket.class,
                UpdateTradingEnabledPacket::encode,
                UpdateTradingEnabledPacket::decode,
                UpdateTradingEnabledPacket::handle
        );
        INSTANCE.registerMessage(
                10,
                UpdateTradeOffersPacket.class,
                UpdateTradeOffersPacket::encode,
                UpdateTradeOffersPacket::decode,
                UpdateTradeOffersPacket::handle
        );
        INSTANCE.registerMessage(
                11,
                OpenTradingScreenPacket.class,
                OpenTradingScreenPacket::encode,
                OpenTradingScreenPacket::decode,
                OpenTradingScreenPacket::handle
        );
        INSTANCE.registerMessage(
                12,
                UpdateSchedulePacket.class,
                UpdateSchedulePacket::encode,
                UpdateSchedulePacket::decode,
                UpdateSchedulePacket::handle
        );
        INSTANCE.registerMessage(
                13,
                ScheduleSyncPacket.class,
                ScheduleSyncPacket::encode,
                ScheduleSyncPacket::decode,
                ScheduleSyncPacket::handle
        );
        INSTANCE.registerMessage(
                14,
                RequestPatrolBrushPacket.class,
                RequestPatrolBrushPacket::encode,
                RequestPatrolBrushPacket::decode,
                RequestPatrolBrushPacket::handle
        );
        INSTANCE.registerMessage(
                15,
                FinishPatrolPaintPacket.class,
                FinishPatrolPaintPacket::encode,
                FinishPatrolPaintPacket::decode,
                FinishPatrolPaintPacket::handle
        );
        INSTANCE.registerMessage(
                16,
                UpdateEquipmentPacket.class,
                UpdateEquipmentPacket::encode,
                UpdateEquipmentPacket::decode,
                UpdateEquipmentPacket::handle
        );
        INSTANCE.registerMessage(
                17,
                UpdateNPCQuestsPacket.class,
                UpdateNPCQuestsPacket::encode,
                UpdateNPCQuestsPacket::decode,
                UpdateNPCQuestsPacket::handle
        );
        INSTANCE.registerMessage(
                18,
                OpenNPCTradePacket.class,
                OpenNPCTradePacket::encode,
                OpenNPCTradePacket::decode,
                OpenNPCTradePacket::handle
        );
        // Stage 5 (v2.9.4): 6 player-side quest пакетов (ID 19-24).
        INSTANCE.registerMessage(
                19,
                OpenNPCQuestsPacket.class,
                OpenNPCQuestsPacket::encode,
                OpenNPCQuestsPacket::decode,
                OpenNPCQuestsPacket::handle
        );
        INSTANCE.registerMessage(
                20,
                OpenPlayerQuestListPacket.class,
                OpenPlayerQuestListPacket::encode,
                OpenPlayerQuestListPacket::decode,
                OpenPlayerQuestListPacket::handle
        );
        INSTANCE.registerMessage(
                21,
                RequestQuestAcceptPacket.class,
                RequestQuestAcceptPacket::encode,
                RequestQuestAcceptPacket::decode,
                RequestQuestAcceptPacket::handle
        );
        INSTANCE.registerMessage(
                22,
                RequestQuestTurnInPacket.class,
                RequestQuestTurnInPacket::encode,
                RequestQuestTurnInPacket::decode,
                RequestQuestTurnInPacket::handle
        );
        INSTANCE.registerMessage(
                23,
                RequestQuestAbandonPacket.class,
                RequestQuestAbandonPacket::encode,
                RequestQuestAbandonPacket::decode,
                RequestQuestAbandonPacket::handle
        );
        INSTANCE.registerMessage(
                24,
                SyncPlayerQuestProgressPacket.class,
                SyncPlayerQuestProgressPacket::encode,
                SyncPlayerQuestProgressPacket::decode,
                SyncPlayerQuestProgressPacket::handle
        );
        // Stage 8 (v2.9.8): пакеты для Player Quest Journal (ID 25-26).
        INSTANCE.registerMessage(
                25,
                RequestJournalRefreshPacket.class,
                RequestJournalRefreshPacket::encode,
                RequestJournalRefreshPacket::decode,
                RequestJournalRefreshPacket::handle
        );
        INSTANCE.registerMessage(
                26,
                RequestJournalAbandonPacket.class,
                RequestJournalAbandonPacket::encode,
                RequestJournalAbandonPacket::decode,
                RequestJournalAbandonPacket::handle
        );
    }

    private ModNetwork() {}
}
