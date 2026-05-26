package com.questnpc.loot;

import com.mojang.serialization.Codec;
import com.questnpc.QuestNPC;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Stage 7 (v2.9.6): регистрация Forge Global Loot Modifier serializer'ов.
 * Вызывается из {@link QuestNPC} конструктора через {@link #register}.
 */
public final class ModLootModifiers {

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, QuestNPC.MOD_ID);

    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> QUEST_KILL_DROP =
            SERIALIZERS.register("quest_kill_drop", () -> QuestKillDropModifier.CODEC);

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }

    private ModLootModifiers() {}
}
