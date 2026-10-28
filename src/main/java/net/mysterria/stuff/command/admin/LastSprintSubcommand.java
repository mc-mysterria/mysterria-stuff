package net.mysterria.stuff.command.admin;

import lombok.RequiredArgsConstructor;
import net.mysterria.stuff.config.ConfigManager;
import net.mysterria.stuff.domain.lastsprint.menu.LastSprintKitMenu;
import net.mysterria.stuff.domain.lastsprint.service.LastSprintService;
import net.mysterria.stuff.util.log.PrettyLogger;
import net.mysterria.stuff.util.text.Feedback;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class LastSprintSubcommand extends Subcommand {

    private static final String PERMISSION = "mysterriastuff.lastsprint";

    private final ConfigManager config;
    private final LastSprintService service;

    @Override
    public String name() {
        return "lastsprint";
    }

    @Override
    public Map<String, String> help(CommandSender sender) {
        Map<String, String> help = new LinkedHashMap<>();
        if (sender.hasPermission(PERMISSION)) {
            help.put("/mystuff lastsprint", "Show Last Sprint status");
            help.put("/mystuff lastsprint setup", "Edit the starter kit");
            help.put("/mystuff lastsprint enable|disable", "Turn auto-give on join on or off");
            help.put("/mystuff lastsprint give|reset <player>", "Give the kit now, or let a player receive it again");
        }
        return help;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!allowed(sender, PERMISSION)) {
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase() : "info";
        switch (action) {
            case "info" -> {
                Feedback.flag(sender, "Auto-give on join", config.isLastSprintActive());
                Feedback.field(sender, "Kit items", String.valueOf(service.getRewardCount()));
            }
            case "setup" -> {
                Player player = self(sender);
                if (player != null) {
                    player.openInventory(new LastSprintKitMenu(service).getInventory());
                }
            }
            case "enable", "disable" -> {
                boolean active = action.equals("enable");
                config.setLastSprintActive(active);
                Feedback.success(sender, active ? "New players will now receive the Last Sprint kit." : "The Last Sprint kit is no longer given on join.");
                PrettyLogger.info(sender.getName() + " turned Last Sprint " + (active ? "on" : "off"));
            }
            case "give", "reset" -> {
                if (args.length < 3) {
                    Feedback.error(sender, "Usage: /mystuff lastsprint " + action + " <player>");
                    return;
                }
                Player target = onlinePlayer(sender, args[2]);
                if (target == null) {
                    return;
                }
                if (action.equals("reset")) {
                    service.setReceivedKit(target.getUniqueId(), false);
                    Feedback.success(sender, target.getName() + " can receive the Last Sprint kit again.");
                } else if (service.getRewardCount() == 0) {
                    Feedback.error(sender, "The kit is empty. Use /mystuff lastsprint setup first.");
                } else {
                    service.giveKit(target);
                    Feedback.success(target, "You received the Last Sprint starter kit.");
                    Feedback.success(sender, "Gave the Last Sprint kit to " + target.getName() + ".");
                    PrettyLogger.info(sender.getName() + " gave the Last Sprint kit to " + target.getName());
                }
            }
            default -> Feedback.error(sender, "Usage: /mystuff lastsprint <setup|give|reset|enable|disable|info>");
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        return switch (args.length) {
            case 2 -> matching(List.of("info", "setup", "give", "reset", "enable", "disable"), args[1]);
            case 3 -> List.of("give", "reset").contains(args[1].toLowerCase()) ? matching(onlineNames(), args[2]) : List.of();
            default -> List.of();
        };
    }
}
