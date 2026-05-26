package com.questnpc.client.debug;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.questnpc.item.PatrolBrushItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

/**
 * v2.6.0: клиентский рендер зоны-в-процессе-рисования.
 * Активируется автоматически когда в main-hand у игрока находится {@link PatrolBrushItem}.
 * Читает {@code PaintedBlocks} из NBT предмета и отрисовывает полупрозрачный оранжевый оверлей
 * + яркий контур на каждый блок зоны.
 *
 * <p>Не зависит от {@link NPCDebugRenderer#enabled} — это всегда-on режим редактирования.
 */
public class PatrolBrushRenderer {

    // Оранжевый — яркая обратная связь для текущего редактирования
    private static final float FILL_R = 1.0F;
    private static final float FILL_G = 0.55F;
    private static final float FILL_B = 0.1F;
    private static final float FILL_A = 0.35F;

    private static final float LINE_R = 1.0F;
    private static final float LINE_G = 0.8F;
    private static final float LINE_B = 0.1F;
    private static final float LINE_A = 0.95F;

    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        ItemStack held = mc.player.getMainHandItem();
        if (!(held.getItem() instanceof PatrolBrushItem)) return;
        CompoundTag tag = held.getTag();
        if (tag == null) return;
        long[] packed = tag.getLongArray("PaintedBlocks");
        if (packed.length == 0) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(2.5F);

        Tesselator tess = Tesselator.getInstance();

        // --- Контур (DEBUG_LINES) с depth test ---
        renderContour(tess, poseStack, packed);

        // --- Полупрозрачная заливка без depth write ---
        RenderSystem.depthMask(false);
        renderFill(tess, poseStack, packed);
        RenderSystem.depthMask(true);

        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private void renderFill(Tesselator tess, PoseStack poseStack, long[] packed) {
        Matrix4f m = poseStack.last().pose();
        RenderSystem.disableCull();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float yOff = 0.015F;
        for (long l : packed) {
            BlockPos p = BlockPos.of(l);
            float x0 = p.getX();
            float y = p.getY() + yOff;
            float z0 = p.getZ();
            buf.vertex(m, x0,        y, z0       ).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
            buf.vertex(m, x0,        y, z0 + 1F  ).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
            buf.vertex(m, x0 + 1F,   y, z0 + 1F  ).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
            buf.vertex(m, x0 + 1F,   y, z0       ).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        }
        tess.end();
        RenderSystem.enableCull();
    }

    /**
     * v2.6.2: рисуем ребро только если у блока НЕТ соседа в зоне с той же позицией.
     * Это даёт объединённый контур вокруг связных групп блоков (как в
     * {@link com.questnpc.client.debug.PatrolZoneCalculator}).
     */
    private void renderContour(Tesselator tess, PoseStack poseStack, long[] packed) {
        Set<Long> set = new HashSet<>(packed.length * 2);
        for (long l : packed) set.add(l);

        Matrix4f m = poseStack.last().pose();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        float yOff = 0.03F;
        for (long l : packed) {
            BlockPos p = BlockPos.of(l);
            float x0 = p.getX();
            float y = p.getY() + yOff;
            float z0 = p.getZ();
            // North (z-1): ребро вдоль верхней грани, рисуем если нет соседа к северу
            if (!set.contains(p.north().asLong())) edge(buf, m, x0,     y, z0,     x0 + 1, y, z0);
            // South (z+1)
            if (!set.contains(p.south().asLong())) edge(buf, m, x0,     y, z0 + 1, x0 + 1, y, z0 + 1);
            // West  (x-1)
            if (!set.contains(p.west().asLong()))  edge(buf, m, x0,     y, z0,     x0,     y, z0 + 1);
            // East  (x+1)
            if (!set.contains(p.east().asLong()))  edge(buf, m, x0 + 1, y, z0,     x0 + 1, y, z0 + 1);
        }
        tess.end();
    }

    private void edge(BufferBuilder buf, Matrix4f m,
                      float x1, float y1, float z1,
                      float x2, float y2, float z2) {
        buf.vertex(m, x1, y1, z1).color(LINE_R, LINE_G, LINE_B, LINE_A).endVertex();
        buf.vertex(m, x2, y2, z2).color(LINE_R, LINE_G, LINE_B, LINE_A).endVertex();
    }
}
