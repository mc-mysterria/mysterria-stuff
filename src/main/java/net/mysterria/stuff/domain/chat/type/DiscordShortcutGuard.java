package net.mysterria.stuff.domain.chat.type;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.ListenerPriority;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.GameChatMessagePreProcessEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.Event;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.function.Supplier;

/**
 * Keeps typed chat shortcuts ({@code !p}, {@code !s}, {@code !m} and the rest) out of the Discord
 * relay.
 *
 * DiscordSRV relays every chat line that starts with {@code !} from the legacy chat event, which
 * Paper fires before ZelChat sees the message. By then the shortcut has not been cancelled yet, so
 * party, staff and private messages were posted to the public Discord channel. This cancels the
 * relay for any line the shortcut module would claim; global {@code !} chat is untouched.
 *
 * Only loaded when DiscordSRV is installed, so its classes never resolve otherwise.
 */
public final class DiscordShortcutGuard implements AutoCloseable {

    private final Supplier<ChatShortcutModule> shortcuts;
    private boolean subscribed;

    private DiscordShortcutGuard(Supplier<ChatShortcutModule> shortcuts) {
        this.shortcuts = shortcuts;
    }

    public static DiscordShortcutGuard register(Supplier<ChatShortcutModule> shortcuts) {
        DiscordShortcutGuard guard = new DiscordShortcutGuard(shortcuts);
        DiscordSRV.api.subscribe(guard);
        guard.subscribed = true;
        return guard;
    }

    @Subscribe(priority = ListenerPriority.LOWEST)
    public void onGameChat(GameChatMessagePreProcessEvent event) {
        if (event.isCancelled()) {
            return;
        }
        ChatShortcutModule integration = shortcuts.get();
        if (integration == null) {
            return;
        }
        // Decide on exactly what the player typed, as the shortcut module does. Only when the chat event
        // is unavailable fall back to the relayed text, which DiscordSRV may have taken the '!' off.
        String raw = rawMessage(event.getTriggeringBukkitEvent());
        boolean shortcut;
        if (raw != null) {
            shortcut = integration.isShortcut(raw);
        } else {
            String relayed = event.getMessage();
            shortcut = integration.isShortcut(relayed)
                    || (relayed != null && !relayed.startsWith("!") && integration.isShortcut("!" + relayed));
        }
        if (shortcut) {
            event.setCancelled(true);
        }
    }

    private static String rawMessage(Event trigger) {
        if (trigger instanceof AsyncPlayerChatEvent legacy) {
            return legacy.getMessage();
        }
        if (trigger instanceof AsyncChatEvent modern) {
            return PlainTextComponentSerializer.plainText().serialize(modern.originalMessage());
        }
        return null;
    }

    @Override
    public void close() {
        if (!subscribed) {
            return;
        }
        try {
            DiscordSRV.api.unsubscribe(this);
        } finally {
            subscribed = false;
        }
    }
}
