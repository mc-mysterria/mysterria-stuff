package net.mysterria.stuff.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/** Forwards the short /o command to the organization plugin's menu command. */
public final class OrganizationShortcutCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length > 0) {
            sender.sendMessage("Use /organization for organization actions.");
            return true;
        }
        return sender.getServer().dispatchCommand(sender, "organization");
    }
}
