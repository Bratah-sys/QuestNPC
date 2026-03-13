package com.questnpc.entity.ai;

import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Патруль NPC в пределах круга радиуса PATROL_RADIUS вокруг привязанного блока.
 * При отсутствии подходящей точки — возврат к центру.
 */
public class BoundedStrollGoal extends WaterAvoidingRandomStrollGoal {

    private final QuestNPCEntity mob;

    public BoundedStrollGoal(QuestNPCEntity mob, double speedModifier) {
        // интервал 120 тиков — дефолт WaterAvoidingRandomStrollGoal
        super(mob, speedModifier);
        this.mob = mob;
    }

    /** Запускаемся только при наличии привязки. */
    @Override
    public boolean canUse() {
        return mob.isBound() && super.canUse();
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
