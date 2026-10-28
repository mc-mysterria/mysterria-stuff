package net.mysterria.stuff.util.log;

import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;

public class PrettyLogger {

    private static final Component PREFIX = Component.text("[MysterriaStuff]", TextColor.color(0xAA55FF));

    @Getter
    @Setter
    private static boolean debugMode;

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
        if (debugMode) {
            log(LogLevel.DEBUG, message);
        }
    }

    private static void log(LogLevel level, String message) {
        Bukkit.getConsoleSender().sendMessage(PREFIX
                .append(Component.text(" [" + level.name() + "] ", level.getColor()))
                .append(Component.text(message, NamedTextColor.WHITE)));
    }
}
