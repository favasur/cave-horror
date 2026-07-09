package com.favasur.cavehorror.entity.custom;

import com.favasur.cavehorror.CaveNoisePlugin;
import com.favasur.cavehorror.entity.EndermanEntity;
import com.favasur.cavehorror.entity.EndermanEntity.State;
import com.hytale.api.HytaleServer;
import com.hytale.api.world.Block;
import com.hytale.api.world.Material;
import com.hytale.api.world.Vector3f;
import com.hytale.api.world.World;

import java.util.Random;

/**
 * StalkGoal — entity follows player from 20-30 blocks while invisible.
 * Phases through walls, steals blocks, builds structures.
 * Transitions to ChaseGoal when stared at 10s or player gets within 15 blocks.
 * 
 * Uses Hytale WorldService for block queries (corridor detection, block stealing)
 * and AudioService for ambient sounds.
 */
public class EndermanStalkGoal {
    
    private final EndermanEntity enderman;
    private final CaveNoisePlugin plugin;
    private final Random random;
    
    private final double speedModifier = 0.5;
    
    private static final double MIN_STALK_DISTANCE = 20.0;
    private static final double MAX_STALK_DISTANCE = 30.0;
    private static final double CHASE_TRIGGER_DISTANCE = 15.0;
    private static final int STARE_TRIGGER_TICKS = 200;
    private static final int PHASE_COOLDOWN_TICKS = 60;
    
    private int playerStareTicks = 0;
    private int stealCooldown;
    private int phaseCooldown = 0;
    private int structureCooldown;
    
    // Corridor ambush tracking
    private double prevPlayerX, prevPlayerZ;
    private boolean hasPrevPlayerPos = false;
    private int runAwayStalkTimer = 0;
    private boolean readyToAmbush = false;
    private int ambushCooldown = 0;
    private static final int RUNAWAY_STALK_TIME = 12000;
    private static final int AMBUSH_COOLDOWN_TICKS = 100;
    
    private static class WallPos {
        final int x, y, z;
        WallPos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    }
    
    public EndermanStalkGoal(EndermanEntity enderman, CaveNoisePlugin plugin) {
        this.enderman = enderman;
        this.plugin = plugin;
        this.random = plugin.getRandom();
        this.stealCooldown = 1200 + random.nextInt(600);
        this.structureCooldown = 24000 + random.nextInt(12000);
    }
    
    public boolean canUse() {
        return enderman.isInvisible() && enderman.getState() != State.CHASING;
    }
    
    public void start() {
        enderman.setState(State.STALKING);
        enderman.setInvisible(true);
        enderman.setEyesVisible(false);
        CaveNoisePlugin.getLogger().info("Enderman started stalking a player.");
    }
    
    public void stop() {
        enderman.setState(State.IDLE);
    }
    
    /**
     * Main tick for stalking behavior.
     */
    public void tick(double targetX, double targetY, double targetZ,
                     double lookX, double lookZ, boolean playerIsSpectator) {
        if (playerIsSpectator) return;
        
        double dx = targetX - enderman.getX();
        double dz = targetZ - enderman.getZ();
        double distToPlayer = Math.sqrt(dx * dx + dz * dz);
        
        // === DISTANCE MAINTENANCE (tug-of-war: 20-30 blocks) ===
        if (distToPlayer < MIN_STALK_DISTANCE) {
            double awayX = enderman.getX() - targetX;
            double awayZ = enderman.getZ() - targetZ;
            double len = Math.sqrt(awayX * awayX + awayZ * awayZ);
            if (len > 0) { awayX /= len; awayZ /= len; }
            enderman.setVelocity(awayX * speedModifier, 0, awayZ * speedModifier);
            updateEntityVelocity(awayX * speedModifier, 0, awayZ * speedModifier);
        } else if (distToPlayer > MAX_STALK_DISTANCE) {
            double towardX = targetX - enderman.getX();
            double towardZ = targetZ - enderman.getZ();
            double len = Math.sqrt(towardX * towardX + towardZ * towardZ);
            if (len > 0) { towardX /= len; towardZ /= len; }
            enderman.setVelocity(towardX * speedModifier, 0, towardZ * speedModifier);
            updateEntityVelocity(towardX * speedModifier, 0, towardZ * speedModifier);
        } else {
            enderman.setVelocity(0, enderman.getVelocityY(), 0);
            updateEntityVelocity(0, enderman.getVelocityY(), 0);
        }
        
        // === STARE TRACKING ===
        boolean playerLooking = isPlayerLookingAt(targetX, targetZ, lookX, lookZ);
        if (playerLooking) {
            playerStareTicks++;
        } else {
            playerStareTicks = Math.max(0, playerStareTicks - 5);
        }
        
        if (playerStareTicks >= STARE_TRIGGER_TICKS || distToPlayer < CHASE_TRIGGER_DISTANCE) {
            transitionToChase();
            return;
        }
        
        // === WALL PHASING (teleport when stuck) ===
        phaseCooldown--;
        if (phaseCooldown <= 0 && distToPlayer > 10.0) {
            // Check navigation state via collision component
            boolean pathBlocked = false;
            if (enderman.getEntity() != null) {
                pathBlocked = enderman.getEntity().getPhysicsComponent().isCollidingHorizontally();
            }
            if (pathBlocked) {
                double ratio = 0.4;
                double tx = enderman.getX() + (targetX - enderman.getX()) * ratio;
                double tz = enderman.getZ() + (targetZ - enderman.getZ()) * ratio;
                tx += (random.nextDouble() - 0.5) * 4.0;
                tz += (random.nextDouble() - 0.5) * 4.0;
                enderman.teleportTo(tx, targetY, tz);
                phaseCooldown = PHASE_COOLDOWN_TICKS + random.nextInt(40);
            }
        }
        
        // === BLOCK STEALING (doors, glass panes, fence gates) ===
        stealCooldown--;
        if (stealCooldown <= 0) {
            stealBlockNearPlayer(targetX, targetY, targetZ);
            stealCooldown = 1200 + random.nextInt(600);
        }
        
        // === STRUCTURE BUILDING ===
        structureCooldown--;
        if (structureCooldown <= 0) {
            buildStructureNearPlayer(targetX, targetY, targetZ);
            structureCooldown = 24000 + random.nextInt(12000);
        }
        
        // === RUN-AWAY STALK TIMER & CORRIDOR AMBUSH ===
        if (hasPrevPlayerPos) {
            double playerDx = targetX - prevPlayerX;
            double playerDz = targetZ - prevPlayerZ;
            double playerSpeed = Math.sqrt(playerDx * playerDx + playerDz * playerDz);
            
            double toEntityX = enderman.getX() - targetX;
            double toEntityZ = enderman.getZ() - targetZ;
            double toEntityLen = Math.sqrt(toEntityX * toEntityX + toEntityZ * toEntityZ);
            
            boolean isRunningAway = distToPlayer > 20.0 && playerSpeed > 0.18;
            if (isRunningAway) {
                double dot = (toEntityX / toEntityLen) * (playerDx / playerSpeed) 
                           + (toEntityZ / toEntityLen) * (playerDz / playerSpeed);
                if (dot > 0.25) {
                    runAwayStalkTimer++;
                } else {
                    runAwayStalkTimer = Math.max(0, runAwayStalkTimer - 100);
                }
            } else {
                runAwayStalkTimer = Math.max(0, runAwayStalkTimer - 100);
            }
            
            boolean failingToKeepUp = distToPlayer > 30.0;
            if (runAwayStalkTimer >= RUNAWAY_STALK_TIME && failingToKeepUp && !readyToAmbush) {
                readyToAmbush = true;
                ambushCooldown = 20;
            }
            
            if (readyToAmbush) {
                ambushCooldown--;
                if (ambushCooldown <= 0) {
                    WallPos selectedWall = detectCorridorAhead(targetX, targetY, targetZ, 
                        playerDx, playerDz, playerSpeed);
                    
                    if (selectedWall != null && distToPlayer > 20.0) {
                        enderman.setState(EndermanEntity.State.HUNTING);
                        enderman.setTargetPosition(selectedWall.x, selectedWall.y, selectedWall.z);
                        
                        readyToAmbush = false;
                        runAwayStalkTimer = 0;
                        ambushCooldown = AMBUSH_COOLDOWN_TICKS + random.nextInt(40);
                        
                        CaveNoisePlugin.getLogger().info(
                            "Enderman ambush triggered! Emerging from wall at ({}, {}, {})",
                            selectedWall.x, selectedWall.y, selectedWall.z);
                    } else {
                        ambushCooldown = 10 + random.nextInt(10);
                    }
                }
            }
        }
        
        this.prevPlayerX = targetX;
        this.prevPlayerZ = targetZ;
        this.hasPrevPlayerPos = true;
        
        // === AMBIENT SOUNDS ===
        if (random.nextInt(200) == 0) {
            double sx = targetX + (random.nextDouble() - 0.5) * 50;
            double sz = targetZ + (random.nextDouble() - 0.5) * 50;
            HytaleServer.getAudioService().playSound(
                null, "cavehorror:enderman_ambient",
                new Vector3f((float)sx, (float)targetY, (float)sz), 2.0f, 1.0f
            );
        }
    }
    
    /**
     * Steal a block (door, glass pane, fence gate, or iron bars) near the player.
     */
    private void stealBlockNearPlayer(double px, double py, double pz) {
        World world = HytaleServer.getWorldService().getWorld("overworld");
        if (world == null) return;
        
        // Scan blocks in 5-block radius of player
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    int bx = (int)px + dx, by = (int)py + dy, bz = (int)pz + dz;
                    Block block = world.getBlockAt(bx, by, bz);
                    Material type = block.getType();
                    
                    // Only steal specific structural blocks
                    if (isStealableBlock(type)) {
                        // Remove the block (steal it)
                        world.setBlock(bx, by, bz, Material.AIR);
                        CaveNoisePlugin.getLogger().debug("Stole block at ({}, {}, {})", bx, by, bz);
                        return; // One steal per cooldown cycle
                    }
                }
            }
        }
    }
    
    /**
     * Build a small structure near the player (sand pillar, pyramid, or mossy dungeon).
     */
    private void buildStructureNearPlayer(double px, double py, double pz) {
        World world = HytaleServer.getWorldService().getWorld("overworld");
        if (world == null) return;
        
        int bx = (int)px + random.nextInt(20) - 10;
        int bz = (int)pz + random.nextInt(20) - 10;
        
        // Find ground level
        int by = world.getHighestBlockYAt(bx, bz);
        if (by < 5) return;
        
        int structureType = random.nextInt(3);
        switch (structureType) {
            case 0: // Sand pillar
                for (int h = 0; h < 3 + random.nextInt(3); h++) {
                    world.setBlock(bx, by + h, bz, Material.SAND);
                }
                break;
            case 1: // Small pyramid (cobblestone)
                for (int layer = 0; layer < 3; layer++) {
                    int size = 3 - layer;
                    for (int sx = -size; sx <= size; sx++) {
                        for (int sz = -size; sz <= size; sz++) {
                            world.setBlock(bx + sx, by + layer, bz + sz, Material.COBBLESTONE);
                        }
                    }
                }
                break;
            case 2: // Mossy dungeon corner
                world.setBlock(bx, by, bz, Material.MOSSY_COBBLESTONE);
                world.setBlock(bx + 1, by, bz, Material.COBBLESTONE);
                world.setBlock(bx, by, bz + 1, Material.COBBLESTONE);
                world.setBlock(bx, by + 1, bz, Material.MOSSY_COBBLESTONE);
                break;
        }
    }
    
    /**
     * Check if a block type is stealable (doors, glass panes, fence gates, iron bars).
     */
    private boolean isStealableBlock(Material type) {
        String name = type.name().toLowerCase();
        return name.endsWith("_door") 
            || name.endsWith("_glass_pane") 
            || name.endsWith("_fence_gate")
            || name.equals("iron_bars")
            || name.equals("glass");
    }
    
    /**
     * Detect if the player is in a corridor by scanning 20-30 blocks ahead.
     */
    private WallPos detectCorridorAhead(double targetX, double targetY, double targetZ,
                                          double playerDx, double playerDz, double playerSpeed) {
        if (playerSpeed < 0.01) return null;
        
        World world = HytaleServer.getWorldService().getWorld("overworld");
        if (world == null) return null;
        
        double dirX = playerDx / playerSpeed;
        double dirZ = playerDz / playerSpeed;
        
        double leftDirX = -dirZ, leftDirZ = dirX;
        double rightDirX = dirZ, rightDirZ = -dirX;
        
        for (int dist = 20; dist <= 30; dist++) {
            int aheadX = (int)Math.round(targetX + dirX * dist);
            int aheadZ = (int)Math.round(targetZ + dirZ * dist);
            int aheadY = (int)targetY;
            
            boolean wallLeft = false;
            boolean wallRight = false;
            Integer leftY = null, rightY = null;
            
            for (int side = 2; side <= 4; side++) {
                int lx = aheadX + (int)Math.round(leftDirX * side);
                int lz = aheadZ + (int)Math.round(leftDirZ * side);
                int rx = aheadX + (int)Math.round(rightDirX * side);
                int rz = aheadZ + (int)Math.round(rightDirZ * side);
                
                for (int h = 1; h <= 3; h++) {
                    if (!wallLeft && world.getBlockAt(lx, aheadY + h, lz).getType().isSolid()) {
                        wallLeft = true; leftY = aheadY + h;
                    }
                    if (!wallRight && world.getBlockAt(rx, aheadY + h, rz).getType().isSolid()) {
                        wallRight = true; rightY = aheadY + h;
                    }
                }
            }
            
            if (!wallLeft || !wallRight) return null;
            
            double distToLeft = leftY != null 
                ? distTo(enderman.getX(), enderman.getZ(), 
                    aheadX + (int)Math.round(leftDirX * 2), aheadZ + (int)Math.round(leftDirZ * 2)) 
                : Double.MAX_VALUE;
            double distToRight = rightY != null 
                ? distTo(enderman.getX(), enderman.getZ(),
                    aheadX + (int)Math.round(rightDirX * 2), aheadZ + (int)Math.round(rightDirZ * 2)) 
                : Double.MAX_VALUE;
            
            if (distToLeft < distToRight && leftY != null) {
                return new WallPos((int)targetX + (int)Math.round(leftDirX * 2), 
                    leftY, (int)targetZ + (int)Math.round(leftDirZ * 2));
            } else if (rightY != null) {
                return new WallPos((int)targetX + (int)Math.round(rightDirX * 2), 
                    rightY, (int)targetZ + (int)Math.round(rightDirZ * 2));
            }
        }
        return null;
    }
    
    private void updateEntityVelocity(double vx, double vy, double vz) {
        if (enderman.getEntity() != null) {
            enderman.getEntity().setVelocity(
                new Vector3f((float)vx, (float)vy, (float)vz));
        }
    }
    
    private double distTo(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1, dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }
    
    private void transitionToChase() {
        enderman.setState(State.CHASING);
        enderman.setInvisible(false);
        enderman.setAggro(true);
        playerStareTicks = 0;
        CaveNoisePlugin.getLogger().info("Enderman transitioning to chase mode.");
    }
    
    private boolean isPlayerLookingAt(double px, double pz, double lookX, double lookZ) {
        double ex = enderman.getX(), ez = enderman.getZ();
        double dx = ex - px, dz = ez - pz;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len == 0) return false;
        dx /= len; dz /= len;
        
        double lookLen = Math.sqrt(lookX * lookX + lookZ * lookZ);
        if (lookLen == 0) return false;
        double nlx = lookX / lookLen, nlz = lookZ / lookLen;
        
        return dx * nlx + dz * nlz > 0.819;
    }
    
    public boolean isPlayerStaring() { return playerStareTicks > 0; }
}
