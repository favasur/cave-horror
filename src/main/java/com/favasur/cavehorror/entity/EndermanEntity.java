package com.favasur.cavehorror.entity;

import com.favasur.cavehorror.CaveNoisePlugin;
import com.favasur.cavehorror.entity.custom.*;
import com.hytale.api.HytaleServer;
import com.hytale.api.entity.Entity;
import com.hytale.api.entity.EntityType;
import com.hytale.api.world.Location;
import com.hytale.api.world.Material;
import com.hytale.api.world.Vector3f;
import com.hytale.api.world.World;
import com.hytale.api.entity.EntityDefinition;
import com.hytale.api.entity.EntityCategory;
import com.hytale.api.entity.EntityAttribute;

import java.util.Random;

/**
 * EndermanEntity — the core stalker entity with wired AI goal dispatch.
 * 
 * Wraps a Hytale native Entity. AI is managed through custom goal classes
 * dispatched by state machine: IDLE → STALKING → CHASING → FLEEING → HUNTING.
 * 
 * == HYTALE API INTEGRATION ==
 * - Registered via EndermanEntityDefinition with EntityRegistry
 * - Native Entity from HytaleServer.getEntityService().spawn()
 * - Uses WorldService for block queries, AudioService for sound effects
 * - ParticleService for mold/eye particles
 */
public class EndermanEntity {

    public enum State { STALKING, CHASING, FLEEING, HUNTING, IDLE }
    
    // Hytale entity type ID — must match the definition registration
    public static final String ENTITY_TYPE_ID = "cavehorror:cave_dweller";
    public static EntityType CAVE_DWELLER_TYPE;
    
    // AI Goals
    private EndermanStalkGoal stalkGoal;
    private EndermanChaseGoal chaseGoal;
    private EndermanBreakInvisGoal breakInvisGoal;
    private EndermanTargetSeesMeGoal targetSeesMeGoal;
    private EndermanTargetTooCloseGoal targetTooCloseGoal;
    
    private final CaveNoisePlugin plugin;
    
    // Wrapped Hytale entity
    private Entity entity;
    
    // Position cache (syncs with Hytale entity position)
    private double x, y, z;
    
    // State
    private State state = State.IDLE;
    private boolean alive = true;
    private boolean invisible = true;
    private boolean eyesVisible = false;
    private boolean aggro = false;
    private boolean spotted = false;
    
    private String targetPlayerId;
    private int tickCount = 0;
    private Random random;
    
    // Wall emergence targets (set by StalkGoal corridor detection)
    private double targetWallX, targetWallY, targetWallZ;
    private boolean hasWallTarget = false;
    
    // Mold particle spreading after wall emergence
    private final java.util.List<double[]> moldParticlePositions = new java.util.ArrayList<>();
    private int moldSpreadTimer = 0;
    private int moldSpreadIndex = 0;
    
    public EndermanEntity(CaveNoisePlugin plugin, Entity entity, double x, double y, double z) {
        this.plugin = plugin;
        this.entity = entity;
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
     */
    public void tickAI(double targetX, double targetY, double targetZ,
                       double lookX, double lookY, double lookZ,
                       boolean playerIsSpectator, boolean playerIsLooking) {
        if (!alive || playerIsSpectator) return;
        
        // BreakInvisGoal check: if player looks at invisible entity, reveal it
        if (invisible && state == State.STALKING) {
            if (breakInvisGoal.canUse(targetX, targetY, targetZ, lookX, lookY, lookZ)) {
                breakInvisGoal.start();
                return;
            }
        }
        
        // Dispatch to active goal
        switch (state) {
            case STALKING:
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
                // Wait for targeting goal to trigger transition
                break;
        }
    }
    
    /**
     * Physics tick — applies gravity, velocity, and ground collision.
     * Called separately from AI tick so physics runs even if AI is paused.
     * Uses Hytale's built-in physics components when available.
     */
    public void tickPhysics() {
        if (!alive) return;
        tickCount++;
        
        // Apply gravity if visible and not phasing
        if (!invisible) {
            // Use Hytale physics component if wrapped entity exists
            if (entity != null) {
                entity.getPhysicsComponent().setGravity(new Vector3f(0, -0.02f, 0));
            }
        }
        
        // Sync position with Hytale entity
        if (entity != null) {
            Location loc = entity.getLocation();
            this.x = loc.getX();
            this.y = loc.getY();
            this.z = loc.getZ();
        }
        
        // Update visibility state on the wrapped entity
        if (entity != null) {
            entity.setInvisible(invisible);
        }
        
        // Mold particle spreading after wall emergence
        if (moldSpreadTimer > 0) {
            tickMoldParticles();
        }
        
        // Auto-despawn after 1 hour (72000 ticks)
        if (tickCount > 72000) despawn();
    }
    
    // ---- BEHAVIOR HELPERS ----
    
    private void tickFleeing(double tx, double ty, double tz) {
        double dx = x - tx, dz = z - tz;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0) {
            if (entity != null) {
                entity.setVelocity(new Vector3f(
                    (float)(dx / len * 1.2), 0, (float)(dz / len * 1.2)));
            }
        }
        // After 5 seconds, teleport away and return to stalking
        if (tickCount % 100 == 0) {
            teleportTo(x + (random.nextDouble() - 0.5) * 30, y, z + (random.nextDouble() - 0.5) * 30);
            setInvisible(true);
            stalkGoal.start();
        }
    }
    
    /**
     * Hunting mode — emerge from a wall with mold particle effects.
     */
    private void tickHunting(double tx, double ty, double tz) {
        if (!hasWallTarget) {
            setState(State.STALKING);
            stalkGoal.start();
            return;
        }
        
        emergenceFromWall(targetWallX, targetWallY, targetWallZ, tx, tz);
        
        hasWallTarget = false;
        setInvisible(false);
        setAggro(true);
        setState(State.CHASING);
        
        // Play emergence sound
        HytaleServer.getAudioService().playSound(
            null, "cavehorror:emergence",
            new Vector3f((float)x, (float)y, (float)z), 1.0f, 0.5f
        );
    }
    
    /**
     * Emerge from a wall with mold particle spreading.
     * Spawns black particles on nearby wall blocks to create a mold effect.
     */
    private void emergenceFromWall(double wallX, double wallY, double wallZ,
                                     double playerX, double playerZ) {
        teleportTo(wallX + 0.5, wallY, wallZ + 0.5);
        
        // Face the player
        double dx = playerX - wallX, dz = playerZ - wallZ;
        float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
        if (entity != null) {
            entity.setYaw(yaw);
        }
        
        // Determine mold area
        moldParticlePositions.clear();
        moldSpreadIndex = 0;
        int moldHeight = 3;
        World world = HytaleServer.getWorldService().getWorld("overworld");
        if (world == null) return;
        
        for (int dy = 0; dy < moldHeight; dy++) {
            int checkY = (int)wallY + dy;
            
            // Check block at wall position
            if (world.getBlockAt((int)wallX, checkY, (int)wallZ).getType().isSolid()) {
                moldParticlePositions.add(new double[]{wallX + 0.5, checkY + 0.5, wallZ + 0.5});
            }
            
            // Check adjacent blocks for organic mold coverage
            int[][] neighbors = {{-1,0},{1,0},{0,-1},{0,1}};
            for (int[] n : neighbors) {
                if (random.nextFloat() < 0.4f) {
                    if (world.getBlockAt((int)wallX + n[0], checkY, (int)wallZ + n[1]).getType().isSolid()) {
                        moldParticlePositions.add(new double[]{
                            wallX + 0.5 + n[0], checkY + 0.5, wallZ + 0.5 + n[1]});
                    }
                }
            }
        }
        
        this.moldSpreadTimer = moldParticlePositions.size() * 2;
    }
    
    /**
     * Tick mold particle spreading after wall emergence.
     * Called from tickPhysics() when moldSpreadTimer > 0.
     */
    private void tickMoldParticles() {
        if (moldSpreadTimer <= 0) return;
        moldSpreadTimer--;
        
        if (moldSpreadTimer % 2 == 0 && moldSpreadIndex < moldParticlePositions.size()) {
            double[] pos = moldParticlePositions.get(moldSpreadIndex++);
            
            // Spawn black dust particles at mold positions
            for (int p = 0; p < 4; p++) {
                HytaleServer.getParticleService().spawnParticle(
                    "cavehorror:mold_dust",
                    new Vector3f(
                        (float)(pos[0] + (random.nextDouble() - 0.5) * 0.8),
                        (float)(pos[1] + (random.nextDouble() - 0.5) * 0.8),
                        (float)(pos[2] + (random.nextDouble() - 0.5) * 0.8)
                    ),
                    new Vector3f(0.02f, 0.02f, 0.02f),
                    0
                );
            }
        }
        
        if (moldSpreadTimer <= 0) {
            moldParticlePositions.clear();
            moldSpreadIndex = 0;
        }
    }
    
    // ---- POSITION HELPERS ----
    
    public void teleportTo(double tx, double ty, double tz) {
        this.x = tx; this.y = ty; this.z = tz;
        if (entity != null) {
            entity.teleport(new Location(entity.getWorld(), tx, ty, tz));
        }
    }
    
    // ---- STATE ACCESSORS ----
    
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    
    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }
    
    public boolean isInvisible() { return invisible; }
    public void setInvisible(boolean invisible) { 
        this.invisible = invisible;
        if (entity != null) entity.setInvisible(invisible);
    }
    
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
    
    public void setPosition(double x, double y, double z) {
        this.x = x; this.y = y; this.z = z;
    }
    
    public Entity getEntity() { return entity; }
    public void setEntity(Entity entity) { this.entity = entity; }
    
    // ---- AI GOAL ACCESSORS ----
    
    public EndermanStalkGoal getStalkGoal() { return stalkGoal; }
    public EndermanChaseGoal getChaseGoal() { return chaseGoal; }
    public EndermanBreakInvisGoal getBreakInvisGoal() { return breakInvisGoal; }
    public EndermanTargetSeesMeGoal getTargetSeesMeGoal() { return targetSeesMeGoal; }
    public EndermanTargetTooCloseGoal getTargetTooCloseGoal() { return targetTooCloseGoal; }
    
    public void despawn() {
        this.alive = false;
        if (entity != null) {
            // Remove from tracked UUID set for event handling
            plugin.getTrackedEntityIds().remove(entity.getUniqueId());
            entity.remove();
        }
        plugin.getEndermanRegistry().untrack(this);
    }
    
    public double distanceTo(double px, double py, double pz) {
        double dx = px - x, dy = py - y, dz = pz - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    public CaveNoisePlugin getPlugin() { return plugin; }
    
    /**
     * EndermanEntityDefinition — registered with EntityRegistry during onEnable().
     * Defines entity attributes, type ID, and spawn settings.
     */
    public static class EndermanEntityDefinition implements com.hytale.api.entity.EntityDefinition {
        private final CaveNoisePlugin plugin;
        
        public EndermanEntityDefinition(CaveNoisePlugin plugin) {
            this.plugin = plugin;
        }
        
        @Override
        public String getId() {
            return ENTITY_TYPE_ID;
        }
        
        @Override
        public String getName() {
            return "Cave Dweller";
        }
        
        @Override
        public EntityCategory getCategory() {
            return EntityCategory.MONSTER;
        }
        
        @Override
        public EntityAttribute getAttributes() {
            return EntityAttribute.builder()
                .maxHealth(65.0)
                .movementSpeed(0.35)
                .attackDamage(6.0)
                .followRange(200.0)
                .build();
        }
        
        @Override
        public boolean isEnabled() {
            return true;
        }
    }
}
