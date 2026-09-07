package net.mysterria.stuff.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.audit.StuffAuditEmitter;
import net.mysterria.stuff.features.joinmsg.JoinMsgTokenManager;
import net.mysterria.stuff.features.coi.BoosterPatriarchListener;
import net.mysterria.stuff.features.joinmsg.JoinMsgSessionHandler;
import net.mysterria.stuff.features.joinmsg.JoinMsgStore;
import net.mysterria.stuff.features.lastsprint.LastSprint;
import net.mysterria.stuff.features.lastsprint.LastSprintGUI;
import net.mysterria.stuff.features.hmcwraps.UniversalTokenManager;
import net.mysterria.stuff.utils.PrettyLogger;
import net.mysterria.stuff.utils.StaticItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;


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
            case "token" -> {
                return handleToken(sender, args);
            }
            case "joinmsg" -> {
                return handleJoinMsg(sender, args);
            }
            case "lastsprint" -> {
                return handleLastSprint(sender, args);
            }
            case "booster" -> {
                return handleBooster(sender, args);
            }
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand. Use /mystuff help for available commands.")
                        .color(NamedTextColor.RED));
                return true;
            }
        }
    }

    private void sendHelpMessage(CommandSender sender) {
        if (!sender.hasPermission("mysterriastuff.*")) {
            return;
        }

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
        sendCommandHelp(sender, "/mystuff token give <player> [amount]", "Give universal tokens");
        sendCommandHelp(sender, "/mystuff joinmsg give <player> [amount]", "Give a Join/Quit Message token");
        sendCommandHelp(sender, "/mystuff joinmsg set <player> <join|quit> <message>", "Set a player's join/quit message");
        sendCommandHelp(sender, "/mystuff joinmsg get <player>", "View a player's join/quit messages");
        sendCommandHelp(sender, "/mystuff joinmsg remove <player> [join|quit]", "Remove a player's join/quit message(s)");
        sendCommandHelp(sender, "/mystuff joinmsg list", "List all configured join/quit messages");
        sendCommandHelp(sender, "/mystuff joinmsg default <get|set>", "View/set the server-wide default message");
        sendCommandHelp(sender, "/mystuff joinmsg firstjoin <get|set>", "View/set the first-ever-join message");
        sendCommandHelp(sender, "/mystuff joinmsg reload", "Reload the join/quit message store from disk");
        sendCommandHelp(sender, "/mystuff joinmsg repair", "Re-import missing entries from the legacy .rs backup files");
        sendCommandHelp(sender, "/mystuff lastsprint setup", "Open the Last Sprint reward kit editor");
        sendCommandHelp(sender, "/mystuff lastsprint give <player>", "Force-give the Last Sprint kit to a player");
        sendCommandHelp(sender, "/mystuff lastsprint reset <player>", "Reset a player's Last Sprint gift status");
        sendCommandHelp(sender, "/mystuff lastsprint enable", "Enable auto-give on join");
        sendCommandHelp(sender, "/mystuff lastsprint disable", "Disable auto-give on join");
        sendCommandHelp(sender, "/mystuff lastsprint info", "Show Last Sprint status and reward count");
        sendCommandHelp(sender, "/mystuff booster check <player>", "Diagnose booster/patriarch state for a player");
        sendCommandHelp(sender, "/mystuff booster grant <player>", "Force-grant Patriarch boon to a player");
        sendCommandHelp(sender, "/mystuff booster revoke <player>", "Force-revoke Patriarch boon from a player");
        sendCommandHelp(sender, "/mystuff booster refresh", "Manually re-fetch the booster list from the API");
        sendCommandHelp(sender, "/mystuff booster list", "List all known boosters and tracked players");

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


        MysterriaStuff.getInstance().getConfigManager().reloadConfig();
        MysterriaStuff.getInstance().reloadChatAliasIntegration();


        PrettyLogger.setDebugMode(MysterriaStuff.getInstance().getConfigManager().isDebugMode());


        if (MysterriaStuff.getInstance().getRecipeManager() != null) {
            MysterriaStuff.getInstance().getRecipeManager().reloadRecipes();
        }

        if (UniversalTokenManager.getInstance() != null) {
            UniversalTokenManager.getInstance().reload();
            PrettyLogger.info("Reloaded HMCWraps category mappings");
        }

        if (MysterriaStuff.getInstance().getJoinMsgStore() != null) {
            MysterriaStuff.getInstance().getJoinMsgStore().load();
            PrettyLogger.info("Reloaded join/quit message store");
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

    private boolean handleToken(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mysterriastuff.token")) {
            sender.sendMessage(Component.text("You don't have permission to use this command!")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /mystuff token give <player> [amount]")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args[1].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("mysterriastuff.token.give")) {
                sender.sendMessage(Component.text("You don't have permission to give tokens!")
                        .color(NamedTextColor.RED));
                return true;
            }

            if (args.length < 3) {
                sender.sendMessage(Component.text("Usage: /mystuff token give <player> [amount]")
                        .color(NamedTextColor.RED));
                return true;
            }

            String playerName = args[2];
            int amount = 1;

            if (args.length >= 4) {
                try {
                    amount = Integer.parseInt(args[3]);
                    if (amount < 1 || amount > 64) {
                        sender.sendMessage(Component.text("Amount must be between 1 and 64!")
                                .color(NamedTextColor.RED));
                        return true;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid amount! Must be a number.")
                            .color(NamedTextColor.RED));
                    return true;
                }
            }

            Player target = Bukkit.getPlayer(playerName);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(Component.text("Player not found or is offline!")
                        .color(NamedTextColor.RED));
                return true;
            }


            UniversalTokenManager tokenManager = UniversalTokenManager.getInstance();
            if (tokenManager == null) {
                sender.sendMessage(Component.text("Universal Token system is not enabled!")
                        .color(NamedTextColor.RED));
                return true;
            }


            ItemStack token = tokenManager.createToken(amount);

            Map<String, Object> delivery = deliverItem(target, token);
            Map<String, Object> metadata = new LinkedHashMap<>(
                    StuffAuditEmitter.tokenMetadata("universal", amount, "admin_give"));
            metadata.putAll(delivery);

            StuffAuditEmitter.emit(MysterriaStuff.getInstance(), "token.granted",
                    StuffAuditEmitter.correlationId(), StuffAuditEmitter.tokenBusinessId("universal"),
                    sender instanceof Player actor ? actor.getUniqueId() : null,
                    target.getUniqueId(), null, "admin_give",
                    metadata);


            target.sendMessage(tokenManager.getMessage("token-received", "amount", String.valueOf(amount)));

            sender.sendMessage(Component.text("Given ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(playerName).color(NamedTextColor.AQUA))
                    .append(Component.text(" " + amount + " Universal Token(s)!").color(NamedTextColor.GREEN)));

            PrettyLogger.info("Gave " + playerName + " " + amount + " Universal Token(s) (by " + sender.getName() + ")");
            return true;
        }
        sender.sendMessage(Component.text("Unknown token subcommand!")
                .color(NamedTextColor.RED));
        sender.sendMessage(Component.text("Available: give")
                .color(NamedTextColor.GRAY));
        return true;
    }

    private boolean handleJoinMsgGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mysterriastuff.joinmsg.give")) {
            sender.sendMessage(Component.text("You don't have permission to give Join/Quit Message tokens!")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /mystuff joinmsg give <player> [amount]")
                    .color(NamedTextColor.RED));
            return true;
        }

        String playerName = args[2];
        int amount = 1;

        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 1 || amount > 64) {
                    sender.sendMessage(Component.text("Amount must be between 1 and 64!")
                            .color(NamedTextColor.RED));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid amount! Must be a number.")
                        .color(NamedTextColor.RED));
                return true;
            }
        }

        Player target = Bukkit.getPlayer(playerName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(Component.text("Player not found or is offline!")
                    .color(NamedTextColor.RED));
            return true;
        }


        JoinMsgTokenManager manager = JoinMsgTokenManager.getInstance();
        if (manager == null) {
            sender.sendMessage(Component.text("Join/Quit Message Token system is not enabled!")
                    .color(NamedTextColor.RED));
            return true;
        }


        ItemStack token = manager.createToken(amount);

        Map<String, Object> delivery = deliverItem(target, token);
        Map<String, Object> metadata = new LinkedHashMap<>(
                StuffAuditEmitter.tokenMetadata("joinmsg", amount, "admin_give"));
        metadata.putAll(delivery);

        StuffAuditEmitter.emit(MysterriaStuff.getInstance(), "token.granted",
                StuffAuditEmitter.correlationId(), StuffAuditEmitter.tokenBusinessId("joinmsg"),
                sender instanceof Player actor ? actor.getUniqueId() : null,
                target.getUniqueId(), null, "admin_give",
                metadata);


        target.sendMessage(manager.getMessage("token-received", "amount", String.valueOf(amount)));

        sender.sendMessage(Component.text("Given ")
                .color(NamedTextColor.GREEN)
                .append(Component.text(playerName).color(NamedTextColor.AQUA))
                .append(Component.text(" " + amount + " Join/Quit Message Token(s)!").color(NamedTextColor.GREEN)));

        PrettyLogger.info("Gave " + playerName + " " + amount + " Join/Quit Message Token(s) (by " + sender.getName() + ")");
        return true;
    }

    private boolean handleJoinMsgConfirm(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players!")
                    .color(NamedTextColor.RED));
            return true;
        }

        JoinMsgSessionHandler sessionHandler = MysterriaStuff.getInstance().getJoinMsgSessionHandler();
        if (sessionHandler == null) {
            player.sendMessage(Component.text("Join/Quit Message Token system is not enabled!")
                    .color(NamedTextColor.RED));
            return true;
        }

        sessionHandler.handleConfirmation(player);
        return true;
    }

    private boolean handleJoinMsgCancel(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players!")
                    .color(NamedTextColor.RED));
            return true;
        }

        JoinMsgSessionHandler sessionHandler = MysterriaStuff.getInstance().getJoinMsgSessionHandler();
        if (sessionHandler == null) {
            player.sendMessage(Component.text("Join/Quit Message Token system is not enabled!")
                    .color(NamedTextColor.RED));
            return true;
        }

        sessionHandler.handleCancellation(player);
        return true;
    }

    private boolean handleJoinMsgRestart(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players!")
                    .color(NamedTextColor.RED));
            return true;
        }

        JoinMsgSessionHandler sessionHandler = MysterriaStuff.getInstance().getJoinMsgSessionHandler();
        if (sessionHandler == null) {
            player.sendMessage(Component.text("Join/Quit Message Token system is not enabled!")
                    .color(NamedTextColor.RED));
            return true;
        }

        sessionHandler.handleRestart(player);
        return true;
    }

    private boolean handleJoinMsg(CommandSender sender, String[] args) {
        JoinMsgStore store = MysterriaStuff.getInstance().getJoinMsgStore();
        if (store == null) {
            sender.sendMessage(Component.text("Join/Quit Message Token system is not enabled!")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /mystuff joinmsg <give|confirm|cancel|restart|set|get|remove|list|default|firstjoin|reload|repair>")
                    .color(NamedTextColor.RED));
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "give" -> {
                return handleJoinMsgGive(sender, args);
            }
            case "confirm" -> {
                return handleJoinMsgConfirm(sender);
            }
            case "cancel" -> {
                return handleJoinMsgCancel(sender);
            }
            case "restart" -> {
                return handleJoinMsgRestart(sender);
            }
        }

        if (!sender.hasPermission("mysterriastuff.joinmsg.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command!")
                    .color(NamedTextColor.RED));
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "set" -> {
                if (args.length < 5) {
                    sender.sendMessage(Component.text("Usage: /mystuff joinmsg set <player> <join|quit> <message>")
                            .color(NamedTextColor.RED));
                    return true;
                }

                // If resolveTarget can't produce a real player (never joined, not cached), fall back
                // to a pending-by-name entry — created fresh if needed. It self-heals into a proper
                // UUID entry the next time that player is actually seen online, so pre-provisioning
                // a message for someone who hasn't joined yet (e.g. delivering a purchase) is fine.
                OfflinePlayer target = store.resolveTarget(args[2]);

                String type = args[3].toLowerCase();
                if (!type.equals("join") && !type.equals("quit")) {
                    sender.sendMessage(Component.text("Message type must be 'join' or 'quit'")
                            .color(NamedTextColor.RED));
                    return true;
                }

                String message = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                JoinMsgStore.SetResult result = target != null
                        ? (type.equals("join") ? store.setPlayerMessages(target, message, null) : store.setPlayerMessages(target, null, message))
                        : (type.equals("join") ? store.setPendingMessages(args[2], message, null) : store.setPendingMessages(args[2], null, message));
                String label = target != null ? displayName(target) : args[2];

                switch (result) {
                    case OK -> sender.sendMessage(Component.text("Set " + type + " message for ")
                            .color(NamedTextColor.GREEN)
                            .append(Component.text(label).color(NamedTextColor.AQUA))
                            .append(Component.text(".").color(NamedTextColor.GREEN)));
                    case MISSING_PLACEHOLDER_JOIN, MISSING_PLACEHOLDER_QUIT -> sender.sendMessage(
                            Component.text("Message must contain %player%!").color(NamedTextColor.RED));
                    case WRITE_ERROR -> sender.sendMessage(
                            Component.text("Failed to save the message store! Check console.").color(NamedTextColor.RED));
                }
                if (result == JoinMsgStore.SetResult.OK) {
                    emitJoinMsgAdmin(sender, "message_set", target, args[2], type);
                }
                return true;
            }
            case "get" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /mystuff joinmsg get <player>")
                            .color(NamedTextColor.RED));
                    return true;
                }

                OfflinePlayer target = store.resolveTarget(args[2]);
                JoinMsgStore.MessageEntry entry = target != null ? store.getEntry(target) : store.findPendingByName(args[2]);
                if (entry == null) {
                    sender.sendMessage(Component.text((target != null
                            ? "No custom messages set for " + displayName(target)
                            : "Unknown player: " + args[2]) + ".")
                            .color(target != null ? NamedTextColor.GRAY : NamedTextColor.RED));
                    return true;
                }

                sender.sendMessage(Component.text("Messages for " + entry.name + ":").color(NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("  Join: ").color(NamedTextColor.GRAY)
                        .append(Component.text(entry.join != null ? entry.join : "(not set)").color(NamedTextColor.WHITE)));
                sender.sendMessage(Component.text("  Quit: ").color(NamedTextColor.GRAY)
                        .append(Component.text(entry.quit != null ? entry.quit : "(not set)").color(NamedTextColor.WHITE)));
                return true;
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /mystuff joinmsg remove <player> [join|quit]")
                            .color(NamedTextColor.RED));
                    return true;
                }

                OfflinePlayer target = store.resolveTarget(args[2]);
                if (target == null && store.findPendingByName(args[2]) == null) {
                    sender.sendMessage(Component.text("Unknown player: " + args[2])
                            .color(NamedTextColor.RED));
                    return true;
                }

                boolean removeJoin = true;
                boolean removeQuit = true;
                if (args.length >= 4) {
                    String type = args[3].toLowerCase();
                    removeJoin = type.equals("join");
                    removeQuit = type.equals("quit");
                    if (!removeJoin && !removeQuit) {
                        sender.sendMessage(Component.text("Message type must be 'join' or 'quit'")
                                .color(NamedTextColor.RED));
                        return true;
                    }
                }

                JoinMsgStore.RemoveResult removal = target != null
                        ? store.removePlayerMessages(target, removeJoin, removeQuit)
                        : store.removePendingMessages(args[2], removeJoin, removeQuit);
                String label = target != null ? displayName(target) : args[2];
                if (removal.changed() && !removal.saved()) {
                    sender.sendMessage(Component.text("Failed to save the message store! Check console.")
                            .color(NamedTextColor.RED));
                } else {
                    Component result = (removal.changed()
                            ? Component.text("Removed message(s) for ").color(NamedTextColor.GREEN)
                            : Component.text("No messages were set for ").color(NamedTextColor.GRAY))
                            .append(Component.text(label).color(NamedTextColor.AQUA))
                            .append(Component.text("."));
                    sender.sendMessage(result);
                }
                if (removal.changed() && removal.saved()) {
                    emitJoinMsgAdmin(sender, "message_removed", target, args[2],
                            removal.messageType());
                }
                return true;
            }
            case "list" -> {
                var entries = store.listEntries();

                Component header = Component.text("═".repeat(40)).color(TextColor.color(0xAA55FF));
                sender.sendMessage(header);
                sender.sendMessage(Component.text(" Custom Join/Quit Messages (" + entries.size() + ")")
                        .color(NamedTextColor.WHITE));
                sender.sendMessage(header);

                if (entries.isEmpty()) {
                    sender.sendMessage(Component.text("  (none configured)").color(NamedTextColor.GRAY));
                } else {
                    entries.stream()
                            .sorted(Comparator.comparing(e -> e.name.toLowerCase()))
                            .forEach(e -> {
                                Component line = Component.text("  • ").color(NamedTextColor.DARK_GRAY)
                                        .append(Component.text(e.name)
                                                .color(e.uuid == null ? NamedTextColor.GRAY : NamedTextColor.AQUA));
                                if (e.uuid == null) {
                                    line = line.append(Component.text(" (pending)").color(NamedTextColor.DARK_GRAY));
                                }
                                sender.sendMessage(line);
                            });
                }

                sender.sendMessage(header);
                return true;
            }
            case "default" -> {
                return handleJoinMsgDefault(sender, store, args);
            }
            case "firstjoin" -> {
                return handleJoinMsgFirstJoin(sender, store, args);
            }
            case "reload" -> {
                store.load();
                sender.sendMessage(Component.text("Join/quit message store reloaded from disk.")
                        .color(NamedTextColor.GREEN));
                return true;
            }
            case "repair" -> {
                int recovered = store.repairFromLegacyBackups();
                if (recovered < 0) {
                    sender.sendMessage(Component.text("No join.rs.migrated/quit.rs.migrated backup files found — nothing to repair.")
                            .color(NamedTextColor.GRAY));
                } else if (recovered == 0) {
                    sender.sendMessage(Component.text("Backup files found, but the current store already has everything from them.")
                            .color(NamedTextColor.GRAY));
                } else {
                    sender.sendMessage(Component.text("Recovered/filled in " + recovered + " entrie(s) from the legacy backup files.")
                            .color(NamedTextColor.GREEN));
                }
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Usage: /mystuff joinmsg <set|get|remove|list|default|firstjoin|reload|repair>")
                        .color(NamedTextColor.RED));
                return true;
            }
        }
    }

    private boolean handleJoinMsgDefault(CommandSender sender, JoinMsgStore store, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /mystuff joinmsg default <get|set> [join|quit] [message]")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args[2].equalsIgnoreCase("get")) {
            sender.sendMessage(Component.text("Default join: ").color(NamedTextColor.GRAY)
                    .append(Component.text(store.getDefaultJoinMessage() != null ? store.getDefaultJoinMessage() : "(not set)")
                            .color(NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("Default quit: ").color(NamedTextColor.GRAY)
                    .append(Component.text(store.getDefaultQuitMessage() != null ? store.getDefaultQuitMessage() : "(not set)")
                            .color(NamedTextColor.WHITE)));
            return true;
        }

        if (args[2].equalsIgnoreCase("set")) {
            if (args.length < 5) {
                sender.sendMessage(Component.text("Usage: /mystuff joinmsg default set <join|quit> <message>")
                        .color(NamedTextColor.RED));
                return true;
            }

            String type = args[3].toLowerCase();
            if (!type.equals("join") && !type.equals("quit")) {
                sender.sendMessage(Component.text("Message type must be 'join' or 'quit'")
                        .color(NamedTextColor.RED));
                return true;
            }

            String message = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
            if (!message.contains("%player%")) {
                sender.sendMessage(Component.text("Message must contain %player%!").color(NamedTextColor.RED));
                return true;
            }

            String stored = message.replace("%player%", "{player}");
            boolean saved = type.equals("join")
                    ? store.setDefaultJoinMessage(stored)
                    : store.setDefaultQuitMessage(stored);

            if (saved) {
                StuffAuditEmitter.emit(MysterriaStuff.getInstance(), "joinmsg.default_changed",
                        StuffAuditEmitter.correlationId(),
                        "joinmsg:default:" + type, sender instanceof Player actor ? actor.getUniqueId() : null,
                        null, null, "admin_set", Map.of("message_type", type));
                sender.sendMessage(Component.text("Default " + type + " message updated.")
                        .color(NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Failed to save the message store! Check console.")
                        .color(NamedTextColor.RED));
            }
            return true;
        }

        sender.sendMessage(Component.text("Usage: /mystuff joinmsg default <get|set> [join|quit] [message]")
                .color(NamedTextColor.RED));
        return true;
    }

    private boolean handleJoinMsgFirstJoin(CommandSender sender, JoinMsgStore store, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /mystuff joinmsg firstjoin <get|set> [message]")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args[2].equalsIgnoreCase("get")) {
            sender.sendMessage(Component.text("First-join message: ").color(NamedTextColor.GRAY)
                    .append(Component.text(store.getFirstJoinMessage() != null ? store.getFirstJoinMessage() : "(not set)")
                            .color(NamedTextColor.WHITE)));
            return true;
        }

        if (args[2].equalsIgnoreCase("set")) {
            if (args.length < 4) {
                sender.sendMessage(Component.text("Usage: /mystuff joinmsg firstjoin set <message>")
                        .color(NamedTextColor.RED));
                return true;
            }

            String message = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).replace("%player%", "{player}");
            boolean saved = store.setFirstJoinMessage(message);
            if (saved) {
                StuffAuditEmitter.emit(MysterriaStuff.getInstance(), "joinmsg.firstjoin_changed",
                        StuffAuditEmitter.correlationId(),
                        "joinmsg:first_join", sender instanceof Player actor ? actor.getUniqueId() : null,
                        null, null, "admin_set", Map.of());
                sender.sendMessage(Component.text("First-join message updated. (Uses MiniMessage tags, e.g. <gold>, not & codes.)")
                        .color(NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Failed to save the message store! Check console.")
                        .color(NamedTextColor.RED));
            }
            return true;
        }

        sender.sendMessage(Component.text("Usage: /mystuff joinmsg firstjoin <get|set> [message]")
                .color(NamedTextColor.RED));
        return true;
    }

    private String displayName(OfflinePlayer player) {
        return player.getName() != null ? player.getName() : player.getUniqueId().toString();
    }

    private void emitJoinMsgAdmin(CommandSender sender, String operation, OfflinePlayer target,
                                  String targetName, String messageType) {
        UUID subjectId = target == null ? null : target.getUniqueId();
        String stableTarget = subjectId == null ? targetName : subjectId.toString();
        StuffAuditEmitter.emit(MysterriaStuff.getInstance(), "joinmsg." + operation,
                StuffAuditEmitter.correlationId(),
                "joinmsg:" + stableTarget,
                sender instanceof Player actor ? actor.getUniqueId() : null,
                subjectId, null, "admin_mutation",
                Map.of("message_type", messageType, "target_name", targetName));
    }

    private Map<String, Object> deliverItem(Player target, ItemStack item) {
        int requestedAmount = item.getAmount();
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
        int droppedAmount = 0;
        for (ItemStack leftover : leftovers.values()) {
            if (leftover == null || leftover.getAmount() <= 0) continue;
            droppedAmount += leftover.getAmount();
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }
        int deliveredAmount = Math.max(0, requestedAmount - droppedAmount);
        return Map.of("delivery_mode", droppedAmount > 0 ? "dropped" : "inventory",
                "delivered_amount", deliveredAmount, "dropped_amount", droppedAmount);
    }

    private boolean handleLastSprint(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mysterriastuff.lastsprint")) {
            sender.sendMessage(Component.text("You don't have permission to use this command!")
                    .color(NamedTextColor.RED));
            return true;
        }

        LastSprint lastSprint = MysterriaStuff.getInstance().getLastSprint();
        if (lastSprint == null) {
            sender.sendMessage(Component.text("Last Sprint feature is not enabled!")
                    .color(NamedTextColor.RED));
            return true;
        }

        String sub = args.length >= 2 ? args[1].toLowerCase() : "info";

        switch (sub) {
            case "setup" -> {
                if (!sender.hasPermission("mysterriastuff.lastsprint.setup")) {
                    sender.sendMessage(Component.text("You don't have permission to edit the reward kit!")
                            .color(NamedTextColor.RED));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("This command can only be used by players!")
                            .color(NamedTextColor.RED));
                    return true;
                }
                LastSprintGUI gui = MysterriaStuff.getInstance().getLastSprintGUI();
                gui.open(player);
                return true;
            }
            case "give" -> {
                if (!sender.hasPermission("mysterriastuff.lastsprint.give")) {
                    sender.sendMessage(Component.text("You don't have permission to give the reward kit!")
                            .color(NamedTextColor.RED));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /mystuff lastsprint give <player>")
                            .color(NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(Component.text("Player not found or is offline!")
                            .color(NamedTextColor.RED));
                    return true;
                }
                if (lastSprint.getRewardCount() == 0) {
                    sender.sendMessage(Component.text("No reward items configured! Use /mystuff lastsprint setup first.")
                            .color(NamedTextColor.RED));
                    return true;
                }
                lastSprint.giveRewards(target);
                lastSprint.markGiftReceived(target.getUniqueId());
                sender.sendMessage(Component.text("Gave Last Sprint kit to ")
                        .color(NamedTextColor.GREEN)
                        .append(Component.text(target.getName()).color(NamedTextColor.AQUA))
                        .append(Component.text("!").color(NamedTextColor.GREEN)));
                target.sendMessage(Component.text("You've been given the Last Sprint starter kit by an admin!")
                        .color(NamedTextColor.GREEN));
                PrettyLogger.info("Force-gave Last Sprint kit to " + target.getName() + " (by " + sender.getName() + ")");
                return true;
            }
            case "reset" -> {
                if (!sender.hasPermission("mysterriastuff.lastsprint.reset")) {
                    sender.sendMessage(Component.text("You don't have permission to reset gift status!")
                            .color(NamedTextColor.RED));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /mystuff lastsprint reset <player>")
                            .color(NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target != null) {
                    lastSprint.unmarkGiftReceived(target.getUniqueId());
                    sender.sendMessage(Component.text("Reset Last Sprint gift status for ")
                            .color(NamedTextColor.GREEN)
                            .append(Component.text(target.getName()).color(NamedTextColor.AQUA))
                            .append(Component.text(".").color(NamedTextColor.GREEN)));
                } else {
                    sender.sendMessage(Component.text("Player not found (must be online to reset).")
                            .color(NamedTextColor.RED));
                }
                return true;
            }
            case "enable" -> {
                if (!sender.hasPermission("mysterriastuff.lastsprint.toggle")) {
                    sender.sendMessage(Component.text("You don't have permission to toggle Last Sprint!")
                            .color(NamedTextColor.RED));
                    return true;
                }
                MysterriaStuff.getInstance().getConfigManager().setLastSprintActive(true);
                sender.sendMessage(Component.text("Last Sprint is now ")
                        .color(NamedTextColor.GREEN)
                        .append(Component.text("ENABLED").color(NamedTextColor.GREEN).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD))
                        .append(Component.text(" — all new players joining will receive the kit.").color(NamedTextColor.GREEN)));
                PrettyLogger.info("Last Sprint activated by " + sender.getName());
                return true;
            }
            case "disable" -> {
                if (!sender.hasPermission("mysterriastuff.lastsprint.toggle")) {
                    sender.sendMessage(Component.text("You don't have permission to toggle Last Sprint!")
                            .color(NamedTextColor.RED));
                    return true;
                }
                MysterriaStuff.getInstance().getConfigManager().setLastSprintActive(false);
                sender.sendMessage(Component.text("Last Sprint is now ")
                        .color(NamedTextColor.YELLOW)
                        .append(Component.text("DISABLED").color(NamedTextColor.RED).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD))
                        .append(Component.text(" — auto-give on join is off.").color(NamedTextColor.YELLOW)));
                PrettyLogger.info("Last Sprint deactivated by " + sender.getName());
                return true;
            }
            case "info" -> {
                boolean active = MysterriaStuff.getInstance().getConfigManager().isLastSprintActive();
                sender.sendMessage(Component.text("Last Sprint — ").color(NamedTextColor.YELLOW)
                        .append(Component.text(active ? "ACTIVE" : "INACTIVE")
                                .color(active ? NamedTextColor.GREEN : NamedTextColor.RED)
                                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)));
                sender.sendMessage(Component.text("Reward items configured: ").color(NamedTextColor.YELLOW)
                        .append(Component.text(lastSprint.getRewardCount()).color(NamedTextColor.AQUA)));
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Usage: /mystuff lastsprint <setup|give|reset|enable|disable|info>")
                        .color(NamedTextColor.RED));
                return true;
            }
        }
    }

    private boolean handleBooster(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mysterriastuff.booster")) {
            sender.sendMessage(Component.text("You don't have permission to use this command!")
                    .color(NamedTextColor.RED));
            return true;
        }

        BoosterPatriarchListener bpl = MysterriaStuff.getInstance().getBoosterPatriarchListener();
        if (bpl == null) {
            sender.sendMessage(Component.text("Booster Patriarch system is not enabled!")
                    .color(NamedTextColor.RED));
            return true;
        }

        String sub = args.length >= 2 ? args[1].toLowerCase() : "help";

        switch (sub) {
            case "check" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /mystuff booster check <player>")
                            .color(NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(Component.text("Player not found or is offline!")
                            .color(NamedTextColor.RED));
                    return true;
                }
                bpl.sendDiagnostics(target, sender);
                return true;
            }
            case "grant" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /mystuff booster grant <player>")
                            .color(NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(Component.text("Player not found or is offline!")
                            .color(NamedTextColor.RED));
                    return true;
                }
                bpl.forceGrantPatriarch(target, sender);
                return true;
            }
            case "revoke" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /mystuff booster revoke <player>")
                            .color(NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(Component.text("Player not found or is offline!")
                            .color(NamedTextColor.RED));
                    return true;
                }
                bpl.forceRevokePatriarch(target, sender);
                return true;
            }
            case "refresh" -> {
                bpl.refreshBoosters();
                sender.sendMessage(Component.text("Booster list refresh triggered (async). Check console for results.")
                        .color(NamedTextColor.GREEN));
                return true;
            }
            case "list" -> {
                var boosters = bpl.getCurrentBoosters();

                Component header = Component.text("═".repeat(45)).color(net.kyori.adventure.text.format.TextColor.color(0xAA55FF));
                sender.sendMessage(header);
                sender.sendMessage(Component.text(" Booster Patriarch List").color(NamedTextColor.WHITE));
                sender.sendMessage(header);

                sender.sendMessage(Component.text("Boosters from API (" + boosters.size() + "):").color(NamedTextColor.YELLOW));
                if (boosters.isEmpty()) {
                    sender.sendMessage(Component.text("  (none — list may still be loading)").color(NamedTextColor.GRAY));
                } else {
                    boosters.stream().sorted().forEach(name ->
                            sender.sendMessage(Component.text("  • " + name).color(NamedTextColor.AQUA)));
                }

                sender.sendMessage(header);
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Usage: /mystuff booster <check|grant|revoke|refresh|list>")
                        .color(NamedTextColor.RED));
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
