package com.magicbili.expbook;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Language Manager for multi-language support
 * Supports loading different language files based on configuration
 */
public class LanguageManager {
    
    private final JavaPlugin plugin;
    private FileConfiguration messagesConfig;
    private String currentLanguage;
    private final Map<String, String> messageCache = new HashMap<>();
    
    // Supported languages
    private static final String[] SUPPORTED_LANGUAGES = {"en", "zh_CN"};
    private static final String DEFAULT_LANGUAGE = "en";
    
    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Load language configuration based on config.yml setting
     */
    public void loadLanguage() {
        // Get language from config.yml
        String configLanguage = plugin.getConfig().getString("language", DEFAULT_LANGUAGE);
        
        // Validate language
        if (!isLanguageSupported(configLanguage)) {
            plugin.getLogger().warning("Unsupported language '" + configLanguage + "', falling back to '" + DEFAULT_LANGUAGE + "'");
            configLanguage = DEFAULT_LANGUAGE;
        }
        
        this.currentLanguage = configLanguage;
        
        // Load the language file
        loadLanguageFile(currentLanguage);
        
        plugin.getLogger().info("Loaded language: " + currentLanguage);
    }
    
    /**
     * Load a specific language file
     */
    private void loadLanguageFile(String language) {
        messageCache.clear();
        
        String fileName = "messages_" + language + ".yml";
        File languageFile = new File(plugin.getDataFolder(), fileName);
        
        // Save default language file if it doesn't exist
        if (!languageFile.exists()) {
            plugin.saveResource(fileName, false);
        }
        
        // Load the language file
        messagesConfig = YamlConfiguration.loadConfiguration(languageFile);
        
        // Load defaults from jar
        InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );
            messagesConfig.setDefaults(defaultConfig);
        }
        
        // Cache all messages
        for (String key : messagesConfig.getKeys(false)) {
            messageCache.put(key, messagesConfig.getString(key));
        }
    }
    
    /**
     * Reload the current language
     */
    public void reload() {
        loadLanguage();
    }
    
    /**
     * Get a message by key
     */
    public String getMessage(String key) {
        String message = messageCache.get(key);
        if (message == null) {
            plugin.getLogger().warning("Missing message key: " + key);
            return "&cMessage not configured: " + key;
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    /**
     * Get a message with placeholder replacements
     */
    public String getMessage(String key, Map<String, String> replacements) {
        String message = getMessage(key);
        
        if (replacements != null) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                message = message.replace(entry.getKey(), entry.getValue());
            }
        }
        
        return message;
    }
    
    /**
     * Get a message with a single placeholder replacement
     */
    public String getMessage(String key, String placeholder, String value) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put(placeholder, value);
        return getMessage(key, replacements);
    }
    
    /**
     * Get raw message without color code translation
     */
    public String getRawMessage(String key) {
        String message = messageCache.get(key);
        if (message == null) {
            plugin.getLogger().warning("Missing message key: " + key);
            return "Message not configured: " + key;
        }
        return message;
    }
    
    /**
     * Check if a language is supported
     */
    private boolean isLanguageSupported(String language) {
        for (String supported : SUPPORTED_LANGUAGES) {
            if (supported.equalsIgnoreCase(language)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get current language code
     */
    public String getCurrentLanguage() {
        return currentLanguage;
    }
    
    /**
     * Get list of supported languages
     */
    public static String[] getSupportedLanguages() {
        return SUPPORTED_LANGUAGES.clone();
    }
    
    /**
     * Get default language
     */
    public static String getDefaultLanguage() {
        return DEFAULT_LANGUAGE;
    }
}
