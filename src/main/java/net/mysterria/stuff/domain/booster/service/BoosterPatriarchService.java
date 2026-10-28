package net.mysterria.stuff.domain.booster.service;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.mysterria.stuff.config.models.BoosterSettings;
import net.mysterria.stuff.util.log.PrettyLogger;
import net.mysterria.stuff.util.text.Feedback;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class BoosterPatriarchService {

    private static final String PATRIARCH = "patriarch";
    private static final String BYPASS_PERMISSION = "mysterriastuff.patriarch.bypass";

    private final Plugin plugin;
    private final CircleOfImaginationAPI coi;
    private final BoosterSettings settings;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Gson gson = new Gson();
    private volatile Set<String> boosters;
    private final BukkitTask refreshTask;

    public BoosterPatriarchService(Plugin plugin, CircleOfImaginationAPI coi, BoosterSettings settings) {
        this.plugin = plugin;
        this.coi = coi;
        this.settings = settings;
        long intervalTicks = settings.getUpdateIntervalSeconds() * 20L;
        this.refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::fetchBoosters, 0L, intervalTicks);
    }

    public Set<String> getBoosters() {
        return boosters == null ? Set.of() : boosters;
    }

    public void refresh() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::fetchBoosters);
    }

    public void shutdown() {
        refreshTask.cancel();
    }

    public void sync(Player player) {
        if (boosters == null) {
            return;
        }
        if (boosters.contains(player.getName().toLowerCase(Locale.ROOT))) {
            if (holdsTooManyBoons(player)) {
                revoke(player, "it can only be held alongside your main pathway");
            } else if (denyReason(player) == null) {
                grant(player);
            }
        } else if (hasPatriarch(player)) {
            revoke(player, "your server boost has ended");
        }
    }

    public void forceGrant(Player player, CommandSender reporter) {
        if (grant(player)) {
            Feedback.success(reporter, "Granted Patriarch to " + player.getName() + ".");
            PrettyLogger.info(reporter.getName() + " force-granted Patriarch to " + player.getName());
        } else {
            Feedback.error(reporter, "CircleOfImagination refused to grant Patriarch to " + player.getName() + ".");
        }
    }

    public void forceRevoke(Player player, CommandSender reporter) {
        if (revoke(player, "it was removed by staff")) {
            Feedback.success(reporter, "Removed Patriarch from " + player.getName() + ".");
        } else {
            Feedback.error(reporter, "Removing Patriarch from " + player.getName() + " failed, check console.");
        }
    }

    public void sendDiagnostics(Player player, CommandSender reporter) {
        Map<String, Integer> pathways = coi.getPathways(player.getName());
        String denyReason = denyReason(player);

        Feedback.info(reporter, "Booster Patriarch: " + player.getName());
        Feedback.flag(reporter, "In booster list", getBoosters().contains(player.getName().toLowerCase(Locale.ROOT)));
        Feedback.flag(reporter, "Beyonder", coi.isBeyonder(player));
        Feedback.flag(reporter, "Has Patriarch", hasPatriarch(player));
        Feedback.field(reporter, "Pathways", pathways == null ? null : pathways.toString());
        Feedback.field(reporter, "Would grant", denyReason == null ? "yes" : "no, " + denyReason);
        Feedback.flag(reporter, "Would remove (too many boons)", holdsTooManyBoons(player));
    }

    private void fetchBoosters() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(settings.getApiUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                PrettyLogger.warn("Booster API returned HTTP " + response.statusCode());
                return;
            }
            String[] names = gson.fromJson(response.body(), String[].class);
            boosters = Arrays.stream(names).map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
            PrettyLogger.debug("Fetched " + names.length + " boosters");
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::sync));
        } catch (IOException | JsonSyntaxException e) {
            PrettyLogger.warn("Could not fetch boosters: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean grant(Player player) {
        boolean granted = coi.addPathwayOffline(player.getUniqueId(), PATRIARCH, 9);
        if (granted) {
            player.sendMessage(Component.text("Thank you for boosting! You have been granted the Patriarch boon.", NamedTextColor.GOLD));
        } else {
            PrettyLogger.warn("CircleOfImagination refused to grant Patriarch to " + player.getName());
        }
        return granted;
    }

    private boolean revoke(Player player, String reason) {
        boolean removed = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coi outer remove " + player.getName() + " " + PATRIARCH);
        if (removed) {
            player.sendMessage(Component.text("Your Patriarch boon was removed: " + reason + ".", NamedTextColor.RED));
        } else {
            PrettyLogger.warn("Could not remove Patriarch from " + player.getName());
        }
        return removed;
    }

    private boolean hasPatriarch(Player player) {
        Map<String, Integer> pathways = coi.getPathways(player.getName());
        return pathways != null && pathways.containsKey(PATRIARCH);
    }

    private String denyReason(Player player) {
        if (!coi.isBeyonder(player)) {
            return "not a Beyonder";
        }
        Map<String, Integer> pathways = coi.getPathways(player.getName());
        if (pathways == null || pathways.isEmpty()) {
            return "no pathways";
        }
        if (pathways.containsKey(PATRIARCH)) {
            return "already has Patriarch";
        }
        if (pathways.size() != 1) {
            return "has " + pathways.size() + " pathways " + pathways.keySet() + ", needs exactly the main one";
        }
        return null;
    }

    private boolean holdsTooManyBoons(Player player) {
        if (player.hasPermission(BYPASS_PERMISSION) || !coi.isBeyonder(player)) {
            return false;
        }
        Map<String, Integer> pathways = coi.getPathways(player.getName());
        return pathways != null && pathways.containsKey(PATRIARCH) && pathways.size() > 2;
    }
}
