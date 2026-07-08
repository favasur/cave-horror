package com.favasur.cavehorror.entity.client;

import com.favasur.cavehorror.entity.custom.EndermanEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.util.Color;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class EndermanEyesLayer extends GeoRenderLayer<EndermanEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("cavehorror", "textures/entity/enderman_eyes_texture.png");

    public EndermanEyesLayer(GeoRenderer<EndermanEntity> entityRendererIn) {
        super(entityRendererIn);
    }

    @Override
    public void render(PoseStack poseStack, EndermanEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        packedLight = 15728880;
        RenderType eyesRenderType = RenderType.entityCutoutNoCull(TEXTURE);
        this.getRenderer().reRender(this.getDefaultBakedModel(animatable), poseStack, bufferSource, animatable,
                eyesRenderType, bufferSource.getBuffer(eyesRenderType), partialTick, packedLight, OverlayTexture.NO_OVERLAY,
                Color.WHITE.getColor());
    }
}
