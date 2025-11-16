package net.mysterria.stuff.features.hmcwraps;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.config.ConfigManager;
import net.mysterria.stuff.utils.AdventureUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages Universal Token creation, validation, and utility methods
 */
public class UniversalTokenManager {

    private static UniversalTokenManager instance;
    private final MysterriaStuff plugin;
    private final ConfigManager configManager;
    private final NamespacedKey tokenKey;
    private final LegacyComponentSerializer serializer;

    private UniversalTokenManager(MysterriaStuff plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.tokenKey = AdventureUtil.getNamespacedKey("universal_token");
        this.serializer = LegacyComponentSerializer.builder().character('&').build();
    }

    /**
     * Initialize the UniversalTokenManager singleton
     */
    public static void initialize(MysterriaStuff plugin) {
        if (instance == null) {
            instance = new UniversalTokenManager(plugin);
        }
    }

    /**
     * Get the singleton instance
     */
    public static UniversalTokenManager getInstance() {
        return instance;
    }

    /**
     * Create a Universal Token item
     * @param amount The stack size
     * @return ItemStack of the token
     */
    public ItemStack createToken(int amount) {
        ItemStack token = new ItemStack(Material.PAPER, amount);
        ItemMeta meta = token.getItemMeta();

        if (meta != null) {
            String nameString = configManager.getTokenItemName();
            Component name = serializer.deserialize(nameString);
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));

            List<String> loreStrings = configManager.getTokenItemLore();
            List<Component> lore = new ArrayList<>();
            for (String line : loreStrings) {
                lore.add(serializer.deserialize(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);

            meta.getPersistentDataContainer().set(tokenKey, PersistentDataType.BYTE, (byte) 1);

            token.setItemMeta(meta);
        }

        return token;
    }

    /**
     * Check if an ItemStack is a Universal Token
     * @param item The item to check
     * @return true if it's a valid token
     */
    public boolean isToken(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        return meta.getPersistentDataContainer().has(tokenKey, PersistentDataType.BYTE);
    }

    /**
     * Consume a token from an ItemStack
     * @param item The item stack containing tokens
     * @param amount The number of tokens to consume
     * @return true if successfully consumed
     */
    public boolean consumeToken(ItemStack item, int amount) {
        if (!isToken(item)) {
            return false;
        }

        if (item.getAmount() < amount) {
            return false;
        }

        item.setAmount(item.getAmount() - amount);
        return true;
    }

    /**
     * Get formatted message from config
     * @param key The message key
     * @return Formatted Component
     */
    public Component getMessage(String key) {
        String message = configManager.getTokenMessage(key);
        return serializer.deserialize(message).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Get formatted message with placeholder replacement
     * @param key The message key
     * @param placeholders Pairs of placeholder name and value
     * @return Formatted Component
     */
    public Component getMessage(String key, String... placeholders) {
        String message = configManager.getTokenMessage(key);

        for (int i = 0; i < placeholders.length - 1; i += 2) {
            message = message.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }

        return serializer.deserialize(message).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Get the config manager
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Get the plugin instance
     */
    public MysterriaStuff getPlugin() {
        return plugin;
    }
}
