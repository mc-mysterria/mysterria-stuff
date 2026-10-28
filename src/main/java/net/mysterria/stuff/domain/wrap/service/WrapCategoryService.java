package net.mysterria.stuff.domain.wrap.service;

import de.skyslycer.hmcwraps.serialization.wrap.Wrap;
import net.mysterria.stuff.util.log.PrettyLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class WrapCategoryService {

    public static final String OTHER = "Other";

    private final File wrapsFolder;
    private final Map<String, String> categoryByWrap = new HashMap<>();

    public WrapCategoryService(Plugin plugin) {
        this.wrapsFolder = new File(plugin.getDataFolder().getParentFile(), "HMCWraps/wraps");
        reload();
    }

    public void reload() {
        categoryByWrap.clear();
        File[] files = wrapsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            PrettyLogger.warn("No HMCWraps wrap files found in " + wrapsFolder.getPath());
            return;
        }
        for (File file : files) {
            readWrapFile(YamlConfiguration.loadConfiguration(file));
        }
        PrettyLogger.info("Loaded categories for " + categoryByWrap.size() + " wraps");
    }

    public Map<String, List<Wrap>> categorize(Map<String, Wrap> wraps) {
        Map<String, List<Wrap>> byCategory = new TreeMap<>(Comparator
                .comparing((String category) -> category.equals(OTHER))
                .thenComparing(Comparator.naturalOrder()));
        wraps.forEach((id, wrap) -> byCategory
                .computeIfAbsent(categoryByWrap.getOrDefault(id, OTHER), key -> new ArrayList<>())
                .add(wrap));
        return new LinkedHashMap<>(byCategory);
    }

    private void readWrapFile(YamlConfiguration yaml) {
        ConfigurationSection items = yaml.getConfigurationSection("items");
        if (!yaml.getBoolean("enabled", true) || items == null) {
            return;
        }
        for (String category : items.getKeys(false)) {
            ConfigurationSection wraps = items.getConfigurationSection(category + ".wraps");
            if (wraps != null) {
                wraps.getKeys(false).forEach(id -> categoryByWrap.put(id, displayName(category)));
            }
        }
    }

    private String displayName(String rawCategory) {
        String lower = rawCategory.toLowerCase(Locale.ROOT);
        if (lower.equals("elytras")) {
            return "Elytra";
        }
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
