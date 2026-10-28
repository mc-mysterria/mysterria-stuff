package net.mysterria.stuff.util.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

public class Feedback {

    public static void error(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
    }

    public static void success(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }

    public static void info(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.GRAY));
    }

    public static void field(CommandSender sender, String label, String value) {
        sender.sendMessage(Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value == null ? "(not set)" : value, NamedTextColor.WHITE)));
    }

    public static void flag(CommandSender sender, String label, boolean value) {
        sender.sendMessage(Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value ? "yes" : "no", value ? NamedTextColor.GREEN : NamedTextColor.RED)));
    }
}
