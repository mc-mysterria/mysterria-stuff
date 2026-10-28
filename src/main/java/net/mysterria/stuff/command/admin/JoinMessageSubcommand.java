package net.mysterria.stuff.command.admin;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.mysterria.stuff.domain.joinmessage.model.MessageEntry;
import net.mysterria.stuff.domain.joinmessage.model.source.SetResult;
import net.mysterria.stuff.domain.joinmessage.service.JoinMessageSessionService;
import net.mysterria.stuff.domain.joinmessage.service.JoinMessageStore;
import net.mysterria.stuff.util.item.ItemDelivery;
import net.mysterria.stuff.util.item.TokenItem;
import net.mysterria.stuff.util.log.PrettyLogger;
import net.mysterria.stuff.util.text.Feedback;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class JoinMessageSubcommand extends Subcommand {

    private static final String GIVE_PERMISSION = "mysterriastuff.joinmsg.give";
    private static final String ADMIN_PERMISSION = "mysterriastuff.joinmsg.admin";
    private static final List<String> SESSION_ACTIONS = List.of("confirm", "cancel", "restart");
    private static final List<String> ADMIN_ACTIONS = List.of("set", "get", "remove", "list", "default", "firstjoin", "reload", "repair");

    private final TokenItem token;
    private final JoinMessageStore store;
    private final JoinMessageSessionService sessions;

    @Override
    public String name() {
        return "joinmsg";
    }

    @Override
    public Map<String, String> help(CommandSender sender) {
        Map<String, String> help = new LinkedHashMap<>();
        if (sender.hasPermission(GIVE_PERMISSION)) {
            help.put("/mystuff joinmsg give <player> [amount]", "Give Custom Message Tokens");
        }
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            help.put("/mystuff joinmsg set <player> <join|quit> <message>", "Set a player's message");
            help.put("/mystuff joinmsg get|remove <player> [join|quit]", "View or remove a player's messages");
            help.put("/mystuff joinmsg list", "List players with custom messages");
            help.put("/mystuff joinmsg default <get|set> [join|quit] [message]", "Server-wide fallback messages");
            help.put("/mystuff joinmsg firstjoin <get|set> [message]", "Message for a player's first join");
            help.put("/mystuff joinmsg reload|repair", "Reload the store, or re-import legacy .rs backups");
        }
        return help;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase() : "";
        if (SESSION_ACTIONS.contains(action)) {
            handleSession(sender, action);
        } else if (action.equals("give")) {
            give(sender, args);
        } else if (ADMIN_ACTIONS.contains(action)) {
            if (allowed(sender, ADMIN_PERMISSION)) {
                handleAdmin(sender, action, args);
            }
        } else {
            Feedback.error(sender, "Usage: /mystuff joinmsg <" + String.join("|", ADMIN_ACTIONS) + "|give>");
        }
    }

    private void handleSession(CommandSender sender, String action) {
        Player player = self(sender);
        if (player == null) {
            return;
        }
        switch (action) {
            case "confirm" -> sessions.confirm(player);
            case "cancel" -> sessions.cancel(player);
            default -> sessions.restart(player);
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (!allowed(sender, GIVE_PERMISSION)) {
            return;
        }
        if (args.length < 3) {
            Feedback.error(sender, "Usage: /mystuff joinmsg give <player> [amount]");
            return;
        }
        Player target = onlinePlayer(sender, args[2]);
        Integer amount = amount(sender, args, 3);
        if (target == null || amount == null) {
            return;
        }
        ItemDelivery.give(target, token.create(amount));
        target.sendMessage(token.message("token-received", "amount", String.valueOf(amount)));
        Feedback.success(sender, "Gave " + target.getName() + " " + amount + " Custom Message Token(s).");
        PrettyLogger.info(sender.getName() + " gave " + target.getName() + " " + amount + " Custom Message Token(s)");
    }

    private void handleAdmin(CommandSender sender, String action, String[] args) {
        switch (action) {
            case "set" -> set(sender, args);
            case "get" -> get(sender, args);
            case "remove" -> remove(sender, args);
            case "list" -> list(sender);
            case "default" -> defaults(sender, args);
            case "firstjoin" -> firstJoin(sender, args);
            case "reload" -> {
                store.load();
                Feedback.success(sender, "Join/quit messages reloaded from disk.");
            }
            default -> repair(sender);
        }
    }

    private void set(CommandSender sender, String[] args) {
        if (args.length < 5 || !isMessageType(args[3])) {
            Feedback.error(sender, "Usage: /mystuff joinmsg set <player> <join|quit> <message>");
            return;
        }
        boolean join = args[3].equalsIgnoreCase("join");
        String message = joinArgs(args, 4);
        OfflinePlayer target = store.resolveTarget(args[2]);
        SetResult result = target != null
                ? store.setPlayerMessages(target, join ? message : null, join ? null : message)
                : store.setPendingMessages(args[2], join ? message : null, join ? null : message);
        switch (result) {
            case OK -> Feedback.success(sender, "Set the " + args[3].toLowerCase() + " message for " + args[2]
                    + (target == null ? " (applies when they next join)." : "."));
            case MISSING_PLACEHOLDER_JOIN, MISSING_PLACEHOLDER_QUIT -> Feedback.error(sender, "The message must contain %player%.");
            case WRITE_ERROR -> Feedback.error(sender, "Could not save the message store, check console.");
        }
    }

    private void get(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Feedback.error(sender, "Usage: /mystuff joinmsg get <player>");
            return;
        }
        MessageEntry entry = store.findEntry(store.resolveTarget(args[2]), args[2]);
        if (entry == null) {
            Feedback.info(sender, args[2] + " has no custom messages.");
            return;
        }
        Feedback.info(sender, "Messages for " + entry.getName() + (entry.isPending() ? " (pending)" : "") + ":");
        Feedback.field(sender, "  Join", entry.getJoin());
        Feedback.field(sender, "  Quit", entry.getQuit());
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length < 3 || (args.length > 3 && !isMessageType(args[3]))) {
            Feedback.error(sender, "Usage: /mystuff joinmsg remove <player> [join|quit]");
            return;
        }
        boolean join = args.length == 3 || args[3].equalsIgnoreCase("join");
        boolean quit = args.length == 3 || args[3].equalsIgnoreCase("quit");
        if (store.removeMessages(store.resolveTarget(args[2]), args[2], join, quit)) {
            Feedback.success(sender, "Removed the message(s) for " + args[2] + ".");
        } else {
            Feedback.info(sender, args[2] + " had no such message.");
        }
    }

    private void list(CommandSender sender) {
        List<MessageEntry> entries = store.listEntries();
        Feedback.info(sender, "Custom join/quit messages (" + entries.size() + "):");
        entries.stream()
                .sorted(Comparator.comparing(entry -> entry.getName().toLowerCase()))
                .forEach(entry -> sender.sendMessage(Component.text("  • ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(entry.getName(), entry.isPending() ? NamedTextColor.GRAY : NamedTextColor.AQUA))
                        .append(Component.text(entry.isPending() ? " (pending)" : "", NamedTextColor.DARK_GRAY))));
    }

    private void defaults(CommandSender sender, String[] args) {
        if (args.length >= 3 && args[2].equalsIgnoreCase("get")) {
            Feedback.field(sender, "Default join", store.getDefaultJoinMessage());
            Feedback.field(sender, "Default quit", store.getDefaultQuitMessage());
            return;
        }
        if (args.length < 5 || !args[2].equalsIgnoreCase("set") || !isMessageType(args[3])) {
            Feedback.error(sender, "Usage: /mystuff joinmsg default <get|set> [join|quit] [message]");
            return;
        }
        String message = joinArgs(args, 4);
        if (!message.contains(JoinMessageStore.PLACEHOLDER)) {
            Feedback.error(sender, "The message must contain %player%.");
            return;
        }
        boolean saved = args[3].equalsIgnoreCase("join") ? store.setDefaultJoinMessage(message) : store.setDefaultQuitMessage(message);
        reportSaved(sender, saved, "Default " + args[3].toLowerCase() + " message updated.");
    }

    private void firstJoin(CommandSender sender, String[] args) {
        if (args.length >= 3 && args[2].equalsIgnoreCase("get")) {
            Feedback.field(sender, "First-join message", store.getFirstJoinMessage());
            return;
        }
        if (args.length < 4 || !args[2].equalsIgnoreCase("set")) {
            Feedback.error(sender, "Usage: /mystuff joinmsg firstjoin <get|set> [message]");
            return;
        }
        reportSaved(sender, store.setFirstJoinMessage(joinArgs(args, 3)), "First-join message updated.");
    }

    private void repair(CommandSender sender) {
        int recovered = store.repairFromLegacyBackups();
        if (recovered < 0) {
            Feedback.info(sender, "No join.rs.migrated or quit.rs.migrated files found.");
        } else if (recovered == 0) {
            Feedback.info(sender, "The store already has everything from the legacy backups.");
        } else {
            Feedback.success(sender, "Recovered " + recovered + " entries from the legacy backups.");
        }
    }

    private void reportSaved(CommandSender sender, boolean saved, String message) {
        if (saved) {
            Feedback.success(sender, message);
        } else {
            Feedback.error(sender, "Could not save the message store, check console.");
        }
    }

    private boolean isMessageType(String type) {
        return type.equalsIgnoreCase("join") || type.equalsIgnoreCase("quit");
    }

    private String joinArgs(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> actions = sender.hasPermission(ADMIN_PERMISSION) ? ADMIN_ACTIONS : SESSION_ACTIONS;
            return matching(sender.hasPermission(GIVE_PERMISSION) ? concat(actions, "give") : actions, args[1]);
        }
        String action = args[1].toLowerCase();
        if (args.length == 3 && List.of("give", "set", "get", "remove").contains(action)) {
            return matching(onlineNames(), args[2]);
        }
        if (args.length == 3 && List.of("default", "firstjoin").contains(action)) {
            return matching(List.of("get", "set"), args[2]);
        }
        if (args.length == 4 && (List.of("set", "remove").contains(action) || (action.equals("default") && args[2].equalsIgnoreCase("set")))) {
            return matching(List.of("join", "quit"), args[3]);
        }
        return List.of();
    }

    private List<String> concat(List<String> list, String extra) {
        return Stream.concat(list.stream(), Stream.of(extra)).toList();
    }
}
