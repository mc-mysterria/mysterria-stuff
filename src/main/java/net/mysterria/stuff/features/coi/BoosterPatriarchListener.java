package net.mysterria.stuff.features.coi;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;
import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class BoosterPatriarchListener implements Listener {

    private static final String BYPASS_PERMISSION = "mysterriastuff.patriarch.bypass";

    private final MysterriaStuff plugin;
    private final HttpClient httpClient;
    private final Gson gson;
    private final String apiUrl;

    private final Set<String> currentBoosters;
    private final Map<UUID, Long> missingSince = new HashMap<>();
    private final AtomicBoolean fetchInProgress = new AtomicBoolean();
    private final PatriarchProgress progress;

    private final long updateIntervalTicks;
    private BukkitRunnable updateTask;

    public BoosterPatriarchListener(MysterriaStuff plugin) {
        this.plugin = plugin;
        this.progress = new PatriarchProgress(plugin);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
        this.apiUrl = plugin.getConfigManager().getConfig()
                .getString("coi-booster-patriarch.api-url", "https://api.mysterria.net/api/user/boosters");

        int updateIntervalSeconds = plugin.getConfigManager().getConfig()
                .getInt("coi-booster-patriarch.update-interval-seconds", 300);
        this.updateIntervalTicks = updateIntervalSeconds * 20L;

        this.currentBoosters = ConcurrentHashMap.newKeySet();

        fetchBoostersAsync();
        startPeriodicUpdate();

        PrettyLogger.info("BoosterPatriarchListener initialized with " + updateIntervalSeconds + "s update interval");
    }

    public Set<String> getCurrentBoosters() {
        return Collections.unmodifiableSet(currentBoosters);
    }

    public void refreshBoosters() {
        fetchBoostersAsync();
    }

    private void startPeriodicUpdate() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                fetchBoostersAsync();
            }
        };
        updateTask.runTaskTimer(plugin, updateIntervalTicks, updateIntervalTicks);
        PrettyLogger.debug("Started periodic booster update task");
    }

    private void fetchBoostersAsync() {
        if (!fetchInProgress.compareAndSet(false, true)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String[] boosters = gson.fromJson(response.body(), String[].class);
                    updateBoosterList(boosters);
                    PrettyLogger.debug("Fetched " + boosters.length + " boosters from API");
                } else {
                    PrettyLogger.warn("Failed to fetch boosters: HTTP " + response.statusCode());
                }
            } catch (JsonSyntaxException e) {
                PrettyLogger.warn("Failed to parse booster list JSON: " + e.getMessage());
            } catch (Exception e) {
                PrettyLogger.warn("Error fetching boosters: " + e.getMessage());
            } finally {
                fetchInProgress.set(false);
            }
        });
    }

    private void updateBoosterList(String[] newBoosters) {
        if (newBoosters == null) return;
        Set<String> newBoosterSet = new HashSet<>();
        for (String booster : newBoosters) {
            if (booster == null || booster.isBlank()) return;
            newBoosterSet.add(booster.toLowerCase(Locale.ROOT));
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            currentBoosters.clear();
            currentBoosters.addAll(newBoosterSet);
            for (Player player : Bukkit.getOnlinePlayers()) {
                reconcilePatriarch(player, true);
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        reconcilePatriarch(event.getPlayer(), false);
    }

    // Only a fetched list may suspend, and only after the player is missing from two lists one interval apart.
    private void reconcilePatriarch(Player player, boolean fromFetch) {
        if (currentBoosters.contains(player.getName().toLowerCase(Locale.ROOT))) {
            missingSince.remove(player.getUniqueId());
            if (shouldRemovePatriarchDueToBoon(player)) {
                suspendPatriarch(player);
            } else {
                addPatriarchRole(player);
            }
        } else if (fromFetch && hasPatriarchInCoI(player)) {
            long now = System.currentTimeMillis();
            long since = missingSince.computeIfAbsent(player.getUniqueId(), id -> now);
            if (now - since >= updateIntervalTicks * 50L) {
                suspendPatriarch(player);
            }
        }
    }

    private void suspendPatriarch(Player player) {
        try {
            progress.suspend(player);
        } catch (ReflectiveOperationException | RuntimeException e) {
            PrettyLogger.warn("Could not preserve Patriarch progression for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void restorePatriarch(Player player, CommandSender reporter) {
        try {
            boolean restored = progress.restore(player);
            if (reporter != null) {
                reporter.sendMessage(restored
                        ? Component.text("Restored Patriarch progression for " + player.getName() + ".").color(NamedTextColor.GREEN)
                        : Component.text("Patriarch progression for " + player.getName() + " was not restored: no native Beyonder data to change.").color(NamedTextColor.YELLOW));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            PrettyLogger.warn("Could not restore Patriarch progression for " + player.getName() + ": " + e.getMessage());
            if (reporter != null) {
                reporter.sendMessage(Component.text("Could not restore Patriarch progression for " + player.getName() + ": " + e.getMessage() + ". Check console.").color(NamedTextColor.RED));
            }
        }
    }

    private void addPatriarchRole(Player player) {
        if (progress.hasSnapshot(player) && hasPatriarchInCoI(player)) {
            restorePatriarch(player, null);
            return;
        }
        String reason = checkShouldAddPatriarch(player);
        if (reason != null) {
            PrettyLogger.debug("Skipping patriarch for " + player.getName() + ": " + reason);
            return;
        }

        if (progress.hasSnapshot(player)) {
            restorePatriarch(player, null);
            return;
        }

        CircleOfImaginationAPI api = plugin.getCoiAPI();
        String playerName = player.getName();

        boolean success = api.addPathwayOffline(player.getUniqueId(), "patriarch", 9);
        if (success) {
            player.sendMessage(Component.text("As a server booster, you have been granted the Patriarch boon!").color(NamedTextColor.GOLD));
            PrettyLogger.debug("Added patriarch role to booster: " + playerName);
        } else {
            PrettyLogger.warn("Failed to add patriarch role to: " + playerName + " (CoI API returned false)");
        }
    }

    private boolean hasPatriarchInCoI(Player player) {
        CircleOfImaginationAPI api = plugin.getCoiAPI();
        if (api == null) return false;
        Map<String, Integer> pathways = api.getPathways(player.getName());
        return pathways != null && pathways.containsKey("patriarch");
    }

    /**
     * Returns null if patriarch can be added, or a reason string explaining why not.
     */
    private String checkShouldAddPatriarch(Player player) {
        CircleOfImaginationAPI api = plugin.getCoiAPI();
        if (api == null) return "CoI API is null (plugin not loaded)";
        if (!api.isBeyonder(player)) return "player is not a Beyonder";
        Map<String, Integer> pathways = api.getPathways(player.getName());
        if (pathways == null) return "getPathways(" + player.getName() + ") returned null";
        if (pathways.isEmpty()) return "pathways map is empty";
        if (pathways.containsKey("patriarch")) return "already has patriarch pathway";
        if (pathways.size() != 1) return "has " + pathways.size() + " pathways " + pathways.keySet() + " (expected exactly 1 — main pathway only)";
        return null;
    }

    private void removePatriarchRole(Player player, CommandSender reporter) {
        String playerName = player.getName();
        String command = "coi outer remove " + playerName + " patriarch";

        boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (success) {
            player.sendMessage(Component.text("Your Patriarch boon has been removed.").color(NamedTextColor.RED));
            if (reporter != null) {
                reporter.sendMessage(Component.text("Removed Patriarch from " + playerName + ".").color(NamedTextColor.GREEN));
            }
            PrettyLogger.debug("Removed patriarch role from: " + playerName);
        } else {
            PrettyLogger.warn("Failed to remove patriarch role from: " + playerName + " (command dispatch returned false)");
            if (reporter != null) {
                reporter.sendMessage(Component.text("Command dispatch failed for " + playerName + ". Check console.").color(NamedTextColor.RED));
            }
        }
    }

    private boolean shouldRemovePatriarchDueToBoon(Player player) {
        CircleOfImaginationAPI api = plugin.getCoiAPI();
        if (api == null) return false;
        if (player.hasPermission(BYPASS_PERMISSION)) return false;
        if (!api.isBeyonder(player)) return false;
        Map<String, Integer> pathways = api.getPathways(player.getName());
        if (pathways == null) return false;
        return pathways.containsKey("patriarch") && pathways.size() > 2;
    }

    public void forceGrantPatriarch(Player player, CommandSender reporter) {
        CircleOfImaginationAPI api = plugin.getCoiAPI();
        if (api == null) {
            reporter.sendMessage(Component.text("CoI API is not available — cannot grant patriarch.").color(NamedTextColor.RED));
            return;
        }

        if (progress.hasSnapshot(player)) {
            restorePatriarch(player, reporter);
            return;
        }

        boolean success = api.addPathwayOffline(player.getUniqueId(), "patriarch", 9);
        if (success) {
            player.sendMessage(Component.text("As a server booster, you have been granted the Patriarch boon!").color(NamedTextColor.GOLD));
            reporter.sendMessage(Component.text("Force-granted Patriarch to " + player.getName() + ".").color(NamedTextColor.GREEN));
            PrettyLogger.info("Force-granted patriarch to " + player.getName() + " by " + reporter.getName());
        } else {
            reporter.sendMessage(Component.text("CoI API returned false — Patriarch was NOT granted to " + player.getName() + ".").color(NamedTextColor.RED));
            PrettyLogger.warn("Force-grant patriarch failed for " + player.getName());
        }
    }

    public void forceRevokePatriarch(Player player, CommandSender reporter) {
        removePatriarchRole(player, reporter);
    }

    public void sendDiagnostics(Player player, CommandSender reporter) {
        String nameLower = player.getName().toLowerCase(Locale.ROOT);
        boolean inBoosterList = currentBoosters.contains(nameLower);

        CircleOfImaginationAPI api = plugin.getCoiAPI();

        reporter.sendMessage(Component.text("═".repeat(45)).color(NamedTextColor.DARK_PURPLE));
        reporter.sendMessage(Component.text(" Booster Patriarch Diagnostics: " + player.getName()).color(NamedTextColor.WHITE));
        reporter.sendMessage(Component.text("═".repeat(45)).color(NamedTextColor.DARK_PURPLE));

        sendLine(reporter, "In booster API list", inBoosterList);

        if (api == null) {
            sendLine(reporter, "CoI API available", false);
            reporter.sendMessage(Component.text("  (plugin not loaded/registered)").color(NamedTextColor.GRAY));
            reporter.sendMessage(Component.text("═".repeat(45)).color(NamedTextColor.DARK_PURPLE));
            return;
        }

        sendLine(reporter, "CoI API available", true);
        sendLine(reporter, "isBeyonder", api.isBeyonder(player));
        sendLine(reporter, "Has patriarch in CoI", hasPatriarchInCoI(player));

        Map<String, Integer> pathways = api.getPathways(player.getName());
        if (pathways == null) {
            reporter.sendMessage(Component.text("  Pathways: ").color(NamedTextColor.GRAY)
                    .append(Component.text("null (getPathways returned null!)").color(NamedTextColor.RED)));
        } else if (pathways.isEmpty()) {
            reporter.sendMessage(Component.text("  Pathways: ").color(NamedTextColor.GRAY)
                    .append(Component.text("empty map").color(NamedTextColor.RED)));
        } else {
            reporter.sendMessage(Component.text("  Pathways (" + pathways.size() + "): ").color(NamedTextColor.GRAY)
                    .append(Component.text(pathways.toString()).color(NamedTextColor.AQUA)));
        }

        String denyReason = checkShouldAddPatriarch(player);
        if (denyReason == null) {
            reporter.sendMessage(Component.text("  Would grant Patriarch: ").color(NamedTextColor.GRAY)
                    .append(Component.text("YES").color(NamedTextColor.GREEN)));
        } else {
            reporter.sendMessage(Component.text("  Would grant Patriarch: ").color(NamedTextColor.GRAY)
                    .append(Component.text("NO").color(NamedTextColor.RED))
                    .append(Component.text(" — " + denyReason).color(NamedTextColor.YELLOW)));
        }

        sendLine(reporter, "Would remove Patriarch (too many boons)", shouldRemovePatriarchDueToBoon(player));

        reporter.sendMessage(Component.text("═".repeat(45)).color(NamedTextColor.DARK_PURPLE));
    }

    private void sendLine(CommandSender sender, String label, boolean value) {
        sender.sendMessage(Component.text("  " + label + ": ").color(NamedTextColor.GRAY)
                .append(Component.text(value ? "YES" : "NO").color(value ? NamedTextColor.GREEN : NamedTextColor.RED)));
    }

    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            PrettyLogger.debug("Stopped booster update task");
        }
    }
}
