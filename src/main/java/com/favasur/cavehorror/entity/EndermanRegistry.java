package com.favasur.cavehorror.entity;

import com.favasur.cavehorror.CaveNoisePlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks all active Enderman entities across the world.
 * Handles registration, proximity checks, and mass despawn.
 */
public class EndermanRegistry {

    private final CaveNoisePlugin plugin;
    private final List<EndermanEntity> activeEntities = new CopyOnWriteArrayList<>();
    
    public EndermanRegistry(CaveNoisePlugin plugin) {
        this.plugin = plugin;
    }
    
    public void register() {
        // HYTALE API: EntityAPI.register("cave_dweller", EndermanEntity.class, ...)
        CaveNoisePlugin.getLogger().info("Registered Cave Dweller entity type.");
    }
    
    public EndermanEntity spawnAt(double x, double y, double z) {
        EndermanEntity entity = new EndermanEntity(plugin, x, y, z);
        entity.setInvisible(true);
        entity.setPersistent(true);
        // HYTALE API: HytaleAPI.getWorld().spawnEntity(entity);
        activeEntities.add(entity);
        return entity;
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
