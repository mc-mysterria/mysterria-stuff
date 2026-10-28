package net.mysterria.stuff.domain.chat.listener;

import lombok.RequiredArgsConstructor;
import net.mysterria.stuff.domain.chat.service.ChatShortcutService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

@RequiredArgsConstructor
public class ZelChatLifecycleListener implements Listener {

    private final ChatShortcutService service;

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equalsIgnoreCase("ZelChat")) {
            service.scheduleRegistration();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().getName().equalsIgnoreCase("ZelChat")) {
            service.close();
        }
    }
}
