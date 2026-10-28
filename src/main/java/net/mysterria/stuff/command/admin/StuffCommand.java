package net.mysterria.stuff.command.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.util.log.PrettyLogger;
import net.mysterria.stuff.util.text.Feedback;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StuffCommand implements TabExecutor {

    private final MysterriaStuff plugin;
    private final Map<String, Subcommand> subcommands = new LinkedHashMap<>();

    public StuffCommand(MysterriaStuff plugin, List<Subcommand> subcommands) {
        this.plugin = plugin;
        subcommands.forEach(subcommand -> this.subcommands.put(subcommand.name(), subcommand));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String name = args.length == 0 ? "help" : args[0].toLowerCase();
        Subcommand subcommand = subcommands.get(name);
        if (subcommand != null) {
            subcommand.execute(sender, args);
            return true;
        }
        switch (name) {
            case "help" -> help(sender);
            case "info" -> info(sender);
            case "reload" -> reload(sender);
            case "debug" -> debug(sender);
            case "export" -> export(sender);
            default -> Feedback.error(sender, "Unknown subcommand. Try /mystuff help.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>(List.of("help", "info"));
            for (String admin : List.of("reload", "debug", "export")) {
                if (sender.hasPermission("mysterriastuff." + admin)) {
                    names.add(admin);
                }
            }
            subcommands.values().stream().filter(subcommand -> !subcommand.help(sender).isEmpty()).map(Subcommand::name).forEach(names::add);
            return names.stream().filter(name -> name.startsWith(args[0].toLowerCase())).toList();
        }
        Subcommand subcommand = subcommands.get(args[0].toLowerCase());
        return subcommand == null ? List.of() : subcommand.complete(sender, args);
    }

    private void help(CommandSender sender) {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("/mystuff info", "Show version and active features");
        if (sender.hasPermission("mysterriastuff.reload")) {
            entries.put("/mystuff reload", "Reload config and data files");
        }
        if (sender.hasPermission("mysterriastuff.debug")) {
            entries.put("/mystuff debug", "Toggle debug logging");
        }
        if (sender.hasPermission("mysterriastuff.export")) {
            entries.put("/mystuff export", "Copy the held item as Base64");
        }
        subcommands.values().forEach(subcommand -> entries.putAll(subcommand.help(sender)));

        sender.sendMessage(Component.text("MysterriaStuff", TextColor.color(0xAA55FF), TextDecoration.BOLD));
        entries.forEach((usage, description) -> sender.sendMessage(Component.text(usage, NamedTextColor.AQUA)
                .clickEvent(ClickEvent.suggestCommand(usage.replaceAll(" [<\\[].*", " ")))
                .append(Component.text(" - " + description, NamedTextColor.GRAY))));
    }

    private void info(CommandSender sender) {
        sender.sendMessage(Component.text("MysterriaStuff " + plugin.getPluginMeta().getVersion(), TextColor.color(0xAA55FF), TextDecoration.BOLD));
        Feedback.flag(sender, "Debug logging", PrettyLogger.isDebugMode());
        Feedback.field(sender, "Active features", String.join(", ", plugin.getActiveFeatures()));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("mysterriastuff.reload")) {
            Feedback.error(sender, "You don't have permission to do that.");
            return;
        }
        plugin.reload();
        Feedback.success(sender, "MysterriaStuff reloaded. Feature toggles apply after a restart.");
        PrettyLogger.info(sender.getName() + " reloaded MysterriaStuff");
    }

    private void debug(CommandSender sender) {
        if (!sender.hasPermission("mysterriastuff.debug")) {
            Feedback.error(sender, "You don't have permission to do that.");
            return;
        }
        boolean enabled = !PrettyLogger.isDebugMode();
        PrettyLogger.setDebugMode(enabled);
        plugin.getConfigManager().setDebugMode(enabled);
        Feedback.success(sender, "Debug logging " + (enabled ? "enabled" : "disabled") + " and saved to config.yml.");
    }

    private void export(CommandSender sender) {
        if (!sender.hasPermission("mysterriastuff.export")) {
            Feedback.error(sender, "You don't have permission to do that.");
            return;
        }
        if (!(sender instanceof Player player) || player.getInventory().getItemInMainHand().isEmpty()) {
            Feedback.error(sender, "Hold the item you want to export.");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        sender.sendMessage(Component.text("Click to copy " + item.getType().getKey().getKey() + " as Base64", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.copyToClipboard(Base64.getEncoder().encodeToString(item.serializeAsBytes()))));
    }
}
