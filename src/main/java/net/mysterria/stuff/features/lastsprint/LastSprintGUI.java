package net.mysterria.stuff.features.lastsprint;

import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditOutcome;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditRisk;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.mysterria.stuff.audit.StuffAuditEmitter;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LastSprintGUI implements Listener {

    private static final int SIZE = 54;
    private static final int REWARD_SLOTS = 45;
    private static final int SLOT_SAVE = 49;
    private static final int SLOT_CLEAR = 53;
    private static final Component TITLE = Component.text("Last Sprint Rewards")
            .color(TextColor.color(0xAA55FF))
            .decoration(TextDecoration.ITALIC, false);

    private final Set<UUID> viewers = new HashSet<>();
    private final LastSprint lastSprint;

    public LastSprintGUI(LastSprint lastSprint) {
        this.lastSprint = lastSprint;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);

        List<ItemStack> rewards = lastSprint.getRewardItems();
        for (int i = 0; i < Math.min(rewards.size(), REWARD_SLOTS); i++) {
            inv.setItem(i, rewards.get(i));
        }

        ItemStack filler = createFiller();
        for (int i = REWARD_SLOTS; i < SIZE; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(SLOT_SAVE, createSaveButton());
        inv.setItem(SLOT_CLEAR, createClearButton());

        viewers.add(player.getUniqueId());
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!viewers.contains(player.getUniqueId())) return;

        int rawSlot = event.getRawSlot();

        // Block clicks on the control row
        if (rawSlot >= REWARD_SLOTS && rawSlot < SIZE) {
            event.setCancelled(true);
            if (rawSlot == SLOT_SAVE) {
                saveAndClose(player, event.getInventory());
            } else if (rawSlot == SLOT_CLEAR) {
                clearSlots(event.getInventory());
                player.sendMessage(Component.text("Reward slots cleared.").color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!viewers.contains(player.getUniqueId())) return;

        for (int slot : event.getRawSlots()) {
            if (slot >= REWARD_SLOTS && slot < SIZE) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!viewers.remove(player.getUniqueId())) return;

        saveRewards(player, event.getInventory());
        player.sendMessage(Component.text("Last Sprint rewards saved!")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
    }

    private void saveAndClose(Player player, Inventory inv) {
        // Remove from viewers before closing so onInventoryClose doesn't double-save
        viewers.remove(player.getUniqueId());
        saveRewards(player, inv);
        player.closeInventory();
        player.sendMessage(Component.text("Last Sprint rewards saved!")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
    }

    private void saveRewards(Player player, Inventory inv) {
        List<ItemStack> rewards = new ArrayList<>();
        for (int i = 0; i < REWARD_SLOTS; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                rewards.add(item.clone());
            }
        }
        Map<String, Object> values = new LinkedHashMap<>(
                StuffAuditEmitter.kitSummary("old", lastSprint.peekRewardItems()));
        boolean saved = lastSprint.setRewardItems(rewards);
        values.putAll(StuffAuditEmitter.kitSummary("new", rewards));
        if (!saved) values.put("failure", "write_error");
        StuffAuditEmitter.emitStaff(saved ? AuditOutcome.COMMITTED : AuditOutcome.FAILED, AuditRisk.HIGH,
                "kit.rewards_updated", StuffAuditEmitter.correlationId(), "kit:last_sprint",
                player, null, "admin_setup", values);
    }

    private void clearSlots(Inventory inv) {
        for (int i = 0; i < REWARD_SLOTS; i++) {
            inv.setItem(i, null);
        }
    }

    private ItemStack createFiller() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        glass.setItemMeta(meta);
        return glass;
    }

    private ItemStack createSaveButton() {
        ItemStack btn = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = btn.getItemMeta();
        meta.displayName(Component.text("✓ Save & Close")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        btn.setItemMeta(meta);
        return btn;
    }

    private ItemStack createClearButton() {
        ItemStack btn = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = btn.getItemMeta();
        meta.displayName(Component.text("✗ Clear All")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        btn.setItemMeta(meta);
        return btn;
    }
}
