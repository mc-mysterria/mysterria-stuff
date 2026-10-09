package net.mysterria.stuff.features.coi;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.mysterria.stuff.MysterriaStuff;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/** Preserves CoI's native pathway data while booster access is suspended. */
final class PatriarchProgress {
    private static final String COI_PACKAGE = "dev.ua.ikeepcalm.coi.";

    private final NamespacedKey key;
    private final Gson gson = new Gson();

    PatriarchProgress(MysterriaStuff plugin) {
        this.key = new NamespacedKey(plugin, "patriarch_progress");
    }

    boolean hasSnapshot(Player player) {
        return player.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    @SuppressWarnings("unchecked")
    void suspend(Player player) throws ReflectiveOperationException {
        var coi = Bukkit.getPluginManager().getPlugin("CircleOfImagination");
        ClassLoader loader = coi.getClass().getClassLoader();
        Class<?> type = Class.forName(COI_PACKAGE + "domain.beyonder.model.Beyonder", true, loader);
        Object beyonder = nativeBeyonder(player, type);
        if (beyonder == null) return;
        List<Object> pathways = (List<Object>) type.getMethod("getPathways").invoke(beyonder);
        Object patriarch = findPatriarch(pathways);
        if (patriarch == null || pathways.size() <= 1) return;
        // Resolve the save hook before changing either authority.
        Method save = coi.getClass().getMethod("saveBeyonderYamlSync", type);
        String snapshot = gson.toJson(patriarch.getClass().getMethod("serialize").invoke(patriarch));
        player.getPersistentDataContainer().set(key, PersistentDataType.STRING, snapshot);
        player.saveData();
        transfers(loader, "endTemporaryTransfers", player);
        pathways.remove(patriarch);
        transfers(loader, "reconcileGathering", player);
        refresh(beyonder, type);
        if (!Boolean.TRUE.equals(save.invoke(coi, beyonder))) {
            throw new IllegalStateException("CoI could not save Patriarch progression");
        }
    }

    @SuppressWarnings("unchecked")
    void restore(Player player) throws ReflectiveOperationException {
        String raw = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null) return;
        Map<String, Object> data = gson.fromJson(raw, new TypeToken<Map<String, Object>>() {}.getType());
        var coi = Bukkit.getPluginManager().getPlugin("CircleOfImagination");
        ClassLoader loader = coi.getClass().getClassLoader();
        Class<?> type = Class.forName(COI_PACKAGE + "domain.beyonder.model.Beyonder", true, loader);
        Object beyonder = nativeBeyonder(player, type);
        if (beyonder == null) return;
        List<Object> pathways = (List<Object>) type.getMethod("getPathways").invoke(beyonder);
        Method save = coi.getClass().getMethod("saveBeyonderYamlSync", type);
        if (findPatriarch(pathways) == null) {
            Class<?> factory = Class.forName(COI_PACKAGE + "pathways.Pathways", true, loader);
            Object patriarch = factory.getMethod("getNewPathway", String.class, int.class, int.class, type)
                    .invoke(null, "patriarch", ((Number) data.get("sequence")).intValue(),
                            ((Number) data.get("acting")).intValue(), beyonder);
            if (patriarch == null) throw new IllegalStateException("CoI could not restore Patriarch");
            Class<?> pathwayType = patriarch.getClass();
            pathwayType.getMethod("loadSourceLedger", Object.class).invoke(patriarch, data.get("acting-sources"));
            pathwayType.getMethod("loadOverflow", Object.class, Object.class, Object.class).invoke(patriarch,
                    data.get("overflow-acting"), data.get("overflow-sources"), data.get("overflow-week"));
            if (data.get("sacrifice-progress") instanceof Number value) {
                pathwayType.getMethod("setSacrificeProgress", int.class).invoke(patriarch, value.intValue());
            }
            if (data.get("last-advancement-at") instanceof Number value) {
                pathwayType.getMethod("setLastAdvancementAt", long.class).invoke(patriarch, value.longValue());
            }
            if (data.get("pathway-acquired-at") instanceof Number value) {
                pathwayType.getMethod("setPathwayAcquiredAt", long.class).invoke(patriarch, value.longValue());
            }
            pathways.add(patriarch);
            refresh(beyonder, type);
        }
        // Consume only after CoI has saved the restored state.
        if (!Boolean.TRUE.equals(save.invoke(coi, beyonder))) {
            throw new IllegalStateException("CoI could not save Patriarch progression");
        }
        player.getPersistentDataContainer().remove(key);
        player.saveData();
    }

    /** The player's Beyonder, or null when its pathways are virtual or mirrored and not ours to change. */
    private Object nativeBeyonder(Player player, Class<?> type) throws ReflectiveOperationException {
        Object beyonder = type.getMethod("of", Player.class).invoke(null, player);
        if (beyonder == null || (boolean) type.getMethod("isVirtual").invoke(beyonder)
                || (boolean) type.getMethod("isPathwaysMirrored").invoke(beyonder)) return null;
        return beyonder;
    }

    private Object findPatriarch(List<Object> pathways) throws ReflectiveOperationException {
        for (Object pathway : pathways) {
            if ("patriarch".equals(pathway.getClass().getMethod("getName").invoke(pathway))) return pathway;
        }
        return null;
    }

    private void transfers(ClassLoader loader, String method, Player player) throws ReflectiveOperationException {
        Class.forName(COI_PACKAGE + "domain.ability.service.AbilityTransferService", true, loader)
                .getMethod(method, Player.class).invoke(null, player);
    }

    private void refresh(Object beyonder, Class<?> type) throws ReflectiveOperationException {
        type.getMethod("invalidateAbilitiesCache").invoke(beyonder);
        int max = (int) type.getMethod("determineMaxSpirituality").invoke(beyonder);
        type.getMethod("setMaxSpirituality", int.class).invoke(beyonder, max);
        int current = (int) type.getMethod("getSpirituality").invoke(beyonder);
        type.getMethod("setSpirituality", int.class).invoke(beyonder, Math.min(current, max));
    }
}
