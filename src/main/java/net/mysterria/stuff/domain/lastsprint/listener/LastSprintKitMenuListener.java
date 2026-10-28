package net.mysterria.stuff.domain.lastsprint.listener;

import net.mysterria.stuff.domain.lastsprint.menu.LastSprintKitMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class LastSprintKitMenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof LastSprintKitMenu menu)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < LastSprintKitMenu.KIT_SLOTS || slot >= event.getInventory().getSize()) {
            return;
        }
        event.setCancelled(true);
        if (slot == LastSprintKitMenu.SAVE_SLOT) {
            event.getWhoClicked().closeInventory();
        } else if (slot == LastSprintKitMenu.CLEAR_SLOT) {
            menu.clear();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof LastSprintKitMenu
                && event.getRawSlots().stream().anyMatch(slot -> slot >= LastSprintKitMenu.KIT_SLOTS && slot < event.getInventory().getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof LastSprintKitMenu menu && event.getPlayer() instanceof Player player) {
            menu.save(player);
        }
    }
}
