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
import net.mysterria.stuff.audit.ItemIdentity;
import net.mysterria.stuff.audit.StuffAuditEmitter;
import net.mysterria.stuff.utils.AdventureUtil;
import net.mysterria.stuff.utils.ItemDelivery;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;


public class WrapConfirmationGUI {

    private static final long PREVIEW_UNAVAILABLE_WINDOW_MS = 10 * 60_000L;
    private static final int PREVIEW_UNAVAILABLE_PRUNE_SIZE = 256;
    /** player|wrap -> last preview_unavailable row time; GUI callbacks run on the main thread. */
    private static final Map<String, Long> PREVIEW_UNAVAILABLE = new HashMap<>();

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
                emitPreviewUnavailable(player, wrap, "wrap_physical_missing");
                return;
            }
            wrapItem = wrap.getPhysical().toItem(hmcWraps, player);
        } catch (Exception e) {
            player.sendMessage(Component.text("Error: Failed to load wrap item.", NamedTextColor.RED));
            player.sendMessage(Component.text("Please contact staff about wrap: " + wrap.getWrapName(), NamedTextColor.YELLOW));
            PrettyLogger.warn("Failed to get physical item for wrap '" + wrap.getWrapName() + "': " + e.getMessage());
            emitPreviewUnavailable(player, wrap, "wrapper_creation_failed");
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

        UUID correlationId = StuffAuditEmitter.correlationId();
        // Snapshot before consumption: consumeToken decrements the stack in place.
        String tokenMaterial = heldItem.getType().getKey().toString();

        if (!manager.consumeToken(heldItem, 1)) {
            player.sendMessage(manager.getMessage("no-token-in-hand"));
            return;
        }

        Map<String, Object> consumed = new LinkedHashMap<>(
                StuffAuditEmitter.tokenMetadata("universal", 1, "wrap_exchange"));
        consumed.put("material", tokenMaterial);
        consumed.putAll(StuffAuditEmitter.location(player));
        StuffAuditEmitter.emit("token.consumed", correlationId,
                StuffAuditEmitter.tokenBusinessId("universal"), player.getUniqueId(),
                player.getUniqueId(), null, "wrap_exchange", consumed);

        ItemStack wrapperItem = null;
        String loaderWrapId = null;
        String failure = "wrapper_tagging_failed";
        try {
            if (wrap.getPhysical() == null) {
                player.sendMessage(Component.text("Error: This wrap has no physical item configured.", NamedTextColor.RED));
                player.sendMessage(Component.text("Please contact staff about wrap: " + wrap.getWrapName(), NamedTextColor.YELLOW));
                PrettyLogger.warn("Wrap '" + wrap.getWrapName() + "' has null physical item during exchange");

                abortExchange(player, wrap, correlationId, "wrap_physical_missing");
                return;
            }
            wrapperItem = wrap.getPhysical().toItem(hmcWraps, player);


            // HMCWraps does not recognise a wrapper without the tag
            loaderWrapId = addWrapperPDC(wrapperItem, wrap, hmcWraps);
        } catch (Exception e) {
            PrettyLogger.warn("Failed to get physical item for wrap '" + wrap.getWrapName() + "' during exchange: " + e.getMessage());
            failure = "wrapper_creation_failed";
        }

        if (loaderWrapId == null) {
            abortCreation(player, wrap, correlationId, failure);
            return;
        }

        deliverWrapper(player, wrap, wrapperItem, loaderWrapId, correlationId);
    }

    private void deliverWrapper(Player player, Wrap wrap, ItemStack wrapperItem, String loaderWrapId,
                                UUID correlationId) {
        String effectiveWrapId = resolveWrapId(wrap, loaderWrapId);
        String wrapperUuid = ItemIdentity.stampShop(wrapperItem);
        // Snapshot before delivery: Inventory.addItem may mutate the passed stack.
        String itemType = wrapperItem.getType().getKey().toString();
        int itemAmount = wrapperItem.getAmount();
        String businessId = StuffAuditEmitter.wrapBusinessId(effectiveWrapId, wrap.getWrapName());
        Map<String, Object> metadata = unlockMetadata(player, wrap, effectiveWrapId, itemType, itemAmount,
                wrapperUuid);

        ItemDelivery.Result delivery;
        try {
            delivery = ItemDelivery.deliver(player, wrapperItem);
        } catch (RuntimeException e) {
            StuffAuditEmitter.emitDeliveryException("cosmetic.unlocked", correlationId, businessId,
                    player.getUniqueId(), player.getUniqueId(), "universal_token_exchange", metadata, e);
            throw e;
        }
        StuffAuditEmitter.emitDelivery("cosmetic.unlocked", correlationId, businessId, player.getUniqueId(),
                player.getUniqueId(), "universal_token_exchange", delivery, metadata);

        if (!delivery.complete()) {
            // Another plugin blocked the drop and the token is already consumed.
            player.sendMessage(Component.text("Error: The wrap item could not be delivered.", NamedTextColor.RED));
            player.sendMessage(Component.text("Please contact staff about wrap: " + wrap.getWrapName(), NamedTextColor.YELLOW));
            PrettyLogger.warn("Wrapper for wrap '" + wrap.getWrapName() + "' could not be delivered to " + player.getName());
            if (!delivery.anyDelivered()) {
                refundToken(player, correlationId);
            }
            return;
        }

        if (delivery.droppedAmount() > 0) {
            player.sendMessage(Component.text("Inventory full! Wrapper dropped at your feet.", NamedTextColor.YELLOW));
        }

        String wrapName = wrap.getName();
        player.sendMessage(manager.getMessage("wrap-exchanged", "wrap", AdventureUtil.convertMiniMessageToLegacy(wrapName)));

        PrettyLogger.debug(player.getName() + " exchanged a token for wrap: " + wrapName);
    }

    private Map<String, Object> unlockMetadata(Player player, Wrap wrap, String effectiveWrapId,
                                               String itemType, int itemAmount,
                                               String wrapperUuid) {
        Map<String, Object> metadata = new LinkedHashMap<>(StuffAuditEmitter.wrapMetadata(
                effectiveWrapId, wrap.getWrapName(), itemType, itemAmount, true));
        metadata.put("delivery", "universal_token_exchange");
        if (wrapperUuid != null) {
            metadata.put("item_uuid", wrapperUuid);
            metadata.put("item_uuid_scope", ItemIdentity.SCOPE_INSTANCE);
            metadata.put("item_origin", ItemIdentity.ORIGIN_SHOP);
        } else {
            metadata.putAll(ItemIdentity.lotMetadata(ItemIdentity.ORIGIN_SHOP, null, itemAmount));
        }
        metadata.putAll(StuffAuditEmitter.location(player));
        return metadata;
    }

    private void abortCreation(Player player, Wrap wrap, UUID correlationId, String reason) {
        player.sendMessage(Component.text("Error: Failed to create wrap item.", NamedTextColor.RED));
        player.sendMessage(Component.text("Please contact staff about wrap: " + wrap.getWrapName(), NamedTextColor.YELLOW));
        abortExchange(player, wrap, correlationId, reason);
    }

    private void abortExchange(Player player, Wrap wrap, UUID correlationId, String reason) {
        emitUnlockFailed(player, wrap, correlationId, reason);
        refundToken(player, correlationId);
    }

    private void refundToken(Player player, UUID correlationId) {
        ItemStack token = manager.createToken(1);
        // Refund tokens stay unstamped so they keep stacking with existing tokens; the lot uuid is row-only.
        Map<String, Object> lot = ItemIdentity.lotMetadata(ItemIdentity.ORIGIN_SHOP, null, 1);
        Map<String, Object> metadata = new LinkedHashMap<>(StuffAuditEmitter.tokenMetadata("universal", 1, "wrap_exchange_refund"));
        metadata.putAll(lot);
        metadata.putAll(StuffAuditEmitter.location(player));
        String businessId = StuffAuditEmitter.tokenBusinessId("universal");
        ItemDelivery.Result delivery;
        try {
            delivery = ItemDelivery.deliver(player, token);
        } catch (RuntimeException e) {
            StuffAuditEmitter.emitDeliveryException("token.granted", correlationId, businessId,
                    player.getUniqueId(), player.getUniqueId(), "wrap_exchange_refund", metadata, e);
            throw e;
        }
        StuffAuditEmitter.emitDelivery("token.granted", correlationId, businessId, player.getUniqueId(),
                player.getUniqueId(), "wrap_exchange_refund", delivery, metadata);
        if (delivery.complete()) {
            player.sendMessage(Component.text("Your token has been refunded.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Your token refund could not be delivered. Please contact staff.",
                    NamedTextColor.RED));
            PrettyLogger.warn("Universal Token refund could not be delivered to " + player.getName());
        }
    }

    /** A preview is not an exchange attempt, so it is traced as OBSERVED LOW, not a FAILED unlock. */
    private void emitPreviewUnavailable(Player player, Wrap wrap, String reason) {
        long now = System.currentTimeMillis();
        String key = player.getUniqueId() + "|" + wrap.getWrapName();
        if (PREVIEW_UNAVAILABLE.size() > PREVIEW_UNAVAILABLE_PRUNE_SIZE) {
            PREVIEW_UNAVAILABLE.values().removeIf(last -> now - last >= PREVIEW_UNAVAILABLE_WINDOW_MS);
        }
        Long last = PREVIEW_UNAVAILABLE.get(key);
        if (last != null && now - last < PREVIEW_UNAVAILABLE_WINDOW_MS) return;
        PREVIEW_UNAVAILABLE.put(key, now);

        String wrapId = resolveWrapId(wrap, null);
        Map<String, Object> metadata = new LinkedHashMap<>(StuffAuditEmitter.wrapMetadata(
                wrapId, wrap.getWrapName(), null, 0, true));
        metadata.put("unavailable_reason", reason);
        metadata.putAll(StuffAuditEmitter.location(player));
        StuffAuditEmitter.emitObservedLow("cosmetic.preview_unavailable", StuffAuditEmitter.correlationId(),
                StuffAuditEmitter.wrapBusinessId(wrapId, wrap.getWrapName()), player.getUniqueId(),
                player.getUniqueId(), reason, metadata);
    }

    /** Only called once an exchange was actually attempted (the token has been consumed). */
    private void emitUnlockFailed(Player player, Wrap wrap, UUID correlationId, String reason) {
        String wrapId = resolveWrapId(wrap, null);
        Map<String, Object> metadata = new LinkedHashMap<>(StuffAuditEmitter.wrapMetadata(
                wrapId, wrap.getWrapName(), null, 0, true));
        metadata.put("delivery", "universal_token_exchange");
        metadata.put("failure", reason);
        metadata.put("failure_stage", "exchange");
        metadata.putAll(StuffAuditEmitter.location(player));
        StuffAuditEmitter.emitFailed("cosmetic.unlocked", correlationId,
                StuffAuditEmitter.wrapBusinessId(wrapId, wrap.getWrapName()), player.getUniqueId(),
                player.getUniqueId(), null, reason, metadata);
    }

    private String resolveWrapId(Wrap wrap, String loaderWrapId) {
        String wrapId = wrap.getUuid();
        if (wrapId == null || wrapId.isBlank()) wrapId = loaderWrapId;
        if (wrapId == null || wrapId.isBlank()) wrapId = wrap.getWrapName();
        return wrapId;
    }

    private String addWrapperPDC(ItemStack item, Wrap wrap, HMCWraps hmcWraps) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            PrettyLogger.warn("Cannot add wrapper PDC - item meta is null for wrap: " + wrap.getWrapName());
            return null;
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
                return null;
            }

            NamespacedKey key = new NamespacedKey("hmcwraps", "wrapper");

            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, wrapIdentifier);
            item.setItemMeta(meta);

            PrettyLogger.debug("Added wrapper PDC to item - Key: " + wrapIdentifier + " for wrap: " + wrap.getWrapName());
            return wrapIdentifier;
        } catch (Exception e) {
            PrettyLogger.warn("Failed to add wrapper PDC for wrap '" + wrap.getWrapName() + "': " + e.getMessage());
            return null;
        }
    }
}
