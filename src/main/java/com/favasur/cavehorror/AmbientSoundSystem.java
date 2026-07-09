package com.favasur.cavehorror;

import com.favasur.cavehorror.CaveNoisePlugin.PlayerData;
import com.favasur.cavehorror.entity.EndermanEntity;
import com.hytale.api.HytaleServer;
import com.hytale.api.world.Location;
import com.hytale.api.world.Vector3f;

import java.util.Random;

/**
 * AmbientSoundSystem — manages creepy ambient sounds that play when
 * the enderman entity is near a player underground.
 * 
 * All sounds are played via HytaleServer.getAudioService().
 * Sound definitions are registered in assets/cavehorror/sounds.json.
 */
public class AmbientSoundSystem {

    private final CaveNoisePlugin plugin;
    private final Random random;
    
    private static final int CREEPY_CAVE_NOISE_START = 8000;
    private static final int CREEPY_CAVE_NOISE_END = 1000;
    private static final float MIN_VOL = 0.1f;
    private static final float MAX_VOL = 1.0f;
    
    public AmbientSoundSystem(CaveNoisePlugin plugin) {
        this.plugin = plugin;
        this.random = plugin.getRandom();
    }
    
    /**
     * Play enderman scream/ambient near the player. Volume rises as calm timer drops.
     */
    public void playCaveSound(PlayerData player) {
        float calmRatio = (float)(plugin.getCalmTimer() - CREEPY_CAVE_NOISE_END)
                         / (float)(CREEPY_CAVE_NOISE_START - CREEPY_CAVE_NOISE_END);
        calmRatio = Math.max(0, Math.min(1, 1.0f - calmRatio));
        float volume = MIN_VOL + (MAX_VOL - MIN_VOL) * calmRatio;
        
        String soundId = random.nextBoolean()
            ? "cavehorror:enderman_scream"
            : "cavehorror:enderman_ambient";
        
        // Play sound at player's position via Hytale audio service
        HytaleServer.getAudioService().playSound(
            player.player,
            soundId,
            new Vector3f((float)player.x, (float)player.y, (float)player.z),
            volume,
            1.0f
        );
        
        // Spawn white particle eyes in a 16-block radius around player
        spawnEyeParticles(player);
        
        // Reveal any stalking entity's eyes
        revealNearbyEyes(player);
    }
    
    /**
     * Play standard cave ambient sound.
     */
    public void playVanillaCaveSound(PlayerData player) {
        HytaleServer.getAudioService().playSound(
            player.player,
            "cavehorror:cave_ambient",
            new Vector3f((float)player.x, (float)player.y, (float)player.z),
            1.0f,
            1.0f
        );
        revealNearbyEyes(player);
    }
    
    /**
     * Play enderman ambient/stare sound offset ~25 blocks from the player.
     * Creates the illusion of the entity being close but unseen.
     */
    public void playStalkSound(PlayerData player) {
        double angle = random.nextDouble() * Math.PI * 2;
        double sx = player.x + Math.cos(angle) * 25;
        double sz = player.z + Math.sin(angle) * 25;
        
        String soundId = random.nextBoolean()
            ? "cavehorror:enderman_ambient"
            : "cavehorror:enderman_stare";
        
        HytaleServer.getAudioService().playSound(
            player.player,
            soundId,
            new Vector3f((float)sx, (float)player.y, (float)sz),
            2.0f,
            1.0f
        );
        revealNearbyEyes(player);
    }
    
    private void spawnEyeParticles(PlayerData player) {
        for (int i = 0; i < 6; i++) {
            double px = player.x + (random.nextDouble() - 0.5) * 16.0;
            double py = player.y + random.nextDouble() * 6.0 - 1.0;
            double pz = player.z + (random.nextDouble() - 0.5) * 16.0;
            
            HytaleServer.getParticleService().spawnParticle(
                "cavehorror:white_eye",
                new Vector3f((float)px, (float)py, (float)pz),
                new Vector3f(0, 0, 0),
                0
            );
        }
    }
    
    private void revealNearbyEyes(PlayerData player) {
        // Only reveal eyes in complete darkness
        int brightness = player.world.getMaxLocalBrightness(
            (int)player.x, (int)player.y, (int)player.z);
        if (brightness > 0) return;
        
        for (EndermanEntity entity : plugin.getEndermanRegistry().getActiveEntities()) {
            double dist = entity.distanceTo(player.x, player.y, player.z);
            if (dist < 50.0 && player.uuid.toString().equals(entity.getTargetPlayerId())) {
                entity.setEyesVisible(true);
            }
        }
    }
}
