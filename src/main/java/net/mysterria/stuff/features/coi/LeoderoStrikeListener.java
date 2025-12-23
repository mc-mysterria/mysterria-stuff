package net.mysterria.stuff.features.coi;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.mysterria.stuff.MysterriaStuff;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class LeoderoStrikeListener implements Listener {

    private static final long BASE_COOLDOWN_TIME = 20000;
    private static final long MAX_COOLDOWN_TIME = 120000;
    private static final long COOLDOWN_RESET_TIME = 60000;
    private final MysterriaStuff plugin;
    private final Map<UUID, Long> cooldowns;
    private final Map<UUID, Integer> usageCount;

    public LeoderoStrikeListener(MysterriaStuff plugin) {
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
        this.usageCount = new HashMap<>();
    }

    @EventHandler
    public void onLeoderoChat(AsyncChatEvent event) {
        Component message = event.message();
        Player player = event.getPlayer();

        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return;
        }

        String serializedMessage = LegacyComponentSerializer.legacyAmpersand().serialize(message);

        String lowerMessage = serializedMessage.toLowerCase();

        boolean isUppercase = serializedMessage.contains("LEODERO");
        boolean containsLeodero = lowerMessage.contains("leodero");

        if (!containsLeodero) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (cooldowns.containsKey(playerId)) {
            long lastUsed = cooldowns.get(playerId);
            int uses = usageCount.getOrDefault(playerId, 0);

            long calculatedCooldown = Math.min(BASE_COOLDOWN_TIME * (long) Math.pow(2, uses), MAX_COOLDOWN_TIME);
            long timeLeft = calculatedCooldown - (currentTime - lastUsed);

            if (timeLeft > 0) {
                player.sendMessage(Component.text("You must wait " + String.format("%.1f", timeLeft / 1000.0) + " seconds before summoning lightning again!")
                        .color(NamedTextColor.RED));

                usageCount.put(playerId, uses + 1);
                return;
            }

            if (currentTime - lastUsed > COOLDOWN_RESET_TIME) {
                usageCount.put(playerId, 0);
            }
        }

        cooldowns.put(playerId, currentTime);

        int currentUses = usageCount.getOrDefault(playerId, 0);
        usageCount.put(playerId, currentUses + 1);

        if (isUppercase) {
            summonLightning(player, 10, 2.5);
        } else {
            summonLightning(player, 1, 1);
        }
    }

    private void summonLightning(Player player, int amount, double offset) {
        new BukkitRunnable() {

            private final Random random = new Random();

            @Override
            public void run() {
                for (int i = 0; i < amount; i++) {
                    double xOffset = random.nextDouble(offset) * 2 - 1;
                    double yOffset = random.nextDouble(offset) * 2 - 1;
                    double zOffset = random.nextDouble(offset) * 2 - 1;

                    player.getWorld().strikeLightning(player.getLocation().add(xOffset, yOffset, zOffset));
                }
            }

        }.runTask(plugin);
    }
}
