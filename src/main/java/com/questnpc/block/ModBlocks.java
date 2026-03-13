package com.questnpc.block;

import com.questnpc.QuestNPC;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, QuestNPC.MOD_ID);

    public static final RegistryObject<Block> FARMNPC_BLOCK = BLOCKS.register("farmnpc_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIRT)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.WOOD)
                    .requiresCorrectToolForDrops()));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
