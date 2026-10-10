package net.mysterria.stuff.features.joinmsg;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.utils.AdventureUtil;
import net.mysterria.stuff.utils.ItemDelivery;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

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


    public void startSession(Player player) {
        UUID playerId = player.getUniqueId();


        activeSessions.remove(playerId);


        PlayerSession session = new PlayerSession(player);
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


        PlayerSession session = activeSessions.get(playerId);
        if (session == null) {
            return;
        }


        event.setCancelled(true);


        String message = PlainTextComponentSerializer.plainText().serialize(event.message());


        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // The session may have been cancelled or replaced in the meantime.
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


        player.sendMessage(manager.getMessage("processing"));

        JoinMsgStore.SetResult result = store.setPlayerMessages(
                player,
                session.getJoinMessage(),
                session.getQuitMessage()
        );

        switch (result) {
            case OK -> {
                activeSessions.remove(playerId);
                player.sendMessage(manager.getMessage("success"));
            }
            case MISSING_PLACEHOLDER_JOIN -> player.sendMessage(manager.getMessage("join-missing-placeholder"));
            case MISSING_PLACEHOLDER_QUIT -> player.sendMessage(manager.getMessage("quit-missing-placeholder"));
            case WRITE_ERROR -> player.sendMessage(manager.getMessage("write-error"));
        }
        if (result != JoinMsgStore.SetResult.OK) {
            // The token is already spent: keep the session so the player can retry or cancel for a refund.
            player.sendMessage(Component.empty());
            sendCancelButton(player);
            sendRestartButton(player);
        }
    }


    public void handleCancellation(Player player) {
        UUID playerId = player.getUniqueId();


        // Removing the session is what claims the refund, so it can only be paid once.
        if (activeSessions.remove(playerId) == null) {
            player.sendMessage(manager.getMessage("no-active-session"));
            return;
        }


        ItemStack token = manager.createToken(1);
        ItemDelivery.Result delivery = ItemDelivery.deliver(player, token);


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

        if (activeSessions.remove(playerId) == null) {
            player.sendMessage(manager.getMessage("no-active-session"));
            return;
        }


        player.sendMessage(manager.getMessage("session-restarted"));


        startSession(player);
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
}
