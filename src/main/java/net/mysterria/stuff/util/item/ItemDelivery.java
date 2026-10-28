package net.mysterria.stuff.util.item;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ItemDelivery {

    public static boolean give(Player player, ItemStack item) {
        var overflow = player.getInventory().addItem(item);
        overflow.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        return !overflow.isEmpty();
    }
}
