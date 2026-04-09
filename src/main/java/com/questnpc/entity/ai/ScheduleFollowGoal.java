package com.questnpc.entity.ai;

import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.schedule.ScheduleEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Цель ИИ: NPC перемещается к позиции текущего активного слота расписания и ждёт там.
 * Активируется только когда {@link QuestNPCEntity#isScheduleEnabled()} == true и существует
 * подходящий слот с ненулевой позицией.
 *
 * <p>Приоритет 1 — выше {@link BoundedStrollGoal} (приоритет 2). Благодаря MOVE-флагу,
 * пока эта цель активна, селектор целей автоматически ставит патруль на паузу.
 */
public class ScheduleFollowGoal extends Goal {

    private static final double REACH_DIST_SQ = 2.25; // 1.5 блока
    private static final int RECOMPUTE_INTERVAL = 20; // тиков

    private final QuestNPCEntity mob;
    private ScheduleEntry currentEntry;
    private int recomputeCooldown = 0;

    public ScheduleFollowGoal(QuestNPCEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!mob.isScheduleEnabled()) return false;
        ScheduleEntry e = mob.getActiveScheduleEntry();
        return e != null && e.position != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        currentEntry = mob.getActiveScheduleEntry();
        recomputeCooldown = 0;
    }

    @Override
    public void tick() {
        if (--recomputeCooldown <= 0) {
            recomputeCooldown = RECOMPUTE_INTERVAL;
            currentEntry = mob.getActiveScheduleEntry();
        }
        if (currentEntry == null || currentEntry.position == null) return;

        BlockPos p = currentEntry.position;
        double cx = p.getX() + 0.5;
        double cy = p.getY();
        double cz = p.getZ() + 0.5;
        double distSq = mob.distanceToSqr(cx, cy, cz);

        if (distSq > REACH_DIST_SQ) {
            mob.getNavigation().moveTo(cx, cy, cz, 1.0D);
        } else {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(cx, cy + mob.getEyeHeight(), cz);
            if (currentEntry.type == ScheduleEntry.Type.ANIMATION
                    && currentEntry.animation != null && !currentEntry.animation.isEmpty()) {
                mob.playScheduleAnimation(currentEntry.animation);
            }
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        currentEntry = null;
    }
}
