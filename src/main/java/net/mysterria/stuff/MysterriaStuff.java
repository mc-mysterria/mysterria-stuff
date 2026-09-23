package net.mysterria.stuff;

import net.mysterria.stuff.audit.StuffAuditEmitter;

import de.skyslycer.hmcwraps.HMCWraps;
import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;
import net.mysterria.stuff.commands.MainCommand;
import net.mysterria.stuff.commands.MainCommandTabCompleter;
import net.mysterria.stuff.config.ConfigManager;
import net.mysterria.stuff.features.battlepass.NetheriteElytraBlocker;
import net.mysterria.stuff.features.coi.*;
import net.mysterria.stuff.features.dungeons.DungeonWorldEnforcer;
import net.mysterria.stuff.features.chat.ChatAliasIntegration;
import net.mysterria.stuff.features.chat.ZelChatAliasIntegration;
import net.mysterria.stuff.features.entities.CamelAiListener;
import net.mysterria.stuff.features.entities.SkeletonHorseListener;
import net.mysterria.stuff.features.hmcwraps.UniversalTokenManager;
import net.mysterria.stuff.features.hmcwraps.listener.UniversalTokenListener;
import net.mysterria.stuff.features.hmcwraps.listener.WrapPreviewListener;
import net.mysterria.stuff.features.husktowns.LightningStrikeFix;
import net.mysterria.stuff.features.joinmsg.*;
import net.mysterria.stuff.features.lastsprint.LastSprint;
import net.mysterria.stuff.features.lastsprint.LastSprintGUI;
import net.mysterria.stuff.features.lastsprint.LastSprintListener;
import net.mysterria.stuff.features.recipes.RecipeManager;
import net.mysterria.stuff.features.zones.CoiZoneManager;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class MysterriaStuff extends JavaPlugin {

    private static MysterriaStuff instance;

    private CircleOfImaginationAPI coiAPI;

    private ConfigManager configManager;
    private RecipeManager recipeManager;
    private BoosterPatriarchListener boosterPatriarchListener;
    private JoinMsgSessionHandler joinMsgSessionHandler;
    private JoinMsgStore joinMsgStore;
    private CoiZoneManager coiZoneManager;
    private LastSprint lastSprint;
    private LastSprintGUI lastSprintGUI;
    private ChatAliasIntegration chatAliasIntegration;
    private boolean chatAliasRegistrationQueued;

    public static MysterriaStuff getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        StuffAuditEmitter.initialize(this);
        instance = this;


        configManager = new ConfigManager(this);


        PrettyLogger.setDebugMode(configManager.isDebugMode());


        if (configManager.isShowHeader()) {
            PrettyLogger.header("MysterriaStuff Initializing");
        }

        PrettyLogger.info("Starting MysterriaStuff v1.0.0");
        PrettyLogger.debug("Debug mode: " + (PrettyLogger.isDebugMode() ? "enabled" : "disabled"));
        PrettyLogger.debug("Config version: " + configManager.getConfigVersion());


        if (getServer().getPluginCommand("mysterriastuff") != null) {
            Objects.requireNonNull(getServer().getPluginCommand("mysterriastuff")).setExecutor(new MainCommand());
            Objects.requireNonNull(getServer().getPluginCommand("mysterriastuff")).setTabCompleter(new MainCommandTabCompleter());
            PrettyLogger.debug("Registered main command with tab completion");
        }


        PrettyLogger.info("Registering event listeners...");
        getServer().getPluginManager().registerEvents(new ChatAliasLifecycleListener(this), this);

        CamelAiListener camelAiListener = new CamelAiListener(this);
        getServer().getPluginManager().registerEvents(camelAiListener, this);
        camelAiListener.restoreAlreadyLoadedCamels();
        PrettyLogger.feature("Camel AI restoration");

        getServer().getPluginManager().registerEvents(new SkeletonHorseListener(), this);
        PrettyLogger.feature("Skeleton horse taming");

        if (configManager.isElytraBlockerEnabled()) {
            getServer().getPluginManager().registerEvents(new NetheriteElytraBlocker(), this);
            PrettyLogger.feature("Reinforced Elytra Blocker");
        }

        if (configManager.isLightningFixEnabled()) {
            getServer().getPluginManager().registerEvents(new LightningStrikeFix(), this);
            PrettyLogger.feature("Lightning Strike Fix (HuskTowns)");
        }

        if (configManager.isCoiProtectionEnabled()) {
            coiZoneManager = new CoiZoneManager(this, configManager.getCoiZones());
            getServer().getPluginManager().registerEvents(coiZoneManager, this);
            getServer().getPluginManager().registerEvents(new LeoderoStrikeListener(this), this);
            getServer().getPluginManager().registerEvents(new AmanisesListener(this), this);
            getServer().getPluginManager().registerEvents(new AucusesListener(this), this);
            getServer().getPluginManager().registerEvents(new HerabergenListener(this), this);
            getServer().getPluginManager().registerEvents(new CheekListener(this), this);
            getServer().getPluginManager().registerEvents(new LilithListener(this), this);
            getServer().getPluginManager().registerEvents(new StianoListener(this), this);
            PrettyLogger.feature("CoI Dangerous Actions Listener");
            if (coiZoneManager.isEnabled()) {
                PrettyLogger.feature("CoI Zone Restrictions");
            }
        }

        if (configManager.isBoosterPatriarchEnabled()) {
            loadCoiApi();
            BoosterPatriarchListener boosterPatriarchListener = new BoosterPatriarchListener(this);
            getServer().getPluginManager().registerEvents(boosterPatriarchListener, this);
            PrettyLogger.feature("CoI Booster Patriarch System");
        }

        if (configManager.isRitualFallbacksEnabled()) {
            loadCoiApi();
            if (getCoiAPI() != null) {
                TargetPracticeRitualListener targetPracticeRitualListener = new TargetPracticeRitualListener(this);
                getServer().getPluginManager().registerEvents(targetPracticeRitualListener, this);
                PrettyLogger.feature("CoI Ritual Fallbacks");
            } else {
                PrettyLogger.warn("CoI ritual fallbacks enabled but CircleOfImagination API is unavailable");
            }
        }

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


        if (configManager.isJoinMsgTokenEnabled()) {
            PrettyLogger.info("Initializing Custom Message Token system...");
            JoinMsgTokenManager.initialize(this);

            joinMsgStore = new JoinMsgStore(this);
            joinMsgSessionHandler = new JoinMsgSessionHandler(this, joinMsgStore);
            getServer().getPluginManager().registerEvents(joinMsgSessionHandler, this);
            getServer().getPluginManager().registerEvents(new JoinMsgTokenListener(joinMsgSessionHandler), this);
            getServer().getPluginManager().registerEvents(new JoinMsgListener(joinMsgStore), this);

            PrettyLogger.feature("Custom Join/Quit Message Token");
        }


        if (configManager.isRecipeManagerEnabled()) {
            PrettyLogger.info("Initializing recipe manager...");
            recipeManager = new RecipeManager();
            recipeManager.initialize();
            PrettyLogger.feature("Runtime Recipe Manager");
        }

        if (configManager.isDungeonWorldEnforcerEnabled()) {
            DungeonWorldEnforcer enforcer = new DungeonWorldEnforcer(this, configManager.getDungeonWorldName());
            getServer().getPluginManager().registerEvents(enforcer, this);
            PrettyLogger.feature("Dungeon World Enforcer (" + configManager.getDungeonWorldName() + " → FLAT)");
        }

        if (configManager.isLastSprintEnabled()) {
            lastSprint = new LastSprint(this);
            lastSprintGUI = new LastSprintGUI(lastSprint);
            LastSprintListener lastSprintListener = new LastSprintListener(this, lastSprint);
            getServer().getPluginManager().registerEvents(lastSprintListener, this);
            getServer().getPluginManager().registerEvents(lastSprintGUI, this);
            PrettyLogger.feature("Last Sprint Welcome Kit");
        }

        scheduleChatAliasIntegrationRegistration();

        PrettyLogger.success("MysterriaStuff enabled successfully!");
        PrettyLogger.info("Use /mystuff help for available commands");

        if (configManager.isShowHeader()) {
            PrettyLogger.header("Initialization Complete");
        }
    }

    private void loadCoiApi() {
        Plugin coiPlugin = getServer().getPluginManager().getPlugin("CircleOfImagination");
        if (coiPlugin == null || !coiPlugin.isEnabled()) {
            PrettyLogger.warn("CircleOfImagination plugin not found or not enabled, boon limit checks will be skipped");
            return;
        }

        try {
            CircleOfImaginationAPI api = Bukkit.getServer().getServicesManager().load(CircleOfImaginationAPI.class);
            if (api == null) {
                PrettyLogger.warn("CircleOfImagination API not registered, boon limit checks will be skipped");
                return;
            }

            this.coiAPI = api;
            PrettyLogger.debug("CircleOfImagination API hooked successfully");
        } catch (Throwable t) {
            PrettyLogger.warn("Failed to hook CircleOfImagination API: " + t.getMessage() + ", boon limit checks will be skipped");
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
            PrettyLogger.warn("Failed to cast " + name + " plugin to HMCWraps: " + e);
            return null;
        } catch (Throwable t) {
            PrettyLogger.warn("Unexpected error while loading " + name + ": " + t);
            return null;
        }
    }

    @Override
    public void onDisable() {
        StuffAuditEmitter.close();

        closeChatAliasIntegration();

        if (coiZoneManager != null) {
            coiZoneManager.shutdown();
        }

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

    public JoinMsgSessionHandler getJoinMsgSessionHandler() {
        return joinMsgSessionHandler;
    }

    public JoinMsgStore getJoinMsgStore() {
        return joinMsgStore;
    }

    public CoiZoneManager getCoiZoneManager() {
        return coiZoneManager;
    }

    public LastSprint getLastSprint() {
        return lastSprint;
    }

    public LastSprintGUI getLastSprintGUI() {
        return lastSprintGUI;
    }

    public CircleOfImaginationAPI getCoiAPI() {
        if (coiAPI == null) {
            try {
                CircleOfImaginationAPI api = Bukkit.getServer().getServicesManager().load(CircleOfImaginationAPI.class);
                if (api != null) {
                    coiAPI = api;
                    PrettyLogger.info("CircleOfImagination API hooked (lazy load)");
                }
            } catch (Throwable ignored) {
            }
        }
        return coiAPI;
    }

    public BoosterPatriarchListener getBoosterPatriarchListener() {
        return boosterPatriarchListener;
    }

    public void reloadChatAliasIntegration() {
        if (!configManager.isChatAliasesEnabled()) {
            closeChatAliasIntegration();
            return;
        }

        Plugin zelChat = getServer().getPluginManager().getPlugin("ZelChat");
        if (zelChat == null || !zelChat.isEnabled()) {
            closeChatAliasIntegration();
            return;
        }

        if (chatAliasIntegration == null) {
            try {
                chatAliasIntegration = ZelChatAliasIntegration.register(this);
                PrettyLogger.feature("ZelChat Channel Aliases");
            } catch (LinkageError | RuntimeException error) {
                PrettyLogger.error("Failed to register ZelChat channel aliases: " + error.getMessage());
            }
            return;
        }

        chatAliasIntegration.reload();
    }

    private void scheduleChatAliasIntegrationRegistration() {
        if (!configManager.isChatAliasesEnabled() || chatAliasRegistrationQueued) {
            return;
        }

        chatAliasRegistrationQueued = true;
        getServer().getScheduler().runTask(this, () -> {
            chatAliasRegistrationQueued = false;
            reloadChatAliasIntegration();
        });
    }

    private void closeChatAliasIntegration() {
        if (chatAliasIntegration == null) {
            return;
        }

        try {
            chatAliasIntegration.close();
        } catch (LinkageError | RuntimeException error) {
            PrettyLogger.error("Failed to unregister ZelChat channel aliases: " + error.getMessage());
        } finally {
            chatAliasIntegration = null;
        }
    }

    /**
     * Kept separate from the plugin class so Bukkit's event-method reflection does not
     * resolve optional COI API types declared by unrelated MysterriaStuff methods before
     * CircleOfImagination has finished loading.
     */
    private static final class ChatAliasLifecycleListener implements Listener {

        private final MysterriaStuff plugin;

        private ChatAliasLifecycleListener(MysterriaStuff plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onPluginEnable(PluginEnableEvent event) {
            if (event.getPlugin().getName().equalsIgnoreCase("ZelChat")) {
                plugin.scheduleChatAliasIntegrationRegistration();
            }
        }

        @EventHandler
        public void onPluginDisable(PluginDisableEvent event) {
            if (event.getPlugin().getName().equalsIgnoreCase("ZelChat")) {
                plugin.closeChatAliasIntegration();
            }
        }
    }
}
