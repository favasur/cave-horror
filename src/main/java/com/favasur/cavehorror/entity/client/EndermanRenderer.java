package com.favasur.cavehorror.entity.client;

import com.favasur.cavehorror.entity.custom.EndermanEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class EndermanRenderer extends GeoEntityRenderer<EndermanEntity> {

    public EndermanRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new EndermanModel());
        this.shadowRadius = 0.3F;
        // Add dark tint layer first, then glowing eyes on top
        this.addRenderLayer(new EndermanDarkLayer(this));
        this.addRenderLayer(new EndermanEyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(EndermanEntity instance) {
        return ResourceLocation.fromNamespaceAndPath("cavehorror", "textures/entity/enderman_texture.png");
    }

    @Override
    public void render(EndermanEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (entity.isBaby()) {
            poseStack.scale(0.1F, 0.1F, 0.1F);
        } else {
            poseStack.scale(1.3F, 1.3F, 1.3F);
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
