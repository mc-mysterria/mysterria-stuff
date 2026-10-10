package net.mysterria.stuff.features.lastsprint;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.audit.StuffAuditEmitter;
import net.mysterria.stuff.utils.ItemDelivery;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LastSprintListener implements Listener {

    private static final TextColor ACCENT = TextColor.color(0xAA55FF);

    private final MysterriaStuff plugin;
    private final LastSprint lastSprint;

    public LastSprintListener(MysterriaStuff plugin, LastSprint lastSprint) {
        this.plugin = plugin;
        this.lastSprint = lastSprint;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();

        if (!plugin.getConfigManager().isLastSprintActive()) return;
        if (lastSprint.hasReceivedGift(player.getUniqueId())) return;
        if (lastSprint.getRewardCount() == 0) return;

        // Delay 1 tick so the player is fully initialized before receiving items
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            // A quick relog can queue this task twice, so check the flag again.
            if (lastSprint.hasReceivedGift(player.getUniqueId())) return;

            List<ItemStack> kit = lastSprint.getRewardItems();
            int kitAmount = ItemDelivery.totalAmount(kit);
            UUID correlationId = StuffAuditEmitter.correlationId();
            // Flag before delivering so the kit can never be granted twice.
            if (!lastSprint.tryMarkGiftReceived(player.getUniqueId())) {
                Map<String, Object> metadata = autoGrantMetadata(player, kit.size(), kitAmount, false);
                metadata.put("failure", "gift_flag_unsaved");
                StuffAuditEmitter.emitFailed("kit.granted", correlationId, "kit:last_sprint",
                        null, player.getUniqueId(), null, "first_join", metadata);
                PrettyLogger.warn("Last Sprint gift flag for " + player.getName()
                        + " could not be saved; the kit was not delivered");
                return;
            }
            ItemDelivery.Result delivery;
            try {
                delivery = lastSprint.giveRewards(player, kit);
            } catch (RuntimeException e) {
                StuffAuditEmitter.emitDeliveryException("kit.granted", correlationId, "kit:last_sprint",
                        null, player.getUniqueId(), "first_join",
                        autoGrantMetadata(player, kit.size(), kitAmount, true), e);
                player.sendMessage(Component.text("Your Last Sprint starter kit could not be delivered. Please contact staff.")
                        .color(NamedTextColor.RED));
                PrettyLogger.error("Last Sprint kit delivery for " + player.getName() + " failed: " + e);
                return;
            }
            StuffAuditEmitter.emitDelivery("kit.granted", correlationId, "kit:last_sprint",
                    null, player.getUniqueId(), "first_join", delivery,
                    autoGrantMetadata(player, kit.size(), delivery.requestedAmount(), true));

            if (!delivery.complete()) {
                player.sendMessage(Component.text("Your Last Sprint starter kit could not be fully delivered. Please contact staff.")
                        .color(NamedTextColor.RED));
                PrettyLogger.warn("Last Sprint kit for " + player.getName() + " was not fully delivered ("
                        + delivery.undeliveredAmount() + " of " + delivery.requestedAmount() + " item(s) undelivered)");
                return;
            }

            player.showTitle(Title.title(
                    Component.text("Welcome to Mysterria!").color(ACCENT)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("You've been given a Last Sprint starter kit!")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1))
            ));

            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("═".repeat(40)).color(ACCENT));
            player.sendMessage(Component.text("  ✦ Last Sprint Starter Kit ✦")
                    .color(ACCENT)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            player.sendMessage(Component.text("═".repeat(40)).color(ACCENT));
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("  The Great Reset is approaching soon!").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            player.sendMessage(Component.text("  We've given you a starter kit to help you")
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            player.sendMessage(Component.text("  experience Mysterria before it all changes.")
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("═".repeat(40)).color(ACCENT));
            player.sendMessage(Component.empty());

            Bukkit.broadcast(
                    Component.text("  ✦ ").color(ACCENT)
                            .append(Component.text(player.getName())
                                    .color(NamedTextColor.WHITE)
                                    .decoration(TextDecoration.BOLD, true)
                                    .decoration(TextDecoration.ITALIC, false))
                            .append(Component.text(" joined for the Last Sprint and received a starter kit! ✦")
                                    .color(ACCENT)
                                    .decoration(TextDecoration.ITALIC, false))
            );

            PrettyLogger.info("Gave Last Sprint kit to new player: " + player.getName());
        }, 20L);
    }

    private static Map<String, Object> autoGrantMetadata(Player player, int stackCount, int amount,
                                                         boolean flagSaved) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("actor_name", "system");
        metadata.put("grant_type", "last_sprint_kit");
        metadata.put("amount", amount);
        metadata.put("delivery", "first_join");
        metadata.put("stack_count", stackCount);
        metadata.put("gift_flag_saved", flagSaved);
        metadata.putAll(StuffAuditEmitter.location(player));
        return metadata;
    }
}
