package net.mysterria.stuff.command.admin;

import net.mysterria.stuff.util.text.Feedback;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public abstract class Subcommand {

    public abstract String name();

    public abstract Map<String, String> help(CommandSender sender);

    public abstract void execute(CommandSender sender, String[] args);

    public abstract List<String> complete(CommandSender sender, String[] args);

    protected boolean allowed(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        Feedback.error(sender, "You don't have permission to do that.");
        return false;
    }

    protected Player onlinePlayer(CommandSender sender, String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            Feedback.error(sender, name + " is not online.");
        }
        return player;
    }

    protected Player self(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        Feedback.error(sender, "Only players can do that.");
        return null;
    }

    protected Integer amount(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            return 1;
        }
        try {
            int amount = Integer.parseInt(args[index]);
            if (amount >= 1 && amount <= 64) {
                return amount;
            }
        } catch (NumberFormatException ignored) {
        }
        Feedback.error(sender, "Amount must be a number from 1 to 64.");
        return null;
    }

    protected List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().map(HumanEntity::getName).toList();
    }

    protected List<String> matching(List<String> options, String typed) {
        return options.stream().filter(option -> option.toLowerCase().startsWith(typed.toLowerCase())).toList();
    }
}
