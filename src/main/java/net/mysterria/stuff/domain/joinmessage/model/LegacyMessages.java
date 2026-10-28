package net.mysterria.stuff.domain.joinmessage.model;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Getter
@Setter
public class LegacyMessages {
    private final Map<String, String> joinByName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, String> quitByName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private String defaultJoin;
    private String defaultQuit;
    private String firstJoin;

    public Set<String> names() {
        Set<String> names = new HashSet<>(joinByName.keySet());
        names.addAll(quitByName.keySet());
        return names;
    }
}
