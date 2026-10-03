package net.mysterria.stuff.utils;

import net.mysterria.stuff.audit.ItemIdentity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single delivery path for items handed to a player: inventory first, leftovers dropped at the
 * player's feet. Amounts and item identities are snapshotted before {@code Inventory.addItem},
 * which may mutate the passed stacks. A leftover only counts as dropped when the spawned item
 * entity is still valid (another plugin may cancel {@code ItemSpawnEvent}); otherwise it is
 * counted as undelivered.
 */
public final class ItemDelivery {

    public static final String KEY_MODE = "delivery_mode";
    public static final String KEY_DELIVERED = "delivered_amount";
    public static final String KEY_DROPPED = "dropped_amount";
    public static final String KEY_UNDELIVERED = "undelivered_amount";

    private ItemDelivery() {
    }

    public record Result(int requestedAmount, int deliveredAmount, int droppedAmount,
                         int undeliveredAmount, List<String> itemUuids) {

        public Result {
            itemUuids = List.copyOf(itemUuids);
        }

        static Result empty() {
            return new Result(0, 0, 0, 0, List.of());
        }

        public String mode() {
            if (undeliveredAmount > 0) {
                return deliveredAmount + droppedAmount <= 0 ? "undelivered" : "partial_undelivered";
            }
            if (droppedAmount <= 0) return "inventory";
            return deliveredAmount <= 0 ? "dropped" : "partial";
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
            List<String> uuids = new ArrayList<>(itemUuids);
            uuids.addAll(other.itemUuids);
            return new Result(requestedAmount + other.requestedAmount,
                    deliveredAmount + other.deliveredAmount, droppedAmount + other.droppedAmount,
                    undeliveredAmount + other.undeliveredAmount, uuids);
        }

        public Map<String, Object> toMetadata() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put(KEY_MODE, mode());
            values.put(KEY_DELIVERED, deliveredAmount);
            values.put(KEY_DROPPED, droppedAmount);
            if (undeliveredAmount > 0) values.put(KEY_UNDELIVERED, undeliveredAmount);
            return values;
        }

        public Map<String, Object> identityMetadata() {
            Map<String, Object> values = new LinkedHashMap<>();
            if (itemUuids.size() == 1) {
                values.put("item_uuid", itemUuids.get(0));
                values.put("item_uuid_scope", ItemIdentity.SCOPE_INSTANCE);
            } else if (!itemUuids.isEmpty()) {
                values.put("item_uuids", String.join(",", itemUuids));
                values.put("item_uuid_count", itemUuids.size());
                values.put("item_uuid_scope", ItemIdentity.SCOPE_INSTANCE);
            }
            return values;
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
        String itemUuid = ItemIdentity.readUuid(item);
        List<String> uuids = itemUuid == null ? List.of() : List.of(itemUuid);
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
        return new Result(requestedAmount, deliveredAmount, droppedAmount, undeliveredAmount, uuids);
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
