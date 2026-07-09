package com.favasur.cavehorror.entity;

import com.favasur.cavehorror.CaveNoisePlugin;
import com.favasur.cavehorror.entity.custom.*;

import java.util.Random;

/**
 * EndermanEntity — the core stalker entity with wired AI goal dispatch.
 * 
 * AI Goals (created in initAI()):
 * - EndermanStalkGoal: Follows player 20-30 blocks back, invisible, phasing
 * - EndermanChaseGoal: Aggressive pursuit with weeping angel freeze
 * - EndermanBreakInvisGoal: Reveals entity when player looks directly at it
 * - EndermanTargetSeesMeGoal: Detects player line-of-sight + staring
 * - EndermanTargetTooCloseGoal: Detects player proximity
 * 
 * State machine: IDLE → STALKING → CHASING → FLEEING → HUNTING
 * 
 * HYTALE API: Integrate with com.hytale.server.entity.Entity ECS system.
 * Register via: EntityTypeRegistry.get().register("cave_dweller", EndermanEntity::new);
 */
public class EndermanEntity {

    public enum State { STALKING, CHASING, FLEEING, HUNTING, IDLE }
    
    // ---- AI Goals (initialized in initAI()) ----
    private EndermanStalkGoal stalkGoal;
    private EndermanChaseGoal chaseGoal;
    private EndermanBreakInvisGoal breakInvisGoal;
    private EndermanTargetSeesMeGoal targetSeesMeGoal;
    private EndermanTargetTooCloseGoal targetTooCloseGoal;
    
    private final CaveNoisePlugin plugin;
    
    // Position & velocity
    private double x, y, z;
    private double velocityX, velocityY, velocityZ;
    
    // State
    private State state = State.IDLE;
    private boolean alive = true;
    private boolean invisible = true;
    private boolean eyesVisible = false;
    private boolean aggro = false;
    private boolean spotted = false;
    
    // HYTALE API: These map to ECS components:
    // - invisible → VisualComponent.setVisible(false)
    // - eyesVisible → custom shader component
    // - aggro → AIComponent.setBehavior(AIBehavior.AGGRESSIVE)
    // - state → AIStateComponent.setCurrentState(State.STALKING)
    
    private String targetPlayerId;
    private int tickCount = 0;
    private Random random;
    
    public EndermanEntity(CaveNoisePlugin plugin, double x, double y, double z) {
        this.plugin = plugin;
        this.x = x; this.y = y; this.z = z;
        this.random = plugin.getRandom();
    }
    
    /**
     * Create AI goal instances. Called after spawning.
     */
    public void initAI() {
        this.stalkGoal = new EndermanStalkGoal(this, plugin);
        this.chaseGoal = new EndermanChaseGoal(this);
        this.breakInvisGoal = new EndermanBreakInvisGoal(this);
        this.targetSeesMeGoal = new EndermanTargetSeesMeGoal(this);
        this.targetTooCloseGoal = new EndermanTargetTooCloseGoal(this, 12.0f);
        
        // Start in stalking mode
        this.stalkGoal.start();
    }
    
    /**
     * AI tick — dispatches to the active goal based on current state.
     * Called from CaveNoisePlugin.serverTick() with target player data.
     */
    public void tickAI(double targetX, double targetY, double targetZ,
                       double lookX, double lookZ, boolean playerIsSpectator,
                       boolean playerIsLooking) {
        if (!alive || playerIsSpectator) return;
        
        // BreakInvisGoal check: if player looks at invisible entity, reveal it
        if (invisible && state == State.STALKING) {
            if (breakInvisGoal.canUse(targetX, targetY, targetZ, lookX, lookZ)) {
                breakInvisGoal.start();
                return;
            }
        }
        
        // Dispatch to active goal
        switch (state) {
            case STALKING:
                // StalkGoal handles its own transition to CHASING
                stalkGoal.tick(targetX, targetY, targetZ, lookX, lookZ, playerIsSpectator);
                break;
                
            case CHASING:
                chaseGoal.tick(targetX, targetY, targetZ, playerIsSpectator, playerIsLooking);
                break;
                
            case FLEEING:
                tickFleeing(targetX, targetY, targetZ);
                break;
                
            case HUNTING:
                tickHunting(targetX, targetY, targetZ);
                break;
                
            case IDLE:
                // HYTALE API: Play idle animation, wait for targeting goal
                break;
        }
    }
    
    /**
     * Physics tick — applies gravity, velocity, and ground collision.
     * Called separately from AI tick so physics runs even if AI is paused.
     * 
     * HYTALE API: This can be replaced by Hytale's own physics system.
     * If the entity is registered with ECS physics components,
     * Hytale handles gravity and collision automatically.
     */
    public void tickPhysics() {
        if (!alive) return;
        tickCount++;
        
        // Apply gravity if visible and not phasing
        if (!invisible) {
            velocityY -= 0.02; // Gravity acceleration
            // HYTALE API: Use PhysicsComponent.setGravity(Vector3f(0, -0.02, 0));
        }
        
        // Apply velocity
        x += velocityX;
        y += velocityY;
        z += velocityZ;
        
        // HYTALE API: Ground collision via world height
        // World world = Server.get().getWorld();
        // int bx = (int)x, bz = (int)z;
        // for (int by = (int)y; by > 5; by--) {
        //     Block block = world.getBlockAt(bx, by, bz);
        //     if (block.isSolid()) {
        //         y = by + 1; velocityY = 0; break;
        //     }
        // }
        
        // HYTALE API: Wall collision for noPhysics phasing
        // if (invisible) {
        //     entity.getComponent(CollisionComponent.class).setEnabled(false);
        // } else {
        //     entity.getComponent(CollisionComponent.class).setEnabled(true);
        // }
        
        // Auto-despawn after 1 hour
        if (tickCount > 72000) despawn();
    }
    
    // ---- BEHAVIOR HELPERS ----
    
    private void tickFleeing(double tx, double ty, double tz) {
        // Run away from target player
        double dx = x - tx, dz = z - tz;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0) {
            setVelocity(dx / len * 1.2, 0, dz / len * 1.2);
        }
        // After 5 seconds, teleport away and return to stalking
        if (tickCount % 100 == 0) {
            teleportTo(x + (random.nextDouble() - 0.5) * 30, y, z + (random.nextDouble() - 0.5) * 30);
            setInvisible(true);
            stalkGoal.start();
        }
    }
    
    private void tickHunting(double tx, double ty, double tz) {
        // Ambush mode — emerge from walls (placeholder for wall emergence mechanic)
        // HYTALE API: Check if player is in a corridor
        // If so, teleport to wall position and trigger emerge animation
    }
    
    // ---- POSITION HELPERS ----
    
    public static double[] generateSpawnPosition(
            double playerX, double playerY, double playerZ,
            double lookX, double lookZ) {
        double dirX = -lookX, dirZ = -lookZ;
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len > 0) { dirX /= len; dirZ /= len; }
        
        double angleVariation = (Math.random() - 0.5) * 0.8;
        double cos = Math.cos(angleVariation), sin = Math.sin(angleVariation);
        double finalDirX = dirX * cos - dirZ * sin;
        double finalDirZ = dirX * sin + dirZ * cos;
        
        double distance = 20.0 + Math.random() * 20.0;
        double spawnX = playerX + finalDirX * distance;
        double spawnZ = playerZ + finalDirZ * distance;
        double spawnY = Math.min(playerY + 5.0, 40.0);
        
        // HYTALE API: Scan downward for solid ground
        // World world = Server.get().getWorld();
        // for (int by = (int)spawnY; by > 5; by--) {
        //     if (world.getBlockAt((int)spawnX, by - 1, (int)spawnZ).isSolid()) {
        //         spawnY = by; break;
        //     }
        // }
        
        return new double[]{spawnX, Math.min(spawnY, 35.0), spawnZ};
    }
    
    public void teleportTo(double tx, double ty, double tz) {
        this.x = tx; this.y = ty; this.z = tz;
        this.velocityX = 0; this.velocityY = 0; this.velocityZ = 0;
        // HYTALE API: entity.getComponent(PositionComponent.class).setPosition(tx, ty, tz);
    }
    
    // ---- STATE ACCESSORS ----
    
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    
    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }
    
    public boolean isInvisible() { return invisible; }
    public void setInvisible(boolean invisible) { this.invisible = invisible; }
    
    public boolean areEyesVisible() { return eyesVisible; }
    public void setEyesVisible(boolean visible) { this.eyesVisible = visible; }
    
    public boolean isAggro() { return aggro; }
    public void setAggro(boolean aggro) { this.aggro = aggro; }
    
    public boolean isSpotted() { return spotted; }
    public void setSpotted(boolean spotted) { this.spotted = spotted; }
    
    public String getTargetPlayerId() { return targetPlayerId; }
    public void setTargetPlayerId(String playerId) { this.targetPlayerId = playerId; }
    
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    
    public double getVelocityX() { return velocityX; }
    public double getVelocityY() { return velocityY; }
    public double getVelocityZ() { return velocityZ; }
    
    public void setPosition(double x, double y, double z) {
        this.x = x; this.y = y; this.z = z;
    }
    public void setVelocity(double vx, double vy, double vz) {
        this.velocityX = vx; this.velocityY = vy; this.velocityZ = vz;
    }
    
    // ---- AI GOAL ACCESSORS (used by CaveNoisePlugin) ----
    
    public EndermanStalkGoal getStalkGoal() { return stalkGoal; }
    public EndermanChaseGoal getChaseGoal() { return chaseGoal; }
    public EndermanBreakInvisGoal getBreakInvisGoal() { return breakInvisGoal; }
    public EndermanTargetSeesMeGoal getTargetSeesMeGoal() { return targetSeesMeGoal; }
    public EndermanTargetTooCloseGoal getTargetTooCloseGoal() { return targetTooCloseGoal; }
    
    public void setPersistent(boolean persistent) {
        // HYTALE API: entity.getComponent(PersistenceComponent.class).setPersistent(persistent);
    }
    
    public void despawn() {
        this.alive = false;
        plugin.getEndermanRegistry().untrack(this);
        // HYTALE API: Server.get().getWorld().removeEntity(this);
    }
    
    public double distanceTo(double px, double py, double pz) {
        double dx = px - x, dy = py - y, dz = pz - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    public CaveNoisePlugin getPlugin() { return plugin; }
}
