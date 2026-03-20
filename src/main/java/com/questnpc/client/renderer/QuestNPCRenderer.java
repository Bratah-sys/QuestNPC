package com.questnpc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.questnpc.QuestNPC;
import com.questnpc.QuestNPCLogger;
import com.questnpc.client.model.CustomModelManager;
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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Гибридный рендерер QuestNPC на базе GeckoLib.
 * Три режима:
 * 1) Дефолт (modelEntityType пуст) — ванильный VillagerModel + FarmerProfessionLayer
 * 2) Ванильный моб (modelEntityType = "minecraft:zombie" и т.д.) — фейковая entity через ванильный рендерер
 * 3) Кастомная модель (modelEntityType = "custom:cool_kid") — GeckoLib pipeline из .geo.json файлов
 */
@Mod.EventBusSubscriber(modid = QuestNPC.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class QuestNPCRenderer extends GeoEntityRenderer<QuestNPCEntity> {

    // ═══ Reflection: WalkAnimationState fields (cached once) ═══
    // WalkAnimationState has exactly 3 private float fields: speed, speedOld, position (in declaration order).
    // We find them by type+order to avoid SRG/official name mismatch between dev and production.
    private static final Field WALK_ANIM_SPEED;
    private static final Field WALK_ANIM_SPEED_OLD;
    private static final Field WALK_ANIM_POSITION;
    private static boolean walkAnimReflectionFailed = false;

    static {
        Field speed = null, speedOld = null, position = null;
        try {
            Field[] allFields = net.minecraft.world.entity.WalkAnimationState.class.getDeclaredFields();
            int floatIndex = 0;
            for (Field f : allFields) {
                if (f.getType() == float.class) {
                    f.setAccessible(true);
                    switch (floatIndex) {
                        case 0 -> speed = f;
                        case 1 -> speedOld = f;
                        case 2 -> position = f;
                    }
                    floatIndex++;
                }
            }
            if (speed == null || speedOld == null || position == null) {
                throw new RuntimeException("Expected 3 float fields, found " + floatIndex);
            }
        } catch (Exception e) {
            QuestNPCLogger.warn("WalkAnimationState reflection failed: {} — using fallback sync", e.getMessage());
            walkAnimReflectionFailed = true;
        }
        WALK_ANIM_SPEED = speed;
        WALK_ANIM_SPEED_OLD = speedOld;
        WALK_ANIM_POSITION = position;
    }

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
        } else if (CustomModelManager.isCustomModel(modelType)) {
            // РЕЖИМ 3: Кастомная .geo.json модель — GeckoLib pipeline
            renderAsCustomGeoModel(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        } else {
            // РЕЖИМ 2: Ванильный моб — фейковая сущность
            renderAsFakeEntity(entity, modelType, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        }
    }

    // ═══════════════════════════════════════════════
    // РЕЖИМ 3: Кастомная .geo.json модель (GeckoLib)
    // ═══════════════════════════════════════════════

    /**
     * Рендерит NPC с кастомной .geo.json моделью через стандартный GeckoLib pipeline.
     * QuestNPCGeoModel динамически возвращает правильные ResourceLocation.
     */
    private void renderAsCustomGeoModel(QuestNPCEntity entity, float entityYaw, float partialTick,
                                        PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        this.shadowRadius = 0.5F;
        // Safety wrapper: GeckoLib может оставить PoseStack в грязном состоянии при ошибке
        // (pushPose без popPose). Оборачиваем в свой push/pop чтобы гарантировать баланс.
        poseStack.pushPose();
        try {
            // GeckoLib pipeline: super.render() использует QuestNPCGeoModel
            // который вернёт ResourceLocation для кастомной модели/текстуры
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        } catch (Exception e) {
            QuestNPCLogger.warn("Ошибка рендера кастомной модели '{}' для NPC {}: {}",
                    entity.getModelEntityType(), entity.getId(), e.getMessage());
            // GeckoLib мог оставить лишние push в стеке — пытаемся вычистить
            try { poseStack.popPose(); } catch (Exception ignored) {}
        }
        poseStack.popPose();
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
     * Fixes two animation bugs:
     * 1) Vanilla mobs (zombie, skeleton): legs moved too fast because walkAnimation.update() was called
     *    every render frame with speed=1.0. Now we copy the NPC's actual walkAnimation fields via reflection.
     * 2) Modded mobs (GeckoLib): static T-pose because fake entity had no movement data.
     *    Now we copy deltaMovement, xOld/yOld/zOld, walkDist/walkDistO so GeckoLib's isMoving() works.
     */
    private void syncFakeEntityPose(Entity fakeEntity, QuestNPCEntity npc, float partialTick) {
        fakeEntity.setPos(npc.getX(), npc.getY(), npc.getZ());
        fakeEntity.setXRot(npc.getXRot());
        fakeEntity.setYRot(npc.getYRot());
        fakeEntity.xRotO = npc.xRotO;
        fakeEntity.yRotO = npc.yRotO;
        fakeEntity.tickCount = npc.tickCount;

        // Copy movement data for GeckoLib isMoving() detection
        fakeEntity.xOld = npc.xOld;
        fakeEntity.yOld = npc.yOld;
        fakeEntity.zOld = npc.zOld;
        fakeEntity.setDeltaMovement(npc.getDeltaMovement());
        fakeEntity.walkDist = npc.walkDist;
        fakeEntity.walkDistO = npc.walkDistO;
        fakeEntity.moveDist = npc.moveDist;

        if (fakeEntity instanceof LivingEntity fakeLiving) {
            fakeLiving.yBodyRot = npc.yBodyRot;
            fakeLiving.yBodyRotO = npc.yBodyRotO;
            fakeLiving.yHeadRot = npc.yHeadRot;
            fakeLiving.yHeadRotO = npc.yHeadRotO;
            fakeLiving.hurtTime = npc.hurtTime;
            fakeLiving.deathTime = npc.deathTime;

            // Copy walkAnimation fields via reflection for correct vanilla mob leg speed
            if (!walkAnimReflectionFailed) {
                try {
                    WALK_ANIM_SPEED.setFloat(fakeLiving.walkAnimation,
                            WALK_ANIM_SPEED.getFloat(npc.walkAnimation));
                    WALK_ANIM_SPEED_OLD.setFloat(fakeLiving.walkAnimation,
                            WALK_ANIM_SPEED_OLD.getFloat(npc.walkAnimation));
                    WALK_ANIM_POSITION.setFloat(fakeLiving.walkAnimation,
                            WALK_ANIM_POSITION.getFloat(npc.walkAnimation));
                } catch (Exception e) {
                    // Fallback: use the old (broken) approach
                    fakeLiving.walkAnimation.update(
                            npc.walkAnimation.isMoving() ? 1.0F : 0.0F, 0.4F);
                }
            } else {
                // Reflection unavailable — fallback
                fakeLiving.walkAnimation.update(
                        npc.walkAnimation.isMoving() ? 1.0F : 0.0F, 0.4F);
            }
        }
    }

    // ═══════════════════════════════════════════════
    // Текстура (для GeoEntityRenderer контракт)
    // ═══════════════════════════════════════════════

    @Override
    public ResourceLocation getTextureLocation(QuestNPCEntity entity) {
        // Для кастомных моделей — делегируем в GeoModel для получения правильной текстуры
        if (CustomModelManager.isCustomModel(entity.getModelEntityType())) {
            return this.model.getTextureResource(entity);
        }
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
