package com.favasur.cavehorror;

import com.favasur.cavehorror.entity.EndermanEntity;
import com.favasur.cavehorror.entity.EndermanRegistry;
import com.favasur.cavehorror.entity.custom.*;
import com.favasur.cavehorror.torch.PluginConfig;
import com.favasur.cavehorror.torch.TorchBurnoutSystem;
import com.hytale.api.plugin.JavaPlugin;
import com.hytale.api.events.EventPriority;
import com.hytale.api.events.Subscribe;
import com.hytale.api.events.player.PlayerJoinEvent;
import com.hytale.api.events.player.PlayerQuitEvent;
import com.hytale.api.events.server.ServerTickEvent;
import com.hytale.api.events.entity.EntityDamageByEntityEvent;
import com.hytale.api.world.Location;
import com.hytale.api.entity.EntityType;
import com.hytale.api.player.Player;
import com.hytale.api.HytaleServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cave Horror: White Eyes — Hytale Plugin
 * 
 * Main plugin class. Extends JavaPlugin as required by the Hytale plugin API.
 * Manages the ambient horror experience: stalking entity, cave sounds, torch burnout.
 * 
 * == HYTALE API INTEGRATION ==
 * Uses com.hytale.api.* service-oriented API:
 * - HytaleServer.getPlayerService().getOnlinePlayers()
 * - HytaleServer.getAudioService().playSound(soundId, location, volume, pitch)
 * - HytaleServer.getWorldService().getBlock(worldName, x, y, z)
 * - HytaleServer.getEntityService().spawn(entityType, location)
 * - HytaleServer.getEntityRegistry().register(entityDefinition)
 * - @Subscribe annotation for event handlers
 */
public class CaveNoisePlugin extends JavaPlugin {

    public static final String PLUGIN_ID = "cave-horror-white-eyes";
    private static final Logger LOGGER = LoggerFactory.getLogger(PLUGIN_ID);
    
    private static CaveNoisePlugin instance;
    
    // Core systems
    private AmbientSoundSystem soundSystem;
    private EntitySpawnSystem spawnSystem;
    private TorchBurnoutSystem torchSystem;
    private EndermanRegistry endermanRegistry;
    private PluginConfig pluginConfig;
    
    // Track native entity UUIDs for event handling
    private final Set<UUID> trackedEntityIds = ConcurrentHashMap.newKeySet();
    
    // Stalker proximity tracking
    private final ConcurrentHashMap<UUID, Boolean> playerHasStalker = new ConcurrentHashMap<>();
    private static final double STALKER_PROXIMITY_RANGE = 300.0;
    
    // Timer state (in ticks)
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
    
    // Cached player snapshots updated per tick
    private final List<PlayerData> cachedPlayers = new ArrayList<>();
    
    // ---- LIFECYCLE ----
    
    @Override
    public void onEnable() {
        instance = this;
        LOGGER.info("Cave Horror: White Eyes initializing...");
        
        this.pluginConfig = new PluginConfig(this);
        this.pluginConfig.load();
        
        this.soundSystem = new AmbientSoundSystem(this);
        this.spawnSystem = new EntitySpawnSystem(this);
        this.torchSystem = new TorchBurnoutSystem(this);
        this.endermanRegistry = new EndermanRegistry(this);
        
        // Register event listeners
        HytaleServer.getEventBus().register(this);
        
        // Register custom entity type and capture the EntityType
        EndermanEntity.EndermanEntityDefinition def = new EndermanEntity.EndermanEntityDefinition(this);
        EntityType registeredType = HytaleServer.getEntityRegistry().register(def);
        if (registeredType != null) {
            EndermanEntity.CAVE_DWELLER_TYPE = registeredType;
        }
        
        resetCalmTimer();
        resetNoiseTimer();
        resetStalkNoiseTimer();
        
        LOGGER.info("Cave Horror: White Eyes initialized successfully.");
    }
    
    @Override
    public void onDisable() {
        LOGGER.info("Cave Horror: White Eyes shutting down...");
        endermanRegistry.despawnAll();
        trackedEntityIds.clear();
        LOGGER.info("Cave Horror: White Eyes shut down.");
    }
    
    // ---- EVENT HANDLERS ----
    
    @Subscribe
    public void onServerTick(ServerTickEvent event) {
        cachedPlayers.clear();
        Collection<Player> onlinePlayers = HytaleServer.getPlayerService().getOnlinePlayers();
        
        for (Player player : onlinePlayers) {
            Location pos = player.getLocation();
            boolean spectator = player.getGamemode() == com.hytale.api.world.Gamemode.SPECTATOR
                             || player.getGamemode() == com.hytale.api.world.Gamemode.CREATIVE;
            boolean canSeeSky = player.getWorld().getHighestBlockYAt(
                (int)pos.getX(), (int)pos.getZ()) <= (int)pos.getY();
            
            cachedPlayers.add(new PlayerData(
                player.getUniqueId(),
                pos.getX(), pos.getY(), pos.getZ(),
                player.getLocation().getYaw(), player.getLocation().getPitch(),
                spectator, canSeeSky,
                player
            ));
        }
        
        if (!cachedPlayers.isEmpty()) {
            serverTick(cachedPlayers);
        }
    }
    
    @Subscribe
    public void onPlayerJoin(PlayerJoinEvent event) {
        LOGGER.info("Player {} joined. The darkness watches.", event.getPlayer().getName());
    }
    
    @Subscribe
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        for (EndermanEntity enderman : endermanRegistry.getActiveEntities()) {
            if (uuid.toString().equals(enderman.getTargetPlayerId())) {
                enderman.despawn();
            }
        }
    }
    
    @Subscribe(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Check if damaged entity is our tracked enderman by UUID
        if (trackedEntityIds.contains(event.getEntity().getUniqueId())) {
            // Only allow players to damage the enderman
            if (!(event.getDamager() instanceof Player)) {
                event.setCancelled(true);
            }
        }
    }
    
    // ---- MAIN TICK ----
    
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
        
        noiseTimer--; vanillaNoiseTimer--; stalkNoiseTimer--; calmTimer--;
        
        // === AMBIENT SOUNDS ===
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
                    victim.uuid, victim.x, victim.y, victim.z,
                    victim.lookYaw, victim.lookPitch);
                if (entity != null) {
                    entity.initAI();
                    // Track the native entity UUID for event handling
                    if (entity.getEntity() != null) {
                        trackedEntityIds.add(entity.getEntity().getUniqueId());
                    }
                }
                resetCalmTimer();
            }
        }
        
        // === ENTITY AI TICK ===
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
            
            double[] lookVector = yawPitchToLookVector(target.lookYaw, target.lookPitch);
            boolean seeMe = enderman.getTargetSeesMeGoal().canUse(
                target.x, target.y, target.z, lookVector[0], lookVector[1], lookVector[2], target.spectator);
            boolean tooClose = enderman.getTargetTooCloseGoal().canUse(
                target.x, target.y, target.z, target.spectator);
            
            if (seeMe) {
                enderman.getTargetSeesMeGoal().start(target.uuid.toString());
            } else if (tooClose) {
                enderman.getTargetTooCloseGoal().start(target.uuid.toString());
            }
            
            boolean playerLooking = isPlayerLookingAtEntity(target, enderman);
            
            enderman.tickAI(target.x, target.y, target.z,
                           lookVector[0], lookVector[1], lookVector[2],
                           target.spectator, playerLooking);
        }
        
        for (EndermanEntity enderman : endermanRegistry.getActiveEntities()) {
            enderman.tickPhysics();
        }
        
        for (PlayerData player : players) {
            if (!playerHasStalker.getOrDefault(player.uuid, false)) continue;
            int depth = (int)(player.world.getHighestBlockYAt((int)player.x, (int)player.z) - player.y);
            if (depth > 30) {
                torchSystem.extinguishNearby(player.x, player.y, player.z, depth);
            }
        }
        
        endermanRegistry.cleanDead();
    }
    
    private boolean isPlayerLookingAtEntity(PlayerData player, EndermanEntity enderman) {
        double ex = enderman.getX(), ez = enderman.getZ();
        double dx = ex - player.x, dz = ez - player.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len == 0) return false;
        dx /= len; dz /= len;
        return dx * player.lookX + dz * player.lookZ > 0.819;
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
    
    private double[] yawPitchToLookVector(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        return new double[]{x, y, z};
    }
    
    // ---- ACCESSORS ----
    
    public static CaveNoisePlugin getInstance() { return instance; }
    public EndermanRegistry getEndermanRegistry() { return endermanRegistry; }
    public AmbientSoundSystem getSoundSystem() { return soundSystem; }
    public TorchBurnoutSystem getTorchSystem() { return torchSystem; }
    public EntitySpawnSystem getSpawnSystem() { return spawnSystem; }
    public PluginConfig getPluginConfig() { return pluginConfig; }
    public Random getRandom() { return random; }
    public static Logger getLogger() { return LOGGER; }
    public Set<UUID> getTrackedEntityIds() { return trackedEntityIds; }
    
    public static class PlayerData {
        public final UUID uuid;
        public final double x, y, z;
        public final float lookYaw, lookPitch;
        public final double lookX, lookZ;
        public final boolean spectator;
        public final boolean canSeeSky;
        public final com.hytale.api.world.World world;
        public final Player player;
        
        public PlayerData(UUID uuid, double x, double y, double z,
                          float lookYaw, float lookPitch,
                          boolean spectator, boolean canSeeSky, Player player) {
            this.uuid = uuid; this.x = x; this.y = y; this.z = z;
            this.lookYaw = lookYaw; this.lookPitch = lookPitch;
            this.spectator = spectator; this.canSeeSky = canSeeSky;
            this.world = player.getWorld();
            this.player = player;
            
            double yawRad = Math.toRadians(lookYaw);
            this.lookX = -Math.sin(yawRad) * Math.cos(Math.toRadians(lookPitch));
            this.lookZ = Math.cos(yawRad) * Math.cos(Math.toRadians(lookPitch));
        }
    }
}
