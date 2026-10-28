package net.mysterria.stuff.domain.joinmessage.service;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.mysterria.stuff.domain.joinmessage.model.JoinMessageSession;
import net.mysterria.stuff.domain.joinmessage.model.source.SessionStep;
import net.mysterria.stuff.domain.joinmessage.model.source.SetResult;
import net.mysterria.stuff.util.item.ItemDelivery;
import net.mysterria.stuff.util.item.TokenItem;
import net.mysterria.stuff.util.text.AdventureUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class JoinMessageSessionService {

    private static final String COMMAND = "/mysterriastuff joinmsg ";

    private final JoinMessageStore store;
    private final TokenItem token;
    private final Map<UUID, JoinMessageSession> sessions = new ConcurrentHashMap<>();

    public boolean isInSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void start(Player player) {
        if (isInSession(player)) {
            player.sendMessage(token.message("already-in-session"));
            return;
        }
        if (!token.takeOne(player)) {
            return;
        }
        sessions.put(player.getUniqueId(), new JoinMessageSession());
        player.sendMessage(Component.empty());
        player.sendMessage(token.message("session-start"));
        promptJoin(player);
    }

    public void handleChat(Player player, String message) {
        JoinMessageSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        switch (session.getStep()) {
            case AWAITING_JOIN -> {
                if (!message.contains(JoinMessageStore.PLACEHOLDER)) {
                    player.sendMessage(token.message("join-missing-placeholder"));
                    return;
                }
                session.setJoinMessage(message);
                session.setStep(SessionStep.AWAITING_QUIT);
                player.sendMessage(token.message("join-received"));
                player.sendMessage(Component.empty());
                player.sendMessage(token.message("quit-prompt"));
                player.sendMessage(buttons(cancelButton()));
            }
            case AWAITING_QUIT -> {
                if (!message.contains(JoinMessageStore.PLACEHOLDER)) {
                    player.sendMessage(token.message("quit-missing-placeholder"));
                    return;
                }
                session.setQuitMessage(message);
                session.setStep(SessionStep.AWAITING_CONFIRMATION);
                player.sendMessage(token.message("quit-received"));
                showPreview(player, session);
            }
            case AWAITING_CONFIRMATION -> player.sendMessage(token.message("use-buttons"));
        }
    }

    public void confirm(Player player) {
        JoinMessageSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage(token.message("no-active-session"));
            return;
        }
        if (session.getStep() != SessionStep.AWAITING_CONFIRMATION) {
            player.sendMessage(token.message("not-ready-to-confirm"));
            return;
        }
        sessions.remove(player.getUniqueId());
        if (store.setPlayerMessages(player, session.getJoinMessage(), session.getQuitMessage()) != SetResult.OK) {
            player.sendMessage(token.message("write-error"));
            refund(player);
            return;
        }
        player.sendMessage(token.message("success"));
        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    public void cancel(Player player) {
        if (sessions.remove(player.getUniqueId()) == null) {
            player.sendMessage(token.message("no-active-session"));
            return;
        }
        player.sendMessage(token.message("session-cancelled"));
        refund(player);
    }

    public void restart(Player player) {
        JoinMessageSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage(token.message("no-active-session"));
            return;
        }
        session.setStep(SessionStep.AWAITING_JOIN);
        session.setJoinMessage(null);
        session.setQuitMessage(null);
        player.sendMessage(token.message("session-restarted"));
        promptJoin(player);
    }

    public void abandon(Player player) {
        if (sessions.remove(player.getUniqueId()) != null) {
            ItemDelivery.give(player, token.create(1));
        }
    }

    public void abandonAll() {
        sessions.keySet().stream().map(Bukkit::getPlayer).filter(player -> player != null).forEach(this::abandon);
    }

    private void promptJoin(Player player) {
        player.sendMessage(Component.empty());
        player.sendMessage(token.message("join-prompt"));
        player.sendMessage(token.message("format-info"));
        player.sendMessage(buttons(cancelButton()));
    }

    private void showPreview(Player player, JoinMessageSession session) {
        player.sendMessage(Component.empty());
        player.sendMessage(token.message("preview-header"));
        player.sendMessage(previewLine("Join: ", session.getJoinMessage(), player));
        player.sendMessage(previewLine("Quit: ", session.getQuitMessage(), player));
        player.sendMessage(Component.empty());
        player.sendMessage(token.message("confirm-prompt"));
        player.sendMessage(buttons(
                button("[✓ Confirm]", NamedTextColor.GREEN, "confirm", "Use your token and apply these messages"),
                button("[↻ Restart]", NamedTextColor.YELLOW, "restart", "Write both messages again"),
                cancelButton()));
    }

    private Component previewLine(String label, String message, Player player) {
        return Component.text(label, NamedTextColor.GRAY)
                .append(AdventureUtil.parsePlayerText(message.replace(JoinMessageStore.PLACEHOLDER, player.getName())));
    }

    private void refund(Player player) {
        ItemDelivery.give(player, token.create(1));
        player.sendMessage(token.message("token-refunded"));
    }

    private Component cancelButton() {
        return button("[✗ Cancel]", NamedTextColor.RED, "cancel", "Stop and get your token back");
    }

    private Component button(String label, NamedTextColor color, String action, String hover) {
        return Component.text(label, color, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(COMMAND + action))
                .hoverEvent(HoverEvent.showText(Component.text(hover + "\nor type " + COMMAND + action, NamedTextColor.GRAY)));
    }

    private Component buttons(Component... buttons) {
        return Component.join(JoinConfiguration.separator(Component.text("  ")), buttons);
    }
}
