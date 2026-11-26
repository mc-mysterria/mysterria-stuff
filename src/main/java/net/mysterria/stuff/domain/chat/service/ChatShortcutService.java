package net.mysterria.stuff.domain.chat.service;

import lombok.RequiredArgsConstructor;
import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.domain.chat.type.ChatShortcutModule;
import net.mysterria.stuff.domain.chat.type.DiscordShortcutGuard;
import net.mysterria.stuff.domain.chat.type.ZelChatShortcutModule;
import net.mysterria.stuff.util.log.PrettyLogger;

@RequiredArgsConstructor
public class ChatShortcutService {

    private final MysterriaStuff plugin;
    private ChatShortcutModule module;
    private AutoCloseable discordGuard;
    private boolean registrationQueued;

    public void scheduleRegistration() {
        if (registrationQueued) {
            return;
        }
        registrationQueued = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            registrationQueued = false;
            reload();
        });
    }

    public void reload() {
        if (!plugin.getConfigManager().getFeatures().isChatAliases()
                || !plugin.getServer().getPluginManager().isPluginEnabled("ZelChat")) {
            close();
            return;
        }
        if (module != null) {
            module.reload();
        } else {
            try {
                module = ZelChatShortcutModule.register(plugin);
            } catch (LinkageError | RuntimeException error) {
                PrettyLogger.error("Could not register ZelChat chat shortcuts: " + error.getMessage());
            }
        }
        reloadDiscordGuard();
    }

    /**
     * Keeps shortcut messages (party, staff, private and the rest) out of the DiscordSRV relay while
     * the shortcuts are active. See {@link DiscordShortcutGuard}.
     */
    public void reloadDiscordGuard() {
        if (module == null || !plugin.getServer().getPluginManager().isPluginEnabled("DiscordSRV")) {
            closeDiscordGuard();
            return;
        }
        if (discordGuard != null) {
            return;
        }
        try {
            discordGuard = DiscordShortcutGuard.register(() -> module);
            PrettyLogger.info("Chat shortcuts kept out of Discord");
        } catch (LinkageError | RuntimeException error) {
            PrettyLogger.error("Could not keep chat shortcuts out of Discord: " + error.getMessage());
        }
    }

    public void closeDiscordGuard() {
        if (discordGuard == null) {
            return;
        }
        try {
            discordGuard.close();
        } catch (Exception | LinkageError error) {
            PrettyLogger.error("Could not unregister the Discord shortcut guard: " + error.getMessage());
        } finally {
            discordGuard = null;
        }
    }

    public void close() {
        closeDiscordGuard();
        if (module == null) {
            return;
        }
        try {
            module.close();
        } catch (LinkageError | RuntimeException error) {
            PrettyLogger.error("Could not unregister ZelChat chat shortcuts: " + error.getMessage());
        } finally {
            module = null;
        }
    }
}
