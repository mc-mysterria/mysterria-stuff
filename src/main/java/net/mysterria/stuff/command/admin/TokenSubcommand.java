package net.mysterria.stuff.command.admin;

import lombok.RequiredArgsConstructor;
import net.mysterria.stuff.util.item.ItemDelivery;
import net.mysterria.stuff.util.item.TokenItem;
import net.mysterria.stuff.util.log.PrettyLogger;
import net.mysterria.stuff.util.text.Feedback;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class TokenSubcommand extends Subcommand {

    private static final String GIVE_PERMISSION = "mysterriastuff.token.give";

    private final TokenItem token;

    @Override
    public String name() {
        return "token";
    }

    @Override
    public Map<String, String> help(CommandSender sender) {
        return sender.hasPermission(GIVE_PERMISSION)
                ? Map.of("/mystuff token give <player> [amount]", "Give Universal Wrap Tokens")
                : Map.of();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!allowed(sender, GIVE_PERMISSION)) {
            return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("give")) {
            Feedback.error(sender, "Usage: /mystuff token give <player> [amount]");
            return;
        }
        Player target = onlinePlayer(sender, args[2]);
        Integer amount = amount(sender, args, 3);
        if (target == null || amount == null) {
            return;
        }
        ItemDelivery.give(target, token.create(amount));
        target.sendMessage(token.message("token-received", "amount", String.valueOf(amount)));
        Feedback.success(sender, "Gave " + target.getName() + " " + amount + " Universal Wrap Token(s).");
        PrettyLogger.info(sender.getName() + " gave " + target.getName() + " " + amount + " Universal Wrap Token(s)");
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        return switch (args.length) {
            case 2 -> matching(List.of("give"), args[1]);
            case 3 -> matching(onlineNames(), args[2]);
            case 4 -> List.of("1", "5", "10", "64");
            default -> List.of();
        };
    }
}
