package net.mysterria.stuff.features.chat;

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
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Routes a deliberately small, fixed set of chat shortcuts. Configuration can rename
 * those routes, but never supplies a command, so chat text cannot become an arbitrary
 * command execution primitive.
 *
 * <p>Messages are identified through {@link ChatMessage#getRawMessage()}, as required by
 * the public API contract. The formatted {@link ChatMessage#getMessage()} is deliberately
 * never parsed or reconstructed.</p>
 *
 * <p>Priority is {@link ModulePriority#LOWEST} on purpose. ZelChat runs its internal
 * modules before any external module, so the filter state is already final here, but
 * MysterriaTranslator registers its own module at {@link ModulePriority#HIGH} and treats a
 * leading {@code !} as the global-chat prefix — it strips the prefix, sends the text to the
 * translation backend, and in its custom range-format path cancels the message and delivers
 * it to the surrounding players itself. Running first is what guarantees a recognised alias
 * is cancelled before the translator can publish {@code !m <player> <text>} or {@code !s
 * <text>} to global or range chat.</p>
 */
@ChatModuleSettings(pluginOwner = "MysterriaStuff", priority = ModulePriority.LOWEST)
public final class ZelChatAliasIntegration implements ChatAliasIntegration, ChatModule {

    private static final Pattern SAFE_ALIAS = Pattern.compile("[a-z0-9]+");
    private static final Pattern CONTROL_CHARACTER = Pattern.compile("\\p{Cntrl}");
    private final MysterriaStuff plugin;
    private final ModuleManager moduleManager;
    private volatile Map<String, ChatShortcut> aliases = defaultAliases();
    private boolean registered;

    private ZelChatAliasIntegration(MysterriaStuff plugin, ModuleManager moduleManager) {
        this.plugin = plugin;
        this.moduleManager = moduleManager;
    }

    public static ZelChatAliasIntegration register(MysterriaStuff plugin) {
        ModuleManager moduleManager = ZelChatAPI.get().getModuleManager();
        ZelChatAliasIntegration integration = new ZelChatAliasIntegration(plugin, moduleManager);
        integration.reload();
        try {
            moduleManager.register(plugin, integration);
            integration.registered = true;
        } catch (LinkageError | RuntimeException registrationFailure) {
            // register() may have called the module before failing. Make a best-effort
            // cleanup so a retry cannot leave two alias modules active.
            try {
                moduleManager.unregister(plugin, integration);
            } catch (LinkageError | RuntimeException cleanupFailure) {
                registrationFailure.addSuppressed(cleanupFailure);
            }
            throw registrationFailure;
        }
        return integration;
    }

    @Override
    public void reload() {
        ConfigurationSection configuredAliases = plugin.getConfigManager().getConfig()
                .getConfigurationSection("chat-aliases.aliases");
        Map<String, ChatShortcut> reloaded = new LinkedHashMap<>();

        for (ChatShortcut shortcut : ChatShortcut.values()) {
            List<String> configured = configuredAliases == null || !configuredAliases.contains(shortcut.routeId())
                    ? shortcut.defaultAliases()
                    : configuredAliases.getStringList(shortcut.routeId());

            for (String configuredAlias : configured) {
                String alias = configuredAlias.toLowerCase(Locale.ROOT).trim();
                if (!SAFE_ALIAS.matcher(alias).matches()) {
                    PrettyLogger.warn("Ignoring invalid chat alias for " + shortcut.routeId() + ": " + configuredAlias);
                    continue;
                }
                ChatShortcut existing = reloaded.putIfAbsent(alias, shortcut);
                if (existing != null && existing != shortcut) {
                    PrettyLogger.warn("Ignoring duplicate chat alias !" + alias + " for " + shortcut.routeId()
                            + "; it is already assigned to " + existing.routeId());
                }
            }
        }

        aliases = Collections.unmodifiableMap(reloaded);
        PrettyLogger.info("Loaded " + aliases.size() + " typed ZelChat chat shortcuts");
    }

    @Override
    public void handleChatMessage(ChatMessage chatMessage) {
        MessageState state = chatMessage.getState();
        if (state == MessageState.CANCELLED || state == MessageState.FILTERED_CANCELLED) {
            return;
        }

        String content = chatMessage.getRawMessage();
        if (content.isEmpty()) {
            return;
        }

        ParsedShortcut parsed = parse(content);
        if (parsed == null) {
            return;
        }

        // The public API identifies FILTERED as a message modified by ZelChat, but does
        // not expose a filtered raw payload. Dispatching the original payload would
        // bypass that modification, so fail closed for recognized provider aliases.
        if (state == MessageState.FILTERED) {
            cancelAndRun(chatMessage, MessageState.FILTERED_CANCELLED, player -> player.sendMessage(Component.text(
                    "That shortcut was not sent because ZelChat filtered the message.", NamedTextColor.RED)));
            return;
        }

        Rejection rejection = parsed.rejection();
        if (rejection != null) {
            cancelAndRun(chatMessage, player -> player.sendMessage(rejection.describe(parsed)));
            return;
        }

        cancelAndRun(chatMessage, sender -> dispatchProviderCommand(sender, parsed));
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

        int payloadStart = aliasEnd;
        while (payloadStart < content.length() && Character.isWhitespace(content.charAt(payloadStart))) {
            payloadStart++;
        }
        String payload = content.substring(payloadStart).strip();

        Rejection rejection = null;
        if (separator < 0 || payload.isEmpty()) {
            rejection = Rejection.MISSING_ARGUMENTS;
        } else if (CONTROL_CHARACTER.matcher(payload).find()) {
            rejection = Rejection.UNSUPPORTED_CHARACTERS;
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

    private void cancelAndRun(ChatMessage chatMessage, java.util.function.Consumer<Player> action) {
        cancelAndRun(chatMessage, MessageState.CANCELLED, action);
    }

    private void cancelAndRun(ChatMessage chatMessage, MessageState cancelledState,
                              java.util.function.Consumer<Player> action) {
        // Cancel first and unconditionally: if the follow-up cannot be scheduled the
        // shortcut is silently dropped, which is the only acceptable failure mode when the
        // alternative is leaking private text into whichever channel the player was in.
        chatMessage.setState(cancelledState);
        Player player = chatMessage.getBukkitPlayer();
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (plugin.isEnabled() && player.isOnline()) {
                    action.accept(player);
                }
            });
        } catch (IllegalPluginAccessException schedulingRefused) {
            // handleChatMessage runs off the main thread, so MysterriaStuff can be disabled
            // between the state check and this call. Swallow it rather than throwing back
            // into ZelChat's message pipeline.
            PrettyLogger.debug("Dropped chat shortcut for " + player.getName()
                    + "; MysterriaStuff is shutting down");
        }
    }

    private void dispatchProviderCommand(Player player, ParsedShortcut parsed) {
        String command = parsed.shortcut().providerCommand();
        try {
            if (player.performCommand(command + " " + parsed.payload())) {
                return;
            }
            // dispatchCommand only reports false for an unknown command, and Bukkit has
            // already told the player so. Surface it to the console instead, because it
            // means the configured provider command is missing or was renamed.
            PrettyLogger.warn("Chat shortcut " + parsed.shortcut().routeId()
                    + " could not run /" + command + "; is the owning plugin installed?");
        } catch (RuntimeException dispatchFailure) {
            PrettyLogger.error("Failed to dispatch chat shortcut " + parsed.shortcut().routeId()
                    + ": " + dispatchFailure.getMessage());
            if (player.isOnline()) {
                player.sendMessage(Component.text("That chat channel is currently unavailable.", NamedTextColor.RED));
            }
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

    private static Map<String, ChatShortcut> defaultAliases() {
        Map<String, ChatShortcut> defaults = new LinkedHashMap<>();
        for (ChatShortcut shortcut : ChatShortcut.values()) {
            for (String alias : shortcut.defaultAliases()) {
                defaults.put(alias, shortcut);
            }
        }
        return Collections.unmodifiableMap(defaults);
    }

    private enum ChatShortcut {
        CHURCH("church", "cc", "<message>", "c", "cc", "church"),
        ORGANIZATION("organization", "oc", "<message>", "o", "oc", "org", "order"),
        // Circle of Imagination registers /coi lineage chat for Error families.
        LINEAGE("lineage", "coi lineage chat", "<message>", "lin", "lineage", "family"),
        LANDS("lands", "lands chat", "<message>", "l", "land", "lands"),
        NATIONS("nations", "nations chat", "<message>", "n", "nation", "nations"),
        // The deployed MythicDungeons plugin registers /p as its party-chat command.
        // /party is the separate party-management command.
        PARTY("party", "p", "<message>", "p", "party", "dp", "dparty"),
        STAFF("staff", "staffchat", "<message>", "s", "sc", "staff"),
        // ZelChat's public configuration documents these command names. Delegating to
        // the commands retains ZelChat's own privacy, ignore and reply checks.
        PRIVATE_MESSAGE("message", "msg", "<player> <message>", "m", "msg", "dm", "pm", "w", "whisper", "tell"),
        REPLY("reply", "reply", "<message>", "r", "reply");

        private final String routeId;
        private final String providerCommand;
        private final String argumentHint;
        private final List<String> defaultAliases;

        ChatShortcut(String routeId, String providerCommand, String argumentHint, String... defaultAliases) {
            this.routeId = routeId;
            this.providerCommand = providerCommand;
            this.argumentHint = argumentHint;
            this.defaultAliases = List.of(defaultAliases);
        }

        String routeId() {
            return routeId;
        }

        String providerCommand() {
            return providerCommand;
        }

        String argumentHint() {
            return argumentHint;
        }

        List<String> defaultAliases() {
            return defaultAliases;
        }
    }

    /**
     * Why a recognised alias was cancelled without being routed. A recognised alias always
     * fails closed rather than falling through to the player's current channel, so the
     * message has to explain what to type instead.
     */
    private enum Rejection {

        MISSING_ARGUMENTS {
            @Override
            Component describe(ParsedShortcut parsed) {
                return Component.text("Usage: !" + parsed.typedAlias() + " "
                        + parsed.shortcut().argumentHint(), NamedTextColor.RED);
            }
        },

        UNSUPPORTED_CHARACTERS {
            @Override
            Component describe(ParsedShortcut parsed) {
                return Component.text("!" + parsed.typedAlias()
                        + " could not be sent: the message contains unsupported characters.",
                        NamedTextColor.RED);
            }
        };

        abstract Component describe(ParsedShortcut parsed);
    }

    private record ParsedShortcut(ChatShortcut shortcut, String typedAlias, String payload, Rejection rejection) {
    }
}
