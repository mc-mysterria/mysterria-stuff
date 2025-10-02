package net.mysterria.stuff.battlepass;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

public class ElytraBlocker implements Listener {

    @EventHandler
    public void onItemEnchantment(PrepareItemEnchantEvent event) {
        if (isElytra(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemCraft(PrepareAnvilEvent event) {
        if (isElytra(event.getResult())) {
            event.setResult(null);
        }
    }

    private boolean isElytra(ItemStack item) {
        if (item == null || item.getType() != Material.ELYTRA) {
            return false;
        }

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            NamespacedKey namespacedKey = getVaneNamespaceKey("custom_item_identifier");
            if (pdc.has(namespacedKey, org.bukkit.persistence.PersistentDataType.STRING)) {
                String value = pdc.get(namespacedKey, org.bukkit.persistence.PersistentDataType.STRING);
                return "vane_trifles:reinforced_elytra".equals(value);
            }
        }

        return false;
    }

    private NamespacedKey getVaneNamespaceKey(String key) {
        return new NamespacedKey("vane", key);
    }

}
