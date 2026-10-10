package net.mysterria.stuff.audit;

import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditOutcome;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditPrivacy;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditProducer;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditRisk;
import net.mysterria.stuff.utils.ItemDelivery;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Best-effort bridge to the optional shared Mysterria audit ledger. */
public final class StuffAuditEmitter {
    private static final String NAMESPACE = "mysterria-stuff.";
    private static final int MAX_METADATA_ENTRIES = 32;
    private static final int MAX_TEXT = 256;
    private static final Pattern METADATA_KEY = Pattern.compile("[a-z][a-z0-9_]*");
    private static final AtomicBoolean EMIT_FAILURE_WARNED = new AtomicBoolean();
    private static volatile AuditProducer producer;
    private static volatile Logger logger;

    private StuffAuditEmitter() {
    }

    /** Never throws: on failure the emitter stays disabled and every emit is a no-op. */
    public static void initialize(JavaPlugin plugin) {
        logger = plugin.getLogger();
        try {
            producer = AuditProducer.create(plugin.getDataFolder().toPath().toAbsolutePath().getParent()
                            .resolve("mysterria-audit-spool"),
                    "mysterria-stuff", plugin.getPluginMeta().getVersion());
        } catch (RuntimeException | LinkageError failure) {
            producer = null;
            warn("Audit producer failed to initialize; audit rows are disabled", failure);
        }
    }

    /** Never throws, so plugin shutdown cleanup always continues. */
    public static void close() {
        AuditProducer current = producer;
        producer = null;
        if (current == null) return;
        try {
            current.close();
        } catch (RuntimeException | LinkageError failure) {
            warn("Audit producer failed to close cleanly", failure);
        }
    }

    private static void warn(String message, Throwable failure) {
        try {
            Logger current = logger;
            if (current != null) current.log(Level.WARNING, message, failure);
        } catch (RuntimeException ignored) {
            // Logging must never break gameplay.
        }
    }

    /** Call only after the state change has been applied and persisted. */
    public static void emit(String operation,
                            UUID correlationId, String businessId,
                            UUID actorId, UUID subjectId, UUID targetId, String reason,
                            Map<String, ?> values) {
        emit(AuditOutcome.COMMITTED, operation, correlationId, businessId, actorId, subjectId,
                targetId, reason, values);
    }

    /**
     * Records an item delivery: COMMITTED when every item reached the inventory or a valid ground
     * drop, FAILED (failure=items_undelivered) when some or all of it never entered the world.
     * Delivery metadata from the result is added to {@code values}.
     */
    public static void emitDelivery(String operation, UUID correlationId, String businessId,
                                    UUID actorId, UUID subjectId, String reason,
                                    ItemDelivery.Result delivery, Map<String, Object> values) {
        Map<String, Object> metadata = new LinkedHashMap<>(values);
        metadata.putAll(delivery.toMetadata());
        if (delivery.complete()) {
            emit(operation, correlationId, businessId, actorId, subjectId, null, reason, metadata);
            return;
        }
        metadata.put("failure", "items_undelivered");
        emitFailed(operation, correlationId, businessId, actorId, subjectId, null, reason, metadata);
    }

    public static void emitDeliveryException(String operation, UUID correlationId, String businessId,
                                             UUID actorId, UUID subjectId, String reason,
                                             Map<String, Object> values, Throwable failure) {
        Map<String, Object> metadata = new LinkedHashMap<>(values);
        metadata.put("failure", "delivery_exception");
        metadata.put("error_class", failure.getClass().getName());
        emitFailed(operation, correlationId, businessId, actorId, subjectId, null, reason, metadata);
    }

    public static void emitFailed(String operation,
                                  UUID correlationId, String businessId,
                                  UUID actorId, UUID subjectId, UUID targetId, String reason,
                                  Map<String, ?> values) {
        emit(AuditOutcome.FAILED, operation, correlationId, businessId, actorId, subjectId,
                targetId, reason, values);
    }

    public static void emitObservedLow(String operation,
                                       UUID correlationId, String businessId,
                                       UUID actorId, UUID subjectId, String reason,
                                       Map<String, ?> values) {
        emit(AuditOutcome.OBSERVED, AuditRisk.LOW, operation, correlationId, businessId, actorId,
                subjectId, null, reason, values);
    }

    /** Safe from any thread: it only reads the given plain values and enqueues on a bounded queue. */
    public static void emitObserved(AuditRisk risk, AuditPrivacy privacy, String operation,
                                    UUID correlationId, String businessId,
                                    UUID actorId, UUID subjectId, String reason,
                                    Map<String, ?> values) {
        emit(AuditOutcome.OBSERVED, risk, privacy, operation, correlationId, businessId, actorId,
                subjectId, null, reason, values);
    }

    private static void emit(AuditOutcome outcome, String operation,
                             UUID correlationId, String businessId,
                             UUID actorId, UUID subjectId, UUID targetId, String reason,
                             Map<String, ?> values) {
        emit(outcome, AuditRisk.NORMAL, operation, correlationId, businessId, actorId, subjectId,
                targetId, reason, values);
    }

    private static void emit(AuditOutcome outcome, AuditRisk risk, String operation,
                             UUID correlationId, String businessId,
                             UUID actorId, UUID subjectId, UUID targetId, String reason,
                             Map<String, ?> values) {
        emit(outcome, risk, AuditPrivacy.STAFF_RESTRICTED, operation, correlationId, businessId,
                actorId, subjectId, targetId, reason, values);
    }

    private static void emit(AuditOutcome outcome, AuditRisk risk, AuditPrivacy privacy,
                             String operation, UUID correlationId, String businessId,
                             UUID actorId, UUID subjectId, UUID targetId, String reason,
                             Map<String, ?> values) {
        if (operation == null || operation.isBlank() || correlationId == null
                || businessId == null || businessId.isBlank()) {
            return;
        }

        AuditProducer current = producer;
        if (current == null) return;
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (actorId == null) metadata.put("actor_name", "console");
            if (values != null) metadata.putAll(values);
            current.emit(NAMESPACE + operation, outcome, risk,
                    privacy, correlationId, businessId, actorId,
                    subjectId, targetId, reason, boundedMetadata(metadata));
        } catch (RuntimeException | LinkageError failure) {
            current.recordFailure();
            if (EMIT_FAILURE_WARNED.compareAndSet(false, true)) {
                warn("Audit emit failed for " + operation + "; further failures are only counted", failure);
            }
        }
    }

    /**
     * Staff-driven or automatic row with an explicit outcome and risk. {@code actor} is the staff sender
     * (player, console or RCON); null means an automatic change and records {@code actor_name=system}.
     * Reads only plain values, so it is safe from any thread.
     */
    public static void emitStaff(AuditOutcome outcome, AuditRisk risk, String operation,
                                 UUID correlationId, String businessId,
                                 CommandSender actor, UUID subjectId, String reason,
                                 Map<String, ?> values) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("actor_name", actor == null ? "system" : actor.getName());
        if (values != null) metadata.putAll(values);
        emit(outcome, risk, AuditPrivacy.STAFF_RESTRICTED, operation, correlationId, businessId,
                actorId(actor), subjectId, null, reason, metadata);
    }

    /**
     * Material and amount per stack under {@code <prefix>_stack_count}, {@code <prefix>_total_amount} and
     * {@code <prefix>_items}. Reads only type and amount, never serializes a stack.
     */
    public static Map<String, Object> kitSummary(String prefix, List<ItemStack> stacks) {
        Map<String, Object> values = new LinkedHashMap<>();
        StringBuilder items = new StringBuilder();
        boolean truncated = false;
        for (ItemStack stack : stacks) {
            String entry = stack.getType().name() + " x" + stack.getAmount();
            if (truncated || items.length() + entry.length() + 2 > MAX_TEXT - 16) {
                truncated = true;
                continue;
            }
            if (items.length() > 0) items.append(", ");
            items.append(entry);
        }
        values.put(prefix + "_stack_count", stacks.size());
        values.put(prefix + "_total_amount", ItemDelivery.totalAmount(stacks));
        values.put(prefix + "_items", items.length() == 0 ? "none" : items.toString());
        if (truncated) values.put(prefix + "_items_truncated", true);
        return values;
    }

    public static UUID actorId(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : null;
    }

    public static Map<String, Object> location(Player player) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (player == null) return values;
        Location location = player.getLocation();
        if (location.getWorld() != null) values.put("world", location.getWorld().getName());
        values.put("x", location.getBlockX());
        values.put("y", location.getBlockY());
        values.put("z", location.getBlockZ());
        return values;
    }

    /** Empty when the world has been unloaded: a dungeon instance world can be gone by the time an end event fires. */
    public static Map<String, Object> location(Location location) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (location == null) return values;
        try {
            if (location.getWorld() != null) values.put("world", location.getWorld().getName());
        } catch (IllegalArgumentException unloadedWorld) {
            return values;
        }
        values.put("x", location.getBlockX());
        values.put("y", location.getBlockY());
        values.put("z", location.getBlockZ());
        return values;
    }

    public static String sha256(String text) {
        if (text == null) return null;
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] bytes) {
        if (bytes == null) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            return null;
        }
    }

    public static UUID correlationId() {
        return UUID.randomUUID();
    }

    public static String tokenBusinessId(String tokenType) {
        return "token:" + safe(tokenType);
    }

    public static String wrapBusinessId(String wrapId, String fallback) {
        String id = wrapId;
        if (id == null || id.isBlank()) id = fallback;
        return "wrap:" + safe(id);
    }

    public static Map<String, Object> tokenMetadata(String tokenType, int amount, String delivery) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("token_type", safe(tokenType));
        values.put("amount", amount);
        if (delivery != null) values.put("delivery", safe(delivery));
        return values;
    }

    public static Map<String, Object> wrapMetadata(String wrapId, String wrapName,
                                                    String itemType, int itemAmount,
                                                    boolean physical) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("wrap_id", safe(wrapId));
        values.put("wrap_name", safe(wrapName));
        values.put("physical", physical);
        if (itemType != null) {
            values.put("item_type", safe(itemType));
            values.put("item_amount", itemAmount);
        }
        return values;
    }


    private static Map<String, Object> boundedMetadata(Map<String, ?> values) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (values == null) return metadata;
        values.forEach((key, value) -> {
            if (metadata.size() >= MAX_METADATA_ENTRIES || key == null
                    || !METADATA_KEY.matcher(key).matches() || value == null) return;
            metadata.put(key, boundedValue(value));
        });
        return Map.copyOf(metadata);
    }

    private static Object boundedValue(Object value) {
        if (value instanceof String text) return safe(text);
        if (value instanceof Number || value instanceof Boolean) return value;
        return safe(String.valueOf(value));
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT);
    }
}
