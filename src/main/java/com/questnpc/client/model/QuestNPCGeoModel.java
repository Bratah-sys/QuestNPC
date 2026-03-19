package com.questnpc.client.model;

import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeoModel для QuestNPCEntity.
 * Плейсхолдерная модель — используется только при загрузке кастомных .geo.json (будущее).
 * В дефолтном режиме рендерер обходит GeckoLib и рисует ванильный VillagerModel.
 */
public class QuestNPCGeoModel extends GeoModel<QuestNPCEntity> {

    @Override
    public ResourceLocation getModelResource(QuestNPCEntity entity) {
        return new ResourceLocation("questnpc", "geo/quest_npc.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(QuestNPCEntity entity) {
        return new ResourceLocation("minecraft", "textures/entity/villager/type/jungle.png");
    }

    @Override
    public ResourceLocation getAnimationResource(QuestNPCEntity entity) {
        return new ResourceLocation("questnpc", "animations/quest_npc.animation.json");
    }
}
