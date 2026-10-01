package net.mysterria.stuff.utils;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Single delivery path for items handed to a player: inventory first, leftovers dropped at the
 * player's feet. The requested amount is snapshotted before {@code Inventory.addItem}, which may
 * mutate the passed stack. A leftover only counts as dropped when the spawned item entity is
 * still valid (another plugin may cancel {@code ItemSpawnEvent}); otherwise it is counted as
 * undelivered.
 */
public final class ItemDelivery {

    private ItemDelivery() {
    }

    public record Result(int requestedAmount, int deliveredAmount, int droppedAmount, int undeliveredAmount) {

        static Result empty() {
            return new Result(0, 0, 0, 0);
        }

        /** True when every requested item reached the inventory or a valid ground drop. */
        public boolean complete() {
            return undeliveredAmount <= 0;
        }

        /** True when at least one requested item reached the inventory or a valid ground drop. */
        public boolean anyDelivered() {
            return deliveredAmount + droppedAmount > 0;
        }

        public Result plus(Result other) {
            return new Result(requestedAmount + other.requestedAmount,
                    deliveredAmount + other.deliveredAmount, droppedAmount + other.droppedAmount,
                    undeliveredAmount + other.undeliveredAmount);
        }
    }

    public static Result deliver(Player player, ItemStack item) {
        return deliver(player, item, false);
    }

    /**
     * Elytra / Last Sprint kit semantics: when the inventory has no empty slot the whole stack is
     * dropped (it is not merged into matching partial stacks). Overflow from a partial add is
     * dropped rather than discarded.
     */
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

    /** Last Sprint kit delivery, one stack at a time with {@link #deliverOrDropWhenFull} semantics. */
    public static Result deliverAll(Player player, List<ItemStack> items) {
        Result total = Result.empty();
        for (ItemStack item : items) {
            total = total.plus(deliverOrDropWhenFull(player, item));
        }
        return total;
    }
}
