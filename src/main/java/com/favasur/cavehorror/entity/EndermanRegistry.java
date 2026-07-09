package com.favasur.cavehorror.entity;

import com.favasur.cavehorror.CaveNoisePlugin;
import com.hytale.api.HytaleServer;
import com.hytale.api.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks all active Enderman entities across the world.
 * Uses HytaleServer.getEntityRegistry() for registration and
 * HytaleServer.getEntityService() for spawning.
 */
public class EndermanRegistry {

    private final CaveNoisePlugin plugin;
    private final List<EndermanEntity> activeEntities = new CopyOnWriteArrayList<>();
    
    public EndermanRegistry(CaveNoisePlugin plugin) {
        this.plugin = plugin;
    }
    
    public void register() {
        // Entity type is registered in CaveNoisePlugin.onEnable() 
        // via HytaleServer.getEntityRegistry().register(definition)
        CaveNoisePlugin.getLogger().info("Registered Cave Dweller entity type: {}",
            EndermanEntity.ENTITY_TYPE_ID);
    }
    
    public EndermanEntity spawnAt(double x, double y, double z) {
        // Use the entity service to spawn — returns a native Entity
        // The actual spawning is handled by EntitySpawnSystem to include validation
        CaveNoisePlugin.getLogger().warn("Direct spawnAt() called — use EntitySpawnSystem instead.");
        return null;
    }
    
    public void track(EndermanEntity entity) {
        if (!activeEntities.contains(entity)) {
            activeEntities.add(entity);
        }
    }
    
    public void untrack(EndermanEntity entity) {
        activeEntities.remove(entity);
    }
    
    public List<EndermanEntity> getActiveEntities() {
        return new ArrayList<>(activeEntities);
    }
    
    public boolean isAnyNear(double x, double y, double z, double range) {
        for (EndermanEntity entity : activeEntities) {
            double dx = entity.getX() - x;
            double dy = entity.getY() - y;
            double dz = entity.getZ() - z;
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) < range) return true;
        }
        return false;
    }
    
    public void despawnAll() {
        for (EndermanEntity entity : activeEntities) {
            entity.despawn();
        }
        activeEntities.clear();
    }
    
    public void cleanDead() {
        activeEntities.removeIf(e -> !e.isAlive());
    }
    
    public CaveNoisePlugin getPlugin() { return plugin; }
}
