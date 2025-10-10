package net.mysterria.stuff.features.husktowns;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.LightningStrikeEvent;

public class LightningStrikeFix implements Listener {

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onLightningStrike(LightningStrikeEvent event) {
        event.setCancelled(false);
    }

}
