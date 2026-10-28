package net.mysterria.stuff.domain.lastsprint.service;

import net.mysterria.stuff.util.item.ItemDelivery;
import net.mysterria.stuff.util.log.PrettyLogger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class LastSprintService {

    private final File dataFile;
    private final Set<UUID> giftedPlayers = new HashSet<>();
    private final List<ItemStack> rewards = new ArrayList<>();

    public LastSprintService(Plugin plugin) {
        this.dataFile = new File(plugin.getDataFolder(), "lastsprint_data.yml");
        load();
    }

    public boolean hasReceivedKit(UUID uuid) {
        return giftedPlayers.contains(uuid);
    }

    public void setReceivedKit(UUID uuid, boolean received) {
        if (received ? giftedPlayers.add(uuid) : giftedPlayers.remove(uuid)) {
            save();
        }
    }

    public List<ItemStack> getRewards() {
        return rewards.stream().map(ItemStack::clone).toList();
    }

    public void setRewards(List<ItemStack> items) {
        rewards.clear();
        rewards.addAll(items);
        save();
        PrettyLogger.info("Last Sprint kit updated: " + rewards.size() + " items");
    }

    public int getRewardCount() {
        return rewards.size();
    }

    public void giveKit(Player player) {
        setReceivedKit(player.getUniqueId(), true);
        getRewards().forEach(reward -> ItemDelivery.give(player, reward));
    }

    private void load() {
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        for (String raw : data.getStringList("gifted-players")) {
            try {
                giftedPlayers.add(UUID.fromString(raw));
            } catch (IllegalArgumentException e) {
                PrettyLogger.warn("Skipping invalid UUID in lastsprint_data.yml: " + raw);
            }
        }
        for (String encoded : data.getStringList("reward-items")) {
            rewards.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)));
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("gifted-players", giftedPlayers.stream().map(UUID::toString).toList());
        data.set("reward-items", rewards.stream()
                .map(item -> Base64.getEncoder().encodeToString(item.serializeAsBytes()))
                .toList());
        try {
            data.save(dataFile);
        } catch (IOException e) {
            PrettyLogger.error("Could not save lastsprint_data.yml: " + e.getMessage());
        }
    }
}
