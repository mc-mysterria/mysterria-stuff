package net.mysterria.stuff.features.joinmsg;

import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
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
    /** Set when the store file exists but could not be parsed; blocks save() so it cannot be clobbered. */
    private boolean loadFailed;

    public JoinMsgStore(MysterriaStuff plugin) {
        this.plugin = plugin;
        load();
    }

    // ---------------------------------------------------------------
    // Loading / saving
    // ---------------------------------------------------------------

    /**
     * Scratch holder for a complete store snapshot. Loads and migrations are built and
     * validated in one of these first and only swapped into the live maps once they are
     * known to be complete, so a failed read can never leave the store half-cleared.
     */
    private static final class StoreState {
        final Map<UUID, MessageEntry> byUuid;
        final Map<String, MessageEntry> pending;
        String defaultJoin;
        String defaultQuit;
        String firstJoin;

        StoreState() {
            this(new HashMap<>(), new HashMap<>(), null, null, null);
        }

        StoreState(Map<UUID, MessageEntry> byUuid, Map<String, MessageEntry> pending,
                   String defaultJoin, String defaultQuit, String firstJoin) {
            this.byUuid = byUuid;
            this.pending = pending;
            this.defaultJoin = defaultJoin;
            this.defaultQuit = defaultQuit;
            this.firstJoin = firstJoin;
        }
    }

    public void load() {
        tryLoad();
    }

    /**
     * Loads the store from disk.
     *
     * @return false if the store or legacy files could not be read; the previous in-memory
     *         state is kept and saving is blocked until a later load succeeds
     */
    public boolean tryLoad() {
        File file = getStoreFile();
        StoreState loaded;
        if (!file.exists()) {
            File legacyDir = findLegacyDir("join.rs", "quit.rs");
            if (legacyDir == null) {
                applyState(new StoreState());
                loadFailed = false;
                PrettyLogger.info("No join/quit message store found, starting fresh");
                return true;
            }
            loaded = migrateLegacyFormat(legacyDir);
        } else {
            loaded = readStoreFile(file);
        }
        if (loaded == null) {
            return false;
        }

        applyState(loaded);
        loadFailed = false;

        PrettyLogger.info("Loaded " + byUuid.size() + " player join/quit message(s)"
                + (pending.isEmpty() ? "" : ", " + pending.size() + " pending name match(es)")
                + (defaultJoinMessage != null || defaultQuitMessage != null ? ", default message(s)" : "")
                + (firstJoinMessage != null ? ", first-join message" : ""));
        return true;
    }

    /**
     * Parses and validates the store file into a scratch state. Returns null (and blocks
     * save()) if the file cannot be read or parsed; the live state is left untouched.
     */
    private StoreState readStoreFile(File file) {
        // Parse into a scratch configuration first: loadConfiguration() swallows read and
        // YAML errors and returns an empty config, which would wipe the live state and let
        // the next save() overwrite the store (and its .bak) with nothing.
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            return readFrom(yaml);
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {
            loadFailed = true;
            PrettyLogger.warn("Failed to read join/quit message store " + file.getName()
                    + ", keeping the current in-memory state and refusing to overwrite the file until it loads: "
                    + e.getMessage());
            return null;
        }
    }

    private void applyState(StoreState state) {
        byUuid.clear();
        byUuid.putAll(state.byUuid);
        pending.clear();
        pending.putAll(state.pending);
        defaultJoinMessage = state.defaultJoin;
        defaultQuitMessage = state.defaultQuit;
        firstJoinMessage = state.firstJoin;
    }

    private StoreState readFrom(YamlConfiguration yaml) {
        StoreState state = new StoreState();
        state.defaultJoin = yaml.getString("default.join", null);
        state.defaultQuit = yaml.getString("default.quit", null);
        state.firstJoin = yaml.getString("first-join", null);

        ConfigurationSection playersSection = yaml.getConfigurationSection("players");
        if (playersSection != null) {
            for (String key : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    ConfigurationSection s = playersSection.getConfigurationSection(key);
                    if (s == null) continue;
                    state.byUuid.put(uuid, new MessageEntry(uuid, s.getString("name", key), s.getString("join"), s.getString("quit")));
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
                state.pending.put(sanitizeKey(name), new MessageEntry(null, name,
                        joinObj != null ? String.valueOf(joinObj) : null,
                        quitObj != null ? String.valueOf(quitObj) : null));
            }
        }
        return state;
    }

    public boolean save() {
        if (loadFailed) {
            try {
                PrettyLogger.warn("Refusing to save join/quit message store: the file on disk failed to load");
            } catch (RuntimeException ignored) {
                // Callers still need a false result so they can restore their snapshots.
            }
            return false;
        }
        return writeState(new StoreState(byUuid, pending, defaultJoinMessage, defaultQuitMessage, firstJoinMessage));
    }

    private boolean writeState(StoreState state) {
        try {
            YamlConfiguration yaml = new YamlConfiguration();

            if (state.defaultJoin != null) yaml.set("default.join", state.defaultJoin);
            if (state.defaultQuit != null) yaml.set("default.quit", state.defaultQuit);
            if (state.firstJoin != null) yaml.set("first-join", state.firstJoin);

            for (MessageEntry entry : state.byUuid.values()) {
                String base = "players." + entry.uuid;
                yaml.set(base + ".name", entry.name);
                if (entry.join != null) yaml.set(base + ".join", entry.join);
                if (entry.quit != null) yaml.set(base + ".quit", entry.quit);
            }

            List<Map<String, Object>> pendingList = new ArrayList<>();
            for (MessageEntry entry : state.pending.values()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("name", entry.name);
                if (entry.join != null) map.put("join", entry.join);
                if (entry.quit != null) map.put("quit", entry.quit);
                pendingList.add(map);
            }
            if (!pendingList.isEmpty()) yaml.set("pending", pendingList);

            writeAtomically(yaml);
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

    private void writeAtomically(YamlConfiguration yaml) throws IOException {
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
    }

    // ---------------------------------------------------------------
    // One-time legacy .rs migration
    // ---------------------------------------------------------------

    /**
     * Builds the store from the legacy .rs files in {@code dir}. Any read failure aborts the
     * whole migration: nothing is saved, nothing is renamed, the live state is left as is and
     * save() is blocked so no partial store can be created before the migration succeeds.
     *
     * @return the migrated state, or null if the migration was aborted
     */
    private StoreState migrateLegacyFormat(File dir) {
        File joinFile = new File(dir, "join.rs");
        File quitFile = new File(dir, "quit.rs");

        PrettyLogger.info("Migrating legacy ChatControl join/quit format to the new store...");

        // Case-insensitive so a stray case difference between join.rs/quit.rs "require"
        // lines for the same player can't silently drop one half of their messages.
        Map<String, String> legacyJoin = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, String> legacyQuit = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        String[] legacyDefaultJoin = new String[1];
        String[] legacyDefaultQuit = new String[1];
        String[] legacyFirstJoin = new String[1];

        try {
            if (joinFile.exists()) parseLegacyFile(joinFile, legacyJoin, "join", legacyDefaultJoin, legacyFirstJoin);
            if (quitFile.exists()) parseLegacyFile(quitFile, legacyQuit, "quit", legacyDefaultQuit, null);
        } catch (IOException | RuntimeException e) {
            loadFailed = true;
            PrettyLogger.warn("Aborting legacy join/quit migration: failed to read a legacy file in "
                    + dir.getPath() + " (" + e.getMessage() + "). Nothing was saved or renamed; "
                    + "the store stays read-only until the migration succeeds on a later load.");
            return null;
        }

        StoreState state = new StoreState();
        state.defaultJoin = legacyDefaultJoin[0];
        state.defaultQuit = legacyDefaultQuit[0];
        state.firstJoin = legacyFirstJoin[0];

        Set<String> names = new HashSet<>();
        names.addAll(legacyJoin.keySet());
        names.addAll(legacyQuit.keySet());

        for (String name : names) {
            state.pending.put(name.toLowerCase(), new MessageEntry(null, name, legacyJoin.get(name), legacyQuit.get(name)));
        }

        if (!writeState(state)) {
            // Keep the .rs sources discoverable so the migration is retried on the next load.
            PrettyLogger.warn("Could not persist migrated join/quit messages; legacy files in "
                    + dir.getPath() + " were left untouched and migration will be retried on the next load.");
            return state;
        }

        backupLegacyFile(joinFile);
        backupLegacyFile(quitFile);

        PrettyLogger.success("Migrated " + names.size() + " legacy join/quit message(s). "
                + "Each will attach to its player's UUID the next time that player is seen online (name match). "
                + "Old .rs files were renamed with a .migrated suffix.");
        return state;
    }

    public static final int REPAIR_SAVE_FAILED = -2;

    /**
     * Re-imports entries from the pre-migration ".rs.migrated" backup files
     * (kept untouched by {@link #migrateLegacyFormat(File)}) that are missing or
     * incomplete in the current store. Never overwrites an existing non-null
     * join/quit message — only fills in gaps. Safe to run repeatedly.
     *
     * @return number of entries added or filled in, -1 if no backup files exist, or
     *         {@link #REPAIR_SAVE_FAILED} if entries were found but could not be saved
     *         (the in-memory store is rolled back in that case)
     */
    public int repairFromLegacyBackups() {
        File dir = findLegacyDir("join.rs.migrated", "quit.rs.migrated");
        if (dir == null) {
            return -1;
        }
        File joinFile = new File(dir, "join.rs.migrated");
        File quitFile = new File(dir, "quit.rs.migrated");

        // Case-insensitive so a stray case difference between join.rs/quit.rs "require"
        // lines for the same player can't silently drop one half of their messages.
        Map<String, String> legacyJoin = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, String> legacyQuit = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        String[] unusedDefault = new String[1];
        String[] unusedFirstJoin = new String[1];

        parseLegacyFileLogged(joinFile, legacyJoin, "join", unusedDefault, unusedFirstJoin);
        parseLegacyFileLogged(quitFile, legacyQuit, "quit", unusedDefault, null);

        Set<String> names = new HashSet<>();
        names.addAll(legacyJoin.keySet());
        names.addAll(legacyQuit.keySet());

        Map<UUID, MessageEntry> byUuidSnapshot = copyEntries(byUuid);
        Map<String, MessageEntry> pendingSnapshot = copyEntries(pending);

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

        if (recovered > 0 && !save()) {
            byUuid.clear();
            byUuid.putAll(byUuidSnapshot);
            pending.clear();
            pending.putAll(pendingSnapshot);
            return REPAIR_SAVE_FAILED;
        }
        return recovered;
    }

    private static <K> Map<K, MessageEntry> copyEntries(Map<K, MessageEntry> entries) {
        Map<K, MessageEntry> copy = new HashMap<>();
        entries.forEach((key, entry) -> copy.put(key, copyEntry(entry)));
        return copy;
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

    /** Repair is fill-only and safe to rerun, so a failed file is logged and skipped as before. */
    private void parseLegacyFileLogged(File file, Map<String, String> out, String type, String[] defaultOut, String[] firstJoinOut) {
        if (!file.exists()) return;
        try {
            parseLegacyFile(file, out, type, defaultOut, firstJoinOut);
        } catch (IOException e) {
            PrettyLogger.warn("Failed to parse legacy " + type + " messages from " + file.getName() + ": " + e.getMessage());
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
    private void parseLegacyFile(File file, Map<String, String> out, String type, String[] defaultOut, String[] firstJoinOut)
            throws IOException {
        List<String> suffixes = type.equals("quit")
                ? List.of("-quit-message", "-leave-message")
                : List.of("-" + type + "-message");

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
            } else {
                // Keep whichever half only the pending entry has instead of dropping it.
                mergeMissing(entry, legacyMatch);
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

        MessageEntry pendingEntry = pending.remove(pendingKey);

        MessageEntry entry = byUuid.computeIfAbsent(uuid, id -> new MessageEntry(id, name, null, null));
        entry.name = name;
        // Fold the pending name-matched half in first so updating only join keeps a pending quit.
        mergeMissing(entry, pendingEntry);
        if (joinMessage != null) entry.join = sanitize(joinMessage).replace("%player%", "{player}");
        if (quitMessage != null) entry.quit = sanitize(quitMessage).replace("%player%", "{player}");

        if (save()) return SetResult.OK;

        restoreEntry(byUuid, uuid, previousPlayer);
        restoreEntry(pending, pendingKey, previousPending);
        return SetResult.WRITE_ERROR;
    }

    public boolean removePlayerMessages(OfflinePlayer target, boolean removeJoin, boolean removeQuit) {
        RemoveResult result = tryRemovePlayerMessages(target, removeJoin, removeQuit);
        return result.changed() && result.saved();
    }

    /** Like {@link #removePlayerMessages}, but reports a failed save (rolled back) separately from no-op. */
    public RemoveResult tryRemovePlayerMessages(OfflinePlayer target, boolean removeJoin, boolean removeQuit) {
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

    public boolean removePendingMessages(String name, boolean removeJoin, boolean removeQuit) {
        RemoveResult result = tryRemovePendingMessages(name, removeJoin, removeQuit);
        return result.changed() && result.saved();
    }

    /** Like {@link #removePendingMessages}, but reports a failed save (rolled back) separately from no-op. */
    public RemoveResult tryRemovePendingMessages(String name, boolean removeJoin, boolean removeQuit) {
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

    private static void mergeMissing(MessageEntry target, MessageEntry source) {
        if (source == null) return;
        if (target.join == null) target.join = source.join;
        if (target.quit == null) target.quit = source.quit;
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

    /**
     * Legacy .rs files were written by the old token flow into ChatControl's own folder
     * (plugins/ChatControl/messages); this plugin's messages folder is checked first so a
     * manually copied set still wins.
     */
    private File findLegacyDir(String joinName, String quitName) {
        File pluginsDir = plugin.getDataFolder().getParentFile();
        List<File> candidates = new ArrayList<>();
        candidates.add(getMessagesDir());
        if (pluginsDir != null) candidates.add(new File(pluginsDir, "ChatControl" + File.separator + "messages"));
        for (File dir : candidates) {
            if (new File(dir, joinName).exists() || new File(dir, quitName).exists()) return dir;
        }
        return null;
    }

    private File getStoreFile() {
        return new File(getMessagesDir(), "join-quit-messages.yml");
    }
}
