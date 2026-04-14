package com.questnpc.item;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.schedule.ScheduleEntry;
import com.questnpc.network.PatrolPaintSessionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Предмет «Кисть патруля» — программная выдача через {@link com.questnpc.network.RequestPatrolBrushPacket}.
 * Не регистрируется в креативной вкладке.
 *
 * <p>NBT (при выдаче сервером):
 * <ul>
 *   <li>{@code TargetNPC} (UUID) — целевой NPC</li>
 *   <li>{@code SlotIndex} (int) — индекс слота расписания</li>
 *   <li>{@code PaintedBlocks} (long[]) — список {@link BlockPos#asLong()}</li>
 * </ul>
 *
 * <p>ПКМ по блоку — добавить позицию (Sneak+ПКМ — удалить).
 * ЛКМ обрабатывается в {@link com.questnpc.events.NPCInteractionHandler#onLeftClickBlock} через Forge-event.
 * При смене активного слота инвентаря {@link #inventoryTick} удаляет предмет и закрывает сессию.
 */
public class PatrolBrushItem extends Item {

    public PatrolBrushItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide) {
            // Клиентский ответ — красивая обратная связь (SUCCESS → swing hand)
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = context.getItemInHand();
        if (!(stack.getItem() instanceof PatrolBrushItem)) return InteractionResult.PASS;
        CompoundTag tag = stack.getTag();
        if (tag == null) return InteractionResult.PASS;

        // Позиция: если клик по верхней грани — блок выше (позиция ног NPC)
        BlockPos clicked = context.getClickedPos();
        BlockPos paintPos = context.getClickedFace() == Direction.UP ? clicked.above() : clicked.immutable();

        long[] existing = tag.getLongArray("PaintedBlocks");
        List<Long> list = new ArrayList<>(existing.length + 1);
        for (long l : existing) list.add(l);
        long packed = paintPos.asLong();

        boolean sneaking = player.isShiftKeyDown();
        if (sneaking) {
            boolean removed = list.remove((Long) packed);
            if (removed) {
                writeBack(tag, list);
                player.sendSystemMessage(Component.translatable(
                        "message.questnpc.patrol_brush.removed", list.size())
                        .withStyle(ChatFormatting.GRAY));
            }
        } else {
            if (list.size() >= ScheduleEntry.MAX_PATROL_ZONE_BLOCKS) {
                player.sendSystemMessage(Component.translatable(
                        "message.questnpc.patrol_brush.limit_reached",
                        ScheduleEntry.MAX_PATROL_ZONE_BLOCKS)
                        .withStyle(ChatFormatting.RED));
                return InteractionResult.CONSUME;
            }
            if (!list.contains(packed)) {
                list.add(packed);
                writeBack(tag, list);
                player.sendSystemMessage(Component.translatable(
                        "message.questnpc.patrol_brush.added", list.size())
                        .withStyle(ChatFormatting.AQUA));
            }
        }
        return InteractionResult.CONSUME;
    }

    private static void writeBack(CompoundTag tag, List<Long> list) {
        long[] arr = new long[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        tag.putLongArray("PaintedBlocks", arr);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide) return;
        if (!(entity instanceof ServerPlayer sp)) return;
        if (isSelected) return;

        boolean hadSession = PatrolPaintSessionManager.getInstance().hasSession(sp.getUUID());
        PatrolPaintSessionManager.getInstance().closeSession(sp.getUUID());
        if (hadSession) {
            sp.sendSystemMessage(Component.translatable("message.questnpc.patrol_brush.cancelled")
                    .withStyle(ChatFormatting.GRAY));
            QuestNPCLogger.debug("PatrolBrush cancelled: игрок {} сменил слот инвентаря",
                    sp.getName().getString());
        }
        // v2.6.1: если кисть заменяла палку — возвращаем палку обратно в тот же слот,
        // иначе — просто удаляем (защита от /give «мусорной» кисти).
        replaceWithStickOrRemove(sp, stack, slotId);
    }

    /** Запрещаем нанесение урона сущностям этой кистью. */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return false;
    }

    /** Не ломать блоки в креативе (обычный break cancel идёт через LeftClickBlock event). */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    /** Моментально «сломать» блок 0.0 сек — в сочетании с canAttackBlock=false это no-op. */
    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return 0.0F;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // визуальный блеск
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.questnpc.patrol_brush.line1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.questnpc.patrol_brush.line2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.questnpc.patrol_brush.line3")
                .withStyle(ChatFormatting.DARK_GRAY));
        CompoundTag tag = stack.getTag();
        int count = tag != null ? tag.getLongArray("PaintedBlocks").length : 0;
        tooltip.add(Component.translatable("tooltip.questnpc.patrol_brush.blocks", count)
                .withStyle(ChatFormatting.AQUA));
    }

    /** Предотвращает ломание блоков левой кнопкой через «mining» (дополнительная страховка). */
    public static boolean isBrush(ItemStack stack) {
        return stack.getItem() instanceof PatrolBrushItem;
    }

    /**
     * v2.6.1: если кисть заменяла палку при выдаче (флаг NBT {@code WasStick=true}) —
     * заменяет стек в указанном слоте на {@link Items#STICK} с количеством 1.
     * Иначе — просто удаляет стек (setCount(0)).
     *
     * <p>Используется при финализации (ЛКМ) и при отмене (смена активного слота).
     */
    public static void replaceWithStickOrRemove(ServerPlayer player, ItemStack brush, int slotId) {
        CompoundTag tag = brush.getTag();
        boolean wasStick = tag != null && tag.getBoolean("WasStick");
        if (wasStick) {
            player.getInventory().setItem(slotId, new ItemStack(Items.STICK));
        } else {
            brush.setCount(0);
        }
    }
}
