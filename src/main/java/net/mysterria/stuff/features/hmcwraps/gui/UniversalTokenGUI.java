package net.mysterria.stuff.features.hmcwraps.gui;

import de.skyslycer.hmcwraps.HMCWraps;
import de.skyslycer.hmcwraps.serialization.wrap.Wrap;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.mysterria.stuff.features.hmcwraps.UniversalTokenManager;
import net.mysterria.stuff.features.hmcwraps.WrapCategoryMapper;
import net.mysterria.stuff.features.hmcwraps.listener.WrapPreviewListener;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;


public class UniversalTokenGUI {

    private final UniversalTokenManager manager;
    private final WrapConfirmationGUI confirmationGUI;
    private final WrapPreviewListener previewHandler;
    private final MiniMessage miniMessage;
    private final WrapCategoryMapper categoryMapper;

    public UniversalTokenGUI(Plugin plugin, WrapPreviewListener previewHandler) {
        this.manager = UniversalTokenManager.getInstance();
        this.confirmationGUI = new WrapConfirmationGUI();
        this.previewHandler = previewHandler;
        this.miniMessage = MiniMessage.miniMessage();
        this.categoryMapper = new WrapCategoryMapper(plugin);
    }


    public void openMainGUI(Player player, HMCWraps hmcWraps) {
        String title = manager.getConfigManager().getGuiMainTitle();
        Component titleComponent = miniMessage.deserialize(title);

        Map<String, Wrap> allWraps = hmcWraps.getWrapsLoader().getWraps();

        if (allWraps.isEmpty()) {
            player.sendMessage(manager.getMessage("no-wraps-available"));
            return;
        }

        Map<String, List<Wrap>> categorizedWraps = categorizeWraps(allWraps);

        Gui gui = Gui.gui()
                .title(titleComponent)
                .rows(5)
                .disableAllInteractions()
                .create();

        int[] slots = {10, 12, 14, 16, 31};
        int slotIndex = 0;
        for (Map.Entry<String, List<Wrap>> entry : categorizedWraps.entrySet()) {
            if (slotIndex >= slots.length) break;

            String category = entry.getKey();
            List<Wrap> wraps = entry.getValue();

            ItemStack categoryItem = createCategoryItem(category, wraps.size());
            GuiItem guiItem = new GuiItem(categoryItem, event -> {
                event.setCancelled(true);
                openCategoryGUI(player, hmcWraps, category, wraps);
            });

            gui.setItem(slots[slotIndex], guiItem);
            slotIndex++;
        }

        gui.open(player);
    }


    private void openCategoryGUI(Player player, HMCWraps hmcWraps, String category, List<Wrap> wraps) {
        String title = manager.getConfigManager().getGuiCategoryTitle()
                .replace("{category}", category);
        Component titleComponent = miniMessage.deserialize(title);

        PaginatedGui gui = Gui.paginated()
                .title(titleComponent)
                .rows(6)
                .pageSize(45)
                .disableAllInteractions()
                .create();

        for (Wrap wrap : wraps) {
            ItemStack wrapItem = createWrapDisplayItem(wrap, hmcWraps, player);

            GuiItem guiItem = new GuiItem(wrapItem, event -> {
                event.setCancelled(true);

                if (event.isLeftClick()) {
                    handlePreview(player, wrap, hmcWraps, () -> openCategoryGUI(player, hmcWraps, category, wraps));
                } else if (event.isRightClick()) {
                    confirmationGUI.open(player, wrap, hmcWraps, () -> openCategoryGUI(player, hmcWraps, category, wraps));
                }
            });

            gui.addItem(guiItem);
        }

        ItemStack previousItem = new ItemStack(Material.ARROW);
        ItemMeta previousMeta = previousItem.getItemMeta();
        if (previousMeta != null) {
            previousMeta.displayName(Component.text("← Previous Page", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            previousItem.setItemMeta(previousMeta);
        }
        gui.setItem(6, 3, new GuiItem(previousItem, event -> {
            event.setCancelled(true);
            gui.previous();
        }));

        ItemStack nextItem = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = nextItem.getItemMeta();
        if (nextMeta != null) {
            nextMeta.displayName(Component.text("Next Page →", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            nextItem.setItemMeta(nextMeta);
        }
        gui.setItem(6, 5, new GuiItem(nextItem, event -> {
            event.setCancelled(true);
            gui.next();
        }));

        ItemStack backItem = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backItem.getItemMeta();
        if (backMeta != null) {
            backMeta.displayName(Component.text("← Back to Categories", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            backItem.setItemMeta(backMeta);
        }
        gui.setItem(6, 1, new GuiItem(backItem, event -> {
            event.setCancelled(true);
            openMainGUI(player, hmcWraps);
        }));

        gui.open(player);
    }


    private void handlePreview(Player player, Wrap wrap, HMCWraps hmcWraps, Runnable returnGui) {
        player.closeInventory();

        previewHandler.startPreview(player, wrap, returnGui);
    }


    private Map<String, List<Wrap>> categorizeWraps(Map<String, Wrap> allWraps) {
        return categoryMapper.categorizeWraps(allWraps);
    }


    private ItemStack createCategoryItem(String category, int count) {
        Material material = getCategoryMaterial(category);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(category, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(Arrays.asList(
                    Component.text(""),
                    Component.text(count + " wrap(s) available", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text(""),
                    Component.text("Click to browse!", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
            ));
            item.setItemMeta(meta);
        }

        return item;
    }


    private Material getCategoryMaterial(String category) {
        return switch (category.toLowerCase()) {
            case "helmets" -> Material.DIAMOND_HELMET;
            case "chestplates" -> Material.DIAMOND_CHESTPLATE;
            case "leggings" -> Material.DIAMOND_LEGGINGS;
            case "boots" -> Material.DIAMOND_BOOTS;
            case "swords" -> Material.DIAMOND_SWORD;
            case "axes" -> Material.DIAMOND_AXE;
            case "bows" -> Material.BOW;
            case "crossbows" -> Material.CROSSBOW;
            case "tridents" -> Material.TRIDENT;
            case "pickaxes" -> Material.DIAMOND_PICKAXE;
            case "shovels" -> Material.DIAMOND_SHOVEL;
            case "hoes" -> Material.DIAMOND_HOE;
            case "shields" -> Material.SHIELD;
            case "elytra" -> Material.ELYTRA;
            case "other" -> Material.PAPER;
            default -> Material.PAPER;
        };
    }


    private ItemStack createWrapDisplayItem(Wrap wrap, HMCWraps hmcWraps, Player player) {
        ItemStack wrapItem = new ItemStack(Material.PAPER);

        wrapItem = hmcWraps.getWrapper().setWrap(wrap, wrapItem, true, player);

        ItemMeta meta = wrapItem.getItemMeta();

        if (meta != null) {
            if (wrap.getName() != null) {
                meta.displayName(MiniMessage.miniMessage().deserialize(wrap.getName()).decoration(TextDecoration.ITALIC, false));
            }
            List<Component> lore = meta.lore() != null ? new ArrayList<>(Objects.requireNonNull(meta.lore())) : new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("Left-click to preview", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Right-click to exchange", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            wrapItem.setItemMeta(meta);
        }

        return wrapItem;
    }
}
