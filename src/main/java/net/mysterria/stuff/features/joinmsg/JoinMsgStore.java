package net.mysterria.stuff.features.joinmsg;

import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stores custom join/quit messages keyed primarily by player UUID (immune to
 * renames, Bedrock/Floodgate name prefixes, and any other nickname quirks).
 * The last-known name is kept alongside each entry purely for admin
 * readability and command lookups.
 * <p>
 * Entries that only have a name (imported from the legacy ChatControl-style
 * .rs format, or created by an admin for a player who has never joined) live
 * in a "pending" bucket and are automatically promoted to a UUID entry the
 * next time a matching player is seen online.
 */
public class JoinMsgStore {

    public enum SetResult {
        OK,
        MISSING_PLACEHOLDER_JOIN,
        MISSING_PLACEHOLDER_QUIT,
        WRITE_ERROR
    }

    public record RemoveResult(boolean changed, boolean saved,
                               boolean removedJoin, boolean removedQuit) {
        public static RemoveResult unchanged() {
            return new RemoveResult(false, true, false, false);
        }

        public String messageType() {
            if (removedJoin && removedQuit) return "join_and_quit";
            if (removedJoin) return "join";
            if (removedQuit) return "quit";
            return "unknown";
        }
    }

    public static class MessageEntry {
        public final UUID uuid;
        public String name;
        public String join;
        public String quit;

        MessageEntry(UUID uuid, String name, String join, String quit) {
            this.uuid = uuid;
            this.name = name;
            this.join = join;
            this.quit = quit;
        }
    }

    private final MysterriaStuff plugin;

    private final Map<UUID, MessageEntry> byUuid = new HashMap<>();
    private final Map<String, MessageEntry> pending = new HashMap<>();

    private String defaultJoinMessage;
    private String defaultQuitMessage;
    private String firstJoinMessage;

    public JoinMsgStore(MysterriaStuff plugin) {
        this.plugin = plugin;
        load();
    }

    // ---------------------------------------------------------------
    // Loading / saving
    // ---------------------------------------------------------------

    public void load() {
        byUuid.clear();
        pending.clear();
        defaultJoinMessage = null;
        defaultQuitMessage = null;
        firstJoinMessage = null;

        File file = getStoreFile();
        if (!file.exists()) {
            if (!migrateLegacyFormat()) {
                PrettyLogger.info("No join/quit message store found, starting fresh");
                return;
            }
        } else {
            readFrom(YamlConfiguration.loadConfiguration(file));
        }

        PrettyLogger.info("Loaded " + byUuid.size() + " player join/quit message(s)"
                + (pending.isEmpty() ? "" : ", " + pending.size() + " pending name match(es)")
                + (defaultJoinMessage != null || defaultQuitMessage != null ? ", default message(s)" : "")
                + (firstJoinMessage != null ? ", first-join message" : ""));
    }

    private void readFrom(YamlConfiguration yaml) {
        defaultJoinMessage = yaml.getString("default.join", null);
        defaultQuitMessage = yaml.getString("default.quit", null);
        firstJoinMessage = yaml.getString("first-join", null);

        ConfigurationSection playersSection = yaml.getConfigurationSection("players");
        if (playersSection != null) {
            for (String key : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    ConfigurationSection s = playersSection.getConfigurationSection(key);
                    if (s == null) continue;
                    byUuid.put(uuid, new MessageEntry(uuid, s.getString("name", key), s.getString("join"), s.getString("quit")));
                } catch (IllegalArgumentException e) {
                    PrettyLogger.warn("Skipping invalid UUID key in join/quit store: " + key);
                }
            }
        }

        // Stored as a list, NOT a map keyed by name: names are free-form (Bedrock/Floodgate
        // accounts can start with '.', which Bukkit's config treats as a path separator when
        // used as a section key, corrupting the file). A list sidesteps that entirely.
        List<?> pendingList = yaml.getList("pending");
        if (pendingList != null) {
            for (Object raw : pendingList) {
                if (!(raw instanceof Map<?, ?> map)) continue;
                Object nameObj = map.get("name");
                if (nameObj == null) continue;
                String name = String.valueOf(nameObj);
                Object joinObj = map.get("join");
                Object quitObj = map.get("quit");
                pending.put(sanitizeKey(name), new MessageEntry(null, name,
                        joinObj != null ? String.valueOf(joinObj) : null,
                        quitObj != null ? String.valueOf(quitObj) : null));
            }
        }
    }

    public boolean save() {
        try {
            YamlConfiguration yaml = new YamlConfiguration();

            if (defaultJoinMessage != null) yaml.set("default.join", defaultJoinMessage);
            if (defaultQuitMessage != null) yaml.set("default.quit", defaultQuitMessage);
            if (firstJoinMessage != null) yaml.set("first-join", firstJoinMessage);

            for (MessageEntry entry : byUuid.values()) {
                String base = "players." + entry.uuid;
                yaml.set(base + ".name", entry.name);
                if (entry.join != null) yaml.set(base + ".join", entry.join);
                if (entry.quit != null) yaml.set(base + ".quit", entry.quit);
            }

            List<Map<String, Object>> pendingList = new ArrayList<>();
            for (MessageEntry entry : pending.values()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("name", entry.name);
                if (entry.join != null) map.put("join", entry.join);
                if (entry.quit != null) map.put("quit", entry.quit);
                pendingList.add(map);
            }
            if (!pendingList.isEmpty()) yaml.set("pending", pendingList);

            File file = getStoreFile();
            file.getParentFile().mkdirs();

            // Write to a temp file and swap it in, keeping a .bak of whatever was last on disk,
            // so a bug or crash mid-write can never silently wipe previously-saved data.
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            yaml.save(tmp);

            if (file.exists()) {
                File backup = new File(file.getParentFile(), file.getName() + ".bak");
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            return true;
        } catch (IOException | RuntimeException e) {
            try {
                PrettyLogger.warn("Failed to save join/quit message store: " + e.getMessage());
            } catch (RuntimeException ignored) {
                // Callers still need a false result so they can restore their snapshots.
            }
            return false;
        }
    }

    // ---------------------------------------------------------------
    // One-time legacy .rs migration
    // ---------------------------------------------------------------

    private boolean migrateLegacyFormat() {
        File dir = getMessagesDir();
        File joinFile = new File(dir, "join.rs");
        File quitFile = new File(dir, "quit.rs");

        if (!joinFile.exists() && !quitFile.exists()) {
            return false;
        }

        PrettyLogger.info("Migrating legacy ChatControl join/quit format to the new store...");

        // Case-insensitive so a stray case difference between join.rs/quit.rs "require"
        // lines for the same player can't silently drop one half of their messages.
        Map<String, String> legacyJoin = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, String> legacyQuit = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        String[] legacyDefaultJoin = new String[1];
        String[] legacyDefaultQuit = new String[1];
        String[] legacyFirstJoin = new String[1];

        if (joinFile.exists()) parseLegacyFile(joinFile, legacyJoin, "join", legacyDefaultJoin, legacyFirstJoin);
        if (quitFile.exists()) parseLegacyFile(quitFile, legacyQuit, "quit", legacyDefaultQuit, null);

        defaultJoinMessage = legacyDefaultJoin[0];
        defaultQuitMessage = legacyDefaultQuit[0];
        firstJoinMessage = legacyFirstJoin[0];

        Set<String> names = new HashSet<>();
        names.addAll(legacyJoin.keySet());
        names.addAll(legacyQuit.keySet());

        for (String name : names) {
            pending.put(name.toLowerCase(), new MessageEntry(null, name, legacyJoin.get(name), legacyQuit.get(name)));
        }

        save();

        backupLegacyFile(joinFile);
        backupLegacyFile(quitFile);

        PrettyLogger.success("Migrated " + names.size() + " legacy join/quit message(s). "
                + "Each will attach to its player's UUID the next time that player is seen online (name match). "
                + "Old .rs files were renamed with a .migrated suffix.");
        return true;
    }

    /**
     * Re-imports entries from the pre-migration ".rs.migrated" backup files
     * (kept untouched by {@link #migrateLegacyFormat()}) that are missing or
     * incomplete in the current store. Never overwrites an existing non-null
     * join/quit message — only fills in gaps. Safe to run repeatedly.
     *
     * @return number of entries added or filled in, or -1 if no backup files exist
     */
    public int repairFromLegacyBackups() {
        File dir = getMessagesDir();
        File joinFile = new File(dir, "join.rs.migrated");
        File quitFile = new File(dir, "quit.rs.migrated");

        if (!joinFile.exists() && !quitFile.exists()) {
            return -1;
        }

        // Case-insensitive so a stray case difference between join.rs/quit.rs "require"
        // lines for the same player can't silently drop one half of their messages.
        Map<String, String> legacyJoin = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, String> legacyQuit = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        String[] unusedDefault = new String[1];
        String[] unusedFirstJoin = new String[1];

        if (joinFile.exists()) parseLegacyFile(joinFile, legacyJoin, "join", unusedDefault, unusedFirstJoin);
        if (quitFile.exists()) parseLegacyFile(quitFile, legacyQuit, "quit", unusedDefault, null);

        Set<String> names = new HashSet<>();
        names.addAll(legacyJoin.keySet());
        names.addAll(legacyQuit.keySet());

        int recovered = 0;
        for (String name : names) {
            String join = legacyJoin.get(name);
            String quit = legacyQuit.get(name);

            MessageEntry existing = findByName(name);
            if (existing == null) {
                pending.put(sanitizeKey(name), new MessageEntry(null, name, join, quit));
                recovered++;
                continue;
            }

            boolean filled = false;
            if (existing.join == null && join != null) { existing.join = join; filled = true; }
            if (existing.quit == null && quit != null) { existing.quit = quit; filled = true; }
            if (filled) recovered++;
        }

        if (recovered > 0) save();
        return recovered;
    }

    private MessageEntry findByName(String name) {
        for (MessageEntry entry : byUuid.values()) {
            if (entry.name.equalsIgnoreCase(name)) return entry;
        }
        return pending.get(sanitizeKey(name));
    }

    private void backupLegacyFile(File file) {
        if (!file.exists()) return;
        File backup = new File(file.getParentFile(), file.getName() + ".migrated");
        if (!file.renameTo(backup)) {
            PrettyLogger.warn("Could not rename legacy file " + file.getName() + " after migration");
        }
    }

    private static final Pattern REQUIRE_SENDER_PATTERN =
            Pattern.compile("require sender script\\s+\"\\{player}\"\\s*==\\s*\"([^\"]+)\"");

    /**
     * Parses a legacy ChatControl-style .rs file. The authoritative identity of a
     * per-player group is whatever name its "require sender script" line checks
     * against — NOT the group name — since group names are sometimes just informal
     * shorthand (e.g. "group job-join-message" / require ... == "Ineedajob"). Trusting
     * the group name instead caused entries to be filed under a key nobody could ever
     * match at runtime.
     * <p>
     * Accepts "-leave-message" as a synonym for "-quit-message": some entries in the
     * wild use that suffix and were being silently dropped entirely.
     * <p>
     * The first message seen for a given resolved player name wins; later duplicate/
     * shorthand groups for the same player are ignored (matches the original engine's
     * Stop_On_First_Match top-to-bottom rule evaluation).
     */
    private void parseLegacyFile(File file, Map<String, String> out, String type, String[] defaultOut, String[] firstJoinOut) {
        List<String> suffixes = type.equals("quit")
                ? List.of("-quit-message", "-leave-message")
                : List.of("-" + type + "-message");

        try {
            List<String> lines = Files.readAllLines(file.toPath());
            String currentGroup = null;
            String currentRealName = null;
            boolean isPlayerGroup = false;
            boolean inMessageBlock = false;

            for (String raw : lines) {
                String line = raw.trim();
                if (line.startsWith("group ")) {
                    String groupName = line.substring("group ".length());
                    currentRealName = null;
                    isPlayerGroup = false;
                    if (groupName.equals("default")) {
                        currentGroup = "default";
                    } else if (firstJoinOut != null && groupName.equals("firstjoinmessage")) {
                        currentGroup = "firstjoinmessage";
                    } else {
                        String derived = stripSuffix(groupName, suffixes);
                        if (derived != null) {
                            currentGroup = "player";
                            currentRealName = derived;
                            isPlayerGroup = true;
                        } else {
                            currentGroup = null;
                        }
                    }
                    inMessageBlock = false;
                } else if (isPlayerGroup && line.startsWith("require sender script")) {
                    Matcher m = REQUIRE_SENDER_PATTERN.matcher(line);
                    if (m.find()) {
                        currentRealName = m.group(1);
                    }
                } else if (line.equals("message:") && currentGroup != null) {
                    inMessageBlock = true;
                } else if (inMessageBlock && line.startsWith("- ") && currentGroup != null) {
                    String message = line.substring(2);
                    if (currentGroup.equals("default")) {
                        defaultOut[0] = message;
                    } else if (firstJoinOut != null && currentGroup.equals("firstjoinmessage")) {
                        firstJoinOut[0] = message;
                    } else if (isPlayerGroup && currentRealName != null) {
                        out.putIfAbsent(currentRealName, message);
                    }
                    inMessageBlock = false;
                }
            }
        } catch (IOException e) {
            PrettyLogger.warn("Failed to parse legacy " + type + " messages from " + file.getName() + ": " + e.getMessage());
        }
    }

    private String stripSuffix(String groupName, List<String> suffixes) {
        for (String suffix : suffixes) {
            if (groupName.endsWith(suffix)) {
                return groupName.substring(0, groupName.length() - suffix.length());
            }
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Runtime resolution (used by the join/quit listener)
    // ---------------------------------------------------------------

    public String resolveJoinMessage(Player player) {
        MessageEntry entry = resolveEntry(player);
        if (entry != null && entry.join != null && !entry.join.isEmpty()) {
            return entry.join.replace("{player}", player.getName());
        }
        if (defaultJoinMessage != null && !defaultJoinMessage.isEmpty()) {
            return defaultJoinMessage.replace("{player}", player.getName());
        }
        return null;
    }

    public String resolveQuitMessage(Player player) {
        MessageEntry entry = resolveEntry(player);
        if (entry != null && entry.quit != null && !entry.quit.isEmpty()) {
            return entry.quit.replace("{player}", player.getName());
        }
        if (defaultQuitMessage != null && !defaultQuitMessage.isEmpty()) {
            return defaultQuitMessage.replace("{player}", player.getName());
        }
        return null;
    }

    public String getFirstJoinMessage() {
        return firstJoinMessage;
    }

    /**
     * Looks up this player's UUID record, self-healing any pending
     * name-matched legacy entry into it along the way.
     */
    private MessageEntry resolveEntry(Player player) {
        UUID uuid = player.getUniqueId();
        MessageEntry entry = byUuid.get(uuid);

        MessageEntry legacyMatch = pending.remove(sanitizeKey(player.getName()));
        if (legacyMatch != null) {
            if (entry == null) {
                entry = new MessageEntry(uuid, player.getName(), legacyMatch.join, legacyMatch.quit);
                byUuid.put(uuid, entry);
            }
            save();
        } else if (entry != null && !player.getName().equals(entry.name)) {
            entry.name = player.getName();
            save();
        }

        return entry;
    }

    // ---------------------------------------------------------------
    // Admin / self-service mutation API
    // ---------------------------------------------------------------

    public SetResult setPlayerMessages(OfflinePlayer target, String joinMessage, String quitMessage) {
        if (joinMessage != null && !joinMessage.contains("%player%")) {
            return SetResult.MISSING_PLACEHOLDER_JOIN;
        }
        if (quitMessage != null && !quitMessage.contains("%player%")) {
            return SetResult.MISSING_PLACEHOLDER_QUIT;
        }

        String name = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        UUID uuid = target.getUniqueId();
        String pendingKey = sanitizeKey(name);
        MessageEntry previousPlayer = copyEntry(byUuid.get(uuid));
        MessageEntry previousPending = copyEntry(pending.get(pendingKey));

        pending.remove(pendingKey);

        MessageEntry entry = byUuid.computeIfAbsent(uuid, id -> new MessageEntry(id, name, null, null));
        entry.name = name;
        if (joinMessage != null) entry.join = sanitize(joinMessage).replace("%player%", "{player}");
        if (quitMessage != null) entry.quit = sanitize(quitMessage).replace("%player%", "{player}");

        if (save()) return SetResult.OK;

        restoreEntry(byUuid, uuid, previousPlayer);
        restoreEntry(pending, pendingKey, previousPending);
        return SetResult.WRITE_ERROR;
    }

    public RemoveResult removePlayerMessages(OfflinePlayer target, boolean removeJoin, boolean removeQuit) {
        UUID uuid = target.getUniqueId();
        String name = target.getName();
        String pendingKey = name == null ? null : sanitizeKey(name);
        MessageEntry previousPlayer = copyEntry(byUuid.get(uuid));
        MessageEntry previousPending = pendingKey == null ? null : copyEntry(pending.get(pendingKey));
        boolean removedJoin = false;
        boolean removedQuit = false;

        MessageEntry entry = byUuid.get(uuid);
        if (entry != null) {
            boolean entryChanged = false;
            if (removeJoin && entry.join != null) { entry.join = null; removedJoin = true; entryChanged = true; }
            if (removeQuit && entry.quit != null) { entry.quit = null; removedQuit = true; entryChanged = true; }
            if (entryChanged && entry.join == null && entry.quit == null) {
                byUuid.remove(uuid);
            }
        }

        MessageEntry pend = pendingKey == null ? null : pending.get(pendingKey);
        if (pend != null) {
            boolean pendingChanged = false;
            if (removeJoin && pend.join != null) { pend.join = null; removedJoin = true; pendingChanged = true; }
            if (removeQuit && pend.quit != null) { pend.quit = null; removedQuit = true; pendingChanged = true; }
            if (pendingChanged && pend.join == null && pend.quit == null) {
                pending.remove(pendingKey);
            }
        }

        if (!removedJoin && !removedQuit) return RemoveResult.unchanged();
        if (save()) return new RemoveResult(true, true, removedJoin, removedQuit);

        restoreEntry(byUuid, uuid, previousPlayer);
        if (pendingKey != null) restoreEntry(pending, pendingKey, previousPending);
        return new RemoveResult(true, false, removedJoin, removedQuit);
    }

    public MessageEntry getEntry(OfflinePlayer target) {
        MessageEntry entry = byUuid.get(target.getUniqueId());
        if (entry != null) return entry;
        String name = target.getName();
        return name != null ? pending.get(sanitizeKey(name)) : null;
    }

    /**
     * Looks up a pending (not-yet-UUID-resolved) entry directly by name.
     * Needed because {@link #resolveTarget(String)} can only find a player who
     * is online, already UUID-resolved, or locally cached by Bukkit — a
     * pending entry for someone who hasn't been seen this session (e.g. a
     * Bedrock player restored from a backup, or set up ahead of their first
     * join) is otherwise invisible to admin commands.
     */
    public MessageEntry findPendingByName(String name) {
        return name != null ? pending.get(sanitizeKey(name)) : null;
    }

    /**
     * Sets a message directly on a pending (name-only) entry, creating one if
     * needed. Used as the fallback for admin commands when {@link #resolveTarget}
     * can't produce a real player reference (never joined / not cached).
     */
    public SetResult setPendingMessages(String name, String joinMessage, String quitMessage) {
        if (joinMessage != null && !joinMessage.contains("%player%")) {
            return SetResult.MISSING_PLACEHOLDER_JOIN;
        }
        if (quitMessage != null && !quitMessage.contains("%player%")) {
            return SetResult.MISSING_PLACEHOLDER_QUIT;
        }

        String key = sanitizeKey(name);
        MessageEntry previous = copyEntry(pending.get(key));
        MessageEntry entry = pending.computeIfAbsent(key, k -> new MessageEntry(null, name, null, null));
        entry.name = name;
        if (joinMessage != null) entry.join = sanitize(joinMessage).replace("%player%", "{player}");
        if (quitMessage != null) entry.quit = sanitize(quitMessage).replace("%player%", "{player}");

        if (save()) return SetResult.OK;

        restoreEntry(pending, key, previous);
        return SetResult.WRITE_ERROR;
    }

    public RemoveResult removePendingMessages(String name, boolean removeJoin, boolean removeQuit) {
        String key = sanitizeKey(name);
        MessageEntry previous = copyEntry(pending.get(key));
        MessageEntry entry = pending.get(key);
        if (entry == null) return RemoveResult.unchanged();

        boolean removedJoin = removeJoin && entry.join != null;
        boolean removedQuit = removeQuit && entry.quit != null;
        if (removedJoin) entry.join = null;
        if (removedQuit) entry.quit = null;
        if ((removedJoin || removedQuit) && entry.join == null && entry.quit == null) {
            pending.remove(key);
        }

        if (!removedJoin && !removedQuit) return RemoveResult.unchanged();
        if (save()) return new RemoveResult(true, true, removedJoin, removedQuit);

        restoreEntry(pending, key, previous);
        return new RemoveResult(true, false, removedJoin, removedQuit);
    }

    public List<MessageEntry> listEntries() {
        List<MessageEntry> all = new ArrayList<>(byUuid.values());
        all.addAll(pending.values());
        return all;
    }

    public String getDefaultJoinMessage() {
        return defaultJoinMessage;
    }

    public String getDefaultQuitMessage() {
        return defaultQuitMessage;
    }

    public boolean setDefaultJoinMessage(String message) {
        String previous = this.defaultJoinMessage;
        this.defaultJoinMessage = message;
        if (save()) return true;
        this.defaultJoinMessage = previous;
        return false;
    }

    public boolean setDefaultQuitMessage(String message) {
        String previous = this.defaultQuitMessage;
        this.defaultQuitMessage = message;
        if (save()) return true;
        this.defaultQuitMessage = previous;
        return false;
    }

    public boolean setFirstJoinMessage(String message) {
        String previous = this.firstJoinMessage;
        this.firstJoinMessage = message;
        if (save()) return true;
        this.firstJoinMessage = previous;
        return false;
    }

    private static MessageEntry copyEntry(MessageEntry entry) {
        return entry == null ? null : new MessageEntry(entry.uuid, entry.name, entry.join, entry.quit);
    }

    private static <K> void restoreEntry(Map<K, MessageEntry> entries, K key, MessageEntry previous) {
        if (previous == null) {
            entries.remove(key);
        } else {
            entries.put(key, previous);
        }
    }

    /**
     * Resolves a command argument (exact UUID, currently online name, a
     * previously-seen name, or a locally cached offline name) to a player
     * reference. Never performs a blocking Mojang lookup.
     */
    public OfflinePlayer resolveTarget(String nameOrUuid) {
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(nameOrUuid));
        } catch (IllegalArgumentException ignored) {
            // not a UUID, fall through to name-based resolution
        }

        Player online = Bukkit.getPlayerExact(nameOrUuid);
        if (online != null) return online;

        for (MessageEntry entry : byUuid.values()) {
            if (entry.name.equalsIgnoreCase(nameOrUuid)) {
                return Bukkit.getOfflinePlayer(entry.uuid);
            }
        }

        return Bukkit.getOfflinePlayerIfCached(nameOrUuid);
    }

    private String sanitize(String message) {
        return message.replace("\0", "").replace("\r", "").replace("\n", " ")
                .replace("`", "").replace("$(", "").replace("${", "");
    }

    private String sanitizeKey(String name) {
        return name == null ? "" : name.toLowerCase();
    }

    private File getMessagesDir() {
        return new File(plugin.getDataFolder(), "messages");
    }

    private File getStoreFile() {
        return new File(getMessagesDir(), "join-quit-messages.yml");
    }
}
