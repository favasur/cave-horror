package com.favasur.cavehorror.entity.custom;

import com.favasur.cavehorror.entity.EndermanEntity;
import com.favasur.cavehorror.entity.EndermanEntity.State;
import com.hytale.api.HytaleServer;
import com.hytale.api.world.RayTraceResult;
import com.hytale.api.world.Vector3f;
import com.hytale.api.world.World;

/**
 * TargetSeesMeGoal — detects when a player has direct line-of-sight to the enderman
 * AND is looking at it. Triggers aggro/chase mode when spotted.
 * 
 * Uses Hytale WorldService raycast for accurate line-of-sight detection.
 */
public class EndermanTargetSeesMeGoal {

    private final EndermanEntity enderman;
    private String pendingTargetId;
    
    public EndermanTargetSeesMeGoal(EndermanEntity enderman) {
        this.enderman = enderman;
    }
    
    /**
     * Check if this goal should activate.
     * Player must: be within 200 blocks, have line-of-sight, be looking at entity.
     */
    public boolean canUse(double playerX, double playerY, double playerZ,
                          double lookX, double lookY, double lookZ, boolean isSpectator) {
        if (enderman.isInvisible()) return false;
        if (isSpectator) return false;
        
        double dx = playerX - enderman.getX();
        double dz = playerZ - enderman.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 200.0) return false;
        
        // Check line of sight via raycast
        boolean hasLineOfSight = checkLineOfSight(playerX, playerY, playerZ);
        if (!hasLineOfSight) return false;
        
        // Check if player is looking at the entity
        return isPlayerLookingAt(lookX, lookZ, playerX, playerZ);
    }
    
    /**
     * Activate this goal — set the player as target and transition to chase.
     */
    public void start(String playerId) {
        this.pendingTargetId = playerId;
        enderman.setTargetPlayerId(playerId);
        enderman.setSpotted(true);
        enderman.setAggro(true);
        enderman.setState(State.CHASING);
    }
    
    /**
     * Raycast from player eyes to entity to check for line-of-sight.
     */
    private boolean checkLineOfSight(double px, double py, double pz) {
        World world = enderman.getPlugin().getServer().getWorld("overworld");
        if (world == null) return false;
        
        // Raycast from player eyes (y+1.6) to entity center (y+1.0)
        RayTraceResult result = world.rayTrace(
            new Vector3f((float)px, (float)(py + 1.6), (float)pz),
            new Vector3f((float)enderman.getX(), (float)(enderman.getY() + 1.0), (float)enderman.getZ()),
            200.0, // max distance
            true   // include entities
        );
        
        return result != null && result.getHitEntity() != null 
            && result.getHitEntity().equals(enderman.getEntity());
    }
    
    /**
     * FOV-based check if the player is looking toward the entity (~70° cone).
     */
    private boolean isPlayerLookingAt(double lookX, double lookZ, 
                                       double px, double pz) {
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
