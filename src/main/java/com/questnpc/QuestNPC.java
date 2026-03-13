package com.questnpc;

import com.questnpc.block.ModBlocks;
import com.questnpc.client.ModKeyBindings;
import com.questnpc.client.debug.NPCDebugRenderer;
import com.questnpc.commands.NpcVisCommand;
import com.questnpc.entity.ModEntityTypes;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.item.ModCreativeTabs;
import com.questnpc.item.ModItems;
import com.questnpc.events.NPCInteractionHandler;
import com.questnpc.network.ModNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Главный класс мода QuestNPC.
 * Служит точкой входа и регистрирует обработчики событий жизненного цикла.
 */
@Mod(QuestNPC.MOD_ID)
public class QuestNPC {

    public static final String MOD_ID = "questnpc";
    public static final String MOD_VERSION = "0.2.2-alpha";

    public QuestNPC() {
        QuestNPCLogger.info("Инициализация мода QuestNPC v{}", MOD_VERSION);

        // Подписка на события шины мода (lifecycle)
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onClientSetup);
        modBus.addListener(ModKeyBindings::register);

        // Регистрация блоков, предметов, сущностей и креативной вкладки
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModEntityTypes.register(modBus);
        ModCreativeTabs.register(modBus);

        // Регистрация атрибутов сущностей (EntityAttributeCreationEvent — mod bus)
        modBus.addListener(this::onEntityAttributeCreation);

        // Подписка на события Forge (команды, серверные события)
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new NPCInteractionHandler());

        QuestNPCLogger.info("Конструктор QuestNPC завершён");
    }

    /**
     * Общая инициализация (клиент + сервер).
     */
    private void onCommonSetup(final FMLCommonSetupEvent event) {
        QuestNPCLogger.info("FMLCommonSetupEvent: начало общей инициализации");

        event.enqueueWork(() -> {
            // Проверка зависимостей
            var results = ModIntegrationTester.runAllTests();
            long passed = results.stream().filter(ModIntegrationTester.TestResult::success).count();
            QuestNPCLogger.info("Предварительная проверка зависимостей: {}/{} пройдено", passed, results.size());

            // Регистрация сетевого канала
            ModNetwork.register();
            QuestNPCLogger.info("ModNetwork зарегистрирован");
        });

        QuestNPCLogger.info("FMLCommonSetupEvent: общая инициализация завершена");
    }

    /**
     * Клиентская инициализация.
     */
    private void onClientSetup(final FMLClientSetupEvent event) {
        QuestNPCLogger.info("FMLClientSetupEvent: начало клиентской инициализации");

        // Регистрация дебаг-рендерера на Forge EVENT_BUS (не на mod bus)
        MinecraftForge.EVENT_BUS.register(new NPCDebugRenderer());
        MinecraftForge.EVENT_BUS.register(new ModKeyBindings());
        QuestNPCLogger.info("NPCDebugRenderer и кейбинды зарегистрированы");

        QuestNPCLogger.info("FMLClientSetupEvent: клиентская инициализация завершена");
    }

    /**
     * Регистрация атрибутов сущностей мода.
     */
    private void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        QuestNPCLogger.info("EntityAttributeCreationEvent: регистрация атрибутов QuestNPCEntity");
        event.put(ModEntityTypes.QUEST_NPC.get(), QuestNPCEntity.createAttributes().build());
        QuestNPCLogger.info("Атрибуты QuestNPCEntity успешно зарегистрированы (MAX_HEALTH=20, MOVEMENT_SPEED=0.35)");
    }

    /**
     * Регистрация команд при запуске сервера.
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        QuestNPCLogger.info("RegisterCommandsEvent: регистрация команд");
        TestModCommand.register(event.getDispatcher());
        NpcVisCommand.register(event.getDispatcher());
    }
}
