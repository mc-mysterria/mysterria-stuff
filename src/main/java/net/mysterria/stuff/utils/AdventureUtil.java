package net.mysterria.stuff.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.mysterria.stuff.MysterriaStuff;
import org.bukkit.NamespacedKey;

public class AdventureUtil {

    public static Component format(String message, NamedTextColor color) {
        return Component.text(message).color(color).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Gets a NamespacedKey for CircleOfImagination plugin
     * @param key The key identifier
     * @return NamespacedKey with CircleOfImagination namespace
     */
    public static NamespacedKey getCoINamespacedKey(String key) {
        return new NamespacedKey("circleofimagination", key);
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
