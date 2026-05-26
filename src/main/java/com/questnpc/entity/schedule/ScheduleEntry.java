package com.questnpc.entity.schedule;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Один слот расписания NPC. Привязан к игровому времени суток (0..24000 тиков).
 * Диапазон {@code [fromTick, toTick)} — полуинтервал; если {@code toTick < fromTick},
 * слот пересекает полночь ("ночная смена").
 *
 * <p>Изменяемый POJO — поля правятся напрямую из админского экрана расписания.
 */
public class ScheduleEntry {

    public enum Type { ACTIVITY, TRADE, ANIMATION }
    public enum Movement { POINT, PATROL }

    public static final int MAX_NAME_LENGTH = 32;
    public static final int DAY_TICKS = 24000;
    /** Максимум блоков в одной зоне патруля (защита от переполнения NBT/пакета). */
    public static final int MAX_PATROL_ZONE_BLOCKS = 256;

    public String name = "";
    public Type type = Type.ACTIVITY;
    public int fromTick = 0;
    public int toTick = DAY_TICKS;
    public Movement movement = Movement.POINT;

    @Nullable
    public BlockPos position;

    /** Для Type.TRADE — имя набора сделок, который NPC будет использовать. */
    public String tradeSet = "";

    /** Для Type.ANIMATION — имя анимации. */
    public String animation = "";

    // Флаги взаимодействия — имеют смысл только для Type.ACTIVITY
    public boolean interactTrade = false;
    public String interactTradeSet = "";
    public boolean interactDialog = false; // WIP
    public boolean interactQuest = false;  // WIP

    /**
     * Зона патруля — набор позиций ног NPC для {@link Movement#PATROL}.
     * Если пустая — PATROL ведёт себя как POINT (fallback на {@link #position}).
     * Редактируется игроком через предмет «Кисть патруля» (v2.6.0).
     */
    public final List<BlockPos> patrolZone = new ArrayList<>();

    /**
     * Проверяет, попадает ли данный тик времени суток ([0..24000)) в диапазон слота.
     * Корректно обрабатывает слоты, пересекающие полночь.
     */
    public boolean containsTime(int timeOfDay) {
        int from = ((fromTick % DAY_TICKS) + DAY_TICKS) % DAY_TICKS;
        int to = ((toTick % DAY_TICKS) + DAY_TICKS) % DAY_TICKS;
        if (from == to) {
            // Диапазон нулевой длины — интерпретируем как "весь день"
            return true;
        }
        if (from < to) {
            return timeOfDay >= from && timeOfDay < to;
        } else {
            // Wrap-around через полночь
            return timeOfDay >= from || timeOfDay < to;
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name != null ? name : "");
        tag.putString("Type", type.name());
        tag.putInt("From", fromTick);
        tag.putInt("To", toTick);
        tag.putString("Movement", movement.name());
        if (position != null) {
            tag.putInt("PosX", position.getX());
            tag.putInt("PosY", position.getY());
            tag.putInt("PosZ", position.getZ());
            tag.putBoolean("HasPos", true);
        } else {
            tag.putBoolean("HasPos", false);
        }
        tag.putString("TradeSet", tradeSet != null ? tradeSet : "");
        tag.putString("Animation", animation != null ? animation : "");
        tag.putBoolean("InteractTrade", interactTrade);
        tag.putString("InteractTradeSet", interactTradeSet != null ? interactTradeSet : "");
        tag.putBoolean("InteractDialog", interactDialog);
        tag.putBoolean("InteractQuest", interactQuest);
        if (!patrolZone.isEmpty()) {
            long[] packed = new long[patrolZone.size()];
            for (int i = 0; i < patrolZone.size(); i++) {
                packed[i] = patrolZone.get(i).asLong();
            }
            tag.putLongArray("PatrolZone", packed);
        }
        return tag;
    }

    public static ScheduleEntry load(CompoundTag tag) {
        ScheduleEntry e = new ScheduleEntry();
        e.name = tag.getString("Name");
        try {
            e.type = Type.valueOf(tag.getString("Type"));
        } catch (IllegalArgumentException | NullPointerException ex) {
            e.type = Type.ACTIVITY;
        }
        e.fromTick = tag.getInt("From");
        e.toTick = tag.contains("To") ? tag.getInt("To") : DAY_TICKS;
        try {
            e.movement = Movement.valueOf(tag.getString("Movement"));
        } catch (IllegalArgumentException | NullPointerException ex) {
            e.movement = Movement.POINT;
        }
        if (tag.getBoolean("HasPos")) {
            e.position = new BlockPos(tag.getInt("PosX"), tag.getInt("PosY"), tag.getInt("PosZ"));
        } else {
            e.position = null;
        }
        e.tradeSet = tag.getString("TradeSet");
        e.animation = tag.getString("Animation");
        e.interactTrade = tag.getBoolean("InteractTrade");
        e.interactTradeSet = tag.getString("InteractTradeSet");
        e.interactDialog = tag.getBoolean("InteractDialog");
        e.interactQuest = tag.getBoolean("InteractQuest");
        e.patrolZone.clear();
        if (tag.contains("PatrolZone", net.minecraft.nbt.Tag.TAG_LONG_ARRAY)) {
            long[] packed = tag.getLongArray("PatrolZone");
            int limit = Math.min(packed.length, MAX_PATROL_ZONE_BLOCKS);
            for (int i = 0; i < limit; i++) {
                e.patrolZone.add(BlockPos.of(packed[i]));
            }
        }
        return e;
    }

    /**
     * Проверка пересечения двух диапазонов на 24000-тиковом круге с учётом wrap-around.
     */
    public static boolean overlaps(ScheduleEntry a, ScheduleEntry b) {
        if (a == null || b == null) return false;
        // Разворачиваем оба интервала в один/два простых [from,to)-подынтервала на [0,24000)
        int[] ra = unwrap(a);
        int[] rb = unwrap(b);
        // ra и rb имеют длину 2 (простой) или 4 (wrap — два куска)
        for (int i = 0; i < ra.length; i += 2) {
            for (int j = 0; j < rb.length; j += 2) {
                int af = ra[i], at = ra[i + 1];
                int bf = rb[j], bt = rb[j + 1];
                if (af < bt && bf < at) return true;
            }
        }
        return false;
    }

    private static int[] unwrap(ScheduleEntry e) {
        int from = ((e.fromTick % DAY_TICKS) + DAY_TICKS) % DAY_TICKS;
        int to = ((e.toTick % DAY_TICKS) + DAY_TICKS) % DAY_TICKS;
        if (from == to) {
            // Весь день
            return new int[]{0, DAY_TICKS};
        }
        if (from < to) {
            return new int[]{from, to};
        } else {
            return new int[]{from, DAY_TICKS, 0, to};
        }
    }
}
