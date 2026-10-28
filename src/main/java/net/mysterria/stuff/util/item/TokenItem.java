package net.mysterria.stuff.util.item;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.mysterria.stuff.config.models.TokenSettings;
import net.mysterria.stuff.util.text.AdventureUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.function.Supplier;

@RequiredArgsConstructor
public class TokenItem {

    private final List<NamespacedKey> keys;
    private final Supplier<TokenSettings> settings;

    public ItemStack create(int amount) {
        ItemStack token = new ItemStack(Material.PAPER, amount);
        token.editMeta(meta -> {
            meta.displayName(AdventureUtil.parseItemText(settings.get().getItemName()));
            meta.lore(settings.get().getItemLore().stream().map(AdventureUtil::parseItemText).toList());
            meta.getPersistentDataContainer().set(keys.getFirst(), PersistentDataType.BYTE, (byte) 1);
        });
        return token;
    }

    public Component message(String key, String... placeholders) {
        return settings.get().message(key, placeholders);
    }

    public boolean matches(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return false;
        }
        var container = item.getItemMeta().getPersistentDataContainer();
        return keys.stream().anyMatch(key -> container.has(key, PersistentDataType.BYTE));
    }

    public int count(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (matches(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public boolean takeOne(Player player) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (matches(item)) {
                item.subtract();
                return true;
            }
        }
        return false;
    }
}
