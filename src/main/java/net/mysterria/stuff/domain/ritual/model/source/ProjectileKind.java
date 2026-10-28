package net.mysterria.stuff.domain.ritual.model.source;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Firework;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LingeringPotion;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.SplashPotion;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WindCharge;

import java.util.Arrays;
import java.util.Locale;

@RequiredArgsConstructor
public enum ProjectileKind {
    SPECTRAL_ARROW(SpectralArrow.class),
    TIPPED_ARROW(Arrow.class),
    ARROW(Arrow.class),
    TRIDENT(Trident.class),
    SNOWBALL(Snowball.class),
    EGG(Egg.class),
    ENDER_PEARL(EnderPearl.class),
    EXPERIENCE_BOTTLE(ThrownExpBottle.class),
    SPLASH_POTION(SplashPotion.class),
    LINGERING_POTION(LingeringPotion.class),
    FIREWORK(Firework.class),
    WIND_CHARGE(WindCharge.class),
    FISHING_HOOK(FishHook.class);

    private final Class<? extends Projectile> type;

    public static ProjectileKind of(Projectile projectile) {
        if (projectile instanceof Arrow arrow) {
            return arrow.getBasePotionType() != null ? TIPPED_ARROW : ARROW;
        }
        return Arrays.stream(values())
                .filter(kind -> kind.type.isInstance(projectile))
                .findFirst()
                .orElse(null);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
