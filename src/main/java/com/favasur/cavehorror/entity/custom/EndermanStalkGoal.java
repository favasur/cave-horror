package com.favasur.cavehorror.entity.custom;

import com.favasur.cavehorror.CaveNoisePlugin;
import com.favasur.cavehorror.entity.EndermanEntity;
import com.favasur.cavehorror.entity.EndermanEntity.State;

import java.util.Random;

/**
 * StalkGoal — entity follows player from 20-30 blocks while invisible.
 * Phases through walls, steals blocks, builds structures.
 * Transitions to ChaseGoal when stared at 10s or player gets within 15 blocks.
 * 
 * Ported from Minecraft EndermanStalkGoal.java
 */
public class EndermanStalkGoal {
    
    private final EndermanEntity enderman;
    private final CaveNoisePlugin plugin;
    private final Random random;
    
    // Speed modifier for movement
    private final double speedModifier = 0.5;
    
    // Distance parameters
    private static final double MIN_STALK_DISTANCE = 20.0;
    private static final double MAX_STALK_DISTANCE = 30.0;
    private static final double CHASE_TRIGGER_DISTANCE = 15.0;
    private static final int STARE_TRIGGER_TICKS = 200;
    private static final int PHASE_COOLDOWN_TICKS = 60;
    
    private int playerStareTicks = 0;
    private int stealCooldown;
    private int phaseCooldown = 0;
    private int structureCooldown;
    
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
     * @param targetX, targetY, targetZ Player's position
     * @param lookX, lookZ Player's look direction (normalized)
     * @param playerIsSpectator Whether player is spectating
     */
    public void tick(double targetX, double targetY, double targetZ,
                     double lookX, double lookZ, boolean playerIsSpectator) {
        if (playerIsSpectator) return;
        
        double dx = targetX - enderman.getX();
        double dz = targetZ - enderman.getZ();
        double distToPlayer = Math.sqrt(dx * dx + dz * dz);
        
        // === DISTANCE MAINTENANCE (tug-of-war: 20-30 blocks) ===
        if (distToPlayer < MIN_STALK_DISTANCE) {
            // Back away
            double awayX = enderman.getX() - targetX;
            double awayZ = enderman.getZ() - targetZ;
            double len = Math.sqrt(awayX * awayX + awayZ * awayZ);
            if (len > 0) { awayX /= len; awayZ /= len; }
            enderman.setVelocity(awayX * speedModifier, 0, awayZ * speedModifier);
        } else if (distToPlayer > MAX_STALK_DISTANCE) {
            // Move closer
            double towardX = targetX - enderman.getX();
            double towardZ = targetZ - enderman.getZ();
            double len = Math.sqrt(towardX * towardX + towardZ * towardZ);
            if (len > 0) { towardX /= len; towardZ /= len; }
            enderman.setVelocity(towardX * speedModifier, 0, towardZ * speedModifier);
        } else {
            // Hold position
            enderman.setVelocity(0, enderman.getVelocityY(), 0);
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
            // HYTALE API: Check navigation state — if path blocked, teleport closer
            boolean pathBlocked = false;
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
        
        // === BLOCK STEALING ===
        stealCooldown--;
        if (stealCooldown <= 0) {
            // HYTALE API: Find and break a stealable block (doors, glass panes, etc.)
            stealCooldown = 1200 + random.nextInt(600);
        }
        
        // === STRUCTURE BUILDING ===
        structureCooldown--;
        if (structureCooldown <= 0) {
            // HYTALE API: Build a small structure (pillar, pyramid, dungeon)
            structureCooldown = 24000 + random.nextInt(12000);
        }
        
        // === AMBIENT SOUNDS ===
        if (random.nextInt(200) == 0) {
            double sx = targetX + (random.nextDouble() - 0.5) * 50;
            double sz = targetZ + (random.nextDouble() - 0.5) * 50;
            // HYTALE API: HytaleSoundAPI.playSound("cavehorror:enderman_ambient", sx, targetY, sz, 2.0f);
        }
    }
    
    private void transitionToChase() {
        enderman.setState(State.CHASING);
        enderman.setInvisible(false);
        enderman.setAggro(true);
        playerStareTicks = 0;
        CaveNoisePlugin.getLogger().info("Enderman transitioning to chase mode.");
    }
    
    /**
     * FOV-based check if the player is looking toward the entity.
     * Uses ~70 degree field of view (cos(35°) ≈ 0.819).
     */
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
