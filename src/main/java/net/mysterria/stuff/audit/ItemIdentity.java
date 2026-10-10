package net.mysterria.stuff.audit;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Only non-stackable single items (max stack size 1, amount 1) are ever stamped: CoI's dupe scan
 * flags any {@code circleofimagination:item_uuid} whose summed amount exceeds 1, and a per-instance
 * PDC value would stop identical items from stacking. Fungible items such as tokens are identified
 * per lot instead ({@link #lotMetadata}), in the audit row only.
 */
public final class ItemIdentity {

    public static final String ORIGIN_STAFF_GRANT = "STAFF_GRANT";
    public static final String ORIGIN_SHOP = "SHOP";
    /** item_uuid_scope for a per-instance id that is also written to the item PDC. */
    public static final String SCOPE_INSTANCE = "instance";
    /** item_uuid_scope for a row-only id naming one granted stack of unstamped items. */
    public static final String SCOPE_LOT = "lot";

    private static final String NAMESPACE = "circleofimagination";
    private static final NamespacedKey ITEM_UUID = new NamespacedKey(NAMESPACE, "item_uuid");
    private static final NamespacedKey ITEM_ORIGIN = new NamespacedKey(NAMESPACE, "item_origin");

    private ItemIdentity() {
    }

    /**
     * Never throws: it runs after the token was consumed.
     *
     * @return the stamped item UUID, or null if the item is stackable, has no meta or cannot be stamped
     */
    public static String stampShop(ItemStack item) {
        try {
            if (!isUniqueInstance(item)) return null;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            String itemUuid = UUID.randomUUID().toString();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ITEM_UUID, PersistentDataType.STRING, itemUuid);
            pdc.set(ITEM_ORIGIN, PersistentDataType.STRING, ORIGIN_SHOP);
            item.setItemMeta(meta);
            return itemUuid;
        } catch (RuntimeException unstampable) {
            return null;
        }
    }

    /** True only for items that can never stack, so a per-instance uuid changes nothing visible. */
    private static boolean isUniqueInstance(ItemStack item) {
        return item != null && item.getAmount() == 1 && item.getMaxStackSize() == 1;
    }

    /**
     * Row-only lot identity for one granted stack of fungible items that are deliberately not
     * PDC-stamped. The returned {@code item_uuid} names the lot, not an item instance, and is never
     * written to the item.
     *
     * @param mintedBy actor UUID string, "console", or null to omit
     */
    public static Map<String, Object> lotMetadata(String origin, String mintedBy, int amount) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("item_uuid", UUID.randomUUID().toString());
        metadata.put("item_uuid_scope", SCOPE_LOT);
        metadata.put("item_mint_qty", amount);
        metadata.put("item_origin", origin);
        if (mintedBy != null) metadata.put("item_minted_by", mintedBy);
        return metadata;
    }
}
