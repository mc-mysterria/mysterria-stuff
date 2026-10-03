package net.mysterria.stuff.features.joinmsg;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.audit.ItemIdentity;
import net.mysterria.stuff.audit.StuffAuditEmitter;
import net.mysterria.stuff.utils.ItemDelivery;
import net.mysterria.stuff.utils.AdventureUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class JoinMsgSessionHandler implements Listener {

    private final MysterriaStuff plugin;
    private final JoinMsgTokenManager manager;
    private final JoinMsgStore store;
    private final Map<UUID, PlayerSession> activeSessions;

    public JoinMsgSessionHandler(MysterriaStuff plugin, JoinMsgStore store) {
        this.plugin = plugin;
        this.manager = JoinMsgTokenManager.getInstance();
        this.store = store;
        // Read from the async chat thread, mutated on the main thread.
        this.activeSessions = new ConcurrentHashMap<>();
    }


    public void startSession(Player player, UUID correlationId) {
        startSession(player, correlationId, null);
    }

    public void startSession(Player player, UUID correlationId, String consumedTokenUuid) {
        UUID playerId = player.getUniqueId();


        activeSessions.remove(playerId);


        PlayerSession session = new PlayerSession(player, correlationId, consumedTokenUuid);
        activeSessions.put(playerId, session);


        player.sendMessage(Component.empty());
        player.sendMessage(manager.getMessage("session-start"));
        player.sendMessage(Component.empty());
        player.sendMessage(manager.getMessage("join-prompt"));
        player.sendMessage(manager.getMessage("format-info"));
        player.sendMessage(Component.empty());


        sendCancelButton(player);
    }


    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();


        // Single lookup: a containsKey/get pair could straddle a main-thread removal and yield null.
        PlayerSession session = activeSessions.get(playerId);
        if (session == null) {
            return;
        }


        event.setCancelled(true);


        String message = PlainTextComponentSerializer.plainText().serialize(event.message());


        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Revalidate on the main thread: the session may have been cancelled or replaced meanwhile.
            if (activeSessions.get(playerId) != session) return;
            processSessionMessage(player, session, message);
        });
    }


    private void processSessionMessage(Player player, PlayerSession session, String message) {
        switch (session.getState()) {
            case AWAITING_JOIN_MESSAGE:
                handleJoinMessage(player, session, message);
                break;
            case AWAITING_QUIT_MESSAGE:
                handleQuitMessage(player, session, message);
                break;
            case AWAITING_CONFIRMATION:

                player.sendMessage(manager.getMessage("use-buttons"));
                break;
        }
    }


    private void handleJoinMessage(Player player, PlayerSession session, String message) {

        if (!message.contains("%player%")) {
            player.sendMessage(manager.getMessage("join-missing-placeholder"));
            player.sendMessage(Component.empty());
            sendCancelButton(player);
            return;
        }


        session.setJoinMessage(message);
        session.setState(SessionState.AWAITING_QUIT_MESSAGE);


        player.sendMessage(Component.empty());
        player.sendMessage(manager.getMessage("join-received"));
        player.sendMessage(Component.empty());
        player.sendMessage(manager.getMessage("quit-prompt"));
        player.sendMessage(Component.empty());
        sendCancelButton(player);
    }


    private void handleQuitMessage(Player player, PlayerSession session, String message) {

        if (!message.contains("%player%")) {
            player.sendMessage(manager.getMessage("quit-missing-placeholder"));
            player.sendMessage(Component.empty());
            sendCancelButton(player);
            return;
        }


        session.setQuitMessage(message);
        session.setState(SessionState.AWAITING_CONFIRMATION);


        showPreviewAndConfirmation(player, session);
    }


    private void showPreviewAndConfirmation(Player player, PlayerSession session) {
        player.sendMessage(Component.empty());
        player.sendMessage(manager.getMessage("preview-header"));
        player.sendMessage(Component.empty());


        String joinWithName = session.getJoinMessage().replace("%player%", player.getName());
        Component joinPreview = Component.text("Join: ", NamedTextColor.GRAY)
                .append(AdventureUtil.parseUniversal(joinWithName)
                        .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(joinPreview);


        String quitWithName = session.getQuitMessage().replace("%player%", player.getName());
        Component quitPreview = Component.text("Quit: ", NamedTextColor.GRAY)
                .append(AdventureUtil.parseUniversal(quitWithName)
                        .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(quitPreview);

        player.sendMessage(Component.empty());
        player.sendMessage(manager.getMessage("confirm-prompt"));
        player.sendMessage(Component.empty());


        Component confirmButton = Component.text("[✓ CONFIRM]", NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true)
                .clickEvent(ClickEvent.runCommand("/mysterriastuff joinmsg confirm"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to apply your custom messages")));

        Component cancelButton = Component.text("[✗ CANCEL]", NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true)
                .clickEvent(ClickEvent.runCommand("/mysterriastuff joinmsg cancel"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to cancel and discard changes")));

        Component buttons = confirmButton.append(Component.text("  ")).append(cancelButton);
        player.sendMessage(buttons);
        player.sendMessage(Component.text("Can't click? Type: ", NamedTextColor.DARK_GRAY)
                .append(Component.text("/mystuff joinmsg confirm", NamedTextColor.GRAY))
                .append(Component.text(" or ", NamedTextColor.DARK_GRAY))
                .append(Component.text("/mystuff joinmsg cancel", NamedTextColor.GRAY)));
        player.sendMessage(Component.empty());


        sendRestartButton(player);
        player.sendMessage(Component.empty());
    }


    private void sendCancelButton(Player player) {
        Component cancelButton = Component.text("[✗ Cancel]", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/mysterriastuff joinmsg cancel"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to cancel")));
        player.sendMessage(cancelButton);
        player.sendMessage(Component.text("Can't click? Type: ", NamedTextColor.DARK_GRAY)
                .append(Component.text("/mystuff joinmsg cancel", NamedTextColor.GRAY)));
        player.sendMessage(Component.empty());
    }


    private void sendRestartButton(Player player) {
        Component restartButton = Component.text("[↻ Restart]", NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/mysterriastuff joinmsg restart"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to start over")));
        player.sendMessage(restartButton);
        player.sendMessage(Component.text("Can't click? Type: ", NamedTextColor.DARK_GRAY)
                .append(Component.text("/mystuff joinmsg restart", NamedTextColor.GRAY)));
    }


    public void handleConfirmation(Player player) {
        UUID playerId = player.getUniqueId();

        PlayerSession session = activeSessions.get(playerId);
        if (session == null) {
            player.sendMessage(manager.getMessage("no-active-session"));
            return;
        }

        if (session.getState() != SessionState.AWAITING_CONFIRMATION) {
            player.sendMessage(manager.getMessage("not-ready-to-confirm"));
            return;
        }


        activeSessions.remove(playerId);


        player.sendMessage(manager.getMessage("processing"));

        JoinMsgStore.SetResult result = store.setPlayerMessages(
                player,
                session.getJoinMessage(),
                session.getQuitMessage()
        );

        switch (result) {
            case OK -> player.sendMessage(manager.getMessage("success"));
            case MISSING_PLACEHOLDER_JOIN -> player.sendMessage(manager.getMessage("join-missing-placeholder"));
            case MISSING_PLACEHOLDER_QUIT -> player.sendMessage(manager.getMessage("quit-missing-placeholder"));
            case WRITE_ERROR -> player.sendMessage(manager.getMessage("write-error"));
        }
        emitMessageSet(player, session, result);
        if (result != JoinMsgStore.SetResult.OK) {
            // The token was consumed when the session started: keep the session so the player can
            // retry the confirmation or cancel for a refund instead of losing the token.
            activeSessions.putIfAbsent(playerId, session);
            player.sendMessage(Component.empty());
            sendCancelButton(player);
            sendRestartButton(player);
        }
    }

    private void emitMessageSet(Player player, PlayerSession session, JoinMsgStore.SetResult result) {
        UUID playerId = player.getUniqueId();
        String joinMessage = session.getJoinMessage();
        String quitMessage = session.getQuitMessage();
        String messageType = joinMessage != null && quitMessage != null
                ? "join_and_quit" : joinMessage != null ? "join" : "quit";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("message_type", messageType);
        metadata.put("target_name", player.getName());
        if (joinMessage != null) metadata.put("join_message_sha256", StuffAuditEmitter.sha256(joinMessage));
        if (quitMessage != null) metadata.put("quit_message_sha256", StuffAuditEmitter.sha256(quitMessage));
        metadata.put("message_sha256", StuffAuditEmitter.sha256(
                (joinMessage == null ? "" : joinMessage) + "\n" + (quitMessage == null ? "" : quitMessage)));
        metadata.putAll(StuffAuditEmitter.location(player));
        String businessId = "joinmsg:" + playerId;
        if (result == JoinMsgStore.SetResult.OK) {
            StuffAuditEmitter.emit("joinmsg.message_set", session.getCorrelationId(),
                    businessId, playerId, playerId, null, "self_service", metadata);
        } else if (result == JoinMsgStore.SetResult.WRITE_ERROR) {
            metadata.put("failure", "write_error");
            StuffAuditEmitter.emitFailed("joinmsg.message_set", session.getCorrelationId(),
                    businessId, playerId, playerId, null, "self_service", metadata);
        }
    }


    public void handleCancellation(Player player) {
        UUID playerId = player.getUniqueId();


        // Single remove: the session is claimed once, so a token is refunded at most once per session.
        PlayerSession session = activeSessions.remove(playerId);
        if (session == null) {
            player.sendMessage(manager.getMessage("no-active-session"));
            return;
        }


        ItemStack token = manager.createToken(1);
        // Refund tokens stay unstamped so they keep stacking with existing tokens; the lot uuid is row-only.
        Map<String, Object> lot = ItemIdentity.lotMetadata(ItemIdentity.ORIGIN_SHOP, null, 1);
        Map<String, Object> metadata = new LinkedHashMap<>(
                StuffAuditEmitter.tokenMetadata("joinmsg", 1, "joinmsg_session_cancelled"));
        metadata.putAll(lot);
        if (session.getConsumedTokenUuid() != null) metadata.put("parent_item_uuid", session.getConsumedTokenUuid());
        metadata.putAll(StuffAuditEmitter.location(player));
        String businessId = StuffAuditEmitter.tokenBusinessId("joinmsg");
        ItemDelivery.Result delivery;
        try {
            delivery = ItemDelivery.deliver(player, token);
        } catch (RuntimeException e) {
            StuffAuditEmitter.emitDeliveryException("token.granted", session.getCorrelationId(), businessId,
                    player.getUniqueId(), player.getUniqueId(), "joinmsg_session_cancelled", metadata, e);
            throw e;
        }

        StuffAuditEmitter.emitDelivery("token.granted", session.getCorrelationId(), businessId,
                player.getUniqueId(), player.getUniqueId(), "joinmsg_session_cancelled", delivery, metadata);


        player.sendMessage(manager.getMessage("session-cancelled"));
        if (delivery.complete()) {
            player.sendMessage(manager.getMessage("token-refunded"));
        } else {
            player.sendMessage(Component.text("Your token refund could not be delivered. Please contact staff.",
                    NamedTextColor.RED));
        }
    }


    public void handleRestart(Player player) {
        UUID playerId = player.getUniqueId();

        PlayerSession session = activeSessions.remove(playerId);
        if (session == null) {
            player.sendMessage(manager.getMessage("no-active-session"));
            return;
        }


        player.sendMessage(manager.getMessage("session-restarted"));


        startSession(player, session.getCorrelationId(), session.getConsumedTokenUuid());
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        activeSessions.remove(event.getPlayer().getUniqueId());
    }


    public boolean hasActiveSession(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    private enum SessionState {
        AWAITING_JOIN_MESSAGE,
        AWAITING_QUIT_MESSAGE,
        AWAITING_CONFIRMATION
    }


    private static class PlayerSession {
        private final Player player;
        private final UUID correlationId;
        private final String consumedTokenUuid;
        private SessionState state;
        private String joinMessage;
        private String quitMessage;

        public PlayerSession(Player player, UUID correlationId, String consumedTokenUuid) {
            this.player = player;
            this.correlationId = correlationId;
            this.consumedTokenUuid = consumedTokenUuid;
            this.state = SessionState.AWAITING_JOIN_MESSAGE;
        }

        public Player getPlayer() {
            return player;
        }

        public UUID getCorrelationId() {
            return correlationId;
        }

        public String getConsumedTokenUuid() {
            return consumedTokenUuid;
        }

        public SessionState getState() {
            return state;
        }

        public void setState(SessionState state) {
            this.state = state;
        }

        public String getJoinMessage() {
            return joinMessage;
        }

        public void setJoinMessage(String joinMessage) {
            this.joinMessage = joinMessage;
        }

        public String getQuitMessage() {
            return quitMessage;
        }

        public void setQuitMessage(String quitMessage) {
            this.quitMessage = quitMessage;
        }
    }
}
