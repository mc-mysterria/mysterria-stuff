package net.mysterria.stuff;

import de.skyslycer.hmcwraps.HMCWraps;
import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;
import lombok.Getter;
import net.mysterria.stuff.command.admin.BoosterSubcommand;
import net.mysterria.stuff.command.admin.JoinMessageSubcommand;
import net.mysterria.stuff.command.admin.LastSprintSubcommand;
import net.mysterria.stuff.command.admin.StuffCommand;
import net.mysterria.stuff.command.admin.Subcommand;
import net.mysterria.stuff.command.admin.TokenSubcommand;
import net.mysterria.stuff.config.ConfigManager;
import net.mysterria.stuff.config.models.Features;
import net.mysterria.stuff.domain.booster.listener.BoosterJoinListener;
import net.mysterria.stuff.domain.booster.service.BoosterPatriarchService;
import net.mysterria.stuff.domain.chat.listener.ZelChatLifecycleListener;
import net.mysterria.stuff.domain.chat.service.ChatShortcutService;
import net.mysterria.stuff.domain.entity.listener.CamelAiListener;
import net.mysterria.stuff.domain.entity.listener.SkeletonHorseListener;
import net.mysterria.stuff.domain.joinmessage.listener.JoinMessageSessionListener;
import net.mysterria.stuff.domain.joinmessage.listener.JoinQuitMessageListener;
import net.mysterria.stuff.domain.joinmessage.service.JoinMessageSessionService;
import net.mysterria.stuff.domain.joinmessage.service.JoinMessageStore;
import net.mysterria.stuff.domain.lastsprint.listener.LastSprintJoinListener;
import net.mysterria.stuff.domain.lastsprint.listener.LastSprintKitMenuListener;
import net.mysterria.stuff.domain.lastsprint.service.LastSprintService;
import net.mysterria.stuff.domain.ritual.listener.TargetPracticeListener;
import net.mysterria.stuff.domain.ritual.service.TargetPracticeService;
import net.mysterria.stuff.domain.wrap.listener.UniversalTokenListener;
import net.mysterria.stuff.domain.wrap.menu.WrapExchangeMenu;
import net.mysterria.stuff.domain.wrap.service.WrapCategoryService;
import net.mysterria.stuff.util.item.TokenItem;
import net.mysterria.stuff.util.log.PrettyLogger;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class MysterriaStuff extends JavaPlugin {

    @Getter
    private ConfigManager configManager;
    @Getter
    private final List<String> activeFeatures = new ArrayList<>();
    private final List<Subcommand> subcommands = new ArrayList<>();
    private ChatShortcutService chatShortcuts;
    private WrapCategoryService wrapCategories;
    private JoinMessageStore joinMessageStore;
    private JoinMessageSessionService joinMessageSessions;
    private BoosterPatriarchService boosterPatriarch;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        PrettyLogger.setDebugMode(configManager.isDebugMode());
        Features features = configManager.getFeatures();

        CamelAiListener camelAi = new CamelAiListener(this);
        register(camelAi, new SkeletonHorseListener());
        camelAi.restoreAlreadyLoadedCamels();

        CircleOfImaginationAPI coi = loadCoi();
        if (features.isBoosterPatriarch()) {
            enableBoosterPatriarch(coi);
        }
        if (features.isRitualFallbacks()) {
            enableRitualFallbacks(coi);
        }
        if (features.isUniversalToken()) {
            enableUniversalToken();
        }
        if (features.isJoinMessageToken()) {
            enableJoinMessages();
        }
        if (features.isLastSprint()) {
            enableLastSprint();
        }
        if (features.isChatAliases()) {
            chatShortcuts = new ChatShortcutService(this);
            register(new ZelChatLifecycleListener(chatShortcuts));
            chatShortcuts.scheduleRegistration();
            activeFeatures.add("chat shortcuts");
        }

        StuffCommand command = new StuffCommand(this, subcommands);
        getCommand("mysterriastuff").setExecutor(command);
        getCommand("mysterriastuff").setTabCompleter(command);
        PrettyLogger.info("Enabled: " + String.join(", ", activeFeatures));
    }

    @Override
    public void onDisable() {
        if (chatShortcuts != null) {
            chatShortcuts.close();
        }
        if (boosterPatriarch != null) {
            boosterPatriarch.shutdown();
        }
        if (joinMessageSessions != null) {
            joinMessageSessions.abandonAll();
        }
    }

    public void reload() {
        configManager.load();
        PrettyLogger.setDebugMode(configManager.isDebugMode());
        if (chatShortcuts != null) {
            chatShortcuts.reload();
        }
        if (wrapCategories != null) {
            wrapCategories.reload();
        }
        if (joinMessageStore != null) {
            joinMessageStore.load();
        }
    }

    private void enableBoosterPatriarch(CircleOfImaginationAPI coi) {
        if (coi == null) {
            PrettyLogger.warn("Booster Patriarch needs CircleOfImagination, skipping");
            return;
        }
        boosterPatriarch = new BoosterPatriarchService(this, coi, configManager.getBoosterSettings());
        register(new BoosterJoinListener(boosterPatriarch));
        subcommands.add(new BoosterSubcommand(boosterPatriarch));
        activeFeatures.add("booster patriarch");
    }

    private void enableRitualFallbacks(CircleOfImaginationAPI coi) {
        if (coi == null) {
            PrettyLogger.warn("Ritual fallbacks need CircleOfImagination, skipping");
            return;
        }
        register(new TargetPracticeListener(new TargetPracticeService(coi)));
        activeFeatures.add("ritual fallbacks");
    }

    private void enableUniversalToken() {
        if (!getServer().getPluginManager().isPluginEnabled("HMCWraps")) {
            PrettyLogger.warn("Universal Wrap Token needs HMCWraps, skipping");
            return;
        }
        HMCWraps hmcWraps = (HMCWraps) getServer().getPluginManager().getPlugin("HMCWraps");
        TokenItem token = new TokenItem(List.of(new NamespacedKey(this, "universal_token")), configManager::getUniversalToken);
        wrapCategories = new WrapCategoryService(this);
        WrapExchangeMenu menu = new WrapExchangeMenu(this, hmcWraps, configManager, token, wrapCategories);
        register(new UniversalTokenListener(token, menu));
        subcommands.add(new TokenSubcommand(token));
        activeFeatures.add("universal wrap token");
    }

    private void enableJoinMessages() {
        TokenItem token = new TokenItem(List.of(new NamespacedKey(this, "joinmsg_token"),
                new NamespacedKey(this, "chatcontrol_token")), configManager::getJoinMessageToken);
        joinMessageStore = new JoinMessageStore(this);
        joinMessageSessions = new JoinMessageSessionService(joinMessageStore, token);
        register(new JoinQuitMessageListener(joinMessageStore), new JoinMessageSessionListener(this, token, joinMessageSessions));
        subcommands.add(new JoinMessageSubcommand(token, joinMessageStore, joinMessageSessions));
        activeFeatures.add("join/quit messages");
    }

    private void enableLastSprint() {
        LastSprintService lastSprint = new LastSprintService(this);
        register(new LastSprintJoinListener(this, configManager, lastSprint), new LastSprintKitMenuListener());
        subcommands.add(new LastSprintSubcommand(configManager, lastSprint));
        activeFeatures.add("last sprint");
    }

    private CircleOfImaginationAPI loadCoi() {
        if (!getServer().getPluginManager().isPluginEnabled("CircleOfImagination")) {
            return null;
        }
        return getServer().getServicesManager().load(CircleOfImaginationAPI.class);
    }

    private void register(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }
}
