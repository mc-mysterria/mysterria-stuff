package net.mysterria.stuff.util.log;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.format.NamedTextColor;

@Getter
@RequiredArgsConstructor
public enum LogLevel {
    INFO(NamedTextColor.GREEN),
    WARN(NamedTextColor.GOLD),
    ERROR(NamedTextColor.RED),
    DEBUG(NamedTextColor.LIGHT_PURPLE);

    private final NamedTextColor color;
}
