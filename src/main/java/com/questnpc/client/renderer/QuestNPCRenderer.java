package com.questnpc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.questnpc.QuestNPC;
import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.ModEntityTypes;
import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Рендерер Квестового NPC.
 * Использует ванильную VillagerModel с хардкодированными текстурами:
 * — тип (биом): джунгли {@code textures/entity/villager/type/jungle.png}
 * — профессия: фермер {@code textures/entity/villager/profession/farmer.png}
 * GeckoLib не используется на данном этапе.
 */
@Mod.EventBusSubscriber(modid = QuestNPC.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class QuestNPCRenderer extends MobRenderer<QuestNPCEntity, VillagerModel<QuestNPCEntity>> {

    /** Базовая текстура жителя джунглевого биома (тип/биом). */
    private static final ResourceLocation JUNGLE_TYPE_TEXTURE =
            new ResourceLocation("minecraft", "textures/entity/villager/type/jungle.png");

    /**
     * Конструктор рендерера. Bake-ит VillagerModel и добавляет слой профессии фермера.
     *
     * @param context контекст провайдера рендерера (содержит baked слои и другие ресурсы)
     */
    public QuestNPCRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
        // Слой профессии фермера поверх базовой текстуры биома
        this.addLayer(new FarmerProfessionLayer(this));
        QuestNPCLogger.info("QuestNPCRenderer инициализирован с моделью VillagerModel (джунгли + фермер)");
    }

    /**
     * Возвращает базовую текстуру — тип жителя 'джунгли'.
     * Профессия (фермер) добавляется отдельным слоем.
     */
    @Override
    public ResourceLocation getTextureLocation(QuestNPCEntity entity) {
        return JUNGLE_TYPE_TEXTURE;
    }

    /**
     * Регистрация рендерера при событии EntityRenderersEvent.RegisterRenderers.
     * Вызывается только на клиенте благодаря value = Dist.CLIENT.
     *
     * @param event событие регистрации рендереров сущностей
     */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        QuestNPCLogger.info("Регистрация рендерера QuestNPCRenderer для сущности 'quest_npc'");
        event.registerEntityRenderer(ModEntityTypes.QUEST_NPC.get(), QuestNPCRenderer::new);
    }

    // ─── Вложенный слой профессии фермера ────────────────────────────────────

    /**
     * Слой рендеринга профессии фермера.
     * Накладывает текстуру одежды фермера поверх базовой текстуры биома.
     */
    private static class FarmerProfessionLayer
            extends RenderLayer<QuestNPCEntity, VillagerModel<QuestNPCEntity>> {

        /** Текстура профессии фермера (одежда поверх базового биома). */
        private static final ResourceLocation FARMER_TEXTURE =
                new ResourceLocation("minecraft", "textures/entity/villager/profession/farmer.png");

        public FarmerProfessionLayer(RenderLayerParent<QuestNPCEntity, VillagerModel<QuestNPCEntity>> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                           QuestNPCEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            // Накладываем текстуру фермера как полупрозрачный cutout-слой.
            // В 1.20.1 метод принимает два EntityModel: source (анимация) и render (геометрия).
            coloredCutoutModelCopyLayerRender(
                    this.getParentModel(),
                    this.getParentModel(),
                    FARMER_TEXTURE,
                    poseStack, buffer, packedLight, entity,
                    limbSwing, limbSwingAmount,
                    ageInTicks, netHeadYaw, headPitch, partialTick,
                    1.0F, 1.0F, 1.0F
            );
        }
    }
}
