package net.mysterria.stuff.features.hmcwraps;

import de.skyslycer.hmcwraps.serialization.wrap.Wrap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * Maps wraps to their categories by reading HMCWraps configuration files
 */
public class WrapCategoryMapper {

    private final Plugin plugin;
    private final Map<String, String> wrapToCategory = new HashMap<>();

    public WrapCategoryMapper(Plugin plugin) {
        this.plugin = plugin;
        loadCategoryMappings();
    }

    /**
     * Load all wrap-to-category mappings from HMCWraps config files
     */
    private void loadCategoryMappings() {
        File hmcWrapsFolder = new File(plugin.getDataFolder().getParentFile(), "HMCWraps");
        File wrapsFolder = new File(hmcWrapsFolder, "wraps");

        if (!wrapsFolder.exists() || !wrapsFolder.isDirectory()) {
            plugin.getLogger().warning("HMCWraps wraps folder not found at: " + wrapsFolder.getAbsolutePath());
            return;
        }

        File[] yamlFiles = wrapsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));

        if (yamlFiles == null || yamlFiles.length == 0) {
            plugin.getLogger().warning("No YAML files found in HMCWraps wraps folder");
            return;
        }

        for (File yamlFile : yamlFiles) {
            try {
                parseWrapFile(yamlFile);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to parse wrap file: " + yamlFile.getName(), e);
            }
        }

        plugin.getLogger().info("Loaded " + wrapToCategory.size() + " wrap category mappings from HMCWraps configs");
    }

    /**
     * Parse a single YAML file and extract wrap categories
     */
    private void parseWrapFile(File yamlFile) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(yamlFile);

        // Check if the wrap file is enabled
        if (!config.getBoolean("enabled", true)) {
            return;
        }

        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection == null) {
            return;
        }

        // Iterate through categories (SWORDS, PICKAXES, HELMETS, etc.)
        for (String category : itemsSection.getKeys(false)) {
            ConfigurationSection categorySection = itemsSection.getConfigurationSection(category);
            if (categorySection == null) {
                continue;
            }

            ConfigurationSection wrapsSection = categorySection.getConfigurationSection("wraps");
            if (wrapsSection == null) {
                continue;
            }

            // Map each wrap ID to its category
            for (String wrapId : wrapsSection.getKeys(false)) {
                wrapToCategory.put(wrapId, normalizeCategory(category));
            }
        }
    }

    /**
     * Normalize category names to be more user-friendly
     */
    private String normalizeCategory(String rawCategory) {
        return switch (rawCategory.toLowerCase()) {
            case "swords" -> "Swords";
            case "pickaxes" -> "Pickaxes";
            case "axes" -> "Axes";
            case "shovels" -> "Shovels";
            case "hoes" -> "Hoes";
            case "helmets" -> "Helmets";
            case "chestplates" -> "Chestplates";
            case "leggings" -> "Leggings";
            case "boots" -> "Boots";
            case "bows" -> "Bows";
            case "crossbows" -> "Crossbows";
            case "tridents" -> "Tridents";
            case "shields" -> "Shields";
            case "elytra", "elytras" -> "Elytra";
            default -> capitalizeFirst(rawCategory);
        };
    }

    /**
     * Capitalize first letter of a string
     */
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    /**
     * Get the category for a specific wrap
     */
    public String getCategory(String wrapId) {
        return wrapToCategory.getOrDefault(wrapId, "Other");
    }

    /**
     * Categorize a map of wraps
     */
    public Map<String, List<Wrap>> categorizeWraps(Map<String, Wrap> allWraps) {
        Map<String, List<Wrap>> categorized = new LinkedHashMap<>();

        for (Map.Entry<String, Wrap> entry : allWraps.entrySet()) {
            String wrapId = entry.getKey();
            Wrap wrap = entry.getValue();
            String category = getCategory(wrapId);

            categorized.computeIfAbsent(category, k -> new ArrayList<>()).add(wrap);
        }

        // Sort categories alphabetically, but put "Other" at the end
        return categorized.entrySet().stream()
                .sorted((e1, e2) -> {
                    if (e1.getKey().equals("Other")) return 1;
                    if (e2.getKey().equals("Other")) return -1;
                    return e1.getKey().compareTo(e2.getKey());
                })
                .collect(LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                        LinkedHashMap::putAll);
    }

    /**
     * Reload category mappings from disk
     */
    public void reload() {
        wrapToCategory.clear();
        loadCategoryMappings();
    }
}