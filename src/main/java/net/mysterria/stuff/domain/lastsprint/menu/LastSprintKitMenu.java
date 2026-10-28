package net.mysterria.stuff.domain.lastsprint.menu;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.mysterria.stuff.domain.lastsprint.service.LastSprintService;
import net.mysterria.stuff.util.item.MenuItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class LastSprintKitMenu implements InventoryHolder {

    public static final int KIT_SLOTS = 45;
    public static final int SAVE_SLOT = 49;
    public static final int CLEAR_SLOT = 53;

    private final LastSprintService service;
    @Getter
    private final Inventory inventory;

    public LastSprintKitMenu(LastSprintService service) {
        this.service = service;
        this.inventory = Bukkit.createInventory(this, 54, Component.text("Last Sprint Kit", TextColor.color(0xAA55FF)));
        List<ItemStack> rewards = service.getRewards();
        for (int slot = 0; slot < Math.min(rewards.size(), KIT_SLOTS); slot++) {
            inventory.setItem(slot, rewards.get(slot));
        }
        for (int slot = KIT_SLOTS; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, MenuItems.filler());
        }
        inventory.setItem(SAVE_SLOT, MenuItems.button(Material.LIME_STAINED_GLASS_PANE, "✓ Save and close", NamedTextColor.GREEN,
                Component.text("Closing the menu also saves", NamedTextColor.GRAY)));
        inventory.setItem(CLEAR_SLOT, MenuItems.button(Material.RED_STAINED_GLASS_PANE, "✗ Empty the kit", NamedTextColor.RED,
                Component.text("Takes effect when the menu closes", NamedTextColor.GRAY)));
    }

    public void save(Player editor) {
        List<ItemStack> kit = new ArrayList<>();
        for (int slot = 0; slot < KIT_SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.isEmpty()) {
                kit.add(item.clone());
            }
        }
        service.setRewards(kit);
        editor.sendMessage(Component.text("Last Sprint kit saved: " + kit.size() + " items.", NamedTextColor.GREEN));
    }

    public void clear() {
        for (int slot = 0; slot < KIT_SLOTS; slot++) {
            inventory.setItem(slot, null);
        }
    }
}
