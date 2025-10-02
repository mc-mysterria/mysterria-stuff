package net.mysterria.stuff.battlepass;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.mysterria.stuff.MysterriaStuff;
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
import java.util.Objects;

public class ElytraCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {
        if (command.getName().equalsIgnoreCase("mysterriastuff")) {
            switch (strings[0]) {
                case "reload" -> {
                    return true;
                }
                case "give" -> {
                    switch (strings[1]) {
                        case "elytra" -> {
                            String playerName = strings[2];
                            Player player = Bukkit.getPlayer(playerName);

                            if (player == null || !player.isOnline()) {
                                return false;
                            }

                            if (player.getInventory().firstEmpty() != -1) {
                                player.getInventory().addItem(Objects.requireNonNull(getElytra()));
                            } else {
                                player.getWorld().dropItemNaturally(player.getLocation(), Objects.requireNonNull(getElytra()));
                            }

                            MysterriaStuff.getInstance().log("Given " + playerName + " an elytra!");

                            return true;
                        }
                    }
                }
                case "export" -> {
                    if (commandSender instanceof Player player) {
                        ItemStack itemStack = player.getInventory().getItemInMainHand();
                        if (itemStack.getType() != Material.AIR) {
                            Component component = Component.text("Click here to copy item as bytes.")
                                    .clickEvent(ClickEvent.copyToClipboard(Base64.getEncoder().encodeToString(itemStack.serializeAsBytes())));
                            commandSender.sendMessage(component);
                            return true;
                        }
                    }

                }
            }
        }

        return false;
    }

    private ItemStack getElytra() {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(StaticItems.REINFORCED_ELYTRA));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
