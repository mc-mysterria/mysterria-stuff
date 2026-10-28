package net.mysterria.stuff.domain.joinmessage.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mysterria.stuff.domain.joinmessage.service.JoinMessageSessionService;
import net.mysterria.stuff.util.item.TokenItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

@RequiredArgsConstructor
public class JoinMessageSessionListener implements Listener {

    private final Plugin plugin;
    private final TokenItem token;
    private final JoinMessageSessionService sessions;

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!event.getAction().isRightClick() || event.getHand() != EquipmentSlot.HAND
                || !token.matches(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        if (!player.hasPermission("mysterriastuff.joinmsg.use")) {
            player.sendMessage(token.message("no-permission"));
            return;
        }
        sessions.start(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!sessions.isInSession(player)) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.getServer().getScheduler().runTask(plugin, () -> sessions.handleChat(player, message));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessions.abandon(event.getPlayer());
    }
}
