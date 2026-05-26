package com.questnpc.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.questnpc.capability.PlayerQuestProvider;
import com.questnpc.capability.QuestKey;
import com.questnpc.capability.QuestRegistry;
import com.questnpc.entity.quest.QuestDefinition;
import com.questnpc.entity.quest.QuestObjective;
import com.questnpc.entity.quest.objective.KillObjective;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

/**
 * Stage 7 (v2.9.6): Forge Global Loot Modifier для quest-locked drops.
 *
 * <p>Срабатывает при vanilla loot generation после смерти моба. Если убийца —
 * {@link ServerPlayer} с активным квестом, у которого есть {@link KillObjective}
 * с непустым {@code lootDrop} и {@code ko.matches(victim)} — добавляет копию
 * lootDrop ItemStack к выпадающему луту.
 *
 * <p>Если objective уже выполнен ({@code progress >= max}) — drop НЕ добавляется,
 * чтобы избежать infinite-farm после выполнения квеста.
 *
 * <p>Применяется ко всем loot tables через единый
 * {@code data/forge/loot_modifiers/global_loot_modifiers.json} — фильтрация по
 * entity делается внутри modifier'а через {@link KillObjective#matches}.
 */
public class QuestKillDropModifier extends LootModifier {

    public static final Codec<QuestKillDropModifier> CODEC =
            RecordCodecBuilder.create(inst ->
                    LootModifier.codecStart(inst).apply(inst, QuestKillDropModifier::new));

    public QuestKillDropModifier(LootItemCondition[] conditionsIn) {
        super(conditionsIn);
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot,
                                                 LootContext context) {
        Entity killer = context.getParamOrNull(LootContextParams.KILLER_ENTITY);
        if (!(killer instanceof ServerPlayer player)) return generatedLoot;

        Entity victim = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (victim == null) return generatedLoot;

        player.getCapability(PlayerQuestProvider.CAP).ifPresent(prog -> {
            for (QuestKey key : new HashSet<>(prog.getActiveKeys())) {
                QuestDefinition q = QuestRegistry.lookup(key, player.getServer());
                if (q == null) continue;
                for (QuestObjective obj : q.getObjectives()) {
                    if (!(obj instanceof KillObjective ko)) continue;
                    if (ko.getLootDrop().isEmpty()) continue;
                    if (!ko.matches(victim)) continue;
                    // Skip если objective уже выполнен — иначе бесконечный фарм после completion
                    if (prog.getProgress(key, obj.getId()) >= obj.getMaxProgress()) continue;
                    generatedLoot.add(ko.getLootDrop().copy());
                }
            }
        });

        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
