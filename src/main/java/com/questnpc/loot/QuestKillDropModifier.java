package com.questnpc.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.questnpc.QuestNPCLogger;
import com.questnpc.capability.PlayerQuestProvider;
import com.questnpc.capability.QuestKey;
import com.questnpc.capability.QuestRegistry;
import com.questnpc.entity.quest.QuestDefinition;
import com.questnpc.entity.quest.QuestObjective;
import com.questnpc.entity.quest.objective.BringObjective;
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
        // v2.9.7 hotfix: KILLER_ENTITY может быть null для проектильных смерти (стрелы и др.) —
        // fallback на LAST_DAMAGE_PLAYER (vanilla: последний игрок нанёсший урон).
        ServerPlayer player = null;
        Entity killer = context.getParamOrNull(LootContextParams.KILLER_ENTITY);
        if (killer instanceof ServerPlayer sp) {
            player = sp;
        } else {
            Entity last = context.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER);
            if (last instanceof ServerPlayer sp) player = sp;
        }
        if (player == null) return generatedLoot;
        if (player.getServer() == null) return generatedLoot;

        Entity victim = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (victim == null) return generatedLoot;

        final ServerPlayer playerFinal = player;
        player.getCapability(PlayerQuestProvider.CAP).ifPresent(prog -> {
            for (QuestKey key : new HashSet<>(prog.getActiveKeys())) {
                QuestDefinition q = QuestRegistry.lookup(key, playerFinal.getServer());
                if (q == null) continue;

                for (QuestObjective obj : q.getObjectives()) {
                    // Kill quest-drop: lootDrop при убийстве совпадающей цели
                    if (obj instanceof KillObjective ko) {
                        if (ko.getLootDrop().isEmpty()) continue;
                        if (!ko.matches(victim)) continue;
                        // Skip если objective уже выполнен — избежание infinite-farm
                        if (prog.getProgress(key, obj.getId()) >= obj.getMaxProgress()) continue;
                        ItemStack drop = ko.getLootDrop().copy();
                        generatedLoot.add(drop);
                        QuestNPCLogger.debug("GLM: added kill-drop {} (quest {} obj {}) for {} kill {}",
                                drop, q.getId(), obj.getId(),
                                playerFinal.getName().getString(),
                                victim.getType().getDescriptionId());
                        continue;
                    }
                    // v2.9.7: Bring source-drop — целевой предмет падает с указанного моба,
                    // пока в инвентаре игрока меньше required count.
                    if (obj instanceof BringObjective bo) {
                        if (bo.getStack().isEmpty() || bo.getDropSourceEntityType() == null) continue;
                        if (!bo.matches(victim)) continue;
                        if (bo.checkInventoryCount(playerFinal) >= bo.getCount()) continue;
                        ItemStack drop = bo.getStack().copy();
                        drop.setCount(1);
                        generatedLoot.add(drop);
                        QuestNPCLogger.debug("GLM: added bring-source drop {} (quest {} obj {}) for {} kill {}",
                                drop, q.getId(), obj.getId(),
                                playerFinal.getName().getString(),
                                victim.getType().getDescriptionId());
                    }
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
