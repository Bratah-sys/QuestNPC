package com.questnpc.client.debug;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.schedule.ScheduleEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

import java.util.*;

/**
 * Клиентский дебаг-рендерер патруля NPC.
 * Рисует:
 *  - красный полупрозрачный оверлей на привязанном farmnpc_block
 *  - синий ступенчатый контур проходимой зоны патруля с заливкой (flood fill по проходимости)
 *  - жёлтую ломаную линию = текущий маршрут к цели
 *
 * Включается/выключается командой /npc_vis через сеть.
 */
public class NPCDebugRenderer {

    public static volatile boolean enabled = false;

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    // Кэш визуализации зоны патруля: entityId → данные
    private static final Map<Integer, CachedZone> zoneCache = new HashMap<>();

    /**
     * v2.6.0: палитра цветов для зон расписания (10 слотов max).
     * R, G, B floats [0..1].
     */
    private static final float[][] SCHED_PALETTE = {
            {1.0f, 0.3f, 0.3f},  // red
            {1.0f, 0.6f, 0.1f},  // orange
            {1.0f, 0.95f, 0.2f}, // yellow
            {0.3f, 1.0f, 0.3f},  // green
            {0.2f, 1.0f, 0.9f},  // cyan
            {0.6f, 0.5f, 1.0f},  // indigo
            {0.9f, 0.3f, 1.0f},  // magenta
            {1.0f, 0.5f, 0.85f}, // pink
            {0.7f, 0.4f, 0.2f},  // brown
            {0.95f, 0.95f, 0.95f}// white
    };

    private static float[] colorForSlot(int slotIndex) {
        return SCHED_PALETTE[Math.floorMod(slotIndex, SCHED_PALETTE.length)];
    }

    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        List<QuestNPCEntity> npcs = mc.level.getEntitiesOfClass(
                QuestNPCEntity.class,
                new AABB(mc.player.blockPosition()).inflate(128)
        );
        if (npcs.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(2.0f);
        // Depth test включён — всё скрывается за блоками

        Tesselator tesselator = Tesselator.getInstance();

        for (QuestNPCEntity npc : npcs) {
            BlockPos boundPos = npc.getBoundBlockPos();

            // Дефолтная зона (flood-fill вокруг boundBlockPos) — только если NPC привязан
            CachedZone zone = null;
            if (boundPos != null) {
                zone = getOrCreateZone(npc.getId(), boundPos,
                        QuestNPCEntity.PATROL_RADIUS, mc.level);
            }

            // v2.6.0: определяем активный слот расписания для подсветки
            int activeIdx = computeActiveScheduleIndex(npc, mc);

            // === Непрозрачные элементы (с depth write) ===

            if (zone != null) {
                // --- Синий контур проходимой зоны (дефолт BoundedStroll) ---
                renderZoneContour(tesselator, poseStack, zone);
            }

            // --- v2.6.0: контур каждой зоны расписания ---
            renderScheduleContours(tesselator, poseStack, npc, activeIdx);

            // --- Жёлтая ломаная линия через все узлы навигационного пути ---
            renderPath(tesselator, poseStack, npc);

            // === Полупрозрачные элементы (без depth write) ===
            RenderSystem.depthMask(false);

            if (boundPos != null) {
                // --- Красный полупрозрачный оверлей на farmnpc_block ---
                renderBlockOverlay(tesselator, poseStack, boundPos);
            }

            if (zone != null) {
                // --- Синяя заливка проходимой зоны ---
                renderZoneFill(tesselator, poseStack, zone);
            }

            // --- v2.6.0: заливка каждой зоны расписания ---
            renderScheduleFills(tesselator, poseStack, npc, activeIdx);

            RenderSystem.depthMask(true);
        }

        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    // =========================================================================
    // Жёлтый путь NPC (не трогаем — код из v0.1.1/v0.1.2)
    // =========================================================================

    private void renderPath(Tesselator tesselator, PoseStack poseStack, QuestNPCEntity npc) {
        List<Vec3> pathNodes = npc.getClientPathNodes();
        if (pathNodes.isEmpty()) return;

        // Ломаная: NPC → node[0] → node[1] → ... → node[last]
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        // Начальная точка — текущая позиция NPC
        buffer.vertex(poseStack.last().pose(),
                        (float) npc.getX(), (float) (npc.getY() + 0.1), (float) npc.getZ())
                .color(1.0f, 1.0f, 0.0f, 1.0f).endVertex();
        for (Vec3 node : pathNodes) {
            buffer.vertex(poseStack.last().pose(),
                            (float) node.x, (float) node.y, (float) node.z)
                    .color(1.0f, 1.0f, 0.0f, 1.0f).endVertex();
        }
        tesselator.end();

        // Маркеры на каждом узле пути (маленькие кресты)
        float ns = 0.15f;
        buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (Vec3 node : pathNodes) {
            float nx = (float) node.x;
            float ny = (float) node.y;
            float nz = (float) node.z;
            // Горизонтальный крест
            buffer.vertex(poseStack.last().pose(), nx - ns, ny, nz)
                    .color(1.0f, 0.8f, 0.0f, 0.8f).endVertex();
            buffer.vertex(poseStack.last().pose(), nx + ns, ny, nz)
                    .color(1.0f, 0.8f, 0.0f, 0.8f).endVertex();
            buffer.vertex(poseStack.last().pose(), nx, ny, nz - ns)
                    .color(1.0f, 0.8f, 0.0f, 0.8f).endVertex();
            buffer.vertex(poseStack.last().pose(), nx, ny, nz + ns)
                    .color(1.0f, 0.8f, 0.0f, 0.8f).endVertex();
        }
        tesselator.end();
    }

    // =========================================================================
    // Красный полупрозрачный куб поверх farmnpc_block
    // =========================================================================

    private void renderBlockOverlay(Tesselator tesselator, PoseStack poseStack, BlockPos pos) {
        // Чуть расширяем куб чтобы избежать z-fighting с текстурой блока
        float e = 0.005f;
        float x0 = pos.getX() - e;
        float y0 = pos.getY() - e;
        float z0 = pos.getZ() - e;
        float x1 = pos.getX() + 1 + e;
        float y1 = pos.getY() + 1 + e;
        float z1 = pos.getZ() + 1 + e;
        float r = 1.0f, g = 0.0f, b = 0.0f, a = 0.35f;

        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.disableCull();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // Верхняя грань (Y+)
        buffer.vertex(matrix, x0, y1, z0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x0, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y1, z0).color(r, g, b, a).endVertex();
        // Нижняя грань (Y-)
        buffer.vertex(matrix, x0, y0, z1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x0, y0, z0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y0, z0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y0, z1).color(r, g, b, a).endVertex();
        // Северная грань (Z-)
        buffer.vertex(matrix, x0, y0, z0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x0, y1, z0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y1, z0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y0, z0).color(r, g, b, a).endVertex();
        // Южная грань (Z+)
        buffer.vertex(matrix, x1, y0, z1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x0, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x0, y0, z1).color(r, g, b, a).endVertex();
        // Западная грань (X-)
        buffer.vertex(matrix, x0, y0, z1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x0, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x0, y1, z0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x0, y0, z0).color(r, g, b, a).endVertex();
        // Восточная грань (X+)
        buffer.vertex(matrix, x1, y0, z0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y1, z0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y0, z1).color(r, g, b, a).endVertex();

        tesselator.end();
        RenderSystem.enableCull();
    }

    // =========================================================================
    // Кэш зоны патруля
    // =========================================================================

    /**
     * Кэшированные данные зоны для одного NPC.
     */
    private static class CachedZone {
        final BlockPos center;
        final int radius;
        final PatrolZoneCalculator.ZoneData data;

        CachedZone(BlockPos center, int radius, PatrolZoneCalculator.ZoneData data) {
            this.center = center;
            this.radius = radius;
            this.data = data;
        }
    }

    private CachedZone getOrCreateZone(int entityId, BlockPos center, int radius,
                                       net.minecraft.world.level.Level level) {
        CachedZone cached = zoneCache.get(entityId);
        if (cached != null && cached.center.equals(center) && cached.radius == radius) {
            return cached;
        }

        PatrolZoneCalculator.ZoneData data = PatrolZoneCalculator.compute(
                center, radius, level, entityId
        );
        CachedZone zone = new CachedZone(center, radius, data);
        zoneCache.put(entityId, zone);
        return zone;
    }

    /**
     * Очищает кэш для указанного NPC (при перепривязке).
     */
    public static void invalidateCache(int entityId) {
        if (zoneCache.remove(entityId) != null) {
            QuestNPCLogger.debug("Кэш визуализации инвалидирован для NPC {}", entityId);
        }
    }

    /**
     * v2.6.0: заглушка для будущего кэша зон расписания. Сейчас зоны читаются напрямую
     * из {@link QuestNPCEntity#getClientSchedule()} каждый кадр — инвалидация не требуется,
     * но метод оставлен как hook для {@link com.questnpc.network.ScheduleSyncPacket#handle}.
     */
    public static void invalidateScheduleZones(int entityId) {
        // No-op: зоны рисуются from-source каждый кадр (см. renderScheduleFills/Contours)
    }

    /**
     * Очищает весь кэш (при смене мира).
     */
    public static void clearCache() {
        zoneCache.clear();
    }

    // =========================================================================
    // Заливка проходимой зоны (полупрозрачные синие квады)
    // =========================================================================

    private void renderZoneFill(Tesselator tesselator, PoseStack poseStack, CachedZone zone) {
        List<BlockPos> blocks = zone.data.reachableBlocks;
        if (blocks.isEmpty()) return;

        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.disableCull();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float r = 0.2f, g = 0.4f, b = 0.9f, a = 0.1f;
        float yOffset = 0.01f;

        for (BlockPos pos : blocks) {
            // Y позиции = уровень ног, квад рисуется на этом уровне (верхняя грань пола)
            float y = pos.getY() + yOffset;
            float bx = pos.getX();
            float bz = pos.getZ();

            buffer.vertex(matrix, bx, y, bz).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, bx, y, bz + 1).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, bx + 1, y, bz + 1).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, bx + 1, y, bz).color(r, g, b, a).endVertex();
        }

        tesselator.end();
        RenderSystem.enableCull();
    }

    // =========================================================================
    // Контур проходимой зоны (ступенчатые синие линии)
    // =========================================================================

    private void renderZoneContour(Tesselator tesselator, PoseStack poseStack, CachedZone zone) {
        List<int[]> edges = zone.data.contourEdges;
        if (edges.isEmpty()) return;

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        float r = 0.2f, g = 0.5f, b = 1.0f, a = 0.8f;
        float yOffset = 0.02f;

        for (int[] edge : edges) {
            // edge = {blockY, edgeX1, edgeZ1, edgeX2, edgeZ2}
            float y = edge[0] + yOffset;

            buffer.vertex(matrix, (float) edge[1], y, (float) edge[2])
                    .color(r, g, b, a).endVertex();
            buffer.vertex(matrix, (float) edge[3], y, (float) edge[4])
                    .color(r, g, b, a).endVertex();
        }

        tesselator.end();
    }

    // =========================================================================
    // v2.6.0: рендер зон расписания (palette + активная зона ярче)
    // =========================================================================

    /**
     * Определяет индекс активного сейчас слота расписания. Возвращает -1 если не активен.
     */
    private int computeActiveScheduleIndex(QuestNPCEntity npc, Minecraft mc) {
        if (!npc.isClientScheduleEnabled()) return -1;
        List<ScheduleEntry> sched = npc.getClientSchedule();
        if (sched.isEmpty() || mc.level == null) return -1;
        int timeOfDay = (int) (mc.level.getDayTime() % 24000L);
        if (timeOfDay < 0) timeOfDay += 24000;
        for (int i = 0; i < sched.size(); i++) {
            if (sched.get(i).containsTime(timeOfDay)) return i;
        }
        return -1;
    }

    private void renderScheduleFills(Tesselator tess, PoseStack poseStack,
                                     QuestNPCEntity npc, int activeIdx) {
        List<ScheduleEntry> sched = npc.getClientSchedule();
        if (sched.isEmpty()) return;

        Matrix4f m = poseStack.last().pose();
        RenderSystem.disableCull();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float yOff = 0.012f;

        for (int i = 0; i < sched.size(); i++) {
            ScheduleEntry e = sched.get(i);
            if (e.movement != ScheduleEntry.Movement.PATROL) continue;
            if (e.patrolZone.isEmpty()) continue;

            float[] c = colorForSlot(i);
            boolean active = (i == activeIdx);
            float a = active ? 0.20f : 0.08f;

            for (BlockPos p : e.patrolZone) {
                float x0 = p.getX();
                float y = p.getY() + yOff;
                float z0 = p.getZ();
                buf.vertex(m, x0,      y, z0     ).color(c[0], c[1], c[2], a).endVertex();
                buf.vertex(m, x0,      y, z0 + 1 ).color(c[0], c[1], c[2], a).endVertex();
                buf.vertex(m, x0 + 1,  y, z0 + 1 ).color(c[0], c[1], c[2], a).endVertex();
                buf.vertex(m, x0 + 1,  y, z0     ).color(c[0], c[1], c[2], a).endVertex();
            }
        }
        tess.end();
        RenderSystem.enableCull();
    }

    /**
     * v2.6.2: контур зоны расписания — ребро рисуется только если соседа в той же зоне нет,
     * что даёт объединённый контур вокруг связных групп (как у BoundedStroll ZoneData).
     */
    private void renderScheduleContours(Tesselator tess, PoseStack poseStack,
                                        QuestNPCEntity npc, int activeIdx) {
        List<ScheduleEntry> sched = npc.getClientSchedule();
        if (sched.isEmpty()) return;

        Matrix4f m = poseStack.last().pose();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        float yOff = 0.025f;

        for (int i = 0; i < sched.size(); i++) {
            ScheduleEntry e = sched.get(i);
            if (e.movement != ScheduleEntry.Movement.PATROL) continue;
            if (e.patrolZone.isEmpty()) continue;

            float[] c = colorForSlot(i);
            boolean active = (i == activeIdx);
            float a = active ? 1.0f : 0.6f;

            // Set для быстрой проверки соседей
            Set<Long> set = new HashSet<>(e.patrolZone.size() * 2);
            for (BlockPos p : e.patrolZone) set.add(p.asLong());

            for (BlockPos p : e.patrolZone) {
                float x0 = p.getX();
                float y = p.getY() + yOff;
                float z0 = p.getZ();
                if (!set.contains(p.north().asLong())) addEdge(buf, m, x0,     y, z0,     x0 + 1, y, z0,     c, a);
                if (!set.contains(p.south().asLong())) addEdge(buf, m, x0,     y, z0 + 1, x0 + 1, y, z0 + 1, c, a);
                if (!set.contains(p.west().asLong()))  addEdge(buf, m, x0,     y, z0,     x0,     y, z0 + 1, c, a);
                if (!set.contains(p.east().asLong()))  addEdge(buf, m, x0 + 1, y, z0,     x0 + 1, y, z0 + 1, c, a);
            }
        }
        tess.end();
    }

    private void addEdge(BufferBuilder buf, Matrix4f m,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float[] c, float a) {
        buf.vertex(m, x1, y1, z1).color(c[0], c[1], c[2], a).endVertex();
        buf.vertex(m, x2, y2, z2).color(c[0], c[1], c[2], a).endVertex();
    }
}
