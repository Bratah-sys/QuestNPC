package com.questnpc.client.model;

import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeoModel для QuestNPCEntity с поддержкой кастомных .geo.json моделей.
 *
 * <p>Режимы:
 * <ul>
 *   <li>Дефолт (modelType пуст / ванильный моб) — плейсхолдерная модель (обходится рендерером)</li>
 *   <li>Кастомная модель (modelType = "custom:...") — динамический путь к .geo.json и .png</li>
 * </ul>
 */
public class QuestNPCGeoModel extends GeoModel<QuestNPCEntity> {

    // Плейсхолдерные пути для дефолтного режима (не используются при реальном рендере)
    private static final ResourceLocation DEFAULT_MODEL =
            new ResourceLocation("questnpc", "geo/quest_npc.geo.json");
    private static final ResourceLocation DEFAULT_TEXTURE =
            new ResourceLocation("minecraft", "textures/entity/villager/type/jungle.png");
    private static final ResourceLocation DEFAULT_ANIMATION =
            new ResourceLocation("questnpc", "animations/quest_npc.animation.json");

    // Fallback текстура для кастомных моделей без .png
    private static final ResourceLocation FALLBACK_TEXTURE =
            new ResourceLocation("minecraft", "textures/entity/villager/type/jungle.png");

    @Override
    public ResourceLocation getModelResource(QuestNPCEntity entity) {
        String modelType = entity.getModelEntityType();
        if (CustomModelManager.isCustomModel(modelType)) {
            String name = CustomModelManager.extractModelName(modelType);
            // Маппится CustomModelPackResources на файл из config/questnpc/custom_models/
            return new ResourceLocation("questnpc", "geo/custom/" + name + ".geo.json");
        }
        return DEFAULT_MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(QuestNPCEntity entity) {
        String modelType = entity.getModelEntityType();
        if (CustomModelManager.isCustomModel(modelType)) {
            String name = CustomModelManager.extractModelName(modelType);
            // Проверяем наличие текстуры через менеджер
            if (CustomModelManager.getInstance().hasTexture(name)) {
                return new ResourceLocation("questnpc", "textures/custom/" + name + ".png");
            }
            return FALLBACK_TEXTURE;
        }
        return DEFAULT_TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(QuestNPCEntity entity) {
        // Кастомные модели используют стандартные анимации (walk/idle)
        // В будущем можно добавить поддержку кастомных .animation.json
        return DEFAULT_ANIMATION;
    }
}
