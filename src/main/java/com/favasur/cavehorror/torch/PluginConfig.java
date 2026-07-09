package com.favasur.cavehorror.torch;

import com.favasur.cavehorror.CaveNoisePlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * PluginConfig — manages configuration for the Cave Horror plugin.
 * 
 * Configuration is stored as config.json in the plugin's data directory.
 * Uses Gson for JSON serialization. Merges user config with defaults.
 * 
 * Configurable settings: torch extinguish radius, spawn rates, sound volumes, entity behavior.
 */
public class PluginConfig {

    private final CaveNoisePlugin plugin;
    private final Gson gson;
    private final File configFile;
    
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
    
    public PluginConfig(CaveNoisePlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.configFile = new File(plugin.getDataFolder(), "config.json");
        config.putAll(DEFAULTS);
    }
    
    /**
     * Load configuration from the plugin's data directory.
     * Merges with defaults so new keys are always present.
     */
    public void load() {
        if (!configFile.exists()) {
            save(); // Create default config
            return;
        }
        
        try (FileReader reader = new FileReader(configFile)) {
            Type type = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                // Merge with defaults (user values take precedence)
                for (Map.Entry<String, Object> entry : DEFAULTS.entrySet()) {
                    if (loaded.containsKey(entry.getKey())) {
                        config.put(entry.getKey(), loaded.get(entry.getKey()));
                    } else {
                        config.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            CaveNoisePlugin.getLogger().info("Configuration loaded from {}",
                configFile.getAbsolutePath());
        } catch (IOException e) {
            CaveNoisePlugin.getLogger().error("Failed to load config: {}", e.getMessage());
        }
    }
    
    /**
     * Save current configuration to disk as JSON.
     */
    public void save() {
        File parentDir = configFile.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(config, writer);
            CaveNoisePlugin.getLogger().info("Configuration saved to {}",
                configFile.getAbsolutePath());
        } catch (IOException e) {
            CaveNoisePlugin.getLogger().error("Failed to save config: {}", e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        return (T) config.getOrDefault(key, defaultValue);
    }
    
    public void set(String key, Object value) {
        config.put(key, value);
    }
    
    public boolean getBoolean(String key) { return get(key, false); }
    public int getInt(String key) { return get(key, 0); }
    public double getDouble(String key) { return get(key, 0.0); }
    public String getString(String key) { return get(key, ""); }
}
