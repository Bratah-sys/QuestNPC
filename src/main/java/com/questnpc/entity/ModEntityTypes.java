package com.questnpc.entity;

import com.questnpc.QuestNPC;
import com.questnpc.QuestNPCLogger;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Реестр типов сущностей мода QuestNPC.
 * Использует DeferredRegister для отложенной регистрации — аналогично ModBlocks/ModItems.
 */
public class ModEntityTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, QuestNPC.MOD_ID);

    /**
     * Тип сущности Квестового NPC.
     * Хитбокс: 0.6 x 1.95 (как у жителя).
     * Категория: CREATURE, не спавнится естественным образом.
     */
    public static final RegistryObject<EntityType<QuestNPCEntity>> QUEST_NPC =
            ENTITY_TYPES.register("quest_npc", () -> {
                QuestNPCLogger.info("Регистрация EntityType 'quest_npc' в моде '{}'", QuestNPC.MOD_ID);
                return EntityType.Builder.<QuestNPCEntity>of(QuestNPCEntity::new, MobCategory.CREATURE)
                        .sized(0.6F, 1.95F)
                        .clientTrackingRange(10)
                        .build("quest_npc");
            });

    /**
     * Регистрирует DeferredRegister на шине событий мода.
     *
     * @param modBus шина событий мода
     */
    public static void register(IEventBus modBus) {
        QuestNPCLogger.info("Регистрация DeferredRegister<EntityType> на mod event bus");
        ENTITY_TYPES.register(modBus);
    }
}
