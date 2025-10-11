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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.*;

public class DangerousActionsListener implements Listener {


    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
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
            // Only keep inventory if configured
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
        // Only reset attributes if configured
        if (!MysterriaStuff.getInstance().getConfigManager().isResetAttributesOnJoin()) {
            return;
        }

        Player player = event.getPlayer();
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

        PrettyLogger.debug("Completed attribute reset for player: " + player.getName());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getDrops() == null) return;

        try {
            List<ItemStack> itemsToRemove = new ArrayList<>();
            for (ItemStack item : event.getDrops()) {
                if (checkForNonIngredientMysticalAlignment(item)) {
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
            if (container.has(AdventureUtil.getCoINamespacedKey("abilitycost")) || container.has(AdventureUtil.getCoINamespacedKey("shortcut"))) {
                event.setCancelled(true);
            }

            if (container.has(AdventureUtil.getCoINamespacedKey("pathway"))) {
                event.setCancelled(true);
            }

            if (container.has(AdventureUtil.getCoINamespacedKey("ingredient"))) {
                event.setCancelled(true);
            }

//            for (SavantItem savantItem : SavantItemRegistry.getAllItems()) {
//                if (container.has(AdventureUtil.getCoINamespacedKey("savant_" + savantItem.getItemId()))) {
//                    event.setCancelled(true);
//                }
//            }
        }
    }

    @EventHandler
    public void onItemMoveEvent(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null) return;
        if (item.getType() == Material.AIR) return;

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();

        if (container.has(AdventureUtil.getCoINamespacedKey("abilitycost")) || container.has(AdventureUtil.getCoINamespacedKey("shortcut")) || container.has(AdventureUtil.getCoINamespacedKey("fogOfHistory"))) {
            InventoryView view = event.getView();
            if (view.getType() != InventoryType.CRAFTING) {
                event.setCancelled(true);
            }
        } else if (container.has(AdventureUtil.getCoINamespacedKey("pathway"))) {
            InventoryView view = event.getView();
            event.setCancelled(!(view.getType() == InventoryType.CRAFTING || view.getType() == InventoryType.CHEST || view.getType() == InventoryType.ENDER_CHEST || view.getType() == InventoryType.SHULKER_BOX || view.getType() == InventoryType.BARREL));
        }
    }

    @EventHandler
    public void onInventoryInteraction(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null) return;
        if (item.getType() == Material.AIR) return;
        if (event.getWhoClicked() instanceof Player player) {
            PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            if (container.has(AdventureUtil.getCoINamespacedKey("abilitycost")) || container.has(AdventureUtil.getCoINamespacedKey("shortcut"))) {
                if (item.getAmount() > 1) {
                    item.setAmount(1);
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
                if (container.has(AdventureUtil.getCoINamespacedKey("pathway"))) {
                    event.setCancelled(true);
                }
            } else {
                PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
                if (container.has(AdventureUtil.getCoINamespacedKey("ingredient"))) {
                    event.setCancelled(true);
                }

                if (container.has(AdventureUtil.getCoINamespacedKey("worm-of-spirit"))) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
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
            if (container.has(AdventureUtil.getCoINamespacedKey("abilitycost")) || container.has(AdventureUtil.getCoINamespacedKey("shortcut"))) {
                event.getInventory().setResult(new ItemStack(Material.AIR));
            }

            if (ingredient.getType() != Material.WRITTEN_BOOK) {
                if (container.has(AdventureUtil.getCoINamespacedKey("pathway"))) {
                    event.getInventory().setResult(new ItemStack(Material.AIR));
                }
            } else {
                if (!isOnlyNonAirItem(ingredients)) {
                    event.getInventory().setResult(new ItemStack(Material.AIR));
                }
            }

            if (container.has(AdventureUtil.getCoINamespacedKey("ingredient"))) {
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
                if (container.has(AdventureUtil.getCoINamespacedKey("ingredient"))) {
                    event.setCancelled(true);
                }
            }
        }
    }

    private boolean checkForNonIngredientMysticalAlignment(ItemStack item) {
        if (item.getType() != Material.AIR) {
            PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            return container.has(AdventureUtil.getCoINamespacedKey("abilitycost")) || container.has(AdventureUtil.getCoINamespacedKey("shortcut")) || container.has(AdventureUtil.getCoINamespacedKey("fogOfHistory"));
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
        if (item.getType() != Material.AIR) {
            PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            return container.has(AdventureUtil.getCoINamespacedKey("abilitycost")) || container.has(AdventureUtil.getCoINamespacedKey("shortcut")) || container.has(AdventureUtil.getCoINamespacedKey("fogOfHistory")) || container.has(AdventureUtil.getCoINamespacedKey("pathway")) || container.has(AdventureUtil.getCoINamespacedKey("ingredient"));
        }
        return false;
    }

}
