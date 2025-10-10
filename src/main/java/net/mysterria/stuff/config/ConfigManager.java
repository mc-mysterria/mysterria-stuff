package net.mysterria.stuff.config;

import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration manager for MysterriaStuff
 * Handles loading, saving, and accessing configuration values
 */
public class ConfigManager {

    private final MysterriaStuff plugin;
    private FileConfiguration config;

    public ConfigManager(MysterriaStuff plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Load or reload the configuration
     */
    public void loadConfig() {
        // Save default config if it doesn't exist
        plugin.saveDefaultConfig();

        // Reload from disk
        plugin.reloadConfig();
        config = plugin.getConfig();

        PrettyLogger.debug("Configuration loaded");
    }

    /**
     * Reload the configuration from disk
     */
    public void reloadConfig() {
        loadConfig();
        PrettyLogger.info("Configuration reloaded");
    }

    /**
     * Save the configuration to disk
     */
    public void saveConfig() {
        plugin.saveConfig();
        PrettyLogger.debug("Configuration saved");
    }

    // ═══════════════════════════════════════════════════════════════
    // General Settings
    // ═══════════════════════════════════════════════════════════════

    public boolean isDebugMode() {
        return config.getBoolean("debug-mode", false);
    }

    public void setDebugMode(boolean enabled) {
        config.set("debug-mode", enabled);
        saveConfig();
    }

    public String getPrefixColor() {
        return config.getString("prefix-color", "#AA55FF");
    }

    // ═══════════════════════════════════════════════════════════════
    // Feature Toggles
    // ═══════════════════════════════════════════════════════════════

    public boolean isElytraBlockerEnabled() {
        return config.getBoolean("features.elytra-blocker", true);
    }

    public boolean isLightningFixEnabled() {
        return config.getBoolean("features.lightning-fix", true);
    }

    public boolean isCoiProtectionEnabled() {
        return config.getBoolean("features.coi-protection", true);
    }

    public boolean isRecipeManagerEnabled() {
        return config.getBoolean("features.recipe-manager", true);
    }

    // ═══════════════════════════════════════════════════════════════
    // CoI Protection Settings
    // ═══════════════════════════════════════════════════════════════

    public boolean isResetAttributesOnJoin() {
        return config.getBoolean("coi-protection.reset-attributes-on-join", true);
    }

    public boolean isRestrictSpectatorNoclip() {
        return config.getBoolean("coi-protection.restrict-spectator-noclip", true);
    }

    public boolean isBlockNightmarePickups() {
        return config.getBoolean("coi-protection.block-nightmare-pickups", true);
    }

    public boolean isNightmareKeepInventory() {
        return config.getBoolean("coi-protection.nightmare-keep-inventory", true);
    }

    // ═══════════════════════════════════════════════════════════════
    // Recipe Manager Settings
    // ═══════════════════════════════════════════════════════════════

    public boolean isRecipesEnabled() {
        return config.getBoolean("recipes.enabled", true);
    }

    public int getMaxRecipes() {
        return config.getInt("recipes.max-recipes", 100);
    }

    public boolean isLogRecipeChanges() {
        return config.getBoolean("recipes.log-changes", true);
    }

    // ═══════════════════════════════════════════════════════════════
    // Logging Settings
    // ═══════════════════════════════════════════════════════════════

    public boolean isUseColors() {
        return config.getBoolean("logging.use-colors", true);
    }

    public boolean isShowHeader() {
        return config.getBoolean("logging.show-header", true);
    }

    public String getMinLogLevel() {
        return config.getString("logging.min-level", "INFO");
    }

    public boolean isLogCommands() {
        return config.getBoolean("logging.log-commands", true);
    }

    public boolean isLogFeatures() {
        return config.getBoolean("logging.log-features", true);
    }

    // ═══════════════════════════════════════════════════════════════
    // Performance Settings
    // ═══════════════════════════════════════════════════════════════

    public boolean isAsyncProcessing() {
        return config.getBoolean("performance.async-processing", true);
    }

    public boolean isEnableCaching() {
        return config.getBoolean("performance.enable-caching", true);
    }

    // ═══════════════════════════════════════════════════════════════
    // Utility Methods
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get the config version
     */
    public int getConfigVersion() {
        return config.getInt("config-version", 1);
    }

    /**
     * Get the raw FileConfiguration object
     */
    public FileConfiguration getConfig() {
        return config;
    }
}
