package net.mysterria.stuff.domain.wrap.listener;

import lombok.RequiredArgsConstructor;
import net.mysterria.stuff.domain.wrap.menu.WrapExchangeMenu;
import net.mysterria.stuff.util.item.TokenItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

@RequiredArgsConstructor
public class UniversalTokenListener implements Listener {

    private final TokenItem token;
    private final WrapExchangeMenu menu;

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!event.getAction().isRightClick() || event.getHand() != EquipmentSlot.HAND
                || !token.matches(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        if (!player.hasPermission("mysterriastuff.token.use")) {
            player.sendMessage(token.message("no-permission"));
            return;
        }
        menu.openCategories(player);
    }
}
