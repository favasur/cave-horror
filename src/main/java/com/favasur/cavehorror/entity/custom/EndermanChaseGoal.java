package com.favasur.cavehorror.entity.custom;

import com.favasur.cavehorror.entity.EndermanEntity;
import com.favasur.cavehorror.entity.EndermanEntity.State;

import java.util.Random;

/**
 * ChaseGoal — aggressive pursuit AI when the entity is visible.
 * Activated when player stares too long or gets too close.
 * 
 * Features:
 * - Fast pursuit pathfinding toward target
 * - Weeping-angel freeze mechanic (stops when looked at, >4 blocks)
 * - Torch destruction on contact
 * - Melee attacks with shield breaking
 * 
 * Ported from Minecraft EndermanChaseGoal.java
 */
public class EndermanChaseGoal {
    
    private final EndermanEntity enderman;
    private final Random random = new Random();
    
    private static final double CHASE_SPEED = 0.85;
    private static final double ATTACK_REACH = 3.0;
    private static final int ATTACK_COOLDOWN = 20;
    private static final double FREEZE_DISTANCE = 4.0;
    
    private int attackCooldown = 0;
    private int pathRecalcCooldown = 0;
    
    public EndermanChaseGoal(EndermanEntity enderman) {
        this.enderman = enderman;
    }
    
    public boolean canUse() {
        return enderman.getState() == State.CHASING && !enderman.isInvisible();
    }
    
    public void start() {
        enderman.setState(State.CHASING);
        enderman.setInvisible(false);
        enderman.setAggro(true);
        enderman.setEyesVisible(true);
    }
    
    public void stop() {
        enderman.setState(State.IDLE);
        enderman.setAggro(false);
    }
    
    /**
     * Main chase tick.
     * @param playerLooking Whether player has this entity in their look cone
     */
    public void tick(double targetX, double targetY, double targetZ,
                     boolean playerIsSpectator, boolean playerLooking) {
        if (playerIsSpectator) return;
        
        double dx = targetX - enderman.getX();
        double dz = targetZ - enderman.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        
        // === WEEPING ANGEL FREEZE ===
        // If player looks at entity from >4 blocks, freeze in place
        if (playerLooking && dist > FREEZE_DISTANCE) {
            enderman.setVelocity(0, 0, 0);
            return;
        }
        
        // === ATTACK ===
        attackCooldown--;
        if (dist < ATTACK_REACH && attackCooldown <= 0) {
            // HYTALE API: Deal damage to target player
            // HytaleDamageAPI.applyDamage(targetId, 6.0f, DamageType.MOB_ATTACK);
            // HYTALE API: Break shield if player has one
            attackCooldown = ATTACK_COOLDOWN;
            // HYTALE API: HytaleSoundAPI.playSound("cavehorror:enderman_attack",
            //     enderman.getX(), enderman.getY(), enderman.getZ(), 3.0f);
            return;
        }
        
        // === MOVEMENT ===
        pathRecalcCooldown--;
        if (pathRecalcCooldown <= 0) {
            double speed = CHASE_SPEED * (dist > 32.0 ? 1.5 : 1.0);
            double towardX = dx / dist;
            double towardZ = dz / dist;
            enderman.setVelocity(towardX * speed, 0, towardZ * speed);
            pathRecalcCooldown = 4 + random.nextInt(7);
        }
        
        // === TORCH DESTRUCTION ===
        // HYTALE API: Check and destroy torch blocks in 1-block radius
        // for each block at (int)enderman.getX() ± 1, (int)enderman.getY() ± 1, (int)enderman.getZ() ± 1:
        //   if block is torch → world.setBlock(pos, AIR)
        
        // === SOUNDS ===
        if (random.nextInt(80) == 0) {
            // HYTALE API: HytaleSoundAPI.playSound("cavehorror:enderman_scream",
            //     enderman.getX(), enderman.getY(), enderman.getZ(), 3.0f);
        }
    }
}
