package com.questnpc.item;

import com.questnpc.QuestNPC;
import com.questnpc.QuestNPCLogger;
import com.questnpc.block.ModBlocks;
import com.questnpc.entity.ModEntityTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, QuestNPC.MOD_ID);

    public static final RegistryObject<Item> FARMNPC_BLOCK = ITEMS.register("farmnpc_block",
            () -> new BlockItem(ModBlocks.FARMNPC_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> QUEST_BOOK = ITEMS.register("quest_book",
            () -> new QuestBookItem(new Item.Properties()));

    /**
     * Яйцо призыва Квестового NPC с кастомной текстурой egg_farm.png.
     * Цвета (0x5C4A1E, 0x4CAF50) используются только как fallback; текстура задана в models/item/.
     */
    public static final RegistryObject<Item> QUEST_NPC_SPAWN_EGG = ITEMS.register("quest_npc_spawn_egg", () -> {
        QuestNPCLogger.info("Регистрация spawn egg 'quest_npc_spawn_egg' в моде '{}'", QuestNPC.MOD_ID);
        return new ForgeSpawnEggItem(ModEntityTypes.QUEST_NPC, 0x5C4A1E, 0x4CAF50, new Item.Properties());
    });

    /**
     * Кисть патруля (v2.6.0). Выдаётся сервером через {@link com.questnpc.network.RequestPatrolBrushPacket};
     * НЕ добавляется в {@link ModCreativeTabs} — доступна только программно.
     */
    public static final RegistryObject<Item> PATROL_BRUSH = ITEMS.register("patrol_brush",
            () -> new PatrolBrushItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
