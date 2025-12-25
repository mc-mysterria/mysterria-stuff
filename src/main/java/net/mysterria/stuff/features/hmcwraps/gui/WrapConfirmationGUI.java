package net.mysterria.stuff.features.hmcwraps.gui;

import de.skyslycer.hmcwraps.HMCWraps;
import de.skyslycer.hmcwraps.serialization.wrap.Wrap;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.mysterria.stuff.features.hmcwraps.UniversalTokenManager;
import net.mysterria.stuff.utils.AdventureUtil;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;


public class WrapConfirmationGUI {

    private final UniversalTokenManager manager;
    private final MiniMessage miniMessage;

    public WrapConfirmationGUI() {
        this.manager = UniversalTokenManager.getInstance();
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void open(Player player, Wrap wrap, HMCWraps hmcWraps, Runnable previousGui) {
        String title = manager.getConfigManager().getGuiConfirmTitle();
        Component titleComponent = miniMessage.deserialize(title);

        Gui gui = Gui.gui()
                .title(titleComponent)
                .rows(4)
                .disableAllInteractions()
                .create();

        ItemStack wrapItem;
        try {
            if (wrap.getPhysical() == null) {
                player.sendMessage(Component.text("Error: This wrap has no physical item configured.", NamedTextColor.RED));
                player.sendMessage(Component.text("Please contact staff about wrap: " + wrap.getWrapName(), NamedTextColor.YELLOW));
                PrettyLogger.warn("Wrap '" + wrap.getWrapName() + "' has null physical item");
                return;
            }
            wrapItem = wrap.getPhysical().toItem(hmcWraps, player);
        } catch (Exception e) {
            player.sendMessage(Component.text("Error: Failed to load wrap item.", NamedTextColor.RED));
            player.sendMessage(Component.text("Please contact staff about wrap: " + wrap.getWrapName(), NamedTextColor.YELLOW));
            PrettyLogger.warn("Failed to get physical item for wrap '" + wrap.getWrapName() + "': " + e.getMessage());
            return;
        }

        gui.setItem(13, new GuiItem(wrapItem, event -> event.setCancelled(true)));

        ItemStack confirmItem = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.displayName(Component.text("✓ Confirm Exchange", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            confirmMeta.lore(Arrays.asList(
                    Component.text("Click to exchange your token", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("for this wrap!", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ));
            confirmItem.setItemMeta(confirmMeta);
        }

        GuiItem confirmButton = new GuiItem(confirmItem, event -> {
            event.setCancelled(true);
            handleConfirm(player, wrap, hmcWraps);
            gui.close(player);
        });

        ItemStack cancelItem = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.displayName(Component.text("✗ Cancel", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            cancelMeta.lore(Arrays.asList(
                    Component.text("Click to go back", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("without exchanging", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ));
            cancelItem.setItemMeta(cancelMeta);
        }

        GuiItem cancelButton = new GuiItem(cancelItem, event -> {
            event.setCancelled(true);
            player.sendMessage(manager.getMessage("exchange-cancelled"));
            gui.close(player);
            if (previousGui != null) {
                previousGui.run();
            }
        });

        for (int slot : Arrays.asList(10, 11, 12, 19, 20, 21)) {
            gui.setItem(slot, confirmButton);
        }

        for (int slot : Arrays.asList(14, 15, 16, 23, 24, 25)) {
            gui.setItem(slot, cancelButton);
        }

        gui.open(player);
    }


    private void handleConfirm(Player player, Wrap wrap, HMCWraps hmcWraps) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (!manager.isToken(heldItem)) {
            player.sendMessage(manager.getMessage("no-token-in-hand"));
            return;
        }

        if (!manager.consumeToken(heldItem, 1)) {
            player.sendMessage(manager.getMessage("no-token-in-hand"));
            return;
        }

        ItemStack wrapperItem;
        try {
            if (wrap.getPhysical() == null) {
                player.sendMessage(Component.text("Error: This wrap has no physical item configured.", NamedTextColor.RED));
                player.sendMessage(Component.text("Please contact staff about wrap: " + wrap.getWrapName(), NamedTextColor.YELLOW));
                PrettyLogger.warn("Wrap '" + wrap.getWrapName() + "' has null physical item during exchange");


                ItemStack tokenRefund = manager.createToken(1);
                if (player.getInventory().firstEmpty() == -1) {
                    player.getWorld().dropItemNaturally(player.getLocation(), tokenRefund);
                } else {
                    player.getInventory().addItem(tokenRefund);
                }
                player.sendMessage(Component.text("Your token has been refunded.", NamedTextColor.GREEN));
                return;
            }
            wrapperItem = wrap.getPhysical().toItem(hmcWraps, player);


            addWrapperPDC(wrapperItem, wrap, hmcWraps);
        } catch (Exception e) {
            player.sendMessage(Component.text("Error: Failed to create wrap item.", NamedTextColor.RED));
            player.sendMessage(Component.text("Please contact staff about wrap: " + wrap.getWrapName(), NamedTextColor.YELLOW));
            PrettyLogger.warn("Failed to get physical item for wrap '" + wrap.getWrapName() + "' during exchange: " + e.getMessage());


            ItemStack tokenRefund = manager.createToken(1);
            if (player.getInventory().firstEmpty() == -1) {
                player.getWorld().dropItemNaturally(player.getLocation(), tokenRefund);
            } else {
                player.getInventory().addItem(tokenRefund);
            }
            player.sendMessage(Component.text("Your token has been refunded.", NamedTextColor.GREEN));
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), wrapperItem);
            player.sendMessage(Component.text("Inventory full! Wrapper dropped at your feet.", NamedTextColor.YELLOW));
        } else {
            player.getInventory().addItem(wrapperItem);
        }

        String wrapName = wrap.getName();
        player.sendMessage(manager.getMessage("wrap-exchanged", "wrap", AdventureUtil.convertMiniMessageToLegacy(wrapName)));

        PrettyLogger.debug(player.getName() + " exchanged a token for wrap: " + wrapName);
    }


    private void addWrapperPDC(ItemStack item, Wrap wrap, HMCWraps hmcWraps) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            PrettyLogger.warn("Cannot add wrapper PDC - item meta is null for wrap: " + wrap.getWrapName());
            return;
        }

        try {
            String wrapIdentifier = null;
            for (var entry : hmcWraps.getWrapsLoader().getWraps().entrySet()) {
                if (entry.getValue() == wrap) {
                    wrapIdentifier = entry.getKey();
                    break;
                }
            }

            if (wrapIdentifier == null) {
                PrettyLogger.warn("Could not find wrap identifier for wrap: " + wrap.getWrapName());
                return;
            }

            NamespacedKey key = new NamespacedKey("hmcwraps", "wrapper");

            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, wrapIdentifier);
            item.setItemMeta(meta);

            PrettyLogger.debug("Added wrapper PDC to item - Key: " + wrapIdentifier + " for wrap: " + wrap.getWrapName());
        } catch (Exception e) {
            PrettyLogger.warn("Failed to add wrapper PDC for wrap '" + wrap.getWrapName() + "': " + e.getMessage());
        }
    }
}
