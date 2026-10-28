package net.mysterria.stuff.domain.entity.listener;

import org.bukkit.entity.SkeletonHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public class SkeletonHorseListener implements Listener {

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof SkeletonHorse horse && !horse.isTrapped() && !horse.isTamed()) {
            horse.setTamed(true);
        }
    }
}
