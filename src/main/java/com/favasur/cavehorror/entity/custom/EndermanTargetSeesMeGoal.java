package com.favasur.cavehorror.entity.custom;

import com.favasur.cavehorror.entity.EndermanEntity;
import com.favasur.cavehorror.entity.EndermanEntity.State;

/**
 * TargetSeesMeGoal — detects when a player has direct line-of-sight to the enderman
 * AND is looking at it. Triggers aggro/chase mode when spotted.
 * 
 * Ported from Minecraft EndermanTargetSeesMeGoal.java
 * The original extends NearestAttackableTargetGoal<Player>.
 */
public class EndermanTargetSeesMeGoal {

    private final EndermanEntity enderman;
    private String pendingTargetId;
    
    public EndermanTargetSeesMeGoal(EndermanEntity enderman) {
        this.enderman = enderman;
    }
    
    /**
     * Check if this goal should activate.
     * Player must:
     * 1. Be within 200 blocks
     * 2. Not be spectating
     * 3. Have line-of-sight to the entity
     * 4. Be looking at the entity
     */
    public boolean canUse(double playerX, double playerY, double playerZ,
                          double lookX, double lookZ, boolean isSpectator) {
        if (enderman.isInvisible()) return false;
        if (isSpectator) return false;
        
        double dx = playerX - enderman.getX();
        double dz = playerZ - enderman.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 200.0) return false;
        
        // Check line of sight (Hytale API)
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
     * Check line of sight between player and entity.
     * Hytale API integration point.
     */
    private boolean checkLineOfSight(double px, double py, double pz) {
        // Hytale API: Raycast from player eyes to entity position
        // return HytaleAPI.getWorld().rayCast(px, py + 1.6, pz, 
        //     enderman.getX(), enderman.getY() + 1.0, enderman.getZ())
        //     .getHitEntity() == enderman;
        return true; // Placeholder
    }
    
    /**
     * FOV-based check if the player is looking toward the entity.
     */
    private boolean isPlayerLookingAt(double lookX, double lookZ, 
                                       double px, double pz) {
        double ex = enderman.getX();
        double ez = enderman.getZ();
        
        double dx = ex - px;
        double dz = ez - pz;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len == 0) return false;
        dx /= len;
        dz /= len;
        
        double lookLen = Math.sqrt(lookX * lookX + lookZ * lookZ);
        if (lookLen == 0) return false;
        double nlx = lookX / lookLen;
        double nlz = lookZ / lookLen;
        
        return dx * nlx + dz * nlz > 0.819; // ~70° FOV
    }
}
