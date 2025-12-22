package net.mysterria.stuff.features.chatcontrol;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mysterria.stuff.MysterriaStuff;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles chat-based interactive sessions for configuring join/quit messages
 */
public class ChatControlSessionHandler implements Listener {

    private final MysterriaStuff plugin;
    private final ChatControlMessageManager manager;
    private final ChatControlFileWriter fileWriter;
    private final Map<UUID, PlayerSession> activeSessions;
    private final LegacyComponentSerializer serializer;

    public ChatControlSessionHandler(MysterriaStuff plugin) {
        this.plugin = plugin;
        this.manager = ChatControlMessageManager.getInstance();
        this.fileWriter = new ChatControlFileWriter(plugin);
        this.activeSessions = new HashMap<>();
        this.serializer = LegacyComponentSerializer.builder().character('&').build();
    }

    /**
     * Start a new configuration session for a player
     */
    public void startSession(Player player) {
        UUID playerId = player.getUniqueId();

        // Cancel any existing session
        if (activeSessions.containsKey(playerId)) {
            activeSessions.remove(playerId);
        }

        // Create new session
        PlayerSession session = new PlayerSession(player);
        activeSessions.put(playerId, session);

        // Send welcome message
        player.sendMessage(Component.empty());
        player.sendMessage(manager.getMessage("session-start"));
        player.sendMessage(Component.empty());
        player.sendMessage(manager.getMessage("join-prompt"));
        player.sendMessage(manager.getMessage("format-info"));
        player.sendMessage(Component.empty());

        // Send cancel button
        sendCancelButton(player);
    }

    /**
     * Handle chat messages during active sessions
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check if player has an active session
        if (!activeSessions.containsKey(playerId)) {
            return;
        }

        // Cancel the event so the message doesn't broadcast
        event.setCancelled(true);

        PlayerSession session = activeSessions.get(playerId);

        // Get the message content
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        // Process the message on the main thread
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            processSessionMessage(player, session, message);
        });
    }

    /**
     * Process a message within a session
     */
    private void processSessionMessage(Player player, PlayerSession session, String message) {
        switch (session.getState()) {
            case AWAITING_JOIN_MESSAGE:
                handleJoinMessage(player, session, message);
                break;
            case AWAITING_QUIT_MESSAGE:
                handleQuitMessage(player, session, message);
                break;
            case AWAITING_CONFIRMATION:
                // Confirmation is handled via clickable buttons, not text input
                player.sendMessage(manager.getMessage("use-buttons"));
                break;
        }
    }

    /**
     * Handle join message input
     */
    private void handleJoinMessage(Player player, PlayerSession session, String message) {
        // Validate message contains placeholder
        if (!message.contains("%player%")) {
            player.sendMessage(manager.getMessage("join-missing-placeholder"));
            player.sendMessage(Component.empty());
            sendCancelButton(player);
            return;
        }

        // Store the join message
        session.setJoinMessage(message);
        session.setState(SessionState.AWAITING_QUIT_MESSAGE);

        // Prompt for quit message
        player.sendMessage(Component.empty());
        player.sendMessage(manager.getMessage("join-received"));
        player.sendMessage(Component.empty());
        player.sendMessage(manager.getMessage("quit-prompt"));
        player.sendMessage(Component.empty());
        sendCancelButton(player);
    }

    /**
     * Handle quit message input
     */
    private void handleQuitMessage(Player player, PlayerSession session, String message) {
        // Validate message contains placeholder
        if (!message.contains("%player%")) {
            player.sendMessage(manager.getMessage("quit-missing-placeholder"));
            player.sendMessage(Component.empty());
            sendCancelButton(player);
            return;
        }

        // Store the quit message
        session.setQuitMessage(message);
        session.setState(SessionState.AWAITING_CONFIRMATION);

        // Show preview and confirmation buttons
        showPreviewAndConfirmation(player, session);
    }

    /**
     * Show preview of messages and confirmation buttons
     */
    private void showPreviewAndConfirmation(Player player, PlayerSession session) {
        player.sendMessage(Component.empty());
        player.sendMessage(manager.getMessage("preview-header"));
        player.sendMessage(Component.empty());

        // Preview join message with color codes
        String joinWithName = session.getJoinMessage().replace("%player%", player.getName());
        Component joinPreview = Component.text("Join: ", NamedTextColor.GRAY)
                .append(serializer.deserialize(joinWithName)
                        .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(joinPreview);

        // Preview quit message with color codes
        String quitWithName = session.getQuitMessage().replace("%player%", player.getName());
        Component quitPreview = Component.text("Quit: ", NamedTextColor.GRAY)
                .append(serializer.deserialize(quitWithName)
                        .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(quitPreview);

        player.sendMessage(Component.empty());
        player.sendMessage(manager.getMessage("confirm-prompt"));
        player.sendMessage(Component.empty());

        // Create clickable confirmation buttons
        Component confirmButton = Component.text("[✓ CONFIRM]", NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true)
                .clickEvent(ClickEvent.runCommand("/mysterriastuff chatcontrol-confirm"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to apply your custom messages")));

        Component cancelButton = Component.text("[✗ CANCEL]", NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true)
                .clickEvent(ClickEvent.runCommand("/mysterriastuff chatcontrol-cancel"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to cancel and discard changes")));

        Component buttons = confirmButton.append(Component.text("  ")).append(cancelButton);
        player.sendMessage(buttons);
        player.sendMessage(Component.empty());

        // Add restart button below
        sendRestartButton(player);
        player.sendMessage(Component.empty());
    }

    /**
     * Send cancel button
     */
    private void sendCancelButton(Player player) {
        Component cancelButton = Component.text("[✗ Cancel]", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/mysterriastuff chatcontrol-cancel"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to cancel")));
        player.sendMessage(cancelButton);
        player.sendMessage(Component.empty());
    }

    /**
     * Send restart button for preview screen
     */
    private void sendRestartButton(Player player) {
        Component restartButton = Component.text("[↻ Restart]", NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/mysterriastuff chatcontrol-restart"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to start over")));
        player.sendMessage(restartButton);
    }

    /**
     * Handle confirmation
     */
    public void handleConfirmation(Player player) {
        UUID playerId = player.getUniqueId();

        if (!activeSessions.containsKey(playerId)) {
            player.sendMessage(manager.getMessage("no-active-session"));
            return;
        }

        PlayerSession session = activeSessions.get(playerId);

        if (session.getState() != SessionState.AWAITING_CONFIRMATION) {
            player.sendMessage(manager.getMessage("not-ready-to-confirm"));
            return;
        }

        // Remove session
        activeSessions.remove(playerId);

        // Write messages to files
        player.sendMessage(manager.getMessage("processing"));

        boolean success = fileWriter.writePlayerMessages(
                player,
                session.getJoinMessage(),
                session.getQuitMessage()
        );

        if (!success) {
            player.sendMessage(manager.getMessage("write-error"));
        }
        // Success message is sent by the file writer
    }

    /**
     * Handle cancellation
     */
    public void handleCancellation(Player player) {
        UUID playerId = player.getUniqueId();

        if (!activeSessions.containsKey(playerId)) {
            player.sendMessage(manager.getMessage("no-active-session"));
            return;
        }

        activeSessions.remove(playerId);
        player.sendMessage(manager.getMessage("session-cancelled"));
    }

    /**
     * Handle restart - clears current session and starts a new one
     */
    public void handleRestart(Player player) {
        UUID playerId = player.getUniqueId();

        if (!activeSessions.containsKey(playerId)) {
            player.sendMessage(manager.getMessage("no-active-session"));
            return;
        }

        // Remove old session and start new one
        activeSessions.remove(playerId);
        player.sendMessage(manager.getMessage("session-restarted"));

        // Start fresh session
        startSession(player);
    }

    /**
     * Clean up session when player quits
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        activeSessions.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Check if player has an active session
     */
    public boolean hasActiveSession(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    /**
     * Player session data
     */
    private static class PlayerSession {
        private final Player player;
        private SessionState state;
        private String joinMessage;
        private String quitMessage;

        public PlayerSession(Player player) {
            this.player = player;
            this.state = SessionState.AWAITING_JOIN_MESSAGE;
        }

        public Player getPlayer() {
            return player;
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

    /**
     * Session states
     */
    private enum SessionState {
        AWAITING_JOIN_MESSAGE,
        AWAITING_QUIT_MESSAGE,
        AWAITING_CONFIRMATION
    }
}
