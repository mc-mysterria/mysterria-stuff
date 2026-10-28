package net.mysterria.stuff.domain.lastsprint.listener;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.mysterria.stuff.config.ConfigManager;
import net.mysterria.stuff.domain.lastsprint.service.LastSprintService;
import net.mysterria.stuff.util.log.PrettyLogger;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;

@RequiredArgsConstructor
public class LastSprintJoinListener implements Listener {

    private static final TextColor ACCENT = TextColor.color(0xAA55FF);

    private final Plugin plugin;
    private final ConfigManager config;
    private final LastSprintService service;

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!config.isLastSprintActive() || service.getRewardCount() == 0 || service.hasReceivedKit(player.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !service.hasReceivedKit(player.getUniqueId())) {
                service.giveKit(player);
                welcome(player);
            }
        }, 20L);
    }

    private void welcome(Player player) {
        player.showTitle(Title.title(
                Component.text("Welcome to Mysterria!", ACCENT),
                Component.text("Your Last Sprint starter kit is in your inventory", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1))));
        player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.sendMessage(Component.text("✦ Last Sprint Starter Kit ✦", ACCENT, TextDecoration.BOLD));
        player.sendMessage(Component.text("The Great Reset is close. This kit lets you see as much of Mysterria as you can before it changes.", NamedTextColor.GRAY));
        Bukkit.broadcast(Component.text("✦ ", ACCENT)
                .append(Component.text(player.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" joined for the Last Sprint and received a starter kit.", ACCENT)));
        PrettyLogger.info("Gave the Last Sprint kit to " + player.getName());
    }
}
