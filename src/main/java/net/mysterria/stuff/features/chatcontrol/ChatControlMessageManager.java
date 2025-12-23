package net.mysterria.stuff.features.chatcontrol;

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


public class ChatControlMessageManager {

    private static ChatControlMessageManager instance;
    private final MysterriaStuff plugin;
    private final ConfigManager configManager;
    private final NamespacedKey tokenKey;
    private final LegacyComponentSerializer serializer;

    private ChatControlMessageManager(MysterriaStuff plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.tokenKey = AdventureUtil.getNamespacedKey("chatcontrol_token");
        this.serializer = LegacyComponentSerializer.builder().character('&').build();
    }


    public static void initialize(MysterriaStuff plugin) {
        if (instance == null) {
            instance = new ChatControlMessageManager(plugin);
        }
    }


    public static ChatControlMessageManager getInstance() {
        return instance;
    }


    public ItemStack createToken(int amount) {
        ItemStack token = new ItemStack(Material.PAPER, amount);
        ItemMeta meta = token.getItemMeta();

        if (meta != null) {
            String nameString = configManager.getChatControlTokenName();
            Component name = serializer.deserialize(nameString);
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));

            List<String> loreStrings = configManager.getChatControlTokenLore();
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


    public Component getMessage(String key) {
        String message = configManager.getChatControlMessage(key);
        return serializer.deserialize(message).decoration(TextDecoration.ITALIC, false);
    }


    public Component getMessage(String key, String... placeholders) {
        String message = configManager.getChatControlMessage(key);

        for (int i = 0; i < placeholders.length - 1; i += 2) {
            message = message.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }

        return serializer.deserialize(message).decoration(TextDecoration.ITALIC, false);
    }


    public ConfigManager getConfigManager() {
        return configManager;
    }


    public MysterriaStuff getPlugin() {
        return plugin;
    }
}
