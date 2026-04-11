package com.questnpc;

import com.questnpc.block.ModBlocks;
import com.questnpc.client.ModKeyBindings;
import com.questnpc.client.debug.NPCDebugRenderer;
import com.questnpc.client.model.CustomModelManager;
import com.questnpc.client.model.CustomModelPackResources;
import com.questnpc.commands.NpcVisCommand;
import com.questnpc.entity.ModEntityTypes;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.item.ModCreativeTabs;
import com.questnpc.item.ModItems;
import com.questnpc.events.NPCInteractionHandler;
import com.questnpc.network.ModNetwork;
import com.questnpc.network.NPCMenuSessionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.network.chat.Component;

/**
 * Главный класс мода QuestNPC.
 * Служит точкой входа и регистрирует обработчики событий жизненного цикла.
 */
@Mod(QuestNPC.MOD_ID)
public class QuestNPC {

    public static final String MOD_ID = "questnpc";
    public static final String MOD_VERSION = "0.3.1-alpha-menurefactor-v2.5.4";

    public QuestNPC() {
        QuestNPCLogger.info("Инициализация мода QuestNPC v{}", MOD_VERSION);

        // Подписка на события шины мода (lifecycle)
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onAddPackFinders);
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

            // Создание папки кастомных моделей
            CustomModelManager.getInstance().ensureDirectoryExists();
        });

        QuestNPCLogger.info("FMLCommonSetupEvent: общая инициализация завершена");
    }

    /**
     * Регистрация кастомного ресурс-пака для загрузки .geo.json моделей из файловой системы.
     */
    private void onAddPackFinders(final AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;

        CustomModelManager.getInstance().ensureDirectoryExists();

        event.addRepositorySource(consumer -> {
            var pack = Pack.readMetaAndCreate(
                    "questnpc_custom_models",
                    Component.literal("QuestNPC Custom Models"),
                    true, // required — всегда активен
                    id -> new CustomModelPackResources(
                            CustomModelManager.getInstance().getModelsDirectory()),
                    PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP,
                    PackSource.BUILT_IN
            );
            if (pack != null) {
                consumer.accept(pack);
                QuestNPCLogger.info("Зарегистрирован ресурс-пак для кастомных моделей");
            }
        });
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

    // -------------------------------------------------------------------------
    // Серверные сессии NPC-меню: очистка
    // -------------------------------------------------------------------------

    /** Счётчик тиков для периодической очистки сессий. */
    private int sessionCleanupCounter = 0;

    /**
     * Закрывает сессию NPC-меню при выходе игрока с сервера.
     */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        NPCMenuSessionManager.getInstance().closeSession(
                event.getEntity().getUUID(),
                event.getEntity().getName().getString());
    }

    /**
     * Периодическая очистка просроченных сессий (каждые ~20 секунд).
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        sessionCleanupCounter++;
        if (sessionCleanupCounter >= NPCMenuSessionManager.CLEANUP_INTERVAL_TICKS) {
            sessionCleanupCounter = 0;
            NPCMenuSessionManager.getInstance().cleanupExpiredSessions();
        }
    }
}
