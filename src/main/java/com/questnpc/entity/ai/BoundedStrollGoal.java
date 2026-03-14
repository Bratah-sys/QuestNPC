package com.questnpc.entity.ai;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Патруль NPC в пределах круга радиуса PATROL_RADIUS вокруг привязанного блока.
 * При отсутствии подходящей точки — возврат к центру.
 * Поддерживает настраиваемый диапазон задержки между прогулками.
 */
public class BoundedStrollGoal extends WaterAvoidingRandomStrollGoal {

    private final QuestNPCEntity mob;

    // Диапазон задержки в тиках
    private int delayMinTicks;
    private int delayMaxTicks;

    // Текущий кулдаун (рандомно выбирается после каждой прогулки)
    private int cooldownTicks = 0;

    public BoundedStrollGoal(QuestNPCEntity mob, int delayMinTicks, int delayMaxTicks) {
        // speedModifier=1.0 — реальная скорость контролируется через атрибут MOVEMENT_SPEED
        super(mob, 1.0D);
        this.mob = mob;
        this.delayMinTicks = delayMinTicks;
        this.delayMaxTicks = delayMaxTicks;
        // Начальный кулдаун
        this.cooldownTicks = rollCooldown();
        // Отключаем стандартный interval — используем свой кулдаун
        this.interval = 1;
    }

    /** Обновляет диапазон задержки (в тиках). */
    public void setDelayRange(int minTicks, int maxTicks) {
        this.delayMinTicks = minTicks;
        this.delayMaxTicks = maxTicks;
    }

    /** Рандомный кулдаун в диапазоне [delayMinTicks, delayMaxTicks]. */
    private int rollCooldown() {
        if (delayMinTicks >= delayMaxTicks) return delayMinTicks;
        int ticks = delayMinTicks + mob.getRandom().nextInt(delayMaxTicks - delayMinTicks + 1);
        QuestNPCLogger.debug("NPC {} выбрал задержку {}с (диапазон {}-{}с)",
                mob.getId(), ticks / 20, delayMinTicks / 20, delayMaxTicks / 20);
        return ticks;
    }

    /** Запускаемся только при наличии привязки и после истечения кулдауна. */
    @Override
    public boolean canUse() {
        if (!mob.isBound()) return false;

        // Свой кулдаун вместо стандартного interval
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return false;
        }

        return super.canUse();
    }

    @Override
    public void stop() {
        super.stop();
        // После окончания прогулки — новый рандомный кулдаун
        this.cooldownTicks = rollCooldown();
    }

    /**
     * Выбираем случайную точку внутри радиуса патруля.
     * До 10 попыток; если ни одна не попала — идём к центру.
     */
    @Nullable
    @Override
    protected Vec3 getPosition() {
        BlockPos center = mob.getBoundBlockPos();
        if (center == null) return null;

        double cr = center.getX() + 0.5;
        double cz = center.getZ() + 0.5;
        double r = QuestNPCEntity.PATROL_RADIUS;

        for (int attempt = 0; attempt < 10; attempt++) {
            Vec3 candidate = DefaultRandomPos.getPos(mob, 10, 7);
            if (candidate == null) continue;

            double dx = candidate.x - cr;
            double dz = candidate.z - cz;
            if (dx * dx + dz * dz <= r * r) {
                mob.setTargetPos(BlockPos.containing(candidate));
                return candidate;
            }
        }

        // Возврат к центру, если все 10 попыток провалились
        Vec3 center3 = Vec3.atCenterOf(center);
        mob.setTargetPos(center);
        return center3;
    }
}
