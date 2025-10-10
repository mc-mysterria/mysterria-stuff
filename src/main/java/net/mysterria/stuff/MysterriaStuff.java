package net.mysterria.stuff;

import net.mysterria.stuff.features.battlepass.NetheriteElytraBlocker;
import net.mysterria.stuff.commands.MainCommand;
import net.mysterria.stuff.commands.MainCommandTabCompleter;
import net.mysterria.stuff.features.coi.DangerousActionsListener;
import net.mysterria.stuff.config.ConfigManager;
import net.mysterria.stuff.features.husktowns.LightningStrikeFix;
import net.mysterria.stuff.features.recipes.RecipeManager;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.plugin.java.JavaPlugin;

public final class MysterriaStuff extends JavaPlugin {

    private static MysterriaStuff instance;
    private ConfigManager configManager;
    private RecipeManager recipeManager;

    @Override
    public void onEnable() {
        instance = this;

        // Load configuration first
        configManager = new ConfigManager(this);

        // Set debug mode from config
        PrettyLogger.setDebugMode(configManager.isDebugMode());

        // Header with beautiful formatting
        if (configManager.isShowHeader()) {
            PrettyLogger.header("MysterriaStuff Initializing");
        }

        PrettyLogger.info("Starting MysterriaStuff v1.0.0");
        PrettyLogger.debug("Debug mode: " + (PrettyLogger.isDebugMode() ? "enabled" : "disabled"));
        PrettyLogger.debug("Config version: " + configManager.getConfigVersion());

        // Register command
        if (getServer().getPluginCommand("mysterriastuff") != null) {
            getServer().getPluginCommand("mysterriastuff").setExecutor(new MainCommand());
            getServer().getPluginCommand("mysterriastuff").setTabCompleter(new MainCommandTabCompleter());
            PrettyLogger.debug("Registered main command with tab completion");
        }

        // Register event listeners based on config
        PrettyLogger.info("Registering event listeners...");

        if (configManager.isElytraBlockerEnabled()) {
            getServer().getPluginManager().registerEvents(new NetheriteElytraBlocker(), this);
            PrettyLogger.feature("Reinforced Elytra Blocker");
        }

        if (configManager.isLightningFixEnabled()) {
            getServer().getPluginManager().registerEvents(new LightningStrikeFix(), this);
            PrettyLogger.feature("Lightning Strike Fix (HuskTowns)");
        }

        if (configManager.isCoiProtectionEnabled()) {
            getServer().getPluginManager().registerEvents(new DangerousActionsListener(), this);
            PrettyLogger.feature("CoI Dangerous Actions Listener");
        }

        // Initialize recipe manager
        if (configManager.isRecipeManagerEnabled()) {
            PrettyLogger.info("Initializing recipe manager...");
            recipeManager = new RecipeManager();
            recipeManager.initialize();
            PrettyLogger.feature("Runtime Recipe Manager");
        }

        PrettyLogger.success("MysterriaStuff enabled successfully!");
        PrettyLogger.info("Use /mystuff help for available commands");

        if (configManager.isShowHeader()) {
            PrettyLogger.header("Initialization Complete");
        }
    }

    @Override
    public void onDisable() {
        PrettyLogger.warn("MysterriaStuff is shutting down...");
        PrettyLogger.info("Thanks for using MysterriaStuff!");
    }

    public static MysterriaStuff getInstance() {
        return instance;
    }

    public RecipeManager getRecipeManager() {
        return recipeManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

}
