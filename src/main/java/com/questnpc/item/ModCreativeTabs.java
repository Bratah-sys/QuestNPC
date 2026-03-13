package com.questnpc.item;

import com.questnpc.QuestNPC;
import com.questnpc.QuestNPCLogger;
import com.questnpc.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, QuestNPC.MOD_ID);

    public static final RegistryObject<CreativeModeTab> QUESTNPC_TAB = CREATIVE_TABS.register("questnpc_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(Items.WHEAT))
                    .title(Component.translatable("creativetab.questnpc"))
                    .displayItems((parameters, output) -> {
                        QuestNPCLogger.info("Добавление предметов в креатив-вкладку 'questnpc_tab'");
                        output.accept(ModBlocks.FARMNPC_BLOCK.get());
                        output.accept(ModItems.QUEST_NPC_SPAWN_EGG.get());
                        QuestNPCLogger.info("Добавлено в вкладку: farmnpc_block, quest_npc_spawn_egg");
                    })
                    .build());

    public static void register(IEventBus modBus) {
        CREATIVE_TABS.register(modBus);
    }
}
