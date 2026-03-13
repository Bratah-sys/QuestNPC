package com.questnpc.client.debug;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
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
            if (boundPos == null) continue;

            CachedZone zone = getOrCreateZone(npc.getId(), boundPos,
                    QuestNPCEntity.PATROL_RADIUS, mc.level);

            // === Непрозрачные элементы (с depth write) ===

            // --- Синий контур проходимой зоны ---
            renderZoneContour(tesselator, poseStack, zone);

            // --- Жёлтая ломаная линия через все узлы навигационного пути ---
            renderPath(tesselator, poseStack, npc);

            // === Полупрозрачные элементы (без depth write) ===
            RenderSystem.depthMask(false);

            // --- Красный полупрозрачный оверлей на farmnpc_block ---
            renderBlockOverlay(tesselator, poseStack, boundPos);

            // --- Синяя заливка проходимой зоны ---
            renderZoneFill(tesselator, poseStack, zone);

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
}
