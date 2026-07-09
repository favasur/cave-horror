package com.favasur.cavehorror;

import com.favasur.cavehorror.entity.EndermanEntity;
import com.favasur.cavehorror.entity.EndermanRegistry;

import java.util.Random;
import java.util.UUID;

/**
 * EntitySpawnSystem — handles spawning Enderman entities behind players.
 * 
 * Ported from Minecraft CaveNoise.java spawn logic.
 * Spawns 20-40 blocks behind the player (opposite of look direction).
 * Entity starts invisible and begins stalking.
 */
public class EntitySpawnSystem {

    private final CaveNoisePlugin plugin;
    private final Random random;
    
    public EntitySpawnSystem(CaveNoisePlugin plugin) {
        this.plugin = plugin;
        this.random = plugin.getRandom();
    }
    
    /**
     * Spawn an Enderman entity behind the given player.
     * 
     * @param playerId The UUID of the target player
     * @param px, py, pz Player position
     * @param lookX, lookZ Player look direction (normalized horizontal)
     * @return The spawned entity, or null if no valid position or too close to another
     */
    public EndermanEntity spawnEndermanBehind(UUID playerId, double px, double py, double pz,
                                               double lookX, double lookZ) {
        EndermanRegistry registry = plugin.getEndermanRegistry();
        
        // Don't spawn if another enderman is already within 300 blocks
        if (registry.isAnyNear(px, py, pz, 300.0)) {
            return null;
        }
        
        // Generate spawn position
        double[] spawnPos = EndermanEntity.generateSpawnPosition(px, py, pz, lookX, lookZ);
        
        // HYTALE API: Verify position is valid (air at spawn, solid ground below, no sky access)
        // Also need to check y < 40 equivalent
        // if (!isValidSpawnPosition(spawnPos[0], spawnPos[1], spawnPos[2])) return null;
        
        // Spawn the entity
        EndermanEntity entity = registry.spawnAt(spawnPos[0], spawnPos[1], spawnPos[2]);
        entity.setInvisible(true);
        entity.setTargetPlayerId(playerId.toString());
        
        CaveNoisePlugin.getLogger().info(
            "Spawned Enderman behind player {} at ({}, {}, {})",
            playerId, spawnPos[0], spawnPos[1], spawnPos[2]
        );
        
        return entity;
    }
    
    /**
     * Check if a position is valid for spawning.
     * HYTALE API: Check blocks using world API.
     */
    public boolean isValidSpawnPosition(double x, double y, double z) {
        // Requirements:
        // - Air at (x, y, z), (x, y+1, z), (x, y+2, z)
        // - Solid block at (x, y-1, z)
        // - No sky access
        // - y < 40
        return true; // Placeholder — needs Hytale world API
    }
}
