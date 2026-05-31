package net.mysterria.stuff.features.coi;


import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.utils.AdventureUtil;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Crafter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DangerousActionsListener implements Listener {


    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (isForbiddenSickle(event.getItem().getItemStack())) {
            event.setCancelled(true);
            event.getItem().remove();
            return;
        }

        if (!MysterriaStuff.getInstance().getConfigManager().isBlockNightmarePickups()) {
            return;
        }

        if (event.getEntity().getWorld().getName().startsWith("world_nightmare_")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerNightmareDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();

        if (player.getWorld().getName().startsWith("world_nightmare_")) {

            if (MysterriaStuff.getInstance().getConfigManager().isNightmareKeepInventory()) {
                event.setKeepInventory(true);
                event.setKeepLevel(true);
                event.getDrops().clear();
            }

            World world = Bukkit.getWorld("world");
            if (world != null) {
                player.teleport(world.getSpawnLocation());
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        scrubForbiddenSickles(player);

        if (!MysterriaStuff.getInstance().getConfigManager().isResetAttributesOnJoin()) {
            return;
        }

        resetAllAttributes(player);
    }

    private void resetAllAttributes(Player player) {
        Map<Attribute, Double> defaultValues = new HashMap<>();
        defaultValues.put(Attribute.MAX_HEALTH, 20.0);
        defaultValues.put(Attribute.FOLLOW_RANGE, 16.0);
        defaultValues.put(Attribute.KNOCKBACK_RESISTANCE, 0.0);
        defaultValues.put(Attribute.STEP_HEIGHT, 0.6);
        defaultValues.put(Attribute.MOVEMENT_SPEED, 0.1);
        defaultValues.put(Attribute.FLYING_SPEED, 0.05);
        defaultValues.put(Attribute.ATTACK_DAMAGE, 2.0);
        defaultValues.put(Attribute.ATTACK_KNOCKBACK, 0.0);
        defaultValues.put(Attribute.ATTACK_SPEED, 4.0);
        defaultValues.put(Attribute.ARMOR, 0.0);
        defaultValues.put(Attribute.ARMOR_TOUGHNESS, 0.0);
        defaultValues.put(Attribute.LUCK, 0.0);

        for (Map.Entry<Attribute, Double> entry : defaultValues.entrySet()) {
            AttributeInstance instance = player.getAttribute(entry.getKey());
            if (instance != null) {
                for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
                    instance.removeModifier(modifier);
                }
                double trueDefault = entry.getValue();
                instance.setBaseValue(trueDefault);
            }
        }

        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.min(player.getHealth(), maxHealth));
        player.setGlowing(false);
        player.setFireTicks(0);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setNoDamageTicks(0);
        player.setGameMode(GameMode.SURVIVAL);

        PrettyLogger.debug("Completed attribute reset for player: " + player.getName());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getDrops() == null) return;

        try {
            List<ItemStack> itemsToRemove = new ArrayList<>();
            for (ItemStack item : event.getDrops()) {
                if (checkForNonIngredientMysticalAlignment(item) || isForbiddenSickle(item)) {
                    itemsToRemove.add(item);
                }
            }
            event.getDrops().removeAll(itemsToRemove);
        } catch (Exception e) {
            PrettyLogger.debug("Error in DupeListener, probably due to a comodification error");
        }
    }

    @EventHandler
    public void onCraftEvent(CrafterCraftEvent event) {
        Crafter crafter = (Crafter) event.getBlock().getState();
        Inventory crafterInventory = crafter.getInventory();
        ItemStack[] matrix = crafterInventory.getContents();

        for (ItemStack ingredient : matrix) {
            if (ingredient == null || ingredient.getType() == Material.AIR) continue;

            if (checkForMysticalAlignment(ingredient)) {
                event.setCancelled(true);
                return;
            }
        }

        ItemStack item = event.getResult();
        if (item == null) return;
        if (item.getType() == Material.AIR) return;
        if (item.hasItemMeta()) {
            PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            NamespacedKey abilityCostKey = AdventureUtil.getCoINamespacedKey("abilityCost");
            NamespacedKey shortcutKey = AdventureUtil.getCoINamespacedKey("shortcut");
            if (abilityCostKey != null && shortcutKey != null) {
                if (container.has(abilityCostKey) || container.has(shortcutKey)) {
                    event.setCancelled(true);
                }
            }

            NamespacedKey pathwayKey = AdventureUtil.getCoINamespacedKey("pathway");
            if (pathwayKey != null && container.has(pathwayKey)) {
                event.setCancelled(true);
            }

            NamespacedKey ingredientKey = AdventureUtil.getCoINamespacedKey("ingredient");
            if (ingredientKey != null && container.has(ingredientKey)) {
                event.setCancelled(true);
            }


        }
    }

    @EventHandler
    public void onItemMoveEvent(InventoryClickEvent event) {
        if (removeForbiddenSickleFromClick(event)) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null) return;
        if (item.getType() == Material.AIR) return;

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();

        NamespacedKey abilityCostKey = AdventureUtil.getCoINamespacedKey("abilityCost");
        NamespacedKey shortcutKey = AdventureUtil.getCoINamespacedKey("shortcut");
        NamespacedKey fogOfHistoryKey = AdventureUtil.getCoINamespacedKey("fogOfHistory");
        if (abilityCostKey != null && shortcutKey != null && fogOfHistoryKey != null) {
            if (container.has(abilityCostKey) || container.has(shortcutKey) || container.has(fogOfHistoryKey)) {
                InventoryView view = event.getView();
                if (view.getType() != InventoryType.CRAFTING) {
                    event.setCancelled(true);
                }
            }
        }

        NamespacedKey pathwayKey = AdventureUtil.getCoINamespacedKey("pathway");
        if (pathwayKey != null && container.has(pathwayKey)) {
            InventoryView view = event.getView();
            event.setCancelled(!(view.getType() == InventoryType.CRAFTING || view.getType() == InventoryType.CHEST || view.getType() == InventoryType.ENDER_CHEST || view.getType() == InventoryType.SHULKER_BOX || view.getType() == InventoryType.BARREL));
        }
    }

    @EventHandler
    public void onInventoryInteraction(InventoryClickEvent event) {
        if (removeForbiddenSickleFromClick(event)) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null) return;
        if (item.getType() == Material.AIR) return;
        if (event.getWhoClicked() instanceof Player player) {
            PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            NamespacedKey abilityCostKey = AdventureUtil.getCoINamespacedKey("abilityCost");
            NamespacedKey shortcutKey = AdventureUtil.getCoINamespacedKey("shortcut");
            if (abilityCostKey != null && shortcutKey != null) {
                if (container.has(abilityCostKey) || container.has(shortcutKey)) {
                    if (item.getAmount() > 1) {
                        item.setAmount(1);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onMysticalItemPlacement(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();
            if (item == null) return;
            if (item.getType() == Material.AIR) return;

            if (item.getType() == Material.PLAYER_HEAD) {
                PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
                NamespacedKey pathwayKey = AdventureUtil.getCoINamespacedKey("pathway");
                if (pathwayKey != null && container.has(pathwayKey)) {
                    event.setCancelled(true);
                }
            } else {
                PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
                NamespacedKey ingredientKey = AdventureUtil.getCoINamespacedKey("ingredient");
                if (ingredientKey != null && container.has(ingredientKey)) {
                    event.setCancelled(true);
                }

                NamespacedKey wormOfSpiritKey = AdventureUtil.getCoINamespacedKey("worm-of-spirit");
                if (wormOfSpiritKey != null && container.has(wormOfSpiritKey)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (removeForbiddenSickleFromClick(event)) {
            return;
        }

        if (event.getView().getType() == InventoryType.CRAFTING) {
            if (event.getSlotType() == InventoryType.SlotType.CRAFTING) {
                if (event.getAction() == InventoryAction.PLACE_ALL || event.getAction() == InventoryAction.PLACE_ONE || event.getAction() == InventoryAction.PLACE_SOME) {
                    ItemStack item = event.getCursor();
                    if (item.getType() == Material.AIR) return;
                    if (item.getType() == Material.WRITTEN_BOOK) return;
                    if (item.hasItemMeta()) {
                        event.setCancelled(true);
                    }
                } else if (event.getAction() == InventoryAction.UNKNOWN || event.getAction() == InventoryAction.HOTBAR_SWAP || event.getAction() == InventoryAction.SWAP_WITH_CURSOR || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY || event.getAction() == InventoryAction.CLONE_STACK) {
                    event.setCancelled(true);
                }

                if (event.getAction() == InventoryAction.NOTHING) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPouchItemDrop(InventoryClickEvent event) {
        if (removeForbiddenSickleFromClick(event)) {
            return;
        }

        if (event.getCurrentItem() == null) return;
        if (event.getCursor().getType() == Material.AIR) return;

        if (Tag.ITEMS_BUNDLES.isTagged(event.getCurrentItem().getType())) {
            if (checkForMysticalAlignment(event.getCursor())) {
                event.setCancelled(true);
            }
        } else if (Tag.ITEMS_BUNDLES.isTagged(event.getCursor().getType())) {
            if (checkForMysticalAlignment(event.getCurrentItem())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPreItemCraft(PrepareItemCraftEvent event) {
        ItemStack[] ingredients = event.getInventory().getMatrix();
        for (ItemStack ingredient : ingredients) {
            if (ingredient == null) continue;
            if (ingredient.getType() == Material.AIR) continue;
            PersistentDataContainer container = ingredient.getItemMeta().getPersistentDataContainer();

            NamespacedKey abilityCostKey = AdventureUtil.getCoINamespacedKey("abilityCost");
            NamespacedKey shortcutKey = AdventureUtil.getCoINamespacedKey("shortcut");
            if (abilityCostKey != null && shortcutKey != null) {
                if (container.has(abilityCostKey) || container.has(shortcutKey)) {
                    event.getInventory().setResult(new ItemStack(Material.AIR));
                }
            }

            if (ingredient.getType() != Material.WRITTEN_BOOK) {
                NamespacedKey pathwayKey = AdventureUtil.getCoINamespacedKey("pathway");
                if (pathwayKey != null && container.has(pathwayKey)) {
                    event.getInventory().setResult(new ItemStack(Material.AIR));
                }
            } else {
                if (!isOnlyNonAirItem(ingredients)) {
                    event.getInventory().setResult(new ItemStack(Material.AIR));
                }
            }

            NamespacedKey ingredientKey = AdventureUtil.getCoINamespacedKey("ingredient");
            if (ingredientKey != null && container.has(ingredientKey)) {
                event.getInventory().setResult(new ItemStack(Material.AIR));
            }
        }
    }

    @EventHandler
    public void onMysticalItemItemFrame(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (event.getPlayer().isOp()) return;
        if (entity instanceof ItemFrame) {
            ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
            ItemStack offHandItem = event.getPlayer().getInventory().getItemInOffHand();
            if (checkForMysticalAlignment(item) || checkForMysticalAlignment(offHandItem)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerThrowEnderPearl(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();
            if (item != null && item.getType() == Material.ENDER_PEARL) {
                PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
                NamespacedKey ingredientKey = AdventureUtil.getCoINamespacedKey("ingredient");
                if (ingredientKey != null && container.has(ingredientKey)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    private boolean checkForNonIngredientMysticalAlignment(ItemStack item) {
        if (item.getType() != Material.AIR) {
            PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            NamespacedKey abilityCostKey = AdventureUtil.getCoINamespacedKey("abilityCost");
            NamespacedKey shortcutKey = AdventureUtil.getCoINamespacedKey("shortcut");
            NamespacedKey fogOfHistoryKey = AdventureUtil.getCoINamespacedKey("fogOfHistory");
            if (abilityCostKey != null && shortcutKey != null && fogOfHistoryKey != null) {
                return container.has(abilityCostKey) || container.has(shortcutKey) || container.has(fogOfHistoryKey);
            }
        }
        return false;
    }

    private boolean isOnlyNonAirItem(ItemStack[] matrix) {
        int nonAirCount = 0;
        for (ItemStack item : matrix) {
            if (item != null && item.getType() != Material.AIR) {
                nonAirCount++;
                if (nonAirCount > 1) {
                    return false;
                }
            }
        }
        return nonAirCount == 1;
    }

    private boolean checkForMysticalAlignment(ItemStack item) {
        if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
            PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            NamespacedKey abilityCostKey = AdventureUtil.getCoINamespacedKey("abilityCost");
            NamespacedKey shortcutKey = AdventureUtil.getCoINamespacedKey("shortcut");
            NamespacedKey fogOfHistoryKey = AdventureUtil.getCoINamespacedKey("fogOfHistory");
            NamespacedKey pathwayKey = AdventureUtil.getCoINamespacedKey("pathway");
            NamespacedKey ingredientKey = AdventureUtil.getCoINamespacedKey("ingredient");
            NamespacedKey bloodDollKey = AdventureUtil.getCoINamespacedKey("blood-servant-doll");

            boolean hasMatch = false;
            if (abilityCostKey != null && container.has(abilityCostKey)) hasMatch = true;
            if (shortcutKey != null && container.has(shortcutKey)) hasMatch = true;
            if (fogOfHistoryKey != null && container.has(fogOfHistoryKey)) hasMatch = true;
            if (pathwayKey != null && container.has(pathwayKey)) hasMatch = true;
            if (ingredientKey != null && container.has(ingredientKey)) hasMatch = true;
            if (bloodDollKey != null && container.has(bloodDollKey)) hasMatch = true;
            return hasMatch;
        }
        return false;
    }

    private boolean containsDoll(ItemStack item) {
        return containsDoll(item, 0);
    }

    private boolean containsDoll(ItemStack item, int depth) {
        if (item == null || item.getType() == Material.AIR || depth > 8) return false;
        if (item.getType() == Material.PAPER && item.hasItemMeta()) {
            PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            NamespacedKey bloodDollKey = AdventureUtil.getCoINamespacedKey("blood-servant-doll");
            if (bloodDollKey != null && container.has(bloodDollKey)) {
                return true;
            }
        }
        if (item.getType() == Material.BUNDLE && item.hasItemMeta()) {
            org.bukkit.inventory.meta.BundleMeta bundleMeta = (org.bukkit.inventory.meta.BundleMeta) item.getItemMeta();
            if (bundleMeta.hasItems()) {
                for (ItemStack bundledItem : bundleMeta.getItems()) {
                    if (containsDoll(bundledItem, depth + 1)) return true;
                }
            }
        }
        return false;
    }

    private boolean isForbiddenInventoryItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return checkForMysticalAlignment(item) || containsDoll(item);
    }

    @EventHandler
    public void onMysticalItemInventoryMove(InventoryClickEvent event) {
        org.bukkit.inventory.InventoryView view = event.getView();
        if (view.getType() == InventoryType.CRAFTING || view.getType() == InventoryType.CREATIVE) {
            return;
        }

        if (removeForbiddenSickleFromClick(event)) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        ItemStack hotbar = null;
        if (event.getAction() == org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP || event.getAction() == org.bukkit.event.inventory.InventoryAction.HOTBAR_MOVE_AND_READD) {
            int button = event.getHotbarButton();
            if (button >= 0 && button < 9) {
                hotbar = view.getBottomInventory().getItem(button);
            }
        }

        if (isForbiddenInventoryItem(cursor) || isForbiddenInventoryItem(current) || isForbiddenInventoryItem(hotbar)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isForbiddenSickle(event.getOldCursor())) {
            return;
        }

        event.setCancelled(true);
        event.setCursor(null);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        boolean updated = cleanInventorySickles(event.getInventory());
        if (event.getPlayer() instanceof Player player) {
            updated |= cleanInventorySickles(player.getInventory());
            if (updated) {
                player.updateInventory();
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!isForbiddenSickle(event.getItemDrop().getItemStack())) {
            return;
        }

        event.setCancelled(true);
        event.getItemDrop().remove();
        event.getPlayer().updateInventory();
    }

    private void scrubForbiddenSickles(Player player) {
        try {
            boolean updated = cleanInventorySickles(player.getInventory());
            updated |= cleanInventorySickles(player.getEnderChest());

            if (updated) {
                player.updateInventory();
                PrettyLogger.info("Successfully removed forbidden sickles for player: " + player.getName());
            }
        } catch (Exception e) {
            PrettyLogger.debug("Error while cleaning sickles for player: " + player.getName() + " - " + e.getMessage());
        }
    }

    private boolean cleanInventorySickles(Inventory inventory) {
        boolean updated = false;

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            if (isForbiddenSickle(item)) {
                inventory.setItem(i, null);
                updated = true;
                continue;
            }

            if (removeForbiddenSicklesFromBundle(item)) {
                inventory.setItem(i, item);
                updated = true;
            }
        }

        return updated;
    }

    private boolean removeForbiddenSickleFromClick(InventoryClickEvent event) {
        boolean removed = false;

        ItemStack cursor = event.getCursor();
        if (isForbiddenSickle(cursor)) {
            event.setCursor(null);
            removed = true;
        } else if (removeForbiddenSicklesFromBundle(cursor)) {
            event.setCursor(cursor);
            removed = true;
        }

        ItemStack current = event.getCurrentItem();
        if (isForbiddenSickle(current)) {
            if (event.getClickedInventory() != null) {
                event.getClickedInventory().setItem(event.getSlot(), null);
            }
            removed = true;
        } else if (removeForbiddenSicklesFromBundle(current)) {
            if (event.getClickedInventory() != null) {
                event.getClickedInventory().setItem(event.getSlot(), current);
            }
            removed = true;
        }

        if (event.getAction() == InventoryAction.HOTBAR_SWAP || event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) {
            int hotbarButton = event.getHotbarButton();
            if (hotbarButton >= 0 && hotbarButton < 9) {
                Inventory bottomInventory = event.getView().getBottomInventory();
                ItemStack hotbar = bottomInventory.getItem(hotbarButton);
                if (isForbiddenSickle(hotbar)) {
                    bottomInventory.setItem(hotbarButton, null);
                    removed = true;
                } else if (removeForbiddenSicklesFromBundle(hotbar)) {
                    bottomInventory.setItem(hotbarButton, hotbar);
                    removed = true;
                }
            }
        }

        if (removed) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.updateInventory();
            }
        }

        return removed;
    }

    private boolean removeForbiddenSicklesFromBundle(ItemStack item) {
        if (item == null || item.getType() != Material.BUNDLE || !item.hasItemMeta()) {
            return false;
        }

        BundleMeta bundleMeta = (BundleMeta) item.getItemMeta();
        if (!bundleMeta.hasItems()) {
            return false;
        }

        List<ItemStack> keptItems = new ArrayList<>();
        boolean updated = false;
        for (ItemStack bundledItem : bundleMeta.getItems()) {
            if (isForbiddenSickle(bundledItem)) {
                updated = true;
                continue;
            }

            if (removeForbiddenSicklesFromBundle(bundledItem)) {
                updated = true;
            }

            keptItems.add(bundledItem);
        }

        if (updated) {
            bundleMeta.setItems(keptItems);
            item.setItemMeta(bundleMeta);
        }

        return updated;
    }

    private boolean isForbiddenSickle(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey namespacedKey = new NamespacedKey("vane", "custom_item_identifier");

        if (pdc.has(namespacedKey, PersistentDataType.STRING)) {
            String value = pdc.get(namespacedKey, PersistentDataType.STRING);
            return value != null && value.contains("sickle");
        }
        return false;
    }

}
