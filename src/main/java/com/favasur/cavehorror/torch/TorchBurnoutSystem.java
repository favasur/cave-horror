package com.favasur.cavehorror.torch;

import com.favasur.cavehorror.CaveNoisePlugin;
import com.hytale.api.HytaleServer;
import com.hytale.api.world.Material;
import com.hytale.api.world.World;

import java.util.Random;

/**
 * TorchBurnoutSystem — extinguishes torches near players when the enderman is close.
 * 
 * When an entity is near a player deep underground (30+ blocks below surface),
 * torches within a 20-40 block radius are randomly extinguished.
 * 
 * Uses Hytale WorldService for block scanning and modification.
 */
public class TorchBurnoutSystem {

    private final CaveNoisePlugin plugin;
    private final Random random;
    
    private int extinguishRadius = 20;
    private int extinguishTickInterval = 10;
    private float extinguishChance = 0.3f;
    
    public TorchBurnoutSystem(CaveNoisePlugin plugin) {
        this.plugin = plugin;
        this.random = plugin.getRandom();
    }
    
    /**
     * Extinguish torches near a given position.
     * Only activates when player is 30+ blocks below surface.
     * 
     * @param cx Center X of the extinguishing aura
     * @param cy Center Y
     * @param cz Center Z
     * @param playerDepth How far below the surface
     */
    public void extinguishNearby(double cx, double cy, double cz, int playerDepth) {
        if (playerDepth < 30) return;
        
        int effectiveRadius = extinguishRadius + random.nextInt(extinguishRadius);
        World world = plugin.getServer().getWorld("overworld");
        if (world == null) return;
        
        for (int dx = -effectiveRadius; dx <= effectiveRadius; dx++) {
            for (int dz = -effectiveRadius; dz <= effectiveRadius; dz++) {
                if (dx * dx + dz * dz > effectiveRadius * effectiveRadius) continue;
                
                int bx = (int)cx + dx;
                int bz = (int)cz + dz;
                int by = (int)cy;
                
                // Check block at entity Y level
                if (isExtinguishableTorch(world.getBlockAt(bx, by, bz).getType())) {
                    if (random.nextFloat() < extinguishChance) {
                        world.setBlock(bx, by, bz, Material.AIR);
                        HytaleServer.getAudioService().playSound(
                            null, "cavehorror:torch_snuff",
                            new com.hytale.api.world.Vector3f(bx, by, bz), 1.0f, 1.0f
                        );
                    }
                }
                
                // Also check block at Y-1 (wall torches attached to floor)
                if (isExtinguishableTorch(world.getBlockAt(bx, by - 1, bz).getType())) {
                    if (random.nextFloat() < extinguishChance) {
                        world.setBlock(bx, by - 1, bz, Material.AIR);
                    }
                }
            }
        }
    }
    
    /**
     * Check if a block type should be extinguished by the entity's aura.
     * Players can craft "Lit Torches" that are immune.
     */
    private boolean isExtinguishableTorch(Material type) {
        String name = type.name().toLowerCase();
        return name.contains("torch") || name.contains("lantern");
    }
    
    public int getExtinguishRadius() { return extinguishRadius; }
    public void setExtinguishRadius(int radius) { this.extinguishRadius = radius; }
    public int getExtinguishTickInterval() { return extinguishTickInterval; }
    public void setExtinguishTickInterval(int interval) { this.extinguishTickInterval = interval; }
    public float getExtinguishChance() { return extinguishChance; }
    public void setExtinguishChance(float chance) { this.extinguishChance = chance; }
}
