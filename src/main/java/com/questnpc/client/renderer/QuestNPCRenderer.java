package com.questnpc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.questnpc.QuestNPC;
import com.questnpc.QuestNPCLogger;
import com.questnpc.client.model.QuestNPCGeoModel;
import com.questnpc.entity.ModEntityTypes;
import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.HashMap;
import java.util.Map;

/**
 * Гибридный рендерер QuestNPC на базе GeckoLib.
 * Три режима:
 * 1) Дефолт (modelEntityType пуст) — ванильный VillagerModel + FarmerProfessionLayer
 * 2) Ванильный моб (modelEntityType = "minecraft:zombie" и т.д.) — фейковая entity через ванильный рендерер
 * 3) Кастомная модель (будущее, modelEntityType = "questnpc:custom/...") — GeckoLib pipeline
 */
@Mod.EventBusSubscriber(modid = QuestNPC.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class QuestNPCRenderer extends GeoEntityRenderer<QuestNPCEntity> {

    // ═══ Ванильная модель (режим 1 — дефолт) ═══
    private static final ResourceLocation JUNGLE_TYPE_TEXTURE =
            new ResourceLocation("minecraft", "textures/entity/villager/type/jungle.png");
    private static final ResourceLocation FARMER_TEXTURE =
            new ResourceLocation("minecraft", "textures/entity/villager/profession/farmer.png");

    private final VillagerModel<QuestNPCEntity> villagerModel;
    private final VillagerModel<QuestNPCEntity> villagerModelCopy;
    private final EntityRendererProvider.Context renderContext;

    // ═══ Кэш фейковых entity (режим 2 — ванильные мобы) ═══
    private final Map<ResourceLocation, Entity> fakeEntityCache = new HashMap<>();
    private static final int MAX_CACHE_SIZE = 50;

    public QuestNPCRenderer(EntityRendererProvider.Context context) {
        super(context, new QuestNPCGeoModel());
        this.renderContext = context;
        // Bake ванильную модель жителя для дефолтного режима
        this.villagerModel = new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER));
        this.villagerModelCopy = new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER));
        QuestNPCLogger.info("QuestNPCRenderer (GeckoLib hybrid) инициализирован");
    }

    @Override
    public void render(QuestNPCEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        String modelType = entity.getModelEntityType();

        if (modelType.isEmpty()) {
            // РЕЖИМ 1: Дефолт — ванильный VillagerModel
            renderAsVillager(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        } else {
            // РЕЖИМ 2: Ванильный моб — фейковая сущность
            renderAsFakeEntity(entity, modelType, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        }
    }

    // ═══════════════════════════════════════════════
    // РЕЖИМ 1: Ванильный VillagerModel (дефолт)
    // ═══════════════════════════════════════════════

    private void renderAsVillager(QuestNPCEntity entity, float entityYaw, float partialTick,
                                  PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // Масштаб + тень
        this.shadowRadius = 0.5F;

        // Рендер ванильной модели жителя (аналог MobRenderer)
        float bodyYaw = net.minecraft.util.Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = net.minecraft.util.Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
        float netHeadYaw = headYaw - bodyYaw;
        float headPitch = net.minecraft.util.Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        float limbSwingAmount = entity.walkAnimation.speed(partialTick);
        float limbSwing = entity.walkAnimation.position(partialTick);
        if (limbSwingAmount > 1.0F) limbSwingAmount = 1.0F;

        poseStack.mulPose(com.mojang.math.Axis.YN.rotationDegrees(bodyYaw));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);

        // Подготовка модели
        villagerModel.setupAnim(entity, limbSwing, limbSwingAmount, entity.tickCount + partialTick, netHeadYaw, headPitch);

        // Рендер базовой текстуры (джунгли)
        RenderType renderType = RenderType.entityCutoutNoCull(JUNGLE_TYPE_TEXTURE);
        VertexConsumer buffer = bufferSource.getBuffer(renderType);
        int overlay = OverlayTexture.pack(OverlayTexture.u(0), OverlayTexture.v(entity.hurtTime > 0 || !entity.isAlive()));
        villagerModel.renderToBuffer(poseStack, buffer, packedLight, overlay, 1.0F, 1.0F, 1.0F, 1.0F);

        // Слой профессии фермера
        villagerModelCopy.setupAnim(entity, limbSwing, limbSwingAmount, entity.tickCount + partialTick, netHeadYaw, headPitch);
        VertexConsumer farmerBuffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(FARMER_TEXTURE));
        villagerModelCopy.renderToBuffer(poseStack, farmerBuffer, packedLight, overlay, 1.0F, 1.0F, 1.0F, 1.0F);

        poseStack.popPose();
    }

    // ═══════════════════════════════════════════════
    // РЕЖИМ 2: Фейковая сущность (ванильные мобы)
    // ═══════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private void renderAsFakeEntity(QuestNPCEntity npc, String modelTypeStr, float entityYaw,
                                    float partialTick, PoseStack poseStack,
                                    MultiBufferSource bufferSource, int packedLight) {
        ResourceLocation modelRL = ResourceLocation.tryParse(modelTypeStr);
        if (modelRL == null) {
            renderAsVillager(npc, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        try {
            Entity fakeEntity = getOrCreateFakeEntity(modelRL, npc);
            if (fakeEntity == null) {
                renderAsVillager(npc, entityYaw, partialTick, poseStack, bufferSource, packedLight);
                return;
            }

            // Копируем позицию/ротацию с NPC
            syncFakeEntityPose(fakeEntity, npc, partialTick);

            // Получаем рендерер для фейковой сущности
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            @SuppressWarnings("rawtypes")
            EntityRenderer renderer = dispatcher.getRenderer(fakeEntity);
            if (renderer == null) {
                renderAsVillager(npc, entityYaw, partialTick, poseStack, bufferSource, packedLight);
                return;
            }

            // Устанавливаем тень
            this.shadowRadius = 0.5F;

            // Рендерим фейковую entity (unchecked — типы гарантированы через EntityRenderDispatcher)
            @SuppressWarnings("unchecked")
            EntityRenderer<Entity> typedRenderer = (EntityRenderer<Entity>) renderer;
            typedRenderer.render(fakeEntity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        } catch (Exception e) {
            QuestNPCLogger.warn("Не удалось отрендерить фейковую entity '{}': {}", modelTypeStr, e.getMessage());
            renderAsVillager(npc, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        }
    }

    /**
     * Получает или создаёт фейковую entity для рендера.
     */
    @SuppressWarnings("unchecked")
    private Entity getOrCreateFakeEntity(ResourceLocation modelRL, QuestNPCEntity npc) {
        Entity cached = fakeEntityCache.get(modelRL);
        if (cached != null && cached.level() == npc.level()) {
            return cached;
        }

        // Лимит кэша
        if (fakeEntityCache.size() >= MAX_CACHE_SIZE) {
            fakeEntityCache.clear();
        }

        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(modelRL);
        if (type == null) return null;

        try {
            Entity entity = type.create(npc.level());
            if (entity != null) {
                fakeEntityCache.put(modelRL, entity);
                QuestNPCLogger.debug("Создана фейковая entity '{}' для рендера", modelRL);
            }
            return entity;
        } catch (Exception e) {
            QuestNPCLogger.warn("Не удалось создать entity '{}': {}", modelRL, e.getMessage());
            return null;
        }
    }

    /**
     * Копирует позицию, ротацию и анимационные данные с NPC на фейковую entity.
     */
    private void syncFakeEntityPose(Entity fakeEntity, QuestNPCEntity npc, float partialTick) {
        fakeEntity.setPos(npc.getX(), npc.getY(), npc.getZ());
        fakeEntity.setXRot(npc.getXRot());
        fakeEntity.setYRot(npc.getYRot());
        fakeEntity.xRotO = npc.xRotO;
        fakeEntity.yRotO = npc.yRotO;
        fakeEntity.tickCount = npc.tickCount;

        if (fakeEntity instanceof LivingEntity fakeLiving) {
            fakeLiving.yBodyRot = npc.yBodyRot;
            fakeLiving.yBodyRotO = npc.yBodyRotO;
            fakeLiving.yHeadRot = npc.yHeadRot;
            fakeLiving.yHeadRotO = npc.yHeadRotO;
            fakeLiving.walkAnimation.update(
                    npc.walkAnimation.isMoving() ? 1.0F : 0.0F,
                    0.4F
            );
            fakeLiving.hurtTime = npc.hurtTime;
            fakeLiving.deathTime = npc.deathTime;
        }
    }

    // ═══════════════════════════════════════════════
    // Текстура (для GeoEntityRenderer контракт)
    // ═══════════════════════════════════════════════

    @Override
    public ResourceLocation getTextureLocation(QuestNPCEntity entity) {
        return JUNGLE_TYPE_TEXTURE;
    }

    // ═══════════════════════════════════════════════
    // Регистрация
    // ═══════════════════════════════════════════════

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        QuestNPCLogger.info("Регистрация QuestNPCRenderer (GeckoLib hybrid) для сущности 'quest_npc'");
        event.registerEntityRenderer(ModEntityTypes.QUEST_NPC.get(), QuestNPCRenderer::new);
    }
}
