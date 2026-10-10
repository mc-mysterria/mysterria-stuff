package net.mysterria.stuff.utils;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gives items to a player: inventory first, leftovers dropped at their feet. A drop only counts
 * when the item entity is still valid, since another plugin may cancel the spawn.
 */
public final class ItemDelivery {

    public static final String KEY_MODE = "delivery_mode";
    public static final String KEY_DELIVERED = "delivered_amount";
    public static final String KEY_DROPPED = "dropped_amount";
    public static final String KEY_UNDELIVERED = "undelivered_amount";

    private ItemDelivery() {
    }

    public record Result(int requestedAmount, int deliveredAmount, int droppedAmount,
                         int undeliveredAmount) {

        static Result empty() {
            return new Result(0, 0, 0, 0);
        }

        public String mode() {
            if (undeliveredAmount > 0) {
                return deliveredAmount + droppedAmount <= 0 ? "undelivered" : "partial_undelivered";
            }
            if (droppedAmount <= 0) return "inventory";
            return deliveredAmount <= 0 ? "dropped" : "partial";
        }

        public boolean complete() {
            return undeliveredAmount <= 0;
        }

        public boolean anyDelivered() {
            return deliveredAmount + droppedAmount > 0;
        }

        public Result plus(Result other) {
            return new Result(requestedAmount + other.requestedAmount,
                    deliveredAmount + other.deliveredAmount, droppedAmount + other.droppedAmount,
                    undeliveredAmount + other.undeliveredAmount);
        }

        public Map<String, Object> toMetadata() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put(KEY_MODE, mode());
            values.put(KEY_DELIVERED, deliveredAmount);
            values.put(KEY_DROPPED, droppedAmount);
            if (undeliveredAmount > 0) values.put(KEY_UNDELIVERED, undeliveredAmount);
            return values;
        }
    }

    public static Result deliver(Player player, ItemStack item) {
        return deliver(player, item, false);
    }

    /** Like {@link #deliver}, but with no empty slot the whole stack is dropped instead of merged into partial stacks. */
    public static Result deliverOrDropWhenFull(Player player, ItemStack item) {
        return deliver(player, item, true);
    }

    private static Result deliver(Player player, ItemStack item, boolean dropWhenNoEmptySlot) {
        if (item == null || item.getType().isAir()) return Result.empty();
        int requestedAmount = item.getAmount();
        Map<Integer, ItemStack> leftovers = dropWhenNoEmptySlot && player.getInventory().firstEmpty() == -1
                ? Map.of(0, item)
                : player.getInventory().addItem(item);
        int droppedAmount = 0;
        int undeliveredAmount = 0;
        for (ItemStack leftover : leftovers.values()) {
            if (leftover == null || leftover.getAmount() <= 0) continue;
            int amount = leftover.getAmount();
            if (dropSpawned(player, leftover)) {
                droppedAmount += amount;
            } else {
                undeliveredAmount += amount;
            }
        }
        int deliveredAmount = Math.max(0, requestedAmount - droppedAmount - undeliveredAmount);
        return new Result(requestedAmount, deliveredAmount, droppedAmount, undeliveredAmount);
    }

    /** False when the drop was refused, e.g. a cancelled ItemSpawnEvent. */
    private static boolean dropSpawned(Player player, ItemStack stack) {
        Item dropped = player.getWorld().dropItemNaturally(player.getLocation(), stack);
        return dropped != null && dropped.isValid() && !dropped.isDead();
    }

    public static Result deliverAll(Player player, List<ItemStack> items) {
        Result total = Result.empty();
        for (ItemStack item : items) {
            total = total.plus(deliverOrDropWhenFull(player, item));
        }
        return total;
    }

    /** Summed amount of the non-air stacks, as reported in audit rows. */
    public static int totalAmount(List<ItemStack> items) {
        int total = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) total += item.getAmount();
        }
        return total;
    }
}
