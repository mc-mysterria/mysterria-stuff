package net.mysterria.stuff.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.mysterria.stuff.MysterriaStuff;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public class AdventureUtil {

    public static Component format(String message, NamedTextColor color) {
        return Component.text(message).color(color).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Gets a NamespacedKey for CircleOfImagination plugin
     * @param key The key identifier (can be camelCase)
     * @return NamespacedKey with CircleOfImagination namespace, or null if plugin not found
     */
    public static NamespacedKey getCoINamespacedKey(String key) {
        JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("CircleOfImagination");
        if (plugin != null) {
            return new NamespacedKey(plugin, key);
        }
        return null;
    }

    /**
     * Convert MiniMessage format to legacy ampersand format
     * @param miniMessage The MiniMessage formatted string (e.g., "<light_purple>Text <gray>More")
     * @return Legacy formatted string (e.g., "&dText &7More")
     */
    public static String convertMiniMessageToLegacy(String miniMessage) {
        if (miniMessage == null || miniMessage.isEmpty()) {
            return miniMessage;
        }

        MiniMessage mm = MiniMessage.miniMessage();
        Component component = mm.deserialize(miniMessage);

        return LegacyComponentSerializer.legacyAmpersand().serialize(component);
    }

    /**
     * Gets a NamespacedKey for MysterriaStuff plugin
     * @param key The key identifier
     * @return NamespacedKey with MysterriaStuff namespace
     */
    public static NamespacedKey getNamespacedKey(String key) {
        return new NamespacedKey(MysterriaStuff.getInstance(), key);
    }

    public static String componentToPlainText(Component line) {
        return LegacyComponentSerializer.builder().build().serialize(line);
    }
}
