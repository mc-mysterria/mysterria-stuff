package net.mysterria.stuff.domain.chat.type;

import it.pino.zelchat.api.ZelChatAPI;
import it.pino.zelchat.api.message.ChatMessage;
import it.pino.zelchat.api.message.state.MessageState;
import it.pino.zelchat.api.module.ChatModule;
import it.pino.zelchat.api.module.ModuleManager;
import it.pino.zelchat.api.module.annotation.ChatModuleSettings;
import it.pino.zelchat.api.module.priority.ModulePriority;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.domain.chat.model.ParsedShortcut;
import net.mysterria.stuff.domain.chat.model.source.ChatShortcut;
import net.mysterria.stuff.domain.chat.model.source.ShortcutRejection;
import net.mysterria.stuff.util.log.PrettyLogger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

// LOWEST so a recognised alias is cancelled before MysterriaTranslator (HIGH) treats '!' as global chat
@ChatModuleSettings(pluginOwner = "MysterriaStuff", priority = ModulePriority.LOWEST)
public class ZelChatShortcutModule implements ChatShortcutModule, ChatModule {

    private static final Pattern SAFE_ALIAS = Pattern.compile("[a-z0-9]+");
    private static final Pattern CONTROL_CHARACTER = Pattern.compile("\\p{Cntrl}");

    private final MysterriaStuff plugin;
    private final ModuleManager moduleManager;
    private volatile Map<String, ChatShortcut> aliases = Map.of();
    private boolean registered;

    private ZelChatShortcutModule(MysterriaStuff plugin, ModuleManager moduleManager) {
        this.plugin = plugin;
        this.moduleManager = moduleManager;
    }

    public static ChatShortcutModule register(MysterriaStuff plugin) {
        ModuleManager moduleManager = ZelChatAPI.get().getModuleManager();
        ZelChatShortcutModule module = new ZelChatShortcutModule(plugin, moduleManager);
        module.reload();
        try {
            moduleManager.register(plugin, module);
            module.registered = true;
        } catch (LinkageError | RuntimeException registrationFailure) {
            try {
                moduleManager.unregister(plugin, module);
            } catch (LinkageError | RuntimeException cleanupFailure) {
                registrationFailure.addSuppressed(cleanupFailure);
            }
            throw registrationFailure;
        }
        return module;
    }

    @Override
    public void reload() {
        Map<String, List<String>> configured = plugin.getConfigManager().getChatAliases();
        Map<String, ChatShortcut> reloaded = new LinkedHashMap<>();

        for (ChatShortcut shortcut : ChatShortcut.values()) {
            for (String configuredAlias : configured.getOrDefault(shortcut.getRouteId(), shortcut.getDefaultAliases())) {
                String alias = configuredAlias.toLowerCase(Locale.ROOT).trim();
                if (!SAFE_ALIAS.matcher(alias).matches()) {
                    PrettyLogger.warn("Ignoring invalid chat alias for " + shortcut.getRouteId() + ": " + configuredAlias);
                    continue;
                }
                ChatShortcut existing = reloaded.putIfAbsent(alias, shortcut);
                if (existing != null && existing != shortcut) {
                    PrettyLogger.warn("Ignoring duplicate chat alias !" + alias + " for " + shortcut.getRouteId()
                            + ", already used by " + existing.getRouteId());
                }
            }
        }

        aliases = Collections.unmodifiableMap(reloaded);
        PrettyLogger.info("Loaded " + aliases.size() + " chat shortcuts");
    }

    @Override
    public void handleChatMessage(ChatMessage chatMessage) {
        MessageState state = chatMessage.getState();
        if (state == MessageState.CANCELLED || state == MessageState.FILTERED_CANCELLED) {
            return;
        }

        ParsedShortcut parsed = parse(chatMessage.getRawMessage());
        if (parsed == null) {
            return;
        }

        if (state == MessageState.FILTERED) {
            cancelAndRun(chatMessage, MessageState.FILTERED_CANCELLED, player -> player.sendMessage(Component.text(
                    "That shortcut was not sent because the chat filter changed it.", NamedTextColor.RED)));
            return;
        }

        ShortcutRejection rejection = parsed.getRejection();
        if (rejection != null) {
            cancelAndRun(chatMessage, MessageState.CANCELLED, player -> player.sendMessage(rejection.describe(parsed)));
            return;
        }

        cancelAndRun(chatMessage, MessageState.CANCELLED, player -> dispatchProviderCommand(player, parsed));
    }

    private ParsedShortcut parse(String content) {
        if (content.length() < 2 || content.charAt(0) != '!') {
            return null;
        }

        int separator = firstWhitespace(content);
        int aliasEnd = separator < 0 ? content.length() : separator;
        String typedAlias = content.substring(1, aliasEnd).toLowerCase(Locale.ROOT);
        ChatShortcut shortcut = aliases.get(typedAlias);
        if (shortcut == null) {
            return null;
        }

        String payload = content.substring(aliasEnd).strip();
        ShortcutRejection rejection = null;
        if (payload.isEmpty()) {
            rejection = ShortcutRejection.MISSING_ARGUMENTS;
        } else if (CONTROL_CHARACTER.matcher(payload).find()) {
            rejection = ShortcutRejection.UNSUPPORTED_CHARACTERS;
        }
        return new ParsedShortcut(shortcut, typedAlias, payload, rejection);
    }

    private int firstWhitespace(String content) {
        for (int index = 1; index < content.length(); index++) {
            if (Character.isWhitespace(content.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private void cancelAndRun(ChatMessage chatMessage, MessageState cancelledState, Consumer<Player> action) {
        // cancel before scheduling: a dropped shortcut is safe, a private message leaking into public chat is not
        chatMessage.setState(cancelledState);
        Player player = chatMessage.getBukkitPlayer();
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    action.accept(player);
                }
            });
        } catch (IllegalPluginAccessException shuttingDown) {
            PrettyLogger.debug("Dropped chat shortcut for " + player.getName() + " during shutdown");
        }
    }

    private void dispatchProviderCommand(Player player, ParsedShortcut parsed) {
        String command = parsed.getShortcut().getProviderCommand();
        try {
            if (!player.performCommand(command + " " + parsed.getPayload())) {
                PrettyLogger.warn("Chat shortcut " + parsed.getShortcut().getRouteId()
                        + " could not run /" + command + ", is its plugin installed?");
            }
        } catch (RuntimeException dispatchFailure) {
            PrettyLogger.error("Chat shortcut " + parsed.getShortcut().getRouteId() + " failed: " + dispatchFailure.getMessage());
            player.sendMessage(Component.text("That chat channel is unavailable right now.", NamedTextColor.RED));
        }
    }

    @Override
    public void close() {
        if (!registered) {
            return;
        }
        try {
            moduleManager.unregister(plugin, this);
        } finally {
            registered = false;
        }
    }
}
