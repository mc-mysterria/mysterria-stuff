package net.mysterria.stuff.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.utils.PrettyLogger;
import net.mysterria.stuff.utils.StaticItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Base64;

/**
 * Main command handler for MysterriaStuff plugin
 */
public class MainCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help" -> {
                sendHelpMessage(sender);
                return true;
            }
            case "reload" -> {
                return handleReload(sender);
            }
            case "give" -> {
                return handleGive(sender, args);
            }
            case "export" -> {
                return handleExport(sender);
            }
            case "debug" -> {
                return handleDebug(sender);
            }
            case "info", "status" -> {
                return handleInfo(sender);
            }
            case "recipe" -> {
                return handleRecipe(sender, args);
            }
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand. Use /mystuff help for available commands.")
                        .color(NamedTextColor.RED));
                return true;
            }
        }
    }

    private void sendHelpMessage(CommandSender sender) {
        Component header = Component.text("═".repeat(35)).color(TextColor.color(0xAA55FF));
        Component title = Component.text(" MysterriaStuff Commands ")
                .color(TextColor.color(0xFFFFFF))
                .decorate(TextDecoration.BOLD);

        sender.sendMessage(header);
        sender.sendMessage(title);
        sender.sendMessage(header);
        sender.sendMessage(Component.empty());

        sendCommandHelp(sender, "/mystuff help", "Show this help message");
        sendCommandHelp(sender, "/mystuff info", "Show plugin status and loaded features");
        sendCommandHelp(sender, "/mystuff reload", "Reload the plugin configuration");
        sendCommandHelp(sender, "/mystuff debug", "Toggle debug mode");
        sendCommandHelp(sender, "/mystuff give <item> <player>", "Give an item to a player");
        sendCommandHelp(sender, "/mystuff export", "Export held item as bytes");
        sendCommandHelp(sender, "/mystuff recipe <list|reload>", "Manage custom recipes");

        sender.sendMessage(Component.empty());
        sender.sendMessage(header);
    }

    private void sendCommandHelp(CommandSender sender, String command, String description) {
        Component cmd = Component.text("  ➜ ").color(TextColor.color(0x55FF55))
                .append(Component.text(command).color(NamedTextColor.AQUA))
                .append(Component.text(" - ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(description).color(NamedTextColor.GRAY));
        sender.sendMessage(cmd);
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("mysterriastuff.reload")) {
            sender.sendMessage(Component.text("You don't have permission to use this command!")
                    .color(NamedTextColor.RED));
            return true;
        }

        PrettyLogger.info("Reloading MysterriaStuff...");

        // Reload configuration
        MysterriaStuff.getInstance().getConfigManager().reloadConfig();

        // Update debug mode
        PrettyLogger.setDebugMode(MysterriaStuff.getInstance().getConfigManager().isDebugMode());

        // Reload recipes if enabled
        if (MysterriaStuff.getInstance().getRecipeManager() != null) {
            MysterriaStuff.getInstance().getRecipeManager().reloadRecipes();
        }

        sender.sendMessage(Component.text("MysterriaStuff reloaded successfully!")
                .color(NamedTextColor.GREEN));
        PrettyLogger.success("Plugin reloaded by " + sender.getName());
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mysterriastuff.give")) {
            sender.sendMessage(Component.text("You don't have permission to use this command!")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /mystuff give <item> <player>")
                    .color(NamedTextColor.RED));
            sender.sendMessage(Component.text("Available items: elytra")
                    .color(NamedTextColor.GRAY));
            return true;
        }

        String itemType = args[1].toLowerCase();
        String playerName = args[2];

        Player target = Bukkit.getPlayer(playerName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(Component.text("Player not found or is offline!")
                    .color(NamedTextColor.RED));
            return true;
        }

        switch (itemType) {
            case "elytra" -> {
                ItemStack elytra = getElytra();
                if (elytra == null) {
                    sender.sendMessage(Component.text("Failed to create elytra item!")
                            .color(NamedTextColor.RED));
                    return true;
                }

                if (target.getInventory().firstEmpty() != -1) {
                    target.getInventory().addItem(elytra);
                } else {
                    target.getWorld().dropItemNaturally(target.getLocation(), elytra);
                }

                sender.sendMessage(Component.text("Given ")
                        .color(NamedTextColor.GREEN)
                        .append(Component.text(playerName).color(NamedTextColor.AQUA))
                        .append(Component.text(" a reinforced elytra!").color(NamedTextColor.GREEN)));

                PrettyLogger.info("Gave " + playerName + " a reinforced elytra (by " + sender.getName() + ")");
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Unknown item type: " + itemType)
                        .color(NamedTextColor.RED));
                sender.sendMessage(Component.text("Available items: elytra")
                        .color(NamedTextColor.GRAY));
                return true;
            }
        }
    }

    private boolean handleExport(CommandSender sender) {
        if (!sender.hasPermission("mysterriastuff.export")) {
            sender.sendMessage(Component.text("You don't have permission to use this command!")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players!")
                    .color(NamedTextColor.RED));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            sender.sendMessage(Component.text("You must be holding an item!")
                    .color(NamedTextColor.RED));
            return true;
        }

        String encoded = Base64.getEncoder().encodeToString(item.serializeAsBytes());
        Component message = Component.text("Click here to copy item as bytes")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.copyToClipboard(encoded));

        sender.sendMessage(message);
        PrettyLogger.debug("Exported item for " + sender.getName() + ": " + item.getType().name());
        return true;
    }

    private boolean handleDebug(CommandSender sender) {
        if (!sender.hasPermission("mysterriastuff.debug")) {
            sender.sendMessage(Component.text("You don't have permission to use this command!")
                    .color(NamedTextColor.RED));
            return true;
        }

        boolean newState = !PrettyLogger.isDebugMode();
        PrettyLogger.setDebugMode(newState);

        // Save to config
        MysterriaStuff.getInstance().getConfigManager().setDebugMode(newState);

        sender.sendMessage(Component.text("Debug mode: ")
                .color(NamedTextColor.YELLOW)
                .append(Component.text(newState ? "ENABLED" : "DISABLED")
                        .color(newState ? NamedTextColor.GREEN : NamedTextColor.RED)));

        sender.sendMessage(Component.text("(Saved to config.yml)")
                .color(NamedTextColor.GRAY));

        return true;
    }

    private boolean handleInfo(CommandSender sender) {
        Component header = Component.text("═".repeat(40)).color(TextColor.color(0xAA55FF));
        Component title = Component.text(" MysterriaStuff Status ")
                .color(TextColor.color(0xFFFFFF))
                .decorate(TextDecoration.BOLD);

        sender.sendMessage(header);
        sender.sendMessage(title);
        sender.sendMessage(header);
        sender.sendMessage(Component.empty());

        var config = MysterriaStuff.getInstance().getConfigManager();

        sender.sendMessage(Component.text("  Version: ").color(NamedTextColor.GRAY)
                .append(Component.text("1.0.0").color(NamedTextColor.AQUA)));

        sender.sendMessage(Component.text("  Config Version: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(config.getConfigVersion())).color(NamedTextColor.AQUA)));

        sender.sendMessage(Component.text("  Debug Mode: ").color(NamedTextColor.GRAY)
                .append(Component.text(PrettyLogger.isDebugMode() ? "Enabled" : "Disabled")
                        .color(PrettyLogger.isDebugMode() ? NamedTextColor.GREEN : NamedTextColor.RED)));

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("  Active Features:").color(NamedTextColor.YELLOW));

        if (config.isElytraBlockerEnabled()) {
            sender.sendMessage(Component.text("    ➜ Reinforced Elytra Blocker").color(NamedTextColor.GREEN));
        }
        if (config.isLightningFixEnabled()) {
            sender.sendMessage(Component.text("    ➜ Lightning Strike Fix (HuskTowns)").color(NamedTextColor.GREEN));
        }
        if (config.isCoiProtectionEnabled()) {
            sender.sendMessage(Component.text("    ➜ CoI Dangerous Actions Listener").color(NamedTextColor.GREEN));
        }
        if (config.isRecipeManagerEnabled()) {
            int recipeCount = MysterriaStuff.getInstance().getRecipeManager() != null ?
                    MysterriaStuff.getInstance().getRecipeManager().getRecipeCount() : 0;
            sender.sendMessage(Component.text("    ➜ Runtime Recipe Manager (" + recipeCount + " recipes)")
                    .color(NamedTextColor.GREEN));
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("  CoI Protection Settings:").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("    • Reset Attributes: ").color(NamedTextColor.GRAY)
                .append(Component.text(config.isResetAttributesOnJoin() ? "✓" : "✗")
                        .color(config.isResetAttributesOnJoin() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        sender.sendMessage(Component.text("    • Spectator Noclip: ").color(NamedTextColor.GRAY)
                .append(Component.text(config.isRestrictSpectatorNoclip() ? "✓" : "✗")
                        .color(config.isRestrictSpectatorNoclip() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        sender.sendMessage(Component.text("    • Block Nightmare Pickups: ").color(NamedTextColor.GRAY)
                .append(Component.text(config.isBlockNightmarePickups() ? "✓" : "✗")
                        .color(config.isBlockNightmarePickups() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        sender.sendMessage(Component.text("    • Nightmare Keep Inventory: ").color(NamedTextColor.GRAY)
                .append(Component.text(config.isNightmareKeepInventory() ? "✓" : "✗")
                        .color(config.isNightmareKeepInventory() ? NamedTextColor.GREEN : NamedTextColor.RED)));

        sender.sendMessage(Component.empty());
        sender.sendMessage(header);

        return true;
    }

    private boolean handleRecipe(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mysterriastuff.recipe")) {
            sender.sendMessage(Component.text("You don't have permission to use this command!")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /mystuff recipe <list|reload>")
                    .color(NamedTextColor.RED));
            return true;
        }

        var recipeManager = MysterriaStuff.getInstance().getRecipeManager();

        switch (args[1].toLowerCase()) {
            case "list" -> {
                Component header = Component.text("═".repeat(40)).color(TextColor.color(0xAA55FF));
                sender.sendMessage(header);
                sender.sendMessage(Component.text(" Custom Recipes (" + recipeManager.getRecipeCount() + ")")
                        .color(TextColor.color(0xFFFFFF))
                        .decorate(TextDecoration.BOLD));
                sender.sendMessage(header);
                sender.sendMessage(Component.empty());

                if (recipeManager.getRecipeCount() == 0) {
                    sender.sendMessage(Component.text("  No custom recipes loaded.")
                            .color(NamedTextColor.GRAY));
                } else {
                    for (String recipeId : recipeManager.getCustomRecipeIds()) {
                        sender.sendMessage(Component.text("  ➜ ")
                                .color(TextColor.color(0x55FF55))
                                .append(Component.text(recipeId).color(NamedTextColor.AQUA)));
                    }
                }

                sender.sendMessage(Component.empty());
                sender.sendMessage(header);
                return true;
            }
            case "reload" -> {
                sender.sendMessage(Component.text("Reloading recipes...")
                        .color(NamedTextColor.YELLOW));
                recipeManager.reloadRecipes();
                sender.sendMessage(Component.text("Recipes reloaded! Total: " + recipeManager.getRecipeCount())
                        .color(NamedTextColor.GREEN));
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Unknown recipe subcommand!")
                        .color(NamedTextColor.RED));
                sender.sendMessage(Component.text("Available: list, reload")
                        .color(NamedTextColor.GRAY));
                return true;
            }
        }
    }

    private ItemStack getElytra() {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(StaticItems.REINFORCED_ELYTRA));
        } catch (Exception e) {
            PrettyLogger.error("Failed to deserialize reinforced elytra: " + e.getMessage());
            return null;
        }
    }
}
