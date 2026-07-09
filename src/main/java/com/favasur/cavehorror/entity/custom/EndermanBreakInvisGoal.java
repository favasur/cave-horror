package com.favasur.cavehorror.entity.custom;

import com.favasur.cavehorror.entity.EndermanEntity;
import com.favasur.cavehorror.entity.EndermanEntity.State;
import com.hytale.api.HytaleServer;
import com.hytale.api.world.Vector3f;

/**
 * BreakInvisGoal — when a player looks directly at the invisible enderman,
 * it briefly becomes visible (spotted state), transitioning to Chase mode.
 * 
 * Uses Hytale AudioService for the spotted sound effect.
 */
public class EndermanBreakInvisGoal {

    private final EndermanEntity enderman;
    
    public EndermanBreakInvisGoal(EndermanEntity enderman) {
        this.enderman = enderman;
    }
    
    /**
     * Check if the player is looking at the invisible entity.
     */
    public boolean canUse(double targetX, double targetY, double targetZ,
                          double lookX, double lookY, double lookZ) {
        if (!enderman.isInvisible()) return false;
        return isPlayerLookingAt(targetX, targetY, targetZ, lookX, lookZ);
    }
    
    /**
     * Break invisibility and transition to spotted/chase state.
     */
    public void start() {
        enderman.setInvisible(false);
        enderman.setSpotted(true);
        enderman.setEyesVisible(true);
        
        // Play spotted sound
        HytaleServer.getAudioService().playSound(
            null, "cavehorror:enderman_stare",
            new Vector3f((float)enderman.getX(), (float)enderman.getY(), (float)enderman.getZ()),
            3.0f, 1.0f
        );
        
        enderman.setState(State.CHASING);
        enderman.setAggro(true);
    }
    
    /**
     * FOV-based check (~70° cone) if player is looking toward the entity.
     */
    private boolean isPlayerLookingAt(double px, double py, double pz,
                                       double lookX, double lookZ) {
        double ex = enderman.getX();
        double ez = enderman.getZ();
        
        double dx = ex - px;
        double dz = ez - pz;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len == 0) return false;
        dx /= len; dz /= len;
        
        double lookLen = Math.sqrt(lookX * lookX + lookZ * lookZ);
        if (lookLen == 0) return false;
        double nlx = lookX / lookLen;
        double nlz = lookZ / lookLen;
        
        return dx * nlx + dz * nlz > 0.819;
    }
}
