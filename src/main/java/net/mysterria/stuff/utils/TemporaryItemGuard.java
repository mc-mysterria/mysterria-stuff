package net.mysterria.stuff.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/** Cross-plugin provenance check; no COI runtime dependency or mutable config required. */
public final class TemporaryItemGuard {
    private TemporaryItemGuard() { }

    public static boolean isTemporary(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var data = item.getItemMeta().getPersistentDataContainer();
        return data.has(new NamespacedKey("circleofimagination", "historical_pact_item_owner"))
                || data.has(new NamespacedKey("circleofimagination", "historical_pact_item_slot"))
                || data.has(new NamespacedKey("circleofimagination", "historical_pact_control_item"));
    }
}
