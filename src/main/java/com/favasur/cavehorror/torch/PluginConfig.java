package com.favasur.cavehorror.torch;

import com.favasur.cavehorror.CaveNoisePlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * PluginConfig — manages configuration for the Cave Horror plugin.
 * 
 * Configuration is stored in a JSON file in the plugin's data directory.
 * Provides default values for all settings and allows runtime reload.
 * 
 * Configurable settings:
 * - Torch extinguish radius
 * - Spawn rates and chances
 * - Sound volumes
 * - Entity behavior parameters
 */
public class PluginConfig {

    private final Map<String, Object> config = new HashMap<>();
    
    // Default configuration values
    private static final Map<String, Object> DEFAULTS = new HashMap<>();
    static {
        DEFAULTS.put("torch_extinguish_radius", 20);
        DEFAULTS.put("torch_extinguish_interval", 10);
        DEFAULTS.put("torch_extinguish_chance", 0.3);
        
        DEFAULTS.put("spawn_chance_per_tick", 0.005);
        DEFAULTS.put("spelunker_spawn_chance", 0.04);
        DEFAULTS.put("stalker_proximity_range", 300.0);
        
        DEFAULTS.put("min_stalk_distance", 20.0);
        DEFAULTS.put("max_stalk_distance", 30.0);
        DEFAULTS.put("chase_trigger_distance", 15.0);
        
        DEFAULTS.put("stare_trigger_ticks", 200);
        DEFAULTS.put("attack_damage", 6.0);
        DEFAULTS.put("max_health", 65.0);
        DEFAULTS.put("move_speed", 0.35);
        
        DEFAULTS.put("ambient_cave_sound_interval", 4800);
        DEFAULTS.put("stalk_sound_interval", 800);
        DEFAULTS.put("calm_timer_min", 15000);
        DEFAULTS.put("calm_timer_max", 18000);
        
        DEFAULTS.put("debug_mode", false);
    }
    
    public PluginConfig() {
        config.putAll(DEFAULTS);
    }
    
    /**
     * Load configuration from the plugin's data directory.
     */
    public void load() {
        // Hytale API: Load from plugin data directory
        // File configFile = new File(plugin.getDataFolder(), "config.json");
        // if (configFile.exists()) {
        //     // Parse JSON and merge with defaults
        // }
    }
    
    /**
     * Save current configuration to disk.
     */
    public void save() {
        // Hytale API: Save to plugin data directory
        // File configFile = new File(plugin.getDataFolder(), "config.json");
        // Write config map as JSON
    }
    
    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        return (T) config.getOrDefault(key, defaultValue);
    }
    
    public void set(String key, Object value) {
        config.put(key, value);
    }
    
    public boolean getBoolean(String key) {
        return get(key, false);
    }
    
    public int getInt(String key) {
        return get(key, 0);
    }
    
    public double getDouble(String key) {
        return get(key, 0.0);
    }
    
    public String getString(String key) {
        return get(key, "");
    }
}
