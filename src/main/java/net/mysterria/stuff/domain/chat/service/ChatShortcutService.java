package net.mysterria.stuff.domain.chat.service;

import lombok.RequiredArgsConstructor;
import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.domain.chat.type.ChatShortcutModule;
import net.mysterria.stuff.domain.chat.type.ZelChatShortcutModule;
import net.mysterria.stuff.util.log.PrettyLogger;

@RequiredArgsConstructor
public class ChatShortcutService {

    private final MysterriaStuff plugin;
    private ChatShortcutModule module;
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
            return;
        }
        try {
            module = ZelChatShortcutModule.register(plugin);
        } catch (LinkageError | RuntimeException error) {
            PrettyLogger.error("Could not register ZelChat chat shortcuts: " + error.getMessage());
        }
    }

    public void close() {
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
