package com.favasur.cavehorror.entity.client;

import com.favasur.cavehorror.entity.custom.EndermanEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class EndermanModel extends GeoModel<EndermanEntity> {
    @Override
    public ResourceLocation getModelResource(EndermanEntity object) {
        return ResourceLocation.fromNamespaceAndPath("cavehorror", "geo/enderman.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(EndermanEntity object) {
        return ResourceLocation.fromNamespaceAndPath("cavehorror", "textures/entity/enderman_texture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(EndermanEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath("cavehorror", "animations/enderman.animation.json");
    }

    @Override
    public void setCustomAnimations(EndermanEntity animatable, long instanceId, AnimationState animationState) {
        GeoBone head = this.getAnimationProcessor().getBone("head");
        if (head != null) {
            EntityModelData entityData = (EntityModelData) animationState.getData(DataTickets.ENTITY_MODEL_DATA);
            head.setRotX(entityData.headPitch() * ((float) Math.PI / 180F));
            head.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180F));
        }
    }
}
