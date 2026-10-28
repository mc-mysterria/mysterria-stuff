package net.mysterria.stuff.domain.joinmessage.service;

import lombok.Getter;
import net.mysterria.stuff.domain.joinmessage.model.LegacyMessages;
import net.mysterria.stuff.domain.joinmessage.model.MessageEntry;
import net.mysterria.stuff.domain.joinmessage.model.source.SetResult;
import net.mysterria.stuff.util.log.PrettyLogger;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class JoinMessageStore {

    public static final String PLACEHOLDER = "%player%";
    private static final String STORED_PLACEHOLDER = "{player}";

    private final File directory;
    private final File file;
    private final LegacyJoinMessageImporter importer = new LegacyJoinMessageImporter();
    private final Map<UUID, MessageEntry> byUuid = new HashMap<>();
    private final Map<String, MessageEntry> pendingByName = new HashMap<>();

    @Getter
    private String defaultJoinMessage;
    @Getter
    private String defaultQuitMessage;
    @Getter
    private String firstJoinMessage;

    public JoinMessageStore(Plugin plugin) {
        this.directory = new File(plugin.getDataFolder(), "messages");
        this.file = new File(directory, "join-quit-messages.yml");
        load();
    }

    public void load() {
        byUuid.clear();
        pendingByName.clear();
        defaultJoinMessage = null;
        defaultQuitMessage = null;
        firstJoinMessage = null;

        if (file.exists()) {
            read(YamlConfiguration.loadConfiguration(file));
        } else {
            migrateLegacyFiles();
        }
        PrettyLogger.info("Loaded " + byUuid.size() + " join/quit messages, " + pendingByName.size() + " pending a name match");
    }

    public String resolveJoinMessage(Player player) {
        MessageEntry entry = resolveEntry(player);
        return withName(entry != null && hasText(entry.getJoin()) ? entry.getJoin() : defaultJoinMessage, player);
    }

    public String resolveQuitMessage(Player player) {
        MessageEntry entry = resolveEntry(player);
        return withName(entry != null && hasText(entry.getQuit()) ? entry.getQuit() : defaultQuitMessage, player);
    }

    public SetResult setPlayerMessages(OfflinePlayer target, String join, String quit) {
        SetResult invalid = validate(join, quit);
        if (invalid != null) {
            return invalid;
        }
        String name = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        pendingByName.remove(nameKey(name));
        MessageEntry entry = byUuid.computeIfAbsent(target.getUniqueId(), uuid -> new MessageEntry(uuid, name, null, null));
        entry.setName(name);
        apply(entry, join, quit);
        return save() ? SetResult.OK : SetResult.WRITE_ERROR;
    }

    public SetResult setPendingMessages(String name, String join, String quit) {
        SetResult invalid = validate(join, quit);
        if (invalid != null) {
            return invalid;
        }
        MessageEntry entry = pendingByName.computeIfAbsent(nameKey(name), key -> new MessageEntry(null, name, null, null));
        entry.setName(name);
        apply(entry, join, quit);
        return save() ? SetResult.OK : SetResult.WRITE_ERROR;
    }

    public boolean removeMessages(OfflinePlayer target, String name, boolean join, boolean quit) {
        boolean changed = false;
        if (target != null) {
            changed = clear(byUuid, target.getUniqueId(), join, quit);
            name = target.getName();
        }
        if (name != null) {
            changed |= clear(pendingByName, nameKey(name), join, quit);
        }
        if (changed) {
            save();
        }
        return changed;
    }

    public MessageEntry findEntry(OfflinePlayer target, String name) {
        if (target != null && byUuid.containsKey(target.getUniqueId())) {
            return byUuid.get(target.getUniqueId());
        }
        String lookup = target != null && target.getName() != null ? target.getName() : name;
        return lookup == null ? null : pendingByName.get(nameKey(lookup));
    }

    public List<MessageEntry> listEntries() {
        List<MessageEntry> all = new ArrayList<>(byUuid.values());
        all.addAll(pendingByName.values());
        return all;
    }

    public boolean setDefaultJoinMessage(String message) {
        defaultJoinMessage = toStored(message);
        return save();
    }

    public boolean setDefaultQuitMessage(String message) {
        defaultQuitMessage = toStored(message);
        return save();
    }

    public boolean setFirstJoinMessage(String message) {
        firstJoinMessage = toStored(message);
        return save();
    }

    public String firstJoinMessageFor(Player player) {
        return withName(firstJoinMessage, player);
    }

    public OfflinePlayer resolveTarget(String nameOrUuid) {
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(nameOrUuid));
        } catch (IllegalArgumentException notUuid) {
            Player online = Bukkit.getPlayerExact(nameOrUuid);
            if (online != null) {
                return online;
            }
            return byUuid.values().stream()
                    .filter(entry -> entry.getName().equalsIgnoreCase(nameOrUuid))
                    .findFirst()
                    .<OfflinePlayer>map(entry -> Bukkit.getOfflinePlayer(entry.getUuid()))
                    .orElseGet(() -> Bukkit.getOfflinePlayerIfCached(nameOrUuid));
        }
    }

    public int repairFromLegacyBackups() {
        File join = new File(directory, "join.rs.migrated");
        File quit = new File(directory, "quit.rs.migrated");
        if (!join.exists() && !quit.exists()) {
            return -1;
        }

        LegacyMessages legacy = importer.read(join, quit);
        int recovered = 0;
        for (String name : legacy.names()) {
            String legacyJoin = legacy.getJoinByName().get(name);
            String legacyQuit = legacy.getQuitByName().get(name);
            MessageEntry existing = findByName(name);
            if (existing == null) {
                pendingByName.put(nameKey(name), new MessageEntry(null, name, legacyJoin, legacyQuit));
                recovered++;
                continue;
            }
            boolean filled = false;
            if (existing.getJoin() == null && legacyJoin != null) {
                existing.setJoin(legacyJoin);
                filled = true;
            }
            if (existing.getQuit() == null && legacyQuit != null) {
                existing.setQuit(legacyQuit);
                filled = true;
            }
            if (filled) {
                recovered++;
            }
        }
        if (recovered > 0) {
            save();
        }
        return recovered;
    }

    private MessageEntry resolveEntry(Player player) {
        MessageEntry entry = byUuid.get(player.getUniqueId());
        MessageEntry legacyMatch = pendingByName.remove(nameKey(player.getName()));
        if (legacyMatch != null) {
            if (entry == null) {
                entry = new MessageEntry(player.getUniqueId(), player.getName(), legacyMatch.getJoin(), legacyMatch.getQuit());
                byUuid.put(player.getUniqueId(), entry);
            }
            save();
        } else if (entry != null && !player.getName().equals(entry.getName())) {
            entry.setName(player.getName());
            save();
        }
        return entry;
    }

    private void read(YamlConfiguration yaml) {
        defaultJoinMessage = yaml.getString("default.join");
        defaultQuitMessage = yaml.getString("default.quit");
        firstJoinMessage = yaml.getString("first-join");

        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    byUuid.put(uuid, new MessageEntry(uuid, players.getString(key + ".name", key),
                            players.getString(key + ".join"), players.getString(key + ".quit")));
                } catch (IllegalArgumentException e) {
                    PrettyLogger.warn("Skipping invalid UUID in join/quit store: " + key);
                }
            }
        }

        // pending entries are a list: Bedrock names can start with '.', which breaks YAML section paths
        for (Map<?, ?> raw : yaml.getMapList("pending")) {
            Object name = raw.get("name");
            if (name != null) {
                pendingByName.put(nameKey(name.toString()), new MessageEntry(null, name.toString(),
                        stringOrNull(raw.get("join")), stringOrNull(raw.get("quit"))));
            }
        }
    }

    private boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("default.join", defaultJoinMessage);
        yaml.set("default.quit", defaultQuitMessage);
        yaml.set("first-join", firstJoinMessage);
        for (MessageEntry entry : byUuid.values()) {
            String base = "players." + entry.getUuid();
            yaml.set(base + ".name", entry.getName());
            yaml.set(base + ".join", entry.getJoin());
            yaml.set(base + ".quit", entry.getQuit());
        }
        List<Map<String, Object>> pending = new ArrayList<>();
        for (MessageEntry entry : pendingByName.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", entry.getName());
            if (entry.getJoin() != null) {
                map.put("join", entry.getJoin());
            }
            if (entry.getQuit() != null) {
                map.put("quit", entry.getQuit());
            }
            pending.add(map);
        }
        if (!pending.isEmpty()) {
            yaml.set("pending", pending);
        }
        return write(yaml);
    }

    private boolean write(YamlConfiguration yaml) {
        File temp = new File(directory, file.getName() + ".tmp");
        try {
            Files.createDirectories(directory.toPath());
            yaml.save(temp);
            if (file.exists()) {
                Files.copy(file.toPath(), new File(directory, file.getName() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            PrettyLogger.error("Could not save join/quit messages: " + e.getMessage());
            return false;
        }
    }

    private void migrateLegacyFiles() {
        File join = new File(directory, "join.rs");
        File quit = new File(directory, "quit.rs");
        if (!join.exists() && !quit.exists()) {
            return;
        }

        LegacyMessages legacy = importer.read(join, quit);
        defaultJoinMessage = legacy.getDefaultJoin();
        defaultQuitMessage = legacy.getDefaultQuit();
        firstJoinMessage = legacy.getFirstJoin();
        for (String name : legacy.names()) {
            pendingByName.put(nameKey(name), new MessageEntry(null, name,
                    legacy.getJoinByName().get(name), legacy.getQuitByName().get(name)));
        }
        save();
        markMigrated(join);
        markMigrated(quit);
        PrettyLogger.info("Migrated " + legacy.names().size() + " legacy join/quit messages; .rs files renamed to .rs.migrated");
    }

    private void markMigrated(File legacyFile) {
        if (legacyFile.exists() && !legacyFile.renameTo(new File(directory, legacyFile.getName() + ".migrated"))) {
            PrettyLogger.warn("Could not rename " + legacyFile.getName() + " after migration");
        }
    }

    private MessageEntry findByName(String name) {
        return byUuid.values().stream()
                .filter(entry -> entry.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(pendingByName.get(nameKey(name)));
    }

    private boolean clear(Map<?, MessageEntry> entries, Object key, boolean join, boolean quit) {
        MessageEntry entry = entries.get(key);
        if (entry == null || (!(join && entry.getJoin() != null) && !(quit && entry.getQuit() != null))) {
            return false;
        }
        if (join) {
            entry.setJoin(null);
        }
        if (quit) {
            entry.setQuit(null);
        }
        if (entry.isEmpty()) {
            entries.remove(key);
        }
        return true;
    }

    private SetResult validate(String join, String quit) {
        if (join != null && !join.contains(PLACEHOLDER)) {
            return SetResult.MISSING_PLACEHOLDER_JOIN;
        }
        if (quit != null && !quit.contains(PLACEHOLDER)) {
            return SetResult.MISSING_PLACEHOLDER_QUIT;
        }
        return null;
    }

    private void apply(MessageEntry entry, String join, String quit) {
        if (join != null) {
            entry.setJoin(toStored(sanitize(join)));
        }
        if (quit != null) {
            entry.setQuit(toStored(sanitize(quit)));
        }
    }

    private String toStored(String message) {
        return message.replace(PLACEHOLDER, STORED_PLACEHOLDER);
    }

    private String withName(String template, Player player) {
        return hasText(template) ? template.replace(STORED_PLACEHOLDER, player.getName()) : null;
    }

    private String sanitize(String message) {
        return message.replace("\0", "").replace("\r", "").replace("\n", " ")
                .replace("`", "").replace("$(", "").replace("${", "");
    }

    private boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private String stringOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    private String nameKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
