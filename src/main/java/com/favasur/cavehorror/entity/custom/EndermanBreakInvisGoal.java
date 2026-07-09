package com.favasur.cavehorror.entity.custom;

import com.favasur.cavehorror.entity.EndermanEntity;
import com.favasur.cavehorror.entity.EndermanEntity.State;

/**
 * BreakInvisGoal — when a player looks directly at the invisible enderman,
 * it briefly becomes visible (spotted state). This triggers the spotted animation
 * and often transitions to Chase mode.
 * 
 * Ported from Minecraft EndermanBreakInvisGoal.java
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
                          double lookX, double lookZ) {
        if (!enderman.isInvisible()) return false;
        return isPlayerLookingAt(targetX, targetY, targetZ, lookX, lookZ);
    }
    
    /**
     * Break invisibility and transition to spotted state.
     */
    public void start() {
        enderman.setInvisible(false);
        enderman.setSpotted(true);
        enderman.setEyesVisible(true);
        
        // Play spotted sound
        // HytaleSoundAPI.playSound("cavehorror:enderman_stare",
        //     enderman.getX(), enderman.getY(), enderman.getZ(), 3.0f);
        
        // Transition to chase mode
        enderman.setState(State.CHASING);
        enderman.setAggro(true);
    }
    
    /**
     * Check if the player's look direction intersects with the entity.
     * Uses FOV-based detection (~70 degree cone).
     */
    private boolean isPlayerLookingAt(double px, double py, double pz,
                                       double lookX, double lookZ) {
        double ex = enderman.getX();
        double ez = enderman.getZ();
        
        // Direction from player to entity
        double dx = ex - px;
        double dz = ez - pz;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len == 0) return false;
        
        dx /= len;
        dz /= len;
        
        // Player's normalized look direction
        double lookLen = Math.sqrt(lookX * lookX + lookZ * lookZ);
        if (lookLen == 0) return false;
        
        double nlx = lookX / lookLen;
        double nlz = lookZ / lookLen;
        
        // Dot product — > 0.819 means within ~35° of center (70° FOV)
        double dot = dx * nlx + dz * nlz;
        return dot > 0.819;
    }
}
