package com.favasur.cavehorror;

import com.favasur.cavehorror.entity.EndermanEntity;
import com.favasur.cavehorror.entity.EndermanRegistry;
import com.hytale.api.HytaleServer;
import com.hytale.api.world.Location;
import com.hytale.api.world.Material;
import com.hytale.api.world.Vector3f;
import com.hytale.api.world.World;
import com.hytale.api.entity.Entity;
import com.hytale.api.entity.EntityType;

import java.util.Random;
import java.util.UUID;

/**
 * EntitySpawnSystem — handles spawning Enderman entities behind players.
 * 
 * Spawns 20-40 blocks behind the player (opposite of look direction).
 * Entity starts invisible and begins stalking.
 * 
 * Uses HytaleServer.getEntityService() for spawning and
 * HytaleServer.getWorldService() for terrain validation.
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
     * @param lookYaw, lookPitch Player look direction
     * @return The spawned entity, or null if no valid position or too close to another
     */
    public EndermanEntity spawnEndermanBehind(UUID playerId, double px, double py, double pz,
                                               float lookYaw, float lookPitch) {
        EndermanRegistry registry = plugin.getEndermanRegistry();
        
        // Don't spawn if another enderman is already within 300 blocks
        if (registry.isAnyNear(px, py, pz, 300.0)) {
            return null;
        }
        
        // Compute look direction vector from yaw/pitch
        double yawRad = Math.toRadians(lookYaw);
        double lookX = -Math.sin(yawRad) * Math.cos(Math.toRadians(lookPitch));
        double lookZ = Math.cos(yawRad) * Math.cos(Math.toRadians(lookPitch));
        
        // Generate spawn position (behind the player)
        double dirX = -lookX, dirZ = -lookZ;
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len > 0) { dirX /= len; dirZ /= len; }
        
        double angleVariation = (random.nextDouble() - 0.5) * 0.8;
        double cos = Math.cos(angleVariation), sin = Math.sin(angleVariation);
        double finalDirX = dirX * cos - dirZ * sin;
        double finalDirZ = dirX * sin + dirZ * cos;
        
        double distance = 20.0 + random.nextDouble() * 20.0;
        double spawnX = px + finalDirX * distance;
        double spawnZ = pz + finalDirZ * distance;
        double spawnY = Math.min(py + 5.0, 40.0);
        
        // Get the overworld
        World world = plugin.getServer().getWorld("overworld");
        if (world == null) return null;
        
        // Scan downward for solid ground
        for (int by = (int)spawnY; by > 5; by--) {
            if (world.getBlockAt((int)spawnX, by - 1, (int)spawnZ).getType().isSolid()) {
                spawnY = by;
                break;
            }
        }
        spawnY = Math.min(spawnY, 35.0);
        
        // Validate spawn position
        if (!isValidSpawnPosition(world, spawnX, spawnY, spawnZ)) {
            return null;
        }
        
        // Spawn via the entity service
        Location spawnLoc = new Location(world, spawnX, spawnY, spawnZ);
        Entity spawned = HytaleServer.getEntityService().spawn(
            EndermanEntity.CAVE_DWELLER_TYPE, spawnLoc);
        
        if (spawned == null) {
            CaveNoisePlugin.getLogger().warn("Failed to spawn Enderman entity at ({}, {}, {})",
                spawnX, spawnY, spawnZ);
            return null;
        }
        
        // Wrap the native entity in our EndermanEntity
        EndermanEntity entity = new EndermanEntity(plugin, spawned, spawnX, spawnY, spawnZ);
        entity.setInvisible(true);
        entity.setTargetPlayerId(playerId.toString());
        registry.track(entity);
        
        CaveNoisePlugin.getLogger().info(
            "Spawned Enderman behind player {} at ({}, {}, {})",
            playerId, spawnX, spawnY, spawnZ
        );
        
        return entity;
    }
    
    /**
     * Check if a position is valid for spawning.
     * Requires: air above, solid ground below, no sky access, y < 40.
     */
    public boolean isValidSpawnPosition(World world, double x, double y, double z) {
        int bx = (int)x, by = (int)y, bz = (int)z;
        
        // Must have air at spawn level and 2 blocks above
        if (!world.getBlockAt(bx, by, bz).getType().isAir()) return false;
        if (!world.getBlockAt(bx, by + 1, bz).getType().isAir()) return false;
        if (!world.getBlockAt(bx, by + 2, bz).getType().isAir()) return false;
        
        // Must have solid ground below
        if (!world.getBlockAt(bx, by - 1, bz).getType().isSolid()) return false;
        
        // Must be underground (no sky access)
        int highestY = world.getHighestBlockYAt(bx, bz);
        if (by >= highestY) return false;
        
        // Must be deep enough (y < 40)
        if (by >= 40) return false;
        
        return true;
    }
}
