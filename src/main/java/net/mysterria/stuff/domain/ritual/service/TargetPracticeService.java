package net.mysterria.stuff.domain.ritual.service;

import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;
import lombok.RequiredArgsConstructor;
import net.mysterria.stuff.domain.ritual.model.source.ProjectileKind;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@RequiredArgsConstructor
public class TargetPracticeService {

    public static final int REQUIRED_TYPES = 12;

    private static final String RITUAL_ID = "blazeandcave:redstone/target_practise";
    private static final NamespacedKey CUSTOM_COMPLETIONS_KEY =
            new NamespacedKey("circleofimagination", "ritual_custom_completions");
    private static final NamespacedKey TYPES_KEY =
            new NamespacedKey("circleofimagination", "ritual_target_practice_types");

    private final CircleOfImaginationAPI coi;

    public boolean isTracking(Player player) {
        if (isSatisfied(player) || !coi.isBeyonder(player)) {
            return false;
        }
        int targetSequence = coi.getLowestSequence(player) - 1;
        return targetSequence >= 0 && coi.hasAssignedRitual(player, targetSequence, RITUAL_ID);
    }

    public boolean recordType(Player player, ProjectileKind kind) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        Set<String> types = readSet(container, TYPES_KEY);
        if (!types.add(kind.id())) {
            return false;
        }
        writeSet(container, TYPES_KEY, types);
        return true;
    }

    public int countTypes(Player player) {
        return readSet(player.getPersistentDataContainer(), TYPES_KEY).size();
    }

    public void complete(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        Set<String> completed = readSet(container, CUSTOM_COMPLETIONS_KEY);
        if (completed.add(RITUAL_ID)) {
            writeSet(container, CUSTOM_COMPLETIONS_KEY, completed);
        }
    }

    private boolean isSatisfied(Player player) {
        if (readSet(player.getPersistentDataContainer(), CUSTOM_COMPLETIONS_KEY).contains(RITUAL_ID)) {
            return true;
        }
        Advancement advancement = Bukkit.getAdvancement(NamespacedKey.fromString(RITUAL_ID));
        return advancement != null && player.getAdvancementProgress(advancement).isDone();
    }

    private Set<String> readSet(PersistentDataContainer container, NamespacedKey key) {
        String raw = container.get(key, PersistentDataType.STRING);
        Set<String> values = new HashSet<>();
        if (raw != null) {
            Arrays.stream(raw.split(",")).map(String::trim).filter(part -> !part.isEmpty()).forEach(values::add);
        }
        return values;
    }

    private void writeSet(PersistentDataContainer container, NamespacedKey key, Set<String> values) {
        container.set(key, PersistentDataType.STRING, String.join(",", values));
    }
}
