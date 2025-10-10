package net.mysterria.stuff.fixes;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.LightningStrikeEvent;

public class HuskTownsLightning implements Listener {

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onLightningStrike(LightningStrikeEvent event) {
        event.setCancelled(false);
    }

}
