package com.favasur.cavehorror;

import com.favasur.cavehorror.entity.EndermanEntity;
import com.favasur.cavehorror.entity.EndermanEntity.State;
import com.favasur.cavehorror.entity.EndermanRegistry;
import com.favasur.cavehorror.entity.custom.*;
import com.favasur.cavehorror.torch.TorchBurnoutSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cave Horror: White Eyes — Hytale Plugin
 * 
 * Ported from Minecraft NeoForge mod. Replicates the ambient horror experience:
 * - White-eyes enderman entity that stalks players in caves
 * - Ambient cave sounds that intensify near the entity
 * - Torch burnout mechanics
 * - Entity spawns behind players in deep caves
 * 
 * == HYTALE API ==
 * Uses com.hytale.* ECS-based API:
 * - Server.get().getPlayerManager().getOnlinePlayers()
 * - Entity components: IdentityComponent, PositionComponent, RotationComponent, GamemodeComponent
 * - World: world.getBlockAt(x, y, z)
 * - Events: @EventHandler with EventBus
 * - Sounds: SoundManager.playSound(soundId, position, volume, pitch)
 */
public class CaveNoisePlugin {

    public static final String PLUGIN_ID = "cave-horror-white-eyes";
    private static final Logger LOGGER = LoggerFactory.getLogger(PLUGIN_ID);
    
    private static CaveNoisePlugin instance;
    
    // Core systems
    private AmbientSoundSystem soundSystem;
    private EntitySpawnSystem spawnSystem;
    private TorchBurnoutSystem torchSystem;
    private EndermanRegistry endermanRegistry;
    
    private ScheduledExecutorService scheduler;
    
    // Stalker proximity tracking
    private final ConcurrentHashMap<UUID, Boolean> playerHasStalker = new ConcurrentHashMap<>();
    private static final double STALKER_PROXIMITY_RANGE = 300.0;
    
    // Timer state (in ticks, 20 ticks = 1 second)
    private int calmTimer = 0;
    private int noiseTimer = 0;
    private int stalkNoiseTimer = 0;
    private int vanillaNoiseTimer = 0;
    
    private static final int TICKS_CALM_RESET_MIN = 15000;
    private static final int TICKS_CALM_RESET_MAX = 18000;
    private static final int TICKS_NOISE_RESET_MIN = 2000;
    private static final int TICKS_NOISE_RESET_MAX = 1600;
    private static final int STALK_NOISE_MIN = 800;
    private static final int STALK_NOISE_MAX = 1000;
    private static final double SPAWN_CHANCE_PER_TICK = 0.005;
    
    private final Random random = new Random();
    
    // ---- LIFECYCLE ----
    // HYTALE API: public class CaveNoisePlugin extends Plugin { ... }
    
    public void onEnable() {
        instance = this;
        LOGGER.info("Cave Horror: White Eyes initializing...");
        
        this.soundSystem = new AmbientSoundSystem(this);
        this.spawnSystem = new EntitySpawnSystem(this);
        this.torchSystem = new TorchBurnoutSystem(this);
        this.endermanRegistry = new EndermanRegistry(this);
        
        // HYTALE API: Register event listeners
        // Server.get().getEventBus().register(this);
        
        // HYTALE API: Register entity type with ECS
        // EntityTypeRegistry.get().register("cave_dweller", EndermanEntity::new);
        
        // HYTALE API: Use server tick event instead of custom scheduler
        // @EventHandler public void onServerTick(ServerTickEvent event) { serverTick(); }
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.scheduler.scheduleAtFixedRate(this::serverTick, 0, 50, TimeUnit.MILLISECONDS);
        
        // HYTALE API: Register commands
        // CommandManager.get().register("cavehorror", new CaveHorrorCommand(this));
        
        resetCalmTimer();
        resetNoiseTimer();
        resetStalkNoiseTimer();
        
        LOGGER.info("Cave Horror: White Eyes initialized successfully.");
    }
    
    public void onDisable() {
        LOGGER.info("Cave Horror: White Eyes shutting down...");
        if (scheduler != null && !scheduler.isShutdown()) scheduler.shutdown();
        endermanRegistry.despawnAll();
        LOGGER.info("Cave Horror: White Eyes shut down.");
    }
    
    // ---- MAIN TICK ----
    
    /**
     * Called by scheduler or @EventHandler(ServerTickEvent).
     * HYTALE API: Use Server.get().getPlayerManager().getOnlinePlayers() to get players.
     */
    public void serverTick() {
        // HYTALE API:
        // List<PlayerData> players = new ArrayList<>();
        // for (Entity player : Server.get().getPlayerManager().getOnlinePlayers()) {
        //     IdentityComponent id = player.getComponent(IdentityComponent.class);
        //     PositionComponent pos = player.getComponent(PositionComponent.class);
        //     RotationComponent rot = player.getComponent(RotationComponent.class);
        //     GamemodeComponent gm = player.getComponent(GamemodeComponent.class);
        //     boolean spectator = gm != null && gm.getGamemode() == Gamemode.SPECTATOR;
        //     boolean canSeeSky = !Server.get().getWorld().isBelowSolidBlock(
        //         (int)pos.getX(), (int)pos.getY(), (int)pos.getZ());
        //     players.add(new PlayerData(id.getUuid(), pos.getX(), pos.getY(), pos.getZ(),
        //         rot.getLookX(), rot.getLookZ(), spectator, canSeeSky));
        // }
        // serverTick(players);
    }
    
    /**
     * Main server tick logic — handles ambient sounds, spawning, entity AI.
     */
    private void serverTick(List<PlayerData> players) {
        if (players.isEmpty()) return;
        
        // === PROXIMITY CHECK ===
        playerHasStalker.clear();
        for (PlayerData player : players) {
            if (player.spectator) continue;
            boolean hasNearby = false;
            for (EndermanEntity enderman : endermanRegistry.getActiveEntities()) {
                if (!enderman.isAlive()) continue;
                if (distance(enderman.getX(), enderman.getY(), enderman.getZ(),
                            player.x, player.y, player.z) < STALKER_PROXIMITY_RANGE) {
                    hasNearby = true;
                    break;
                }
            }
            playerHasStalker.put(player.uuid, hasNearby);
        }
        
        boolean anyPlayerHasStalker = playerHasStalker.values().stream().anyMatch(b -> b);
        if (anyPlayerHasStalker) resetCalmTimer();
        
        // Decrement timers
        noiseTimer--; vanillaNoiseTimer--; stalkNoiseTimer--; calmTimer--;
        
        // === AMBIENT SOUNDS (only for players with nearby stalker) ===
        for (PlayerData player : players) {
            if (!playerHasStalker.getOrDefault(player.uuid, false)) continue;
            boolean underground = player.y < 40 && !player.canSeeSky;
            if (!underground) continue;
            
            if (noiseTimer <= 0 && calmTimer <= 8000) {
                soundSystem.playCaveSound(player);
                resetNoiseTimer();
            }
            if (vanillaNoiseTimer <= 0 && calmTimer <= 15000) {
                soundSystem.playVanillaCaveSound(player);
                resetVanillaNoiseTimer();
            }
            if (stalkNoiseTimer <= 0) {
                soundSystem.playStalkSound(player);
                resetStalkNoiseTimer();
            }
        }
        
        // === SPAWN LOGIC ===
        if (calmTimer <= 0 && !anyPlayerHasStalker) {
            List<PlayerData> spelunkers = new ArrayList<>();
            for (PlayerData p : players) {
                if (p.spectator) continue;
                if (p.y < 40 && !p.canSeeSky) spelunkers.add(p);
            }
            if (spelunkers.isEmpty()) {
                for (PlayerData p : players) {
                    if (!p.spectator) spelunkers.add(p);
                }
            }
            if (!spelunkers.isEmpty() && random.nextDouble() <= SPAWN_CHANCE_PER_TICK) {
                PlayerData victim = spelunkers.get(random.nextInt(spelunkers.size()));
                EndermanEntity entity = spawnSystem.spawnEndermanBehind(
                    victim.uuid, victim.x, victim.y, victim.z, victim.lookX, victim.lookZ);
                if (entity != null) {
                    // Create AI goals for the new entity
                    entity.initAI();
                }
                resetCalmTimer();
            }
        }
        
        // === ENTITY AI TICK ===
        // Evaluate targeting and dispatch goals for each entity-player pair
        for (EndermanEntity enderman : endermanRegistry.getActiveEntities()) {
            if (!enderman.isAlive()) continue;
            
            PlayerData target = null;
            for (PlayerData p : players) {
                if (p.spectator) continue;
                if (p.uuid.toString().equals(enderman.getTargetPlayerId())) {
                    target = p;
                    break;
                }
            }
            if (target == null) continue;
            
            // Run targeting goals to detect state transitions
            boolean seeMe = enderman.getTargetSeesMeGoal().canUse(
                target.x, target.y, target.z, target.lookX, target.lookZ, target.spectator);
            boolean tooClose = enderman.getTargetTooCloseGoal().canUse(
                target.x, target.y, target.z, target.spectator);
            
            if (seeMe) {
                enderman.getTargetSeesMeGoal().start(target.uuid.toString());
            } else if (tooClose) {
                enderman.getTargetTooCloseGoal().start(target.uuid.toString());
            }
            
            // Compute if player is looking at the entity (shared FOV check)
            boolean playerLooking = isPlayerLookingAtEntity(target, enderman);
            
            // Dispatch to the active state's goal
            enderman.tickAI(target.x, target.y, target.z, target.lookX, target.lookZ, 
                           target.spectator, playerLooking);
        }
        
        // Physics tick for all entities
        for (EndermanEntity enderman : endermanRegistry.getActiveEntities()) {
            enderman.tickPhysics();
        }
        
        endermanRegistry.cleanDead();
    }
    
    /**
     * FOV-based check if a player is looking at an entity (~70° cone).
     */
    private boolean isPlayerLookingAtEntity(PlayerData player, EndermanEntity enderman) {
        double ex = enderman.getX(), ez = enderman.getZ();
        double dx = ex - player.x, dz = ez - player.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len == 0) return false;
        dx /= len; dz /= len;
        
        double lookLen = Math.sqrt(player.lookX * player.lookX + player.lookZ * player.lookZ);
        if (lookLen == 0) return false;
        double nlx = player.lookX / lookLen, nlz = player.lookZ / lookLen;
        
        return dx * nlx + dz * nlz > 0.819; // cos(35°)
    }
    
    // ---- TIMERS ----
    
    public int getCalmTimer() { return calmTimer; }
    
    private void resetCalmTimer() {
        calmTimer = TICKS_CALM_RESET_MIN + random.nextInt(TICKS_CALM_RESET_MAX - TICKS_CALM_RESET_MIN);
    }
    private void resetNoiseTimer() {
        noiseTimer = TICKS_NOISE_RESET_MIN + random.nextInt(TICKS_NOISE_RESET_MAX);
    }
    private void resetVanillaNoiseTimer() {
        vanillaNoiseTimer = 8000 + random.nextInt(2000);
    }
    private void resetStalkNoiseTimer() {
        stalkNoiseTimer = STALK_NOISE_MIN + random.nextInt(STALK_NOISE_MAX - STALK_NOISE_MIN);
    }
    
    private double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    // ---- ACCESSORS ----
    
    public static CaveNoisePlugin getInstance() { return instance; }
    public EndermanRegistry getEndermanRegistry() { return endermanRegistry; }
    public TorchBurnoutSystem getTorchSystem() { return torchSystem; }
    public Random getRandom() { return random; }
    public static Logger getLogger() { return LOGGER; }
    
    /**
     * Lightweight player state snapshot for one tick.
     * HYTALE API: Populate from Entity components:
     * IdentityComponent (uuid), PositionComponent (xyz),
     * RotationComponent (lookX/lookZ), GamemodeComponent (spectator).
     */
    public static class PlayerData {
        public final UUID uuid;
        public final double x, y, z;
        public final double lookX, lookZ;
        public final boolean spectator;
        public final boolean canSeeSky;
        
        public PlayerData(UUID uuid, double x, double y, double z,
                          double lookX, double lookZ, boolean spectator, boolean canSeeSky) {
            this.uuid = uuid; this.x = x; this.y = y; this.z = z;
            this.lookX = lookX; this.lookZ = lookZ;
            this.spectator = spectator; this.canSeeSky = canSeeSky;
        }
    }
}
