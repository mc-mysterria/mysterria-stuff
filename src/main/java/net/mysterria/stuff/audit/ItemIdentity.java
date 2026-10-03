package net.mysterria.stuff.audit;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
    private static final NamespacedKey ITEM_MINTED_BY = new NamespacedKey(NAMESPACE, "item_minted_by");
    private static final NamespacedKey ITEM_PARENT = new NamespacedKey(NAMESPACE, "item_parent");

    private ItemIdentity() {
    }

    /** @return the stamped item UUID, or null if the item is stackable or has no meta */
    public static String stamp(ItemStack item, String origin, String mintedBy, String parentUuid) {
        if (!isUniqueInstance(item)) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String itemUuid = UUID.randomUUID().toString();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ITEM_UUID, PersistentDataType.STRING, itemUuid);
        pdc.set(ITEM_ORIGIN, PersistentDataType.STRING, origin);
        if (mintedBy != null) pdc.set(ITEM_MINTED_BY, PersistentDataType.STRING, mintedBy);
        if (parentUuid != null) pdc.set(ITEM_PARENT, PersistentDataType.STRING, parentUuid);
        item.setItemMeta(meta);
        return itemUuid;
    }

    public static String stampShop(ItemStack item, String parentUuid) {
        return stamp(item, ORIGIN_SHOP, null, parentUuid);
    }

    /** True only for items that can never stack, so a per-instance uuid changes nothing visible. */
    public static boolean isUniqueInstance(ItemStack item) {
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

    /**
     * Call before the stack is decremented. {@code item_sha256} hashes a one-item copy so it does
     * not depend on how many tokens were in the stack.
     */
    public static Map<String, Object> consumedTokenIdentity(ItemStack item, String tokenMarker) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (item == null) return metadata;
        String itemUuid = readUuid(item);
        if (itemUuid != null) {
            metadata.put("item_uuid", itemUuid);
            metadata.put("item_uuid_scope", SCOPE_INSTANCE);
            return metadata;
        }
        if (tokenMarker != null) metadata.put("token_marker", tokenMarker);
        metadata.put("material", item.getType().getKey().toString());
        String displayNameHash = displayNameHash(item);
        if (displayNameHash != null) metadata.put("display_name_sha256", displayNameHash);
        String itemHash = itemBytesHash(item);
        if (itemHash != null) metadata.put("item_sha256", itemHash);
        return metadata;
    }

    private static String displayNameHash(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || meta.displayName() == null) return null;
        return StuffAuditEmitter.sha256(PlainTextComponentSerializer.plainText().serialize(meta.displayName()));
    }

    private static String itemBytesHash(ItemStack item) {
        try {
            ItemStack single = item.clone();
            single.setAmount(1);
            return StuffAuditEmitter.sha256(single.serializeAsBytes());
        } catch (RuntimeException unserializable) {
            return null;
        }
    }

    public static String readUuid(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        try {
            return item.getItemMeta().getPersistentDataContainer().get(ITEM_UUID, PersistentDataType.STRING);
        } catch (IllegalArgumentException wrongType) {
            return null;
        }
    }
}
