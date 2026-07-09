package com.favasur.cavehorror.entity.custom;

import com.favasur.cavehorror.entity.EndermanEntity;
import com.favasur.cavehorror.entity.EndermanEntity.State;
import com.hytale.api.HytaleServer;
import com.hytale.api.world.RayTraceResult;
import com.hytale.api.world.Vector3f;
import com.hytale.api.world.World;

/**
 * TargetTooCloseGoal — triggers aggro when a player gets within a close radius
 * (12 blocks) of the enderman. Creates sudden jump-scare moments in corridors.
 * 
 * Uses Hytale WorldService raycast for line-of-sight validation.
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
        
        return checkLineOfSight(playerX, playerY, playerZ);
    }
    
    /**
     * Activate aggro mode — transition to chase.
     */
    public void start(String playerId) {
        enderman.setTargetPlayerId(playerId);
        enderman.setAggro(true);
        enderman.setState(State.CHASING);
        enderman.setInvisible(false);
    }
    
    /**
     * Raycast from player to entity to verify line-of-sight.
     */
    private boolean checkLineOfSight(double px, double py, double pz) {
        World world = enderman.getPlugin().getServer().getWorld("overworld");
        if (world == null) return false;
        
        RayTraceResult result = world.rayTrace(
            new Vector3f((float)px, (float)(py + 1.6), (float)pz),
            new Vector3f((float)enderman.getX(), (float)(enderman.getY() + 1.0), (float)enderman.getZ()),
            distanceThreshold,
            true
        );
        
        return result != null && result.getHitEntity() != null 
            && result.getHitEntity().equals(enderman.getEntity());
    }
}
