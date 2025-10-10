package net.mysterria.stuff.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tab completion handler for MysterriaStuff commands
 */
public class MainCommandTabCompleter implements TabCompleter {

    private static final List<String> MAIN_COMMANDS = Arrays.asList(
            "help", "info", "status", "reload", "give", "export", "debug", "recipe"
    );

    private static final List<String> ITEM_TYPES = List.of(
            "elytra"
    );

    private static final List<String> RECIPE_SUBCOMMANDS = Arrays.asList(
            "list", "reload"
    );

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument - main subcommands
            return filterStartingWith(MAIN_COMMANDS, args[0]);
        } else if (args.length == 2) {
            // Second argument depends on first
            switch (args[0].toLowerCase()) {
                case "give" -> {
                    return filterStartingWith(ITEM_TYPES, args[1]);
                }
                case "recipe" -> {
                    return filterStartingWith(RECIPE_SUBCOMMANDS, args[1]);
                }
            }
        } else if (args.length == 3) {
            // Third argument
            if (args[0].equalsIgnoreCase("give")) {
                // Suggest online player names
                return filterStartingWith(
                        Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .collect(Collectors.toList()),
                        args[2]
                );
            }
        }

        return completions;
    }

    /**
     * Filter a list to only include strings starting with the given prefix
     */
    private List<String> filterStartingWith(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }
}
