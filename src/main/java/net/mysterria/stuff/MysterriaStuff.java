package net.mysterria.stuff;

import de.skyslycer.hmcwraps.HMCWraps;
import net.mysterria.stuff.commands.MainCommand;
import net.mysterria.stuff.commands.MainCommandTabCompleter;
import net.mysterria.stuff.config.ConfigManager;
import net.mysterria.stuff.features.battlepass.NetheriteElytraBlocker;
import net.mysterria.stuff.features.chatcontrol.ChatControlMessageManager;
import net.mysterria.stuff.features.chatcontrol.ChatControlSessionHandler;
import net.mysterria.stuff.features.chatcontrol.ChatControlTokenListener;
import net.mysterria.stuff.features.coi.BoosterPatriarchListener;
import net.mysterria.stuff.features.coi.DangerousActionsListener;
import net.mysterria.stuff.features.coi.LeoderoStrikeListener;
import net.mysterria.stuff.features.hmcwraps.listener.UniversalTokenListener;
import net.mysterria.stuff.features.hmcwraps.UniversalTokenManager;
import net.mysterria.stuff.features.hmcwraps.listener.WrapPreviewListener;
import net.mysterria.stuff.features.husktowns.LightningStrikeFix;
import net.mysterria.stuff.features.recipes.RecipeManager;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class MysterriaStuff extends JavaPlugin {

    private static MysterriaStuff instance;
    private ConfigManager configManager;
    private RecipeManager recipeManager;
    private BoosterPatriarchListener boosterPatriarchListener;
    private ChatControlSessionHandler chatControlSessionHandler;

    public static MysterriaStuff getInstance() {
        return instance;
    }

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
            getServer().getPluginManager().registerEvents(new LeoderoStrikeListener(this), this);
            PrettyLogger.feature("CoI Dangerous Actions Listener");
        }

        if (configManager.isBoosterPatriarchEnabled()) {
            boosterPatriarchListener = new BoosterPatriarchListener(this);
            getServer().getPluginManager().registerEvents(boosterPatriarchListener, this);
            PrettyLogger.feature("CoI Booster Patriarch System");
        }

        // Initialize Universal Token system
        if (configManager.isUniversalTokenEnabled()) {
            HMCWraps hmcWraps = loadHmcWraps();
            if (hmcWraps != null) {
                PrettyLogger.info("Initializing Universal Token system...");
                UniversalTokenManager.initialize(this);

                WrapPreviewListener previewHandler = new WrapPreviewListener(hmcWraps);
                getServer().getPluginManager().registerEvents(previewHandler, this);
                getServer().getPluginManager().registerEvents(new UniversalTokenListener(this, hmcWraps, previewHandler), this);

                PrettyLogger.feature("Universal Token (HMCWraps Integration)");
            } else {
                PrettyLogger.warn("Universal Token enabled but HMCWraps plugin not found!");
            }
        }

        // Initialize ChatControl Message Token system
        if (configManager.isChatControlTokenEnabled()) {
            PrettyLogger.info("Initializing ChatControl Message Token system...");
            ChatControlMessageManager.initialize(this);

            chatControlSessionHandler = new ChatControlSessionHandler(this);
            getServer().getPluginManager().registerEvents(chatControlSessionHandler, this);
            getServer().getPluginManager().registerEvents(new ChatControlTokenListener(chatControlSessionHandler), this);

            PrettyLogger.feature("ChatControl Message Token (Custom Join/Quit Messages)");
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

    private HMCWraps loadHmcWraps() {
        String name = "HMCWraps";
        try {
            boolean enabled = Bukkit.getPluginManager().isPluginEnabled(name);
            PrettyLogger.debug(name + " enabled: " + enabled);
            Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
            if (plugin == null) {
                PrettyLogger.warn(name + " plugin not found (getPlugin returned null).");
                return null;
            }
            PrettyLogger.debug(name + " plugin instance class: " + plugin.getClass().getName());
            if (!(plugin instanceof HMCWraps)) {
                PrettyLogger.warn(name + " found but is not an instance of HMCWraps. Actual: " + plugin.getClass().getName());
                return null;
            }
            return (HMCWraps) plugin;
        } catch (ClassCastException e) {
            PrettyLogger.warn("Failed to cast " + name + " plugin to HMCWraps: " + e.toString());
            return null;
        } catch (Throwable t) {
            PrettyLogger.warn("Unexpected error while loading " + name + ": " + t.toString());
            return null;
        }
    }

    @Override
    public void onDisable() {
        // Shutdown booster patriarch listener if it was enabled
        if (boosterPatriarchListener != null) {
            boosterPatriarchListener.shutdown();
        }

        PrettyLogger.warn("MysterriaStuff is shutting down...");
        PrettyLogger.info("Thanks for using MysterriaStuff!");
    }

    public RecipeManager getRecipeManager() {
        return recipeManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ChatControlSessionHandler getChatControlSessionHandler() {
        return chatControlSessionHandler;
    }

}
