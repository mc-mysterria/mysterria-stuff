package net.mysterria.stuff.features.lastsprint;

import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.utils.ItemDelivery;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class LastSprint {

    private final File dataFile;
    private FileConfiguration data;
    private final Set<UUID> giftedPlayers = new HashSet<>();
    private final List<ItemStack> rewardItems = new ArrayList<>();

    public LastSprint(MysterriaStuff plugin) {
        this.dataFile = new File(plugin.getDataFolder(), "lastsprint_data.yml");
        load();
    }

    public void load() {
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                PrettyLogger.error("Failed to create lastsprint_data.yml: " + e.getMessage());
                return;
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        giftedPlayers.clear();
        for (String raw : data.getStringList("gifted-players")) {
            try {
                giftedPlayers.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {}
        }

        rewardItems.clear();
        for (String encoded : data.getStringList("reward-items")) {
            try {
                rewardItems.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)));
            } catch (Exception e) {
                PrettyLogger.warn("Failed to deserialize Last Sprint reward item: " + e.getMessage());
            }
        }

        PrettyLogger.debug("Last Sprint: " + giftedPlayers.size() + " gifted players, " + rewardItems.size() + " reward items");
    }

    private boolean save() {
        data.set("gifted-players", giftedPlayers.stream().map(UUID::toString).toList());

        List<String> encoded = new ArrayList<>();
        for (ItemStack item : rewardItems) {
            try {
                encoded.add(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            } catch (Exception e) {
                PrettyLogger.warn("Failed to serialize Last Sprint reward item: " + e.getMessage());
            }
        }
        data.set("reward-items", encoded);

        try {
            data.save(dataFile);
            return true;
        } catch (IOException e) {
            PrettyLogger.error("Failed to save lastsprint_data.yml: " + e.getMessage());
            return false;
        }
    }

    public boolean hasReceivedGift(UUID uuid) {
        return giftedPlayers.contains(uuid);
    }

    public void markGiftReceived(UUID uuid) {
        tryMarkGiftReceived(uuid);
    }

    /**
     * Flags the player as gifted and persists the flag.
     *
     * @return false if the flag could not be saved; callers must not deliver the kit in that case
     */
    public boolean tryMarkGiftReceived(UUID uuid) {
        boolean added = giftedPlayers.add(uuid);
        boolean saved = false;
        try {
            saved = save();
            return saved;
        } finally {
            // Callers skip delivery when the flag is not persisted, so drop a flag this call added
            // rather than suppress the undelivered kit. A previously saved flag is left untouched.
            if (!saved && added) giftedPlayers.remove(uuid);
        }
    }

    public void unmarkGiftReceived(UUID uuid) {
        giftedPlayers.remove(uuid);
        save();
    }

    public List<ItemStack> getRewardItems() {
        List<ItemStack> clones = new ArrayList<>();
        for (ItemStack item : rewardItems) {
            clones.add(item.clone());
        }
        return clones;
    }

    public void setRewardItems(List<ItemStack> items) {
        rewardItems.clear();
        rewardItems.addAll(items);
        save();
        PrettyLogger.info("Last Sprint rewards updated: " + rewardItems.size() + " item(s)");
    }

    public int getRewardCount() {
        return rewardItems.size();
    }

    public void giveRewards(Player player) {
        giveRewards(player, getRewardItems());
    }

    /** Delivers a snapshot of the reward kit (see {@link #getRewardItems()}) to the player. */
    public ItemDelivery.Result giveRewards(Player player, List<ItemStack> kit) {
        return ItemDelivery.deliverAll(player, kit);
    }
}
