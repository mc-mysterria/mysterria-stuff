package net.mysterria.stuff.domain.booster.listener;

import lombok.RequiredArgsConstructor;
import net.mysterria.stuff.domain.booster.service.BoosterPatriarchService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@RequiredArgsConstructor
public class BoosterJoinListener implements Listener {

    private final BoosterPatriarchService service;

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        service.sync(event.getPlayer());
    }
}
