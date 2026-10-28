package net.mysterria.stuff.domain.ritual.listener;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.mysterria.stuff.domain.ritual.model.source.ProjectileKind;
import net.mysterria.stuff.domain.ritual.service.TargetPracticeService;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;

@RequiredArgsConstructor
public class TargetPracticeListener implements Listener {

    private final TargetPracticeService service;

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        if (event.getHitBlock() == null || event.getHitBlock().getType() != Material.TARGET) {
            return;
        }
        ProjectileKind kind = ProjectileKind.of(event.getEntity());
        if (kind == null || !service.isTracking(player)) {
            return;
        }

        boolean counted = service.recordType(player, kind);
        int types = service.countTypes(player);
        if (types >= TargetPracticeService.REQUIRED_TYPES) {
            service.complete(player);
            player.sendMessage(Component.text("Target Practice ritual completed.", NamedTextColor.GREEN));
            player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            return;
        }

        String name = kind.id().replace('_', ' ');
        player.sendActionBar(Component.text("Target Practice " + types + "/" + TargetPracticeService.REQUIRED_TYPES, NamedTextColor.YELLOW)
                .append(Component.text(counted ? "  +" + name : "  " + name + " already counted",
                        counted ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
    }
}
