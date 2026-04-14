package com.questnpc.entity.ai;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.schedule.ScheduleEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Цель ИИ: NPC перемещается к позиции текущего активного слота расписания и ждёт там.
 * Активируется только когда {@link QuestNPCEntity#isScheduleEnabled()} == true и существует
 * подходящий слот с ненулевой позицией или непустой зоной патруля.
 *
 * <p>Приоритет 1 — выше {@link BoundedStrollGoal} (приоритет 2). Благодаря MOVE-флагу,
 * пока эта цель активна, селектор целей автоматически ставит патруль на паузу.
 *
 * <p>Поддерживает два режима через {@link ScheduleEntry.Movement}:
 * <ul>
 *   <li>{@code POINT} — идти к {@code position} и стоять</li>
 *   <li>{@code PATROL} — если {@code patrolZone} не пуст, циклически выбирать случайную позицию
 *       из зоны и ходить между ними с паузами 2–6 сек. При пустой зоне — fallback на POINT.</li>
 * </ul>
 */
public class ScheduleFollowGoal extends Goal {

    private static final double REACH_DIST_SQ = 2.25; // 1.5 блока
    private static final int RECOMPUTE_INTERVAL = 20; // тиков
    private static final int UNREACHABLE_RETRY_TICKS = 100; // 5 сек — кулдаун между попытками после провала пути

    /** v2.6.2: сколько случайных блоков зоны пробуем за один тик перед уходом в unreachableCooldown. */
    private static final int PATROL_PATH_ATTEMPTS = 10;

    /** v2.6.3: диапазон переходов внутри одного кластера до переключения в другой. */
    private static final int SWITCH_STEPS_MIN = 2;
    private static final int SWITCH_STEPS_MAX = 5;

    private final QuestNPCEntity mob;
    private final RandomSource random = RandomSource.create();
    private ScheduleEntry currentEntry;
    private int recomputeCooldown = 0;
    private int unreachableCooldown = 0;
    private BlockPos lastWarnedPos = null;

    // PATROL state
    private BlockPos currentPatrolTarget = null;
    private int patrolWaitTicks = 0;

    // v2.6.3: состояние кластеризации зоны патруля
    private List<List<BlockPos>> clusters = null;
    private int currentCluster = 0;
    private int stepsInCluster = 0;
    private int stepsUntilSwitch = 0;

    public ScheduleFollowGoal(QuestNPCEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!mob.isScheduleEnabled()) return false;
        ScheduleEntry e = mob.getActiveScheduleEntry();
        if (e == null) return false;
        // PATROL с непустой зоной — self-sufficient (position не требуется)
        if (e.movement == ScheduleEntry.Movement.PATROL && !e.patrolZone.isEmpty()) return true;
        // POINT или fallback-PATROL (пустая зона) — нужен position
        return e.position != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        currentEntry = mob.getActiveScheduleEntry();
        recomputeCooldown = 0;
        unreachableCooldown = 0;
        lastWarnedPos = null;
        currentPatrolTarget = null;
        patrolWaitTicks = 0;
        clusters = null;
        currentCluster = 0;
        stepsInCluster = 0;
    }

    @Override
    public void tick() {
        if (--recomputeCooldown <= 0) {
            recomputeCooldown = RECOMPUTE_INTERVAL;
            ScheduleEntry next = mob.getActiveScheduleEntry();
            // Сменился активный слот — сбрасываем patrol-state и кластеры
            if (next != currentEntry) {
                currentPatrolTarget = null;
                patrolWaitTicks = 0;
                unreachableCooldown = 0;
                clusters = null;
                currentCluster = 0;
                stepsInCluster = 0;
            }
            currentEntry = next;
        }
        if (currentEntry == null) return;

        // --- PATROL mode ---
        if (currentEntry.movement == ScheduleEntry.Movement.PATROL && !currentEntry.patrolZone.isEmpty()) {
            tickPatrol();
            return;
        }

        // --- POINT mode (или PATROL с пустой зоной) ---
        if (currentEntry.position == null) return;
        BlockPos p = currentEntry.position;
        double cx = p.getX() + 0.5;
        double cy = p.getY();
        double cz = p.getZ() + 0.5;
        double distSq = mob.distanceToSqr(cx, cy, cz);

        if (distSq > REACH_DIST_SQ) {
            if (unreachableCooldown > 0) {
                unreachableCooldown--;
                return;
            }
            Path path = mob.getNavigation().createPath(p, 0);
            if (path == null || !path.canReach()) {
                unreachableCooldown = UNREACHABLE_RETRY_TICKS;
                if (lastWarnedPos == null || !lastWarnedPos.equals(p)) {
                    lastWarnedPos = p.immutable();
                    QuestNPCLogger.warn("ScheduleFollowGoal: NPC {} не может построить путь к слоту на [{}, {}, {}]",
                            mob.getId(), p.getX(), p.getY(), p.getZ());
                }
                return;
            }
            mob.getNavigation().moveTo(path, 1.0D);
        } else {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(cx, cy + mob.getEyeHeight(), cz);
            if (currentEntry.type == ScheduleEntry.Type.ANIMATION
                    && currentEntry.animation != null && !currentEntry.animation.isEmpty()) {
                mob.playScheduleAnimation(currentEntry.animation);
            }
        }
    }

    /**
     * Patrol по нарисованной зоне: случайный блок в текущем кластере → идти → пауза → новый блок.
     * <p>v2.6.3: зона автоматически разбивается на connected components (6-directional flood fill).
     * NPC делает {@link #SWITCH_STEPS_MIN}–{@link #SWITCH_STEPS_MAX} переходов в одном кластере,
     * после чего пытается переключиться в другой кластер (через обычную навигацию мира).
     * Если новый кластер недостижим — остаётся в текущем без сбоя.
     * <p>v2.6.2: при выборе нового таргета делаем до {@link #PATROL_PATH_ATTEMPTS} попыток подряд.
     * Паузы между точками берутся из {@code mob.getPatrolDelayMin()/Max()} (секунды → тики).
     */
    private void tickPatrol() {
        // Пауза между переходами
        if (patrolWaitTicks > 0) {
            patrolWaitTicks--;
            mob.getNavigation().stop();
            return;
        }

        // Если таргет уже выбран — проверяем, достигли ли
        if (currentPatrolTarget != null) {
            double distSq = mob.distanceToSqr(
                    currentPatrolTarget.getX() + 0.5,
                    currentPatrolTarget.getY(),
                    currentPatrolTarget.getZ() + 0.5);
            if (distSq <= REACH_DIST_SQ) {
                mob.getNavigation().stop();
                int minTicks = Math.max(1, mob.getPatrolDelayMin()) * 20;
                int maxTicks = Math.max(minTicks, mob.getPatrolDelayMax() * 20);
                patrolWaitTicks = minTicks + random.nextInt(maxTicks - minTicks + 1);
                currentPatrolTarget = null;
                stepsInCluster++;
                return;
            }
            // Идёт к таргету — ничего не делаем, навигация уже запущена
            return;
        }

        // Отсчёт cooldown после провала всех попыток
        if (unreachableCooldown > 0) {
            unreachableCooldown--;
            return;
        }

        // Ленивая инициализация кластеров (при первом выборе target после start/смены entry)
        ensureClustersReady();
        if (clusters == null || clusters.isEmpty()) return;

        // Пришло время сменить кластер?
        if (clusters.size() > 1 && stepsInCluster >= stepsUntilSwitch) {
            int candidate = pickOtherCluster(currentCluster);
            if (candidate != currentCluster && tryTargetInCluster(candidate)) {
                QuestNPCLogger.debug("ScheduleFollowGoal PATROL: NPC {} сменил кластер {} → {} после {} переходов",
                        mob.getId(), currentCluster, candidate, stepsInCluster);
                currentCluster = candidate;
                stepsInCluster = 0;
                stepsUntilSwitch = rollStepsUntilSwitch();
                return;
            }
            // Новый кластер недостижим — остаёмся, но сбрасываем stepsInCluster чтобы
            // попробовать снова на следующем цикле (не застревать в проверках каждый тик).
            stepsInCluster = 0;
        }

        // Обычный выбор в текущем кластере
        if (tryTargetInCluster(currentCluster)) return;

        // Ни одна попытка не успешна — cooldown и ждём
        unreachableCooldown = UNREACHABLE_RETRY_TICKS;
        if (lastWarnedPos == null || !lastWarnedPos.equals(mob.blockPosition())) {
            lastWarnedPos = mob.blockPosition();
            QuestNPCLogger.warn("ScheduleFollowGoal PATROL: NPC {} не нашёл достижимой точки в кластере {} из {} после {} попыток",
                    mob.getId(), currentCluster, clusters.size(), PATROL_PATH_ATTEMPTS);
        }
    }

    /**
     * v2.6.3: строит кластеры (если ещё не построены) и выбирает стартовый — тот, к блокам
     * которого NPC сейчас ближе всего.
     */
    private void ensureClustersReady() {
        if (clusters != null) return;
        clusters = computeClusters(currentEntry.patrolZone);
        if (clusters.isEmpty()) return;
        currentCluster = nearestClusterIndex(clusters, mob.blockPosition());
        stepsInCluster = 0;
        stepsUntilSwitch = rollStepsUntilSwitch();
        QuestNPCLogger.debug("ScheduleFollowGoal PATROL: NPC {} инициализировал {} кластер(а/ов), стартовый={}",
                mob.getId(), clusters.size(), currentCluster);
    }

    private int rollStepsUntilSwitch() {
        return SWITCH_STEPS_MIN + random.nextInt(SWITCH_STEPS_MAX - SWITCH_STEPS_MIN + 1);
    }

    /** Возвращает случайный индекс кластера ≠ {@code current}. Если кластер один — вернёт current. */
    private int pickOtherCluster(int current) {
        int n = clusters.size();
        if (n <= 1) return current;
        int idx = random.nextInt(n - 1);
        return idx >= current ? idx + 1 : idx;
    }

    /**
     * Пытается найти достижимый блок в указанном кластере за {@link #PATROL_PATH_ATTEMPTS} попыток.
     * При успехе запускает навигацию и устанавливает {@link #currentPatrolTarget}. Возвращает true.
     * При провале — возвращает false, ничего не меняет.
     */
    private boolean tryTargetInCluster(int clusterIdx) {
        if (clusterIdx < 0 || clusterIdx >= clusters.size()) return false;
        List<BlockPos> cluster = clusters.get(clusterIdx);
        if (cluster.isEmpty()) return false;
        for (int attempt = 0; attempt < PATROL_PATH_ATTEMPTS; attempt++) {
            BlockPos candidate = cluster.get(random.nextInt(cluster.size()));
            Path path = mob.getNavigation().createPath(candidate, 0);
            if (path != null && path.canReach()) {
                currentPatrolTarget = candidate;
                mob.getNavigation().moveTo(path, 1.0D);
                return true;
            }
        }
        return false;
    }

    /**
     * v2.6.3: разбивает плоский список {@link BlockPos} на кластеры по 6-directional adjacency.
     * Блоки, соприкасающиеся по граням (включая y±1), попадают в один кластер — позволяет
     * патрулировать многоуровневые тропы/лестницы как единое целое.
     */
    static List<List<BlockPos>> computeClusters(List<BlockPos> zone) {
        List<List<BlockPos>> result = new ArrayList<>();
        if (zone == null || zone.isEmpty()) return result;

        Map<Long, BlockPos> byLong = new HashMap<>(zone.size() * 2);
        for (BlockPos p : zone) byLong.put(p.asLong(), p);
        Set<Long> remaining = new HashSet<>(byLong.keySet());

        int[][] dirs = {
                {1, 0, 0}, {-1, 0, 0},
                {0, 1, 0}, {0, -1, 0},
                {0, 0, 1}, {0, 0, -1}
        };

        Deque<Long> bfs = new ArrayDeque<>();
        while (!remaining.isEmpty()) {
            long seed = remaining.iterator().next();
            remaining.remove(seed);
            bfs.clear();
            bfs.add(seed);
            List<BlockPos> cluster = new ArrayList<>();
            cluster.add(byLong.get(seed));

            while (!bfs.isEmpty()) {
                long cur = bfs.poll();
                BlockPos curPos = BlockPos.of(cur);
                for (int[] d : dirs) {
                    long nl = BlockPos.asLong(curPos.getX() + d[0], curPos.getY() + d[1], curPos.getZ() + d[2]);
                    if (remaining.remove(nl)) {
                        bfs.add(nl);
                        cluster.add(byLong.get(nl));
                    }
                }
            }
            result.add(cluster);
        }
        return result;
    }

    /** Выбирает индекс кластера, ближайшего к позиции NPC (по минимальному квадрату расстояния). */
    private static int nearestClusterIndex(List<List<BlockPos>> clusters, BlockPos mobPos) {
        int best = 0;
        double bestDistSq = Double.MAX_VALUE;
        for (int i = 0; i < clusters.size(); i++) {
            for (BlockPos p : clusters.get(i)) {
                double dx = p.getX() - mobPos.getX();
                double dy = p.getY() - mobPos.getY();
                double dz = p.getZ() - mobPos.getZ();
                double dSq = dx * dx + dy * dy + dz * dz;
                if (dSq < bestDistSq) {
                    bestDistSq = dSq;
                    best = i;
                }
            }
        }
        return best;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        currentEntry = null;
        unreachableCooldown = 0;
        lastWarnedPos = null;
        currentPatrolTarget = null;
        patrolWaitTicks = 0;
        clusters = null;
        currentCluster = 0;
        stepsInCluster = 0;
    }
}
