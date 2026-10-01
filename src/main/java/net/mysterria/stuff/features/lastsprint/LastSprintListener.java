package net.mysterria.stuff.features.lastsprint;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.utils.ItemDelivery;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.List;

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
            if (lastSprint.hasReceivedGift(player.getUniqueId())) return;

            List<ItemStack> kit = lastSprint.getRewardItems();
            // Flag before delivering so a kit can never be granted twice: if delivery throws or is
            // partial, the flag stays set and the shortfall is reported instead of re-sent on next join.
            if (!lastSprint.tryMarkGiftReceived(player.getUniqueId())) {
                PrettyLogger.warn("Last Sprint gift flag for " + player.getName()
                        + " could not be saved; the kit was not delivered");
                return;
            }
            ItemDelivery.Result delivery;
            try {
                delivery = lastSprint.giveRewards(player, kit);
            } catch (RuntimeException e) {
                player.sendMessage(Component.text("Your Last Sprint starter kit could not be delivered. Please contact staff.")
                        .color(NamedTextColor.RED));
                PrettyLogger.warn("Last Sprint kit delivery to " + player.getName() + " failed: " + e.getMessage());
                throw e;
            }

            if (!delivery.complete()) {
                player.sendMessage(Component.text(delivery.anyDelivered()
                                ? "Part of your Last Sprint starter kit could not be delivered. Please contact staff."
                                : "Your Last Sprint starter kit could not be delivered. Please contact staff.")
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

}
