package net.mysterria.stuff.features.chatcontrol;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;


public class ChatControlTokenListener implements Listener {

    private final ChatControlMessageManager manager;
    private final ChatControlSessionHandler sessionHandler;

    public ChatControlTokenListener(ChatControlSessionHandler sessionHandler) {
        this.manager = ChatControlMessageManager.getInstance();
        this.sessionHandler = sessionHandler;
    }


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!manager.isToken(item)) {
            return;
        }

        if (!player.hasPermission("mysterriastuff.chatcontrol.use")) {
            player.sendMessage(manager.getMessage("no-permission"));
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);


        if (sessionHandler.hasActiveSession(player.getUniqueId())) {
            player.sendMessage(manager.getMessage("already-in-session"));
            return;
        }


        if (!manager.consumeToken(item, 1)) {
            player.sendMessage(manager.getMessage("token-error"));
            return;
        }


        sessionHandler.startSession(player);
    }
}
