package net.mysterria.stuff.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.mysterria.stuff.MysterriaStuff;
import org.bukkit.command.ConsoleCommandSender;


public class PrettyLogger {

    private static boolean debugMode = false;

    private static Component createGradientPrefix() {
        return Component.text("[").color(TextColor.color(0xFF69B4))
                .append(Component.text("M").color(TextColor.color(0xFF1493)))
                .append(Component.text("y").color(TextColor.color(0xDA70D6)))
                .append(Component.text("s").color(TextColor.color(0xBA55D3)))
                .append(Component.text("t").color(TextColor.color(0x9370DB)))
                .append(Component.text("e").color(TextColor.color(0x8A2BE2)))
                .append(Component.text("r").color(TextColor.color(0x9400D3)))
                .append(Component.text("r").color(TextColor.color(0x8B00FF)))
                .append(Component.text("i").color(TextColor.color(0x9932CC)))
                .append(Component.text("a").color(TextColor.color(0xAA00FF)))
                .append(Component.text("]").color(TextColor.color(0xFF69B4)));
    }

    private static Component createSimpleGradientPrefix() {
        return Component.text("[Mysterria]").color(TextColor.color(0xAA55FF));
    }

    public static void log(LogLevel level, String message) {
        if (level == LogLevel.DEBUG && !debugMode) {
            return;
        }

        ConsoleCommandSender console = MysterriaStuff.getInstance().getServer().getConsoleSender();

        Component prefix = createGradientPrefix();
        Component levelComponent = Component.text(" [" + level.getLabel() + "] ").color(level.getColor());
        Component messageComponent = Component.text(message).color(NamedTextColor.WHITE);

        console.sendMessage(prefix.append(levelComponent).append(messageComponent));
    }

    public static void info(String message) {
        log(LogLevel.INFO, message);
    }

    public static void warn(String message) {
        log(LogLevel.WARN, message);
    }

    public static void error(String message) {
        log(LogLevel.ERROR, message);
    }

    public static void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    public static void success(String message) {
        log(LogLevel.SUCCESS, message);
    }

    public static boolean isDebugMode() {
        return debugMode;
    }

    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
        if (enabled) {
            info("Debug mode enabled");
        } else {
            info("Debug mode disabled");
        }
    }

    public static void header(String message) {
        ConsoleCommandSender console = MysterriaStuff.getInstance().getServer().getConsoleSender();

        Component line = Component.text("═".repeat(50)).color(TextColor.color(0xAA55FF));
        Component header = Component.text("  " + message + "  ").color(TextColor.color(0xFFFFFF));

        console.sendMessage(line);
        console.sendMessage(createGradientPrefix().append(header));
        console.sendMessage(line);
    }

    public static void feature(String featureName) {
        ConsoleCommandSender console = MysterriaStuff.getInstance().getServer().getConsoleSender();

        Component prefix = createGradientPrefix();
        Component arrow = Component.text(" ➜ ").color(TextColor.color(0x55FF55));
        Component feature = Component.text(featureName).color(NamedTextColor.AQUA);

        console.sendMessage(prefix.append(arrow).append(feature));
    }


    public enum LogLevel {
        INFO(TextColor.color(0x55FF55), "INFO"),
        WARN(TextColor.color(0xFFAA00), "WARN"),
        ERROR(TextColor.color(0xFF5555), "ERROR"),
        DEBUG(TextColor.color(0xAA55FF), "DEBUG"),
        SUCCESS(TextColor.color(0x55FFFF), "SUCCESS");

        private final TextColor color;
        private final String label;

        LogLevel(TextColor color, String label) {
            this.color = color;
            this.label = label;
        }

        public TextColor getColor() {
            return color;
        }

        public String getLabel() {
            return label;
        }
    }
}
