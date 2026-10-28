package net.mysterria.stuff.config.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BoosterSettings {
    private String apiUrl;
    private int updateIntervalSeconds;
}
