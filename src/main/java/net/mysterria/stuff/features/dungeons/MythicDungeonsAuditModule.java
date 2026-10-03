package net.mysterria.stuff.features.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Optional audit rows for MythicDungeons parties and dungeon runs. MythicDungeons is premium and
 * third-party, so there is no compile dependency: event classes are looked up by name through
 * MythicDungeons' own class loader and bound with {@code registerEvent} at MONITOR priority, and
 * every field is read reflectively. Purely observational: no event is modified or cancelled.
 *
 * <p>Failure isolation: an event class that is missing or has a different shape is logged once
 * and only that row is disabled; any other failure is logged once and disables the whole module.
 * Nothing here ever propagates into MythicDungeons' event dispatch.</p>
 */
public final class MythicDungeonsAuditModule implements Listener {

    public static final String PLUGIN_NAME = "MythicDungeons";
    static final String MAIN_CLASS = "net.playavalon.mythicdungeons.MythicDungeons";
    private static final String PARTY_EVENTS = "net.playavalon.mythicdungeons.api.events.party.";
    private static final String DUNGEON_EVENTS = "net.playavalon.mythicdungeons.api.events.dungeon.";

    private final Plugin owner;
    private final Logger logger;
    private final MythicDungeonsAuditRows rows =
            new MythicDungeonsAuditRows(new ReflectiveReader(), System::currentTimeMillis);
    private final Set<String> disabledEvents = ConcurrentHashMap.newKeySet();
    private final Set<String> offThreadWarned = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean moduleFailed = new AtomicBoolean();
    private volatile boolean active = true;

    private MythicDungeonsAuditModule(Plugin owner) {
        this.owner = owner;
        this.logger = owner.getLogger();
    }

    /**
     * Binds the audit listeners when MythicDungeons is enabled and its main class resolves.
     * Returns null (and registers nothing) otherwise. Must be called on the main thread.
     */
    public static MythicDungeonsAuditModule register(Plugin owner) {
        Plugin mythicDungeons = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (mythicDungeons == null || !mythicDungeons.isEnabled()) return null;
        ClassLoader loader = mythicDungeons.getClass().getClassLoader();
        MythicDungeonsAuditModule module = new MythicDungeonsAuditModule(owner);
        try {
            Class.forName(MAIN_CLASS, false, loader);
        } catch (ClassNotFoundException | LinkageError missing) {
            module.warn("MythicDungeons main class not found; dungeon audit rows are disabled", missing);
            return null;
        }
        if (module.bindAll(loader) == 0) {
            module.close();
            return null;
        }
        return module;
    }

    /** Unregisters every listener this module bound. Main thread only. */
    public void close() {
        active = false;
        HandlerList.unregisterAll(this);
    }

    private int bindAll(ClassLoader loader) {
        int bound = 0;
        bound += bind(loader, PARTY_EVENTS + "MythicPartyCreateEvent", true, rows::partyCreated);
        bound += bind(loader, PARTY_EVENTS + "MythicPartyJoinEvent", true, rows::partyJoined);
        bound += bind(loader, PARTY_EVENTS + "MythicPartyLeaveEvent", true, rows::partyLeft);
        bound += bind(loader, PARTY_EVENTS + "MythicPartyKickEvent", true, rows::partyKicked);
        bound += bind(loader, PARTY_EVENTS + "AsyncMythicPartyChatEvent", false, rows::partyChat);
        bound += bind(loader, DUNGEON_EVENTS + "DungeonStartEvent", true, rows::dungeonStarted);
        bound += bind(loader, DUNGEON_EVENTS + "DungeonEndEvent", true, rows::dungeonEnded);
        bound += bind(loader, DUNGEON_EVENTS + "PlayerLeaveDungeonEvent", true, rows::playerLeftDungeon);
        bound += bind(loader, DUNGEON_EVENTS + "DungeonGenerateLootEvent", true, rows::lootGenerated);
        return bound;
    }

    private int bind(ClassLoader loader, String className, boolean mainThreadOnly, RowHandler handler) {
        String key = className.substring(className.lastIndexOf('.') + 1);
        try {
            Class<? extends Event> type = Class.forName(className, true, loader).asSubclass(Event.class);
            EventExecutor executor = (listener, event) -> dispatch(key, type, mainThreadOnly, handler, event);
            Bukkit.getPluginManager().registerEvent(type, this, EventPriority.MONITOR, executor, owner, true);
            return 1;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            warn("MythicDungeons event " + key + " unavailable; its audit row is disabled", unavailable);
            return 0;
        }
    }

    /**
     * Event subclasses share their parent's HandlerList (DungeonEvent, MythicPartyEvent), so the
     * executor sees sibling events too; the isInstance check keeps each row on its own event.
     */
    private void dispatch(String key, Class<?> type, boolean mainThreadOnly, RowHandler handler, Event event) {
        if (!active || !type.isInstance(event) || disabledEvents.contains(key)) return;
        if (mainThreadOnly && !Bukkit.isPrimaryThread()) {
            if (offThreadWarned.add(key)) warn(key + " fired off the main thread; those rows are skipped", null);
            return;
        }
        try {
            handler.handle(event);
        } catch (ReflectiveOperationException | ClassCastException apiMismatch) {
            if (disabledEvents.add(key)) warn("MythicDungeons " + key + " API differs; its audit row is disabled", apiMismatch);
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable failure) {
            active = false;
            if (moduleFailed.compareAndSet(false, true)) {
                warn("MythicDungeons audit failed on " + key + "; dungeon audit rows are disabled", failure);
            }
        }
    }

    private void warn(String message, Throwable failure) {
        try {
            if (failure == null) logger.warning(message);
            else logger.log(Level.WARNING, message, failure);
        } catch (RuntimeException ignored) {
            // Logging must never break gameplay.
        }
    }

    @FunctionalInterface
    interface RowHandler {
        void handle(Object event) throws ReflectiveOperationException;
    }
}
