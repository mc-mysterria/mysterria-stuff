package net.mysterria.stuff.features.chatcontrol;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Listener for ChatControl Message Token interactions
 */
public class ChatControlTokenListener implements Listener {

    private final ChatControlMessageManager manager;
    private final ChatControlSessionHandler sessionHandler;

    public ChatControlTokenListener(ChatControlSessionHandler sessionHandler) {
        this.manager = ChatControlMessageManager.getInstance();
        this.sessionHandler = sessionHandler;
    }

    /**
     * Handle right-click with ChatControl Message Token
     */
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

        // Check if player already has an active session
        if (sessionHandler.hasActiveSession(player.getUniqueId())) {
            player.sendMessage(manager.getMessage("already-in-session"));
            return;
        }

        // Consume the token
        if (!manager.consumeToken(item, 1)) {
            player.sendMessage(manager.getMessage("token-error"));
            return;
        }

        // Start the configuration session
        sessionHandler.startSession(player);
    }
}
