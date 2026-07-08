package com.favasur.cavehorror.entity.client;

import com.favasur.cavehorror.entity.custom.EndermanEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.Color;

public class EndermanDarkLayer extends GeoRenderLayer<EndermanEntity> {
    private static final ResourceLocation DARK_TEXTURE = ResourceLocation.fromNamespaceAndPath("cavehorror", "textures/entity/enderman_texture.png");

    public EndermanDarkLayer(GeoRenderer<EndermanEntity> entityRendererIn) {
        super(entityRendererIn);
    }

    @Override
    public void render(PoseStack poseStack, EndermanEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        RenderType darkRenderType = RenderType.entityCutoutNoCull(DARK_TEXTURE);
        // Use packed ARGB color for dark tint (0.08F RGB = ~20 in 0-255)
        this.getRenderer().reRender(this.getDefaultBakedModel(animatable), poseStack, bufferSource, animatable,
                darkRenderType, bufferSource.getBuffer(darkRenderType), partialTick, packedLight, OverlayTexture.NO_OVERLAY,
                Color.ofRGBA(0.08F, 0.08F, 0.08F, 1.0F).getColor());
    }
}
