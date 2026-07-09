package com.favasur.cavehorror.entity.custom;

import com.favasur.cavehorror.entity.EndermanEntity;
import com.favasur.cavehorror.entity.EndermanEntity.State;

/**
 * TargetTooCloseGoal — triggers aggro when a player gets within a close radius
 * (12 blocks) of the enderman. This creates sudden jump-scare moments when
 * exploring cave corridors.
 * 
 * Ported from Minecraft EndermanTargetTooCloseGoal.java
 * The original extends NearestAttackableTargetGoal<Player>.
 */
public class EndermanTargetTooCloseGoal {

    private final EndermanEntity enderman;
    private final float distanceThreshold;
    
    public EndermanTargetTooCloseGoal(EndermanEntity enderman, float distanceThreshold) {
        this.enderman = enderman;
        this.distanceThreshold = distanceThreshold;
    }
    
    /**
     * Check if a player is within the distance threshold.
     */
    public boolean canUse(double playerX, double playerY, double playerZ,
                          boolean isSpectator) {
        if (enderman.isInvisible()) return false;
        if (isSpectator) return false;
        
        double dx = playerX - enderman.getX();
        double dy = playerY - enderman.getY();
        double dz = playerZ - enderman.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        
        if (dist > distanceThreshold) return false;
        
        // Check line of sight
        return checkLineOfSight(playerX, playerY, playerZ);
    }
    
    /**
     * Activate aggro mode.
     */
    public void start(String playerId) {
        enderman.setTargetPlayerId(playerId);
        enderman.setAggro(true);
        enderman.setState(State.CHASING);
        enderman.setInvisible(false);
    }
    
    /**
     * Check line of sight between player and entity.
     */
    private boolean checkLineOfSight(double px, double py, double pz) {
        // Hytale API: Raycast
        // return HytaleAPI.getWorld().rayCast(...).getHitEntity() == enderman;
        return true; // Placeholder
    }
}
