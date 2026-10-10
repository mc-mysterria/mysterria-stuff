package net.mysterria.stuff.features.coi;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditOutcome;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditRisk;
import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.audit.StuffAuditEmitter;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BoosterPatriarchListener implements Listener {

    private static final String BYPASS_PERMISSION = "mysterriastuff.patriarch.bypass";
    private static final String BOOSTER_LIST_BUSINESS_ID = "boon:patriarch:booster_list";

    /**
     * Why a boon change is happening, carried to the audit rows. {@code requester} is the staff sender for
     * admin triggers (null for automatic ones); a null correlation id is filled in only when a row is emitted.
     */
    private record Cause(String trigger, UUID correlationId, CommandSender requester, String adminReason) {
        Cause withCorrelation() {
            return correlationId != null ? this
                    : new Cause(trigger, StuffAuditEmitter.correlationId(), requester, adminReason);
        }
    }

    private static final Cause JOIN_CAUSE = new Cause("booster_join", null, null, null);
    private static final Cause SYNC_CAUSE = new Cause("booster_sync", null, null, null);

    private final MysterriaStuff plugin;
    private final HttpClient httpClient;
    private final Gson gson;
    private final String apiUrl;

    private final Set<String> currentBoosters;

    private final long updateIntervalTicks;
    private BukkitRunnable updateTask;

    public BoosterPatriarchListener(MysterriaStuff plugin) {
        this.plugin = plugin;
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

        fetchBoostersAsync(SYNC_CAUSE);
        startPeriodicUpdate();

        PrettyLogger.info("BoosterPatriarchListener initialized with " + updateIntervalSeconds + "s update interval");
    }

    public Set<String> getCurrentBoosters() {
        return Collections.unmodifiableSet(currentBoosters);
    }

    public void refreshBoosters() {
        fetchBoostersAsync(SYNC_CAUSE);
    }

    /** Staff-requested refresh: the grants and revokes it causes share one correlation id with its row. */
    public void refreshBoosters(CommandSender requester) {
        Cause cause = new Cause("admin_refresh", StuffAuditEmitter.correlationId(), requester, null);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("known_booster_count", currentBoosters.size());
        StuffAuditEmitter.emitStaff(AuditOutcome.OBSERVED, AuditRisk.HIGH, "boon.booster_refresh_requested",
                cause.correlationId(), BOOSTER_LIST_BUSINESS_ID, requester, null, "admin_refresh", values);
        fetchBoostersAsync(cause);
    }

    private void startPeriodicUpdate() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                fetchBoostersAsync(SYNC_CAUSE);
            }
        };
        updateTask.runTaskTimer(plugin, updateIntervalTicks, updateIntervalTicks);
        PrettyLogger.debug("Started periodic booster update task");
    }

    private void fetchBoostersAsync(Cause cause) {
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
                    updateBoosterList(boosters, cause.withCorrelation());
                    PrettyLogger.debug("Fetched " + boosters.length + " boosters from API");
                } else {
                    PrettyLogger.warn("Failed to fetch boosters: HTTP " + response.statusCode());
                }
            } catch (JsonSyntaxException e) {
                PrettyLogger.warn("Failed to parse booster list JSON: " + e.getMessage());
            } catch (Exception e) {
                PrettyLogger.warn("Error fetching boosters: " + e.getMessage());
            }
        });
    }

    private void updateBoosterList(String[] newBoosters, Cause cause) {
        Set<String> newBoosterSet = new HashSet<>();
        for (String booster : newBoosters) {
            newBoosterSet.add(booster.toLowerCase());
        }

        Set<String> addedBoosters = new HashSet<>(newBoosterSet);
        addedBoosters.removeAll(currentBoosters);

        Set<String> removedBoosters = new HashSet<>(currentBoosters);
        removedBoosters.removeAll(newBoosterSet);

        currentBoosters.clear();
        currentBoosters.addAll(newBoosterSet);

        if (!addedBoosters.isEmpty() || !removedBoosters.isEmpty()) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("trigger", cause.trigger());
            values.put("added_count", addedBoosters.size());
            values.put("removed_count", removedBoosters.size());
            values.put("booster_count", newBoosterSet.size());
            StuffAuditEmitter.emitStaff(AuditOutcome.OBSERVED, AuditRisk.NORMAL, "boon.booster_list_synced",
                    cause.correlationId(), BOOSTER_LIST_BUSINESS_ID, cause.requester(), null,
                    cause.trigger(), values);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                String playerName = player.getName().toLowerCase();
                boolean isBooster = currentBoosters.contains(playerName);

                if (isBooster) {
                    if (shouldRemovePatriarchDueToBoon(player)) {
                        removePatriarchRole(player, null, cause, "too_many_boons");
                    } else {
                        addPatriarchRole(player, cause);
                    }
                } else if (hasPatriarchInCoI(player)) {
                    removePatriarchRole(player, null, cause, "not_booster");
                }
            }

            if (!addedBoosters.isEmpty()) {
                PrettyLogger.debug("Added boosters: " + addedBoosters);
            }
            if (!removedBoosters.isEmpty()) {
                PrettyLogger.debug("Removed boosters: " + removedBoosters);
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName().toLowerCase();

        if (currentBoosters.contains(playerName)) {
            if (shouldRemovePatriarchDueToBoon(player)) {
                removePatriarchRole(player, null, JOIN_CAUSE, "too_many_boons");
            } else {
                addPatriarchRole(player, JOIN_CAUSE);
            }
        } else if (hasPatriarchInCoI(player)) {
            removePatriarchRole(player, null, JOIN_CAUSE, "not_booster");
        }
    }

    private void addPatriarchRole(Player player, Cause cause) {
        String reason = checkShouldAddPatriarch(player);
        if (reason != null) {
            PrettyLogger.debug("Skipping patriarch for " + player.getName() + ": " + reason);
            return;
        }

        CircleOfImaginationAPI api = plugin.getCoiAPI();
        String playerName = player.getName();

        boolean success = api.addPathwayOffline(player.getUniqueId(), "patriarch", 9);
        emitBoonGrant(player, cause, success);
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

    private void removePatriarchRole(Player player, CommandSender reporter, Cause cause, String removalCause) {
        String playerName = player.getName();
        String command = "coi outer remove " + playerName + " patriarch";

        boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        // The CoI command gives no completion signal, so a dispatched removal is OBSERVED, not COMMITTED.
        Map<String, Object> removal = new LinkedHashMap<>();
        removal.put("removal_cause", removalCause);
        emitBoon(success ? AuditOutcome.OBSERVED : AuditOutcome.FAILED, "boon.patriarch_revoked", player, cause,
                success ? null : "command_dispatch_failed", removal);
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
        forceGrantPatriarch(player, reporter, null);
    }

    public void forceGrantPatriarch(Player player, CommandSender reporter, String adminReason) {
        Cause cause = new Cause("admin_grant", StuffAuditEmitter.correlationId(), reporter, adminReason);
        CircleOfImaginationAPI api = plugin.getCoiAPI();
        if (api == null) {
            emitBoon(AuditOutcome.FAILED, "boon.patriarch_granted", player, cause, "coi_api_unavailable", null);
            reporter.sendMessage(Component.text("CoI API is not available — cannot grant patriarch.").color(NamedTextColor.RED));
            return;
        }

        boolean success = api.addPathwayOffline(player.getUniqueId(), "patriarch", 9);
        emitBoonGrant(player, cause, success);
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
        forceRevokePatriarch(player, reporter, null);
    }

    public void forceRevokePatriarch(Player player, CommandSender reporter, String adminReason) {
        removePatriarchRole(player, reporter,
                new Cause("admin_revoke", StuffAuditEmitter.correlationId(), reporter, adminReason),
                "admin_revoke");
    }

    /** addPathwayOffline writes the player file before it returns, so true means the grant is saved. */
    private void emitBoonGrant(Player player, Cause cause, boolean success) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("sequence", 9);
        emitBoon(success ? AuditOutcome.COMMITTED : AuditOutcome.FAILED, "boon.patriarch_granted", player, cause,
                success ? null : "coi_api_returned_false", values);
    }

    /** Safe on the main thread: only plain values already in hand are read. */
    private void emitBoon(AuditOutcome outcome, String operation, Player player, Cause cause,
                          String failure, Map<String, Object> extra) {
        Cause traced = cause.withCorrelation();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("boon", "patriarch");
        values.put("target_name", player.getName());
        values.put("trigger", traced.trigger());
        if (traced.adminReason() != null) values.put("admin_reason", traced.adminReason());
        if (failure != null) values.put("failure", failure);
        if (extra != null) values.putAll(extra);
        values.putAll(StuffAuditEmitter.location(player));
        StuffAuditEmitter.emitStaff(outcome, traced.requester() != null ? AuditRisk.HIGH : AuditRisk.NORMAL,
                operation, traced.correlationId(), "boon:patriarch:" + player.getUniqueId(),
                traced.requester(), player.getUniqueId(), traced.trigger(), values);
    }

    public void sendDiagnostics(Player player, CommandSender reporter) {
        String nameLower = player.getName().toLowerCase();
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
