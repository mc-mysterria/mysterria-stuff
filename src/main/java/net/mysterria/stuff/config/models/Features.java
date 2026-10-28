package net.mysterria.stuff.config.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Features {
    private boolean boosterPatriarch;
    private boolean ritualFallbacks;
    private boolean universalToken;
    private boolean joinMessageToken;
    private boolean lastSprint;
    private boolean chatAliases;
}
