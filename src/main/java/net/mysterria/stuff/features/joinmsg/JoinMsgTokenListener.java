package net.mysterria.stuff.features.joinmsg;

import net.mysterria.stuff.audit.ItemIdentity;
import net.mysterria.stuff.audit.StuffAuditEmitter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;


public class JoinMsgTokenListener implements Listener {

    private final JoinMsgTokenManager manager;
    private final JoinMsgSessionHandler sessionHandler;

    public JoinMsgTokenListener(JoinMsgSessionHandler sessionHandler) {
        this.manager = JoinMsgTokenManager.getInstance();
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

        if (!player.hasPermission("mysterriastuff.joinmsg.use")) {
            player.sendMessage(manager.getMessage("no-permission"));
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);


        if (sessionHandler.hasActiveSession(player.getUniqueId())) {
            player.sendMessage(manager.getMessage("already-in-session"));
            return;
        }


        String tokenUuid = ItemIdentity.readUuid(item);
        // Snapshot before consumption: consumeToken decrements the stack in place.
        Map<String, Object> tokenIdentity = ItemIdentity.consumedTokenIdentity(item, manager.tokenMarker(item));
        if (!manager.consumeToken(item, 1)) {
            player.sendMessage(manager.getMessage("token-error"));
            return;
        }

        UUID correlationId = StuffAuditEmitter.correlationId();
        Map<String, Object> metadata = new LinkedHashMap<>(
                StuffAuditEmitter.tokenMetadata("joinmsg", 1, "joinmsg_session_started"));
        metadata.putAll(tokenIdentity);
        metadata.putAll(StuffAuditEmitter.location(player));
        StuffAuditEmitter.emit("token.consumed", correlationId,
                StuffAuditEmitter.tokenBusinessId("joinmsg"), player.getUniqueId(),
                player.getUniqueId(), null, "joinmsg_session_started", metadata);

        sessionHandler.startSession(player, correlationId, tokenUuid);
    }
}
