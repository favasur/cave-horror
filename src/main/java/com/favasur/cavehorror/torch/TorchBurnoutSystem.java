package com.favasur.cavehorror.torch;

import com.favasur.cavehorror.CaveNoisePlugin;

import java.util.Random;

/**
 * TorchBurnoutSystem — manages torch extinguishing mechanics.
 * 
 * When the enderman entity is near a player deep underground (30+ blocks below surface),
 * torches within a 20-40 block radius are randomly extinguished.
 * 
 * Also handles the "Lit Torch" item — a torch that stays lit even when placed
 * in the entity's extinguishing aura, giving players a way to fight back.
 * 
 * Ported from Minecraft:
 * - LitTorch.java (custom torch block)
 * - ModTorchConfig.java (configuration)
 * - EndermanEntity.extinguishNearbyTorches()
 * - EndermanChaseGoal torch destruction
 */
public class TorchBurnoutSystem {

    private final CaveNoisePlugin plugin;
    private final Random random;
    
    // Config values with defaults
    private int extinguishRadius = 20;
    private int extinguishTickInterval = 10;
    private float extinguishChance = 0.3f;
    
    public TorchBurnoutSystem(CaveNoisePlugin plugin) {
        this.plugin = plugin;
        this.random = plugin.getRandom();
    }
    
    /**
     * Extinguish torches near a given position.
     * Called every 10 ticks from the entity's tick() method.
     * 
     * @param cx Center X of the extinguishing aura
     * @param cy Center Y
     * @param cz Center Z
     * @param playerDepth How far below the surface the player is
     */
    public void extinguishNearby(double cx, double cy, double cz, int playerDepth) {
        // Only extinguish when player is 30+ blocks below surface
        if (playerDepth < 30) return;
        
        // Vary the radius each time for organic feel
        int effectiveRadius = extinguishRadius + random.nextInt(extinguishRadius);
        
        // Hytale API: Scan blocks in radius
        for (int dx = -effectiveRadius; dx <= effectiveRadius; dx++) {
            for (int dz = -effectiveRadius; dz <= effectiveRadius; dz++) {
                if (dx * dx + dz * dz > effectiveRadius * effectiveRadius) continue;
                
                int bx = (int)cx + dx;
                int bz = (int)cz + dz;
                int by = (int)cy;
                
                // Check block at position
                // Hytale API: if (block is torch or wall_torch) destroy
                
                // Check block at y-1
                // Hytale API: if (block is torch) destroy
            }
        }
    }
    
    /**
     * Check if a torch should remain lit (Lit Torch items are immune).
     */
    public boolean isTorchImmune(int bx, int by, int bz) {
        // Hytale API: Check if block at position is a "lit_torch"
        // If so, it never extinguishes by the entity's aura
        // (Player-crafted lit torches are a counter-measure)
        return false; // Placeholder
    }
    
    public int getExtinguishRadius() { return extinguishRadius; }
    public void setExtinguishRadius(int radius) { this.extinguishRadius = radius; }
    
    public int getExtinguishTickInterval() { return extinguishTickInterval; }
    public void setExtinguishTickInterval(int interval) { this.extinguishTickInterval = interval; }
    
    public float getExtinguishChance() { return extinguishChance; }
    public void setExtinguishChance(float chance) { this.extinguishChance = chance; }
}
