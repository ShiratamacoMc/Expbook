package com.magicbili.expbook;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

/**
 * Configuration Manager with version-based update system
 * Automatically updates configuration when version changes
 */
public class ConfigManager {
    
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;
    
    // Current config version (must match config.yml)
    private static final int CURRENT_CONFIG_VERSION = 2;
    
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
    }
    
    /**
     * Load and update configuration based on version
     */
    public void loadConfig() {
        // Create config file if it doesn't exist
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
            plugin.getLogger().info("Created default configuration file");
        }
        
        // Load current config
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Check config version
        int configVersion = config.getInt("config-version", 1);
        
        if (configVersion < CURRENT_CONFIG_VERSION) {
            plugin.getLogger().warning("Configuration file is outdated (v" + configVersion + " -> v" + CURRENT_CONFIG_VERSION + ")");
            updateConfig(configVersion);
        } else if (configVersion > CURRENT_CONFIG_VERSION) {
            plugin.getLogger().warning("Configuration file is from a newer version! (v" + configVersion + ")");
            plugin.getLogger().warning("Some features may not work correctly. Please update the plugin.");
        } else {
            plugin.getLogger().info("Configuration version: v" + configVersion);
        }
        
        // Load default config from jar for missing values
        InputStream defaultConfigStream = plugin.getResource("config.yml");
        if (defaultConfigStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8)
            );
            config.setDefaults(defaultConfig);
        }
        
        // Reload plugin's config
        plugin.reloadConfig();
    }
    
    /**
     * Update configuration from old version to current version
     */
    private void updateConfig(int oldVersion) {
        try {
            // Backup old config
            backupConfig(oldVersion);
            
            // Load default config
            InputStream defaultConfigStream = plugin.getResource("config.yml");
            if (defaultConfigStream == null) {
                plugin.getLogger().severe("Cannot load default configuration from jar!");
                return;
            }
            
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8)
            );
            
            // Migrate settings based on version
            boolean updated = false;
            
            // Version 1 -> 2: Add language support
            if (oldVersion < 2) {
                if (!config.contains("language")) {
                    config.set("language", defaultConfig.get("language"));
                    plugin.getLogger().info("Added 'language' configuration option");
                    updated = true;
                }
            }
            
            // Add any missing keys from default config
            for (String key : defaultConfig.getKeys(true)) {
                if (!config.contains(key) && !key.equals("config-version")) {
                    config.set(key, defaultConfig.get(key));
                    plugin.getLogger().info("Added missing config key: " + key);
                    updated = true;
                }
            }
            
            // Update version number
            config.set("config-version", CURRENT_CONFIG_VERSION);
            updated = true;
            
            // Save updated config
            if (updated) {
                config.save(configFile);
                plugin.getLogger().info("Configuration has been updated to v" + CURRENT_CONFIG_VERSION);
                
                // Reload the config
                config = YamlConfiguration.loadConfiguration(configFile);
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update configuration", e);
        }
    }
    
    /**
     * Create a backup of the current config file
     */
    private void backupConfig(int version) {
        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String backupName = "config_v" + version + "_backup_" + timestamp + ".yml";
            File backupFile = new File(plugin.getDataFolder(), backupName);
            
            Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Created configuration backup: " + backupName);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create config backup", e);
        }
    }
    
    /**
     * Get the configuration
     */
    public FileConfiguration getConfig() {
        if (config == null) {
            loadConfig();
        }
        return config;
    }
    
    /**
     * Reload configuration
     */
    public void reload() {
        loadConfig();
    }
    
    /**
     * Save configuration
     */
    public void save() {
        try {
            config.save(configFile);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save configuration", e);
        }
    }
    
    /**
     * Get current config version
     */
    public static int getCurrentVersion() {
        return CURRENT_CONFIG_VERSION;
    }
}
