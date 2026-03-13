package com.questnpc.client.debug;

import com.questnpc.QuestNPCLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Вычисляет проходимую зону патруля NPC методом 3D flood fill (BFS).
 * Учитывает рельеф, стены, обрывы, пещеры — рисуется только то, куда NPC реально может дойти.
 */
public final class PatrolZoneCalculator {

    /** Максимальный подъём за один шаг (блоки). Стандартный maxUpStep для мобов. */
    private static final int MAX_CLIMB = 1;
    /** Максимальное безопасное падение (блоки). ~3-4 блока без летального урона. */
    private static final int MAX_DROP = 4;

    private PatrolZoneCalculator() {}

    /**
     * Результат вычисления зоны: проходимые блоки + рёбра контура.
     */
    public static class ZoneData {
        /** Все проходимые позиции (x, y, z) — y = уровень ног NPC. */
        public final List<BlockPos> reachableBlocks;
        /** Рёбра контура: {blockY, edgeX1, edgeZ1, edgeX2, edgeZ2} — Y от блока-владельца. */
        public final List<int[]> contourEdges;

        ZoneData(List<BlockPos> reachableBlocks, List<int[]> contourEdges) {
            this.reachableBlocks = reachableBlocks;
            this.contourEdges = contourEdges;
        }
    }

    /**
     * Выполняет 3D BFS от центра (блок НАД farmnpc_block) и возвращает проходимую зону.
     *
     * @param center   позиция farmnpc_block
     * @param radius   радиус патруля
     * @param level    мир (клиентский)
     * @param entityId ID сущности (для логирования)
     * @return данные зоны с проходимыми блоками и контуром
     */
    public static ZoneData compute(BlockPos center, int radius, Level level, int entityId) {
        long startTime = System.currentTimeMillis();

        // NPC стоит НА farmnpc_block, ноги на Y+1
        BlockPos startPos = center.above();
        double r2 = (double) radius * radius;
        int cx = center.getX();
        int cz = center.getZ();

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        // Стартовая позиция должна быть проходимой
        if (isStandable(startPos, level)) {
            visited.add(startPos);
            queue.add(startPos);
        }

        // 4 горизонтальных направления
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        // --- BFS ---
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            int curY = current.getY();

            for (int[] dir : directions) {
                int nx = current.getX() + dir[0];
                int nz = current.getZ() + dir[1];

                // Проверка радиуса (XZ-расстояние от центра)
                double dx = nx - cx;
                double dz = nz - cz;
                if (dx * dx + dz * dz > r2) continue;

                // Сканируем Y-уровни: от (curY - MAX_DROP) до (curY + MAX_CLIMB)
                int minY = curY - MAX_DROP;
                int maxY = curY + MAX_CLIMB;

                for (int candidateY = minY; candidateY <= maxY; candidateY++) {
                    BlockPos candidate = new BlockPos(nx, candidateY, nz);
                    if (visited.contains(candidate)) continue;

                    if (isStandable(candidate, level)) {
                        visited.add(candidate);
                        queue.add(candidate);
                    }
                }
            }
        }

        // --- Построение контура ---
        List<BlockPos> reachableList = new ArrayList<>(visited);
        List<int[]> contourEdges = buildContour(visited, directions);

        long elapsed = System.currentTimeMillis() - startTime;

        // Определяем количество уникальных Y-уровней
        Set<Integer> yLevels = new HashSet<>();
        for (BlockPos pos : visited) {
            yLevels.add(pos.getY());
        }

        QuestNPCLogger.info(
                "Flood fill для NPC {}: {} проходимых блоков, {} Y-уровней, {} рёбер контура, {}ms",
                entityId, visited.size(), yLevels.size(), contourEdges.size(), elapsed
        );

        if (elapsed > 500) {
            QuestNPCLogger.warn(
                    "Flood fill для NPC {} занял {}ms — возможно проблемы с производительностью",
                    entityId, elapsed
            );
        }

        return new ZoneData(reachableList, contourEdges);
    }

    /**
     * Проверяет, может ли NPC стоять на данной позиции.
     * Позиция = уровень ног. Нужен твёрдый пол на Y-1, проходимое пространство на Y и Y+1.
     */
    private static boolean isStandable(BlockPos feetPos, Level level) {
        BlockPos groundPos = feetPos.below();
        BlockPos headPos = feetPos.above();

        BlockState ground = level.getBlockState(groundPos);
        BlockState feet = level.getBlockState(feetPos);
        BlockState head = level.getBlockState(headPos);

        // Пол должен быть твёрдым (блокирует движение)
        if (!ground.blocksMotion()) return false;

        // Пространство для ног и головы должно быть проходимым
        if (feet.blocksMotion()) return false;
        if (head.blocksMotion()) return false;

        return true;
    }

    /**
     * Строит рёбра контура: для каждого проходимого блока проверяем 4 соседа.
     * Ребро рисуется там, где сосед на том же XZ не является проходимым на близком Y-уровне.
     */
    private static List<int[]> buildContour(Set<BlockPos> visited, int[][] directions) {
        List<int[]> edges = new ArrayList<>();

        for (BlockPos pos : visited) {
            int bx = pos.getX();
            int by = pos.getY();
            int bz = pos.getZ();

            for (int[] dir : directions) {
                int nx = bx + dir[0];
                int nz = bz + dir[1];

                // Сосед считается "присутствующим" если есть проходимый блок
                // на близком Y-уровне (±1) — чтобы контур не рисовался на ступеньках
                boolean neighborPresent = false;
                for (int dy = -1; dy <= 1; dy++) {
                    if (visited.contains(new BlockPos(nx, by + dy, nz))) {
                        neighborPresent = true;
                        break;
                    }
                }

                if (!neighborPresent) {
                    // Определяем какое ребро рисовать в зависимости от направления
                    if (dir[0] == 1 && dir[1] == 0) {
                        // +X → правое ребро
                        edges.add(new int[]{by, bx + 1, bz, bx + 1, bz + 1});
                    } else if (dir[0] == -1 && dir[1] == 0) {
                        // -X → левое ребро
                        edges.add(new int[]{by, bx, bz, bx, bz + 1});
                    } else if (dir[0] == 0 && dir[1] == 1) {
                        // +Z → дальнее ребро
                        edges.add(new int[]{by, bx, bz + 1, bx + 1, bz + 1});
                    } else {
                        // -Z → ближнее ребро
                        edges.add(new int[]{by, bx, bz, bx + 1, bz});
                    }
                }
            }
        }

        return edges;
    }
}
