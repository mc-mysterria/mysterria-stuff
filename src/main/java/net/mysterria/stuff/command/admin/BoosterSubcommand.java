package net.mysterria.stuff.command.admin;

import lombok.RequiredArgsConstructor;
import net.mysterria.stuff.domain.booster.service.BoosterPatriarchService;
import net.mysterria.stuff.util.text.Feedback;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
public class BoosterSubcommand extends Subcommand {

    private static final String PERMISSION = "mysterriastuff.booster";

    private final BoosterPatriarchService service;

    @Override
    public String name() {
        return "booster";
    }

    @Override
    public Map<String, String> help(CommandSender sender) {
        Map<String, String> help = new LinkedHashMap<>();
        if (sender.hasPermission(PERMISSION)) {
            help.put("/mystuff booster check <player>", "Explain a player's Patriarch state");
            help.put("/mystuff booster grant|revoke <player>", "Force-grant or remove Patriarch");
            help.put("/mystuff booster refresh", "Re-fetch the booster list now");
            help.put("/mystuff booster list", "List current boosters");
        }
        return help;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!allowed(sender, PERMISSION)) {
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase() : "";
        switch (action) {
            case "refresh" -> {
                service.refresh();
                Feedback.success(sender, "Refreshing the booster list; online players sync when it arrives.");
            }
            case "list" -> {
                Set<String> boosters = service.getBoosters();
                Feedback.info(sender, "Boosters (" + boosters.size() + "): " + (boosters.isEmpty() ? "none loaded yet" : String.join(", ", boosters.stream().sorted().toList())));
            }
            case "check", "grant", "revoke" -> {
                if (args.length < 3) {
                    Feedback.error(sender, "Usage: /mystuff booster " + action + " <player>");
                    return;
                }
                Player target = onlinePlayer(sender, args[2]);
                if (target == null) {
                    return;
                }
                switch (action) {
                    case "check" -> service.sendDiagnostics(target, sender);
                    case "grant" -> service.forceGrant(target, sender);
                    default -> service.forceRevoke(target, sender);
                }
            }
            default -> Feedback.error(sender, "Usage: /mystuff booster <check|grant|revoke|refresh|list>");
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        return switch (args.length) {
            case 2 -> matching(List.of("check", "grant", "revoke", "refresh", "list"), args[1]);
            case 3 -> List.of("check", "grant", "revoke").contains(args[1].toLowerCase()) ? matching(onlineNames(), args[2]) : List.of();
            default -> List.of();
        };
    }
}
