package com.favasur.cavehorror.entity.custom;

import com.favasur.cavehorror.entity.EndermanEntity;
import com.favasur.cavehorror.entity.EndermanEntity.State;
import com.hytale.api.HytaleServer;
import com.hytale.api.world.Material;
import com.hytale.api.world.Vector3f;
import com.hytale.api.world.World;
import com.hytale.api.entity.damage.DamageSource;

import java.util.Random;

/**
 * ChaseGoal — aggressive pursuit AI when the entity is visible.
 * Features: weeping-angel freeze, melee attacks, torch destruction.
 * 
 * Uses Hytale AudioService for sounds, WorldService for block destruction,
 * and Entity damage API for attacks.
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
     */
    public void tick(double targetX, double targetY, double targetZ,
                     boolean playerIsSpectator, boolean playerLooking) {
        if (playerIsSpectator) return;
        
        double dx = targetX - enderman.getX();
        double dz = targetZ - enderman.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        
        // === WEEPING ANGEL FREEZE ===
        if (playerLooking && dist > FREEZE_DISTANCE) {
            if (enderman.getEntity() != null) {
                enderman.getEntity().setVelocity(new Vector3f(0, 0, 0));
            }
            return;
        }
        
        // === ATTACK ===
        attackCooldown--;
        if (dist < ATTACK_REACH && attackCooldown <= 0) {
            // Deal damage to target player
            com.hytale.api.player.Player target = getTargetPlayer();
            if (target != null) {
                target.damage(6.0, DamageSource.mobAttack(enderman.getEntity()));
                
                // Break shield if player has one
                if (target.isBlocking()) {
                    target.setBlocking(false);
                }
            }
            
            HytaleServer.getAudioService().playSound(
                null, "cavehorror:enderman_scream",
                new Vector3f((float)enderman.getX(), (float)enderman.getY(), (float)enderman.getZ()),
                3.0f, 1.0f
            );
            attackCooldown = ATTACK_COOLDOWN;
            return;
        }
        
        // === MOVEMENT ===
        pathRecalcCooldown--;
        if (pathRecalcCooldown <= 0) {
            double speed = CHASE_SPEED * (dist > 32.0 ? 1.5 : 1.0);
            double towardX = dx / dist;
            double towardZ = dz / dist;
            if (enderman.getEntity() != null) {
                enderman.getEntity().setVelocity(
                    new Vector3f((float)(towardX * speed), 0, (float)(towardZ * speed)));
            }
            pathRecalcCooldown = 4 + random.nextInt(7);
        }
        
        // === TORCH DESTRUCTION ===
        destroyNearbyTorches();
        
        // === SOUNDS ===
        if (random.nextInt(80) == 0) {
            HytaleServer.getAudioService().playSound(
                null, "cavehorror:enderman_scream",
                new Vector3f((float)enderman.getX(), (float)enderman.getY(), (float)enderman.getZ()),
                3.0f, 1.0f
            );
        }
    }
    
    /**
     * Destroy torches within 1-block radius of the entity.
     */
    private void destroyNearbyTorches() {
        World world = HytaleServer.getWorldService().getWorld("overworld");
        if (world == null) return;
        
        int bx = (int)enderman.getX();
        int by = (int)enderman.getY();
        int bz = (int)enderman.getZ();
        
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Material type = world.getBlockAt(bx + dx, by + dy, bz + dz).getType();
                    if (isTorch(type)) {
                        world.setBlock(bx + dx, by + dy, bz + dz, Material.AIR);
                        HytaleServer.getAudioService().playSound(
                            null, "cavehorror:torch_snuff",
                            new Vector3f(bx + dx, by + dy, bz + dz), 1.0f, 1.0f
                        );
                    }
                }
            }
        }
    }
    
    private boolean isTorch(Material type) {
        String name = type.name().toLowerCase();
        return name.contains("torch") || name.contains("lantern");
    }
    
    /**
     * Get the target player for this entity by UUID.
     */
    private com.hytale.api.player.Player getTargetPlayer() {
        String targetId = enderman.getTargetPlayerId();
        if (targetId == null) return null;
        for (com.hytale.api.player.Player player : 
             HytaleServer.getPlayerService().getOnlinePlayers()) {
            if (player.getUniqueId().toString().equals(targetId)) {
                return player;
            }
        }
        return null;
    }
}
