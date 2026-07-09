package com.favasur.cavehorror.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adapted from True Darkness mod for Minecraft 1.21.1.
 * Makes darkness truly dark: any pixel in the lightmap with block light level 0
 * (i.e., no nearby light source) renders as pure black, and gamma cannot brighten it.
 * 
 * The lightmap is a 16x16 NativeImage where:
 * - Column (x) = block light level (0-15)
 * - Row (y) = sky light level (0-15)
 * 
 * We set column 0 (block light = 0) to pure black, making any area with zero
 * block light render as pitch black regardless of sky light or gamma settings.
 * Columns 1-2 are also significantly darkened to create a sharp visible edge.
 */
@Mixin(LightTexture.class)
public class LightTextureMixin {

    @Shadow
    private NativeImage lightPixels;

    @Inject(method = "updateLightTexture", at = @At("TAIL"))
    private void onUpdateLightTexture(float partialTick, CallbackInfo ci) {
        // Column 0: block light = 0 → pure black (no light source at all)
        for (int sky = 0; sky < 16; sky++) {
            lightPixels.setPixelRGBA(0, sky, 0xFF000000);
        }
        // Column 1: block light = 1 → very dark (nearly black)
        for (int sky = 0; sky < 16; sky++) {
            // Slightly less dark than pure black to create a gradient edge
            lightPixels.setPixelRGBA(1, sky, 0xFF010101);
        }
        // Column 2: block light = 2 → still very dark
        for (int sky = 0; sky < 16; sky++) {
            lightPixels.setPixelRGBA(2, sky, 0xFF030303);
        }
    }
}
