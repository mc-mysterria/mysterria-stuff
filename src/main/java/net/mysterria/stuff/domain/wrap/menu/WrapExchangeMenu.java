package net.mysterria.stuff.domain.wrap.menu;

import de.skyslycer.hmcwraps.HMCWraps;
import de.skyslycer.hmcwraps.serialization.wrap.Wrap;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.mysterria.stuff.config.ConfigManager;
import net.mysterria.stuff.domain.wrap.service.WrapCategoryService;
import net.mysterria.stuff.util.item.ItemDelivery;
import net.mysterria.stuff.util.item.MenuItems;
import net.mysterria.stuff.util.item.TokenItem;
import net.mysterria.stuff.util.log.PrettyLogger;
import net.mysterria.stuff.util.text.AdventureUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RequiredArgsConstructor
public class WrapExchangeMenu {

    private static final NamespacedKey WRAPPER_KEY = new NamespacedKey("hmcwraps", "wrapper");

    private final Plugin plugin;
    private final HMCWraps hmcWraps;
    private final ConfigManager config;
    private final TokenItem token;
    private final WrapCategoryService categories;

    public void openCategories(Player player) {
        Map<String, List<Wrap>> byCategory = categories.categorize(hmcWraps.getWrapsLoader().getWraps());
        if (byCategory.isEmpty()) {
            player.sendMessage(token.message("no-wraps-available"));
            return;
        }

        PaginatedGui gui = paginated(config.getWrapMenuTitles().getMain());
        byCategory.forEach((category, wraps) -> gui.addItem(new GuiItem(
                MenuItems.button(categoryIcon(category), category, NamedTextColor.GOLD,
                        Component.text(wraps.size() + " wraps", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click to browse", NamedTextColor.YELLOW)),
                event -> openCategory(player, category, wraps))));
        addFooter(gui, player);
        gui.open(player);
    }

    private void openCategory(Player player, String category, List<Wrap> wraps) {
        PaginatedGui gui = paginated(config.getWrapMenuTitles().getCategory().replace("{category}", category));
        Runnable reopen = () -> openCategory(player, category, wraps);
        for (Wrap wrap : wraps) {
            gui.addItem(new GuiItem(displayItem(player, wrap), event -> {
                if (event.isLeftClick()) {
                    preview(player, wrap, reopen);
                } else if (event.isRightClick()) {
                    openConfirm(player, wrap, reopen);
                }
            }));
        }
        gui.setItem(6, 1, new GuiItem(
                MenuItems.button(Material.DARK_OAK_DOOR, "Back to categories", NamedTextColor.YELLOW),
                event -> openCategories(player)));
        addFooter(gui, player);
        gui.open(player);
    }

    private void openConfirm(Player player, Wrap wrap, Runnable back) {
        ItemStack wrapper = createWrapper(player, wrap);
        if (wrapper == null) {
            return;
        }

        Gui gui = Gui.gui()
                .title(AdventureUtil.parseUniversal(config.getWrapMenuTitles().getConfirm()))
                .rows(4)
                .disableAllInteractions()
                .create();
        gui.getFiller().fill(new GuiItem(MenuItems.filler()));
        gui.setItem(13, new GuiItem(wrapper));

        GuiItem confirm = new GuiItem(MenuItems.button(Material.LIME_STAINED_GLASS_PANE, "✓ Exchange", NamedTextColor.GREEN,
                Component.text("Costs 1 Universal Wrap Token", NamedTextColor.GRAY),
                Component.text("You have " + token.count(player), NamedTextColor.GRAY)),
                event -> {
                    gui.close(player);
                    exchange(player, wrap, wrapper);
                });
        GuiItem cancel = new GuiItem(MenuItems.button(Material.RED_STAINED_GLASS_PANE, "✗ Back", NamedTextColor.RED,
                Component.text("Return without using a token", NamedTextColor.GRAY)),
                event -> {
                    player.sendMessage(token.message("exchange-cancelled"));
                    back.run();
                });
        for (int slot : List.of(10, 11, 12, 19, 20, 21)) {
            gui.setItem(slot, confirm);
        }
        for (int slot : List.of(14, 15, 16, 23, 24, 25)) {
            gui.setItem(slot, cancel);
        }
        gui.open(player);
    }

    private void exchange(Player player, Wrap wrap, ItemStack wrapper) {
        if (!token.takeOne(player)) {
            player.sendMessage(token.message("no-token"));
            return;
        }
        if (ItemDelivery.give(player, wrapper)) {
            player.sendMessage(Component.text("Your inventory is full, so the wrapper was dropped at your feet.", NamedTextColor.YELLOW));
        }
        player.sendMessage(token.message("wrap-exchanged", "wrap", wrap.getName()));
        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        PrettyLogger.info(player.getName() + " exchanged a Universal Token for wrap " + wrap.getWrapName());
    }

    private void preview(Player player, Wrap wrap, Runnable reopen) {
        player.closeInventory();
        player.sendMessage(token.message("preview-started", "wrap", wrap.getName()));
        hmcWraps.getPreviewManager().create(player,
                previewer -> plugin.getServer().getScheduler().runTaskLater(plugin, reopen, 5L), wrap);
    }

    private ItemStack createWrapper(Player player, Wrap wrap) {
        String wrapId = wrapId(wrap);
        if (wrap.getPhysical() == null || wrapId == null) {
            player.sendMessage(Component.text("This wrap can't be exchanged right now. Please tell staff: " + wrap.getWrapName(), NamedTextColor.RED));
            PrettyLogger.warn("Wrap " + wrap.getWrapName() + " has no physical item or id");
            return null;
        }
        try {
            ItemStack wrapper = wrap.getPhysical().toItem(hmcWraps, player);
            wrapper.editMeta(meta -> meta.getPersistentDataContainer().set(WRAPPER_KEY, PersistentDataType.STRING, wrapId));
            return wrapper;
        } catch (RuntimeException e) {
            player.sendMessage(Component.text("This wrap can't be exchanged right now. Please tell staff: " + wrap.getWrapName(), NamedTextColor.RED));
            PrettyLogger.warn("Could not build wrapper for " + wrap.getWrapName() + ": " + e.getMessage());
            return null;
        }
    }

    private String wrapId(Wrap wrap) {
        return hmcWraps.getWrapsLoader().getWraps().entrySet().stream()
                .filter(entry -> entry.getValue() == wrap)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private ItemStack displayItem(Player player, Wrap wrap) {
        ItemStack item = hmcWraps.getWrapper().setWrap(wrap, new ItemStack(Material.PAPER), true, player);
        item.editMeta(meta -> {
            if (wrap.getName() != null) {
                meta.displayName(AdventureUtil.parseItemText(wrap.getName()));
            }
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            lore.add(Component.text("Left-click to preview", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Right-click to exchange", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        return item;
    }

    private PaginatedGui paginated(String title) {
        return Gui.paginated()
                .title(AdventureUtil.parseUniversal(title))
                .rows(6)
                .pageSize(45)
                .disableAllInteractions()
                .create();
    }

    private void addFooter(PaginatedGui gui, Player player) {
        gui.setItem(6, 5, new GuiItem(MenuItems.button(Material.PAPER, "Universal Wrap Tokens: " + token.count(player), NamedTextColor.GOLD,
                Component.text("Each exchange uses one token", NamedTextColor.GRAY))));
        if (gui.getPagesNum() > 1) {
            gui.setItem(6, 3, new GuiItem(MenuItems.button(Material.ARROW, "← Previous page", NamedTextColor.YELLOW), event -> gui.previous()));
            gui.setItem(6, 7, new GuiItem(MenuItems.button(Material.ARROW, "Next page →", NamedTextColor.YELLOW), event -> gui.next()));
        }
    }

    private Material categoryIcon(String category) {
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "helmets" -> Material.DIAMOND_HELMET;
            case "chestplates" -> Material.DIAMOND_CHESTPLATE;
            case "leggings" -> Material.DIAMOND_LEGGINGS;
            case "boots" -> Material.DIAMOND_BOOTS;
            case "swords" -> Material.DIAMOND_SWORD;
            case "axes" -> Material.DIAMOND_AXE;
            case "pickaxes" -> Material.DIAMOND_PICKAXE;
            case "shovels" -> Material.DIAMOND_SHOVEL;
            case "hoes" -> Material.DIAMOND_HOE;
            case "bows" -> Material.BOW;
            case "crossbows" -> Material.CROSSBOW;
            case "tridents" -> Material.TRIDENT;
            case "shields" -> Material.SHIELD;
            case "elytra" -> Material.ELYTRA;
            default -> Material.PAPER;
        };
    }
}
