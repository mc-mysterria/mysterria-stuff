package net.mysterria.stuff.features.coi;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener that manages the Patriarch role for boosters
 * Fetches booster list from API and grants/removes the patriarch role accordingly
 */
public class BoosterPatriarchListener implements Listener {

    private final MysterriaStuff plugin;
    private final HttpClient httpClient;
    private final Gson gson;
    private final String apiUrl;
    private final File dataFile;

    // Set of current boosters (thread-safe)
    private final Set<String> currentBoosters;

    // Set of players who currently have the patriarch role (thread-safe)
    private final Set<String> playersWithPatriarch;

    // Update interval in ticks (20 ticks = 1 second)
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

        // Get update interval from config (in seconds), default to 5 minutes
        int updateIntervalSeconds = plugin.getConfigManager().getConfig()
                .getInt("coi-booster-patriarch.update-interval-seconds", 300);
        this.updateIntervalTicks = updateIntervalSeconds * 20L;

        this.currentBoosters = ConcurrentHashMap.newKeySet();
        this.playersWithPatriarch = ConcurrentHashMap.newKeySet();

        // Setup data file for persistence
        this.dataFile = new File(plugin.getDataFolder(), "booster-patriarch-data.json");

        // Load persisted data
        loadPersistedData();

        // Initial fetch
        fetchBoostersAsync();

        // Start periodic update task
        startPeriodicUpdate();

        PrettyLogger.info("BoosterPatriarchListener initialized with " + updateIntervalSeconds + "s update interval");
    }

    /**
     * Start the periodic task to fetch boosters
     */
    private void startPeriodicUpdate() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                fetchBoostersAsync();
            }
        };

        // Run task periodically (delay = interval)
        updateTask.runTaskTimer(plugin, updateIntervalTicks, updateIntervalTicks);
        PrettyLogger.debug("Started periodic booster update task");
    }

    /**
     * Fetch boosters from API asynchronously
     */
    private void fetchBoostersAsync() {
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
            }
        });
    }

    /**
     * Update the booster list and sync roles for online players
     */
    private void updateBoosterList(String[] newBoosters) {
        Set<String> newBoosterSet = new HashSet<>();
        for (String booster : newBoosters) {
            newBoosterSet.add(booster.toLowerCase());
        }

        Set<String> addedBoosters = new HashSet<>(newBoosterSet);
        addedBoosters.removeAll(currentBoosters);

        Set<String> removedBoosters = new HashSet<>(currentBoosters);
        removedBoosters.removeAll(newBoosterSet);

        // Update the current booster list
        currentBoosters.clear();
        currentBoosters.addAll(newBoosterSet);

        // Sync roles for online players
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                String playerName = player.getName().toLowerCase();
                boolean isBooster = currentBoosters.contains(playerName);
                boolean hasRole = playersWithPatriarch.contains(playerName);

                if (isBooster && !hasRole) {
                    addPatriarchRole(player);
                } else if (!isBooster && hasRole) {
                    removePatriarchRole(player);
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

        // Check if player is in booster list
        if (currentBoosters.contains(playerName)) {
            addPatriarchRole(player);
        } else {
            // If player was previously a booster but no longer is, remove the role
            if (playersWithPatriarch.contains(playerName)) {
                removePatriarchRole(player);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up tracking when player leaves
        String playerName = event.getPlayer().getName().toLowerCase();
        playersWithPatriarch.remove(playerName);
    }

    /**
     * Add patriarch role to a player
     */
    private void addPatriarchRole(Player player) {
        String playerName = player.getName();
        String command = "coi outer add " + playerName + " patriarch 9";

        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            if (success) {
                playersWithPatriarch.add(playerName.toLowerCase());
                savePersistedData();
                PrettyLogger.debug("Added patriarch role to booster: " + playerName);
            } else {
                PrettyLogger.warn("Failed to add patriarch role to: " + playerName);
            }
        });
    }

    /**
     * Remove patriarch role from a player
     */
    private void removePatriarchRole(Player player) {
        String playerName = player.getName();
        String command = "coi outer remove " + playerName + " patriarch";

        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            if (success) {
                playersWithPatriarch.remove(playerName.toLowerCase());
                savePersistedData();
                PrettyLogger.debug("Removed patriarch role from: " + playerName);
            } else {
                PrettyLogger.warn("Failed to remove patriarch role from: " + playerName);
            }
        });
    }

    /**
     * Load persisted data from file
     */
    private void loadPersistedData() {
        if (!dataFile.exists()) {
            PrettyLogger.debug("No persisted booster-patriarch data found, starting fresh");
            return;
        }

        try (FileReader reader = new FileReader(dataFile)) {
            Type setType = new TypeToken<HashSet<String>>(){}.getType();
            Set<String> loadedData = gson.fromJson(reader, setType);
            if (loadedData != null) {
                playersWithPatriarch.addAll(loadedData);
                PrettyLogger.debug("Loaded " + loadedData.size() + " players with patriarch role from disk");
            }
        } catch (IOException | JsonSyntaxException e) {
            PrettyLogger.warn("Failed to load persisted booster-patriarch data: " + e.getMessage());
        }
    }

    /**
     * Save persisted data to file
     */
    private void savePersistedData() {
        try {
            // Ensure data folder exists
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            try (FileWriter writer = new FileWriter(dataFile)) {
                gson.toJson(playersWithPatriarch, writer);
                PrettyLogger.debug("Saved booster-patriarch data to disk");
            }
        } catch (IOException e) {
            PrettyLogger.warn("Failed to save persisted booster-patriarch data: " + e.getMessage());
        }
    }

    /**
     * Stop the periodic update task
     */
    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            PrettyLogger.debug("Stopped booster update task");
        }
    }
}
