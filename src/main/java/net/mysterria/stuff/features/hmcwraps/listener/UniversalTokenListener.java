package net.mysterria.stuff.features.hmcwraps.listener;

import de.skyslycer.hmcwraps.HMCWraps;
import net.mysterria.stuff.features.hmcwraps.UniversalTokenManager;
import net.mysterria.stuff.features.hmcwraps.gui.UniversalTokenGUI;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Listener for Universal Token interactions
 */
public class UniversalTokenListener implements Listener {

    private final UniversalTokenManager manager;
    private final UniversalTokenGUI tokenGUI;
    private final HMCWraps hmcWraps;

    public UniversalTokenListener(Plugin plugin, HMCWraps hmcWraps, WrapPreviewListener previewHandler) {
        this.manager = UniversalTokenManager.getInstance();
        this.hmcWraps = hmcWraps;
        this.tokenGUI = new UniversalTokenGUI(plugin, previewHandler);
    }

    /**
     * Handle right-click with Universal Token
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

        if (!player.hasPermission("mysterriastuff.token.use")) {
            player.sendMessage(manager.getMessage("no-permission"));
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        if (hmcWraps == null) {
            player.sendMessage(manager.getMessage("hmcwraps-not-loaded"));
            return;
        }

        tokenGUI.openMainGUI(player, hmcWraps);
    }
}
