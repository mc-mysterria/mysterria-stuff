package net.mysterria.stuff.features.dungeons;

import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditPrivacy;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditRisk;
import net.mysterria.stuff.audit.ItemIdentity;
import net.mysterria.stuff.audit.StuffAuditEmitter;
import net.mysterria.stuff.features.dungeons.PartyTracker.PartyState;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Builds the MythicDungeons party and dungeon audit rows. Every event object is read through
 * {@link ReflectiveReader}; no MythicDungeons type is referenced at compile time. All handlers
 * except {@link #partyChat} run on the main thread; {@link #partyChat} only reads plain values.
 */
final class MythicDungeonsAuditRows {

    static final int MAX_TRACKED = 512;
    private static final int MAX_LISTED_UUIDS = 6;
    private static final String[] PARTY_ID_GETTERS = {"getUuid", "getUniqueId", "getId", "getPartyId"};

    private final ReflectiveReader reader;
    private final LongSupplier clock;
    private final PartyTracker parties = new PartyTracker(MAX_TRACKED);
    private final BoundedMap<UUID, DungeonRun> runs = new BoundedMap<>(MAX_TRACKED);

    MythicDungeonsAuditRows(ReflectiveReader reader, LongSupplier clock) {
        this.reader = reader;
        this.clock = clock;
    }

    void partyCreated(Object event) throws ReflectiveOperationException {
        Object party = reader.read(event, "getParty");
        Player leader = playerOf(reader.read(event, "getHostPlayer"));
        UUID leaderId = leader != null ? leader.getUniqueId() : leaderId(party);
        PartyState state = partyState(party, leaderId, PartyTracker.SOURCE_CREATED);
        if (state == null) return;
        List<UUID> members = members(party);
        state.memberCount(members.size());
        Map<String, Object> values = partyValues(state);
        putUuids(values, "members", "member_count", members);
        values.putAll(StuffAuditEmitter.location(leader));
        emitParty("party.created", state, leaderId, null, values);
    }

    void partyJoined(Object event) throws ReflectiveOperationException {
        Object party = reader.read(event, "getParty");
        Player joiner = playerOf(reader.read(event, "getJoiningPlayer"));
        UUID joinerId = uuid(joiner);
        UUID leaderId = leaderId(party);
        PartyState state = partyState(party, leaderId, PartyTracker.SOURCE_FIRST_SEEN);
        if (state == null) return;
        // The join event fires before MythicDungeons adds the player to the party.
        List<UUID> members = members(party);
        if (joinerId != null && !members.contains(joinerId)) members.add(joinerId);
        state.memberCount(members.size());
        Map<String, Object> values = partyValues(state);
        putUuids(values, "members", "member_count", members);
        values.putAll(StuffAuditEmitter.location(joiner));
        emitParty("party.joined", state, joinerId, leaderId, values);
    }

    void partyLeft(Object event) throws ReflectiveOperationException {
        Object party = reader.read(event, "getParty");
        Player leaving = playerOf(reader.read(event, "getLeavingPlayer"));
        UUID leavingId = uuid(leaving);
        PartyState state = partyState(party, leaderId(party), PartyTracker.SOURCE_FIRST_SEEN);
        if (state == null) return;
        List<UUID> remaining = members(party);
        remaining.remove(leavingId);
        state.memberCount(remaining.size());
        Map<String, Object> values = partyValues(state);
        putUuids(values, "remaining_members", "remaining_count", remaining);
        values.putAll(StuffAuditEmitter.location(leaving));
        emitParty("party.left", state, leavingId, null, values);
    }

    void partyKicked(Object event) throws ReflectiveOperationException {
        Object party = reader.read(event, "getParty");
        Player kicked = playerOf(reader.read(event, "getKickedPlayer"));
        Player kicker = playerOf(reader.readOptional(event, "getWhoKicked"));
        PartyState state = partyState(party, leaderId(party), PartyTracker.SOURCE_FIRST_SEEN);
        if (state == null) return;
        // The kick event fires before removal; a party.left row follows once it is applied.
        List<UUID> remaining = members(party);
        remaining.remove(uuid(kicked));
        Map<String, Object> values = partyValues(state);
        values.put("kicker_exposed", kicker != null);
        if (kicker == null) values.put("actor_name", "unknown");
        putUuids(values, "remaining_members", "remaining_count", remaining);
        values.putAll(StuffAuditEmitter.location(kicker != null ? kicker : kicked));
        values.put("location_source", kicker != null ? "actor" : "subject");
        emitParty("party.kicked", state, uuid(kicker), uuid(kicked), values);
    }

    /**
     * Async: only plain values are read (the party reference, the message and the tracker's cached
     * member count). The event does not expose the sender, so the row carries no actor.
     */
    void partyChat(Object event) throws ReflectiveOperationException {
        Object party = reader.read(event, "getParty");
        String message = reader.read(event, "getMessage", String.class);
        PartyState state = partyState(party, null, PartyTracker.SOURCE_ANONYMOUS);
        if (state == null) return;
        Map<String, Object> values = partyValues(state);
        values.put("actor_name", "unknown");
        values.put("sender_exposed", false);
        if (state.memberCount() >= 0) values.put("recipient_count", state.memberCount());
        values.put("message", message == null ? "" : message);
        values.put("message_length", message == null ? 0 : message.length());
        StuffAuditEmitter.emitObserved(AuditRisk.LOW, AuditPrivacy.CHAT_CONTENT, "party.chat",
                state.id(), partyBusinessId(state.id()), null, null, null, values);
    }

    void dungeonStarted(Object event) throws ReflectiveOperationException {
        Object instance = reader.read(event, "getInstance");
        UUID instanceId = reader.read(instance, "getUuid", UUID.class);
        if (instanceId == null) return;
        List<Player> participants = players(reader.read(event, "getPlayers", Collection.class));
        Object party = partyOf(reader.readOptional(event, "getMythicPlayers", Collection.class));
        UUID leaderId = leaderId(party);
        UUID partyId = partyId(party, leaderId);
        runs.put(instanceId, new DungeonRun(clock.getAsLong(), partyId));
        Map<String, Object> values = dungeonValues(event, instance, instanceId, partyId);
        putUuids(values, "participants", "participant_count", ids(participants));
        putLocation(values, reader.readOptional(instance, "getStartLoc", Location.class), participants);
        UUID actor = leaderId != null ? leaderId : participants.isEmpty() ? null : participants.getFirst().getUniqueId();
        emitDungeon("dungeon.started", AuditRisk.LOW, instanceId, partyId, actor, values);
    }

    void dungeonEnded(Object event) throws ReflectiveOperationException {
        Object instance = reader.read(event, "getInstance");
        UUID instanceId = reader.read(instance, "getUuid", UUID.class);
        if (instanceId == null) return;
        DungeonRun run = runs.remove(instanceId);
        Object party = reader.readOptional(event, "getParty");
        UUID partyId = party != null ? partyId(party, leaderId(party)) : run != null ? run.partyId() : null;
        List<Player> participants = players(reader.readOptional(event, "getPlayers", Collection.class));
        Map<String, Object> values = dungeonValues(event, instance, instanceId, partyId);
        putUuids(values, "participants", "participant_count", ids(participants));
        putResult(values, instance, run);
        putLocation(values, null, participants);
        emitDungeon("dungeon.ended", AuditRisk.LOW, instanceId, partyId, null, values);
    }

    void playerLeftDungeon(Object event) throws ReflectiveOperationException {
        Object instance = reader.read(event, "getInstance");
        UUID instanceId = reader.read(instance, "getUuid", UUID.class);
        if (instanceId == null) return;
        Player player = reader.read(event, "getPlayer", Player.class);
        UUID partyId = runPartyId(instanceId);
        Map<String, Object> values = dungeonValues(event, instance, instanceId, partyId);
        Boolean editMode = reader.readOptional(event, "isEditMode", Boolean.class);
        if (editMode != null) values.put("edit_mode", editMode);
        values.put("reason_exposed", false);
        values.putAll(StuffAuditEmitter.location(player));
        emitDungeon("dungeon.player_left", AuditRisk.LOW, instanceId, partyId, uuid(player), values);
    }

    /** One event per generated stack (MythicDungeons fires it per item), so the row names one item. */
    void lootGenerated(Object event) throws ReflectiveOperationException {
        Object instance = reader.read(event, "getInstance");
        UUID instanceId = reader.read(instance, "getUuid", UUID.class);
        ItemStack item = reader.read(event, "getGeneratedItem", ItemStack.class);
        if (instanceId == null || item == null) return;
        Player player = reader.readOptional(event, "getPlayer", Player.class);
        UUID partyId = runPartyId(instanceId);
        Map<String, Object> values = dungeonValues(event, instance, instanceId, partyId);
        values.put("loot_table", reader.readOptional(event, "getLootTableNamespace", String.class));
        values.put("material", item.getType().name());
        values.put("amount", item.getAmount());
        String itemUuid = ItemIdentity.readUuid(item);
        if (itemUuid != null) {
            values.put("item_uuid", itemUuid);
            values.put("item_uuid_scope", ItemIdentity.SCOPE_INSTANCE);
        }
        values.putAll(StuffAuditEmitter.location(player));
        values.put("location_source", "player");
        emitDungeon("dungeon.loot_generated", AuditRisk.NORMAL, instanceId, partyId, uuid(player), values);
    }

    PartyState partyState(Object party, UUID leaderId, String source) throws ReflectiveOperationException {
        if (party == null) return null;
        PartyState known = parties.lookup(party);
        if (known != null) return known;
        UUID exposed = exposedPartyId(party);
        if (exposed != null) return parties.track(party, exposed, PartyTracker.SOURCE_EXPOSED);
        long now = clock.getAsLong();
        if (leaderId == null) {
            return parties.track(party, PartyTracker.deriveAnonymous(party, now), PartyTracker.SOURCE_ANONYMOUS);
        }
        return parties.track(party, PartyTracker.derive(leaderId, now), source);
    }

    private UUID partyId(Object party, UUID leaderId) throws ReflectiveOperationException {
        PartyState state = partyState(party, leaderId, PartyTracker.SOURCE_FIRST_SEEN);
        return state == null ? null : state.id();
    }

    private UUID exposedPartyId(Object party) throws ReflectiveOperationException {
        for (String getter : PARTY_ID_GETTERS) {
            UUID id = reader.readOptional(party, getter, UUID.class);
            if (id != null) return id;
        }
        return null;
    }

    private UUID runPartyId(UUID instanceId) {
        DungeonRun run = runs.get(instanceId);
        return run == null ? null : run.partyId();
    }

    private Object partyOf(Collection<?> mythicPlayers) throws ReflectiveOperationException {
        if (mythicPlayers == null) return null;
        for (Object mythicPlayer : mythicPlayers) {
            Object party = reader.readOptional(mythicPlayer, "getDungeonParty");
            if (party != null) return party;
        }
        return null;
    }

    private Player playerOf(Object mythicPlayer) throws ReflectiveOperationException {
        if (mythicPlayer == null || mythicPlayer instanceof Player) return (Player) mythicPlayer;
        return reader.read(mythicPlayer, "getPlayer", Player.class);
    }

    private UUID leaderId(Object party) throws ReflectiveOperationException {
        return uuid(reader.readOptional(party, "getLeader", OfflinePlayer.class));
    }

    private List<UUID> members(Object party) throws ReflectiveOperationException {
        if (party == null) return new ArrayList<>();
        return ids(players(reader.read(party, "getPlayers", Collection.class)));
    }

    private static List<Player> players(Collection<?> values) {
        List<Player> players = new ArrayList<>();
        if (values == null) return players;
        for (Object value : values) {
            if (value instanceof Player player) players.add(player);
        }
        return players;
    }

    private static List<UUID> ids(List<Player> players) {
        List<UUID> ids = new ArrayList<>(players.size());
        for (Player player : players) ids.add(player.getUniqueId());
        return ids;
    }

    private static UUID uuid(OfflinePlayer player) {
        return player == null ? null : player.getUniqueId();
    }

    private static Map<String, Object> partyValues(PartyState state) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("party_id", state.id().toString());
        values.put("party_id_source", state.source());
        values.put("source_plugin", MythicDungeonsAuditModule.PLUGIN_NAME);
        return values;
    }

    private Map<String, Object> dungeonValues(Object event, Object instance, UUID instanceId, UUID partyId)
            throws ReflectiveOperationException {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("instance_id", instanceId.toString());
        if (partyId != null) values.put("party_id", partyId.toString());
        Object dungeon = reader.readOptional(event, "getDungeon");
        if (dungeon == null) dungeon = reader.readOptional(instance, "getDungeon");
        values.put("dungeon", reader.readOptional(dungeon, "getWorldName", String.class));
        values.put("dungeon_display_name", reader.readOptional(dungeon, "getDisplayName", String.class));
        values.put("instance_name", reader.readOptional(instance, "getInstName", String.class));
        values.put("source_plugin", MythicDungeonsAuditModule.PLUGIN_NAME);
        return values;
    }

    private void putResult(Map<String, Object> values, Object instance, DungeonRun run)
            throws ReflectiveOperationException {
        Boolean finished = reader.readOptional(instance, "isDungeonFinished", Boolean.class);
        values.put("result", finished == null ? "unknown" : finished ? "completed" : "not_completed");
        values.put("status", reader.readOptional(instance, "getStatus", String.class));
        values.put("time_elapsed", reader.readOptional(instance, "getTimeElapsed", Integer.class));
        if (run != null) values.put("duration_ms", Math.max(0L, clock.getAsLong() - run.startedMillis()));
    }

    private static void putLocation(Map<String, Object> values, Location preferred, List<Player> participants) {
        Map<String, Object> location = StuffAuditEmitter.location(preferred);
        String source = "instance_start";
        if (location.isEmpty() && !participants.isEmpty()) {
            location = StuffAuditEmitter.location(participants.getFirst());
            source = "participant";
        }
        if (location.isEmpty()) return;
        values.putAll(location);
        values.put("location_source", source);
    }

    /** Comma-joined UUID list capped so it fits the emitter's 256-character value bound. */
    static void putUuids(Map<String, Object> values, String listKey, String countKey, List<UUID> ids) {
        List<String> listed = new ArrayList<>();
        for (UUID id : ids) {
            if (listed.size() >= MAX_LISTED_UUIDS) break;
            listed.add(id.toString());
        }
        values.put(listKey, String.join(",", listed));
        values.put(countKey, ids.size());
        if (ids.size() > MAX_LISTED_UUIDS) values.put(listKey + "_truncated", true);
    }

    static String partyBusinessId(UUID partyId) {
        return "party:" + partyId;
    }

    private static void emitParty(String operation, PartyState state, UUID actor, UUID subject,
                                  Map<String, Object> values) {
        StuffAuditEmitter.emitObserved(AuditRisk.LOW, AuditPrivacy.INTERNAL, operation, state.id(),
                partyBusinessId(state.id()), actor, subject, null, values);
    }

    private static void emitDungeon(String operation, AuditRisk risk, UUID instanceId, UUID partyId,
                                    UUID actor, Map<String, Object> values) {
        if (actor == null) values.putIfAbsent("actor_name", "system");
        String businessId = partyId != null ? partyBusinessId(partyId) : "dungeon_instance:" + instanceId;
        StuffAuditEmitter.emitObserved(risk, AuditPrivacy.INTERNAL, operation, instanceId, businessId,
                actor, null, null, values);
    }

    private record DungeonRun(long startedMillis, UUID partyId) {
    }
}
