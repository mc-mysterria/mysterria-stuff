package net.mysterria.stuff.config;

import lombok.Getter;
import net.mysterria.stuff.config.models.BoosterSettings;
import net.mysterria.stuff.config.models.Features;
import net.mysterria.stuff.config.models.TokenSettings;
import net.mysterria.stuff.config.models.WrapMenuTitles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private boolean debugMode;
    private boolean lastSprintActive;
    private Features features;
    private Map<String, List<String>> chatAliases;
    private BoosterSettings boosterSettings;
    private TokenSettings universalToken;
    private WrapMenuTitles wrapMenuTitles;
    private TokenSettings joinMessageToken;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        config.options().copyDefaults(true);
        plugin.saveConfig();

        debugMode = requireBoolean("debug-mode");
        lastSprintActive = requireBoolean("last-sprint.active");
        features = new Features(
                requireBoolean("features.booster-patriarch"),
                requireBoolean("features.ritual-fallbacks"),
                requireBoolean("features.universal-token"),
                requireBoolean("features.joinmsg-token"),
                requireBoolean("features.last-sprint"),
                requireBoolean("features.chat-aliases"));
        chatAliases = readAliases();
        boosterSettings = new BoosterSettings(
                requireString("coi-booster-patriarch.api-url"),
                requireInt("coi-booster-patriarch.update-interval-seconds"));
        universalToken = readToken("universal-token");
        wrapMenuTitles = new WrapMenuTitles(
                requireString("universal-token.gui-main-title"),
                requireString("universal-token.gui-category-title"),
                requireString("universal-token.gui-confirm-title"));
        joinMessageToken = readToken("joinmsg-token");
    }

    public void setDebugMode(boolean enabled) {
        debugMode = enabled;
        config.set("debug-mode", enabled);
        plugin.saveConfig();
    }

    public void setLastSprintActive(boolean active) {
        lastSprintActive = active;
        config.set("last-sprint.active", active);
        plugin.saveConfig();
    }

    private Map<String, List<String>> readAliases() {
        ConfigurationSection section = requireSection("chat-aliases.aliases");
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        for (String route : section.getKeys(false)) {
            aliases.put(route, section.getStringList(route));
        }
        return aliases;
    }

    private TokenSettings readToken(String path) {
        ConfigurationSection messages = requireSection(path + ".messages");
        Map<String, String> texts = new LinkedHashMap<>();
        for (String key : messages.getKeys(false)) {
            texts.put(key, messages.getString(key));
        }
        if (!config.isList(path + ".item-lore")) {
            throw invalid(path + ".item-lore");
        }
        return new TokenSettings(requireString(path + ".item-name"), config.getStringList(path + ".item-lore"), texts);
    }

    private boolean requireBoolean(String path) {
        if (!config.isBoolean(path)) {
            throw invalid(path);
        }
        return config.getBoolean(path);
    }

    private int requireInt(String path) {
        if (!config.isInt(path)) {
            throw invalid(path);
        }
        return config.getInt(path);
    }

    private String requireString(String path) {
        if (!config.isString(path)) {
            throw invalid(path);
        }
        return config.getString(path);
    }

    private ConfigurationSection requireSection(String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            throw invalid(path);
        }
        return section;
    }

    private IllegalStateException invalid(String path) {
        return new IllegalStateException("config.yml: missing or invalid '" + path + "'");
    }
}
