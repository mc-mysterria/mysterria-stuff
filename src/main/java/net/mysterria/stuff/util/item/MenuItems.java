package net.mysterria.stuff.util.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

public class MenuItems {

    public static ItemStack button(Material material, String name, TextColor color, Component... lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
            meta.lore(Arrays.stream(lore).map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        });
        return item;
    }

    public static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        item.editMeta(meta -> meta.setHideTooltip(true));
        return item;
    }
}
