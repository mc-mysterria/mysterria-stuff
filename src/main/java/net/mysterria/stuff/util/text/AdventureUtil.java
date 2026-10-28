package net.mysterria.stuff.util.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

import java.util.Map;

public class AdventureUtil {

    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"),
            Map.entry('f', "white"), Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"), Map.entry('o', "italic"),
            Map.entry('r', "reset")
    );

    private static final MiniMessage STYLE_ONLY = MiniMessage.builder()
            .tags(TagResolver.resolver(StandardTags.color(), StandardTags.decorations(),
                    StandardTags.gradient(), StandardTags.rainbow(), StandardTags.reset()))
            .build();

    public static Component parseUniversal(String text) {
        return MiniMessage.miniMessage().deserialize(legacyToMiniMessageTags(text));
    }

    public static Component parsePlayerText(String text) {
        return STYLE_ONLY.deserialize(legacyToMiniMessageTags(text));
    }

    public static Component parseItemText(String text) {
        return parseUniversal(text).decoration(TextDecoration.ITALIC, false);
    }

    private static String legacyToMiniMessageTags(String text) {
        StringBuilder result = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                char next = Character.toLowerCase(text.charAt(i + 1));
                if (next == '#' && i + 7 < text.length() && text.substring(i + 2, i + 8).matches("[0-9a-fA-F]{6}")) {
                    result.append("<#").append(text, i + 2, i + 8).append('>');
                    i += 8;
                    continue;
                }
                String tag = LEGACY_TAGS.get(next);
                if (tag != null) {
                    result.append('<').append(tag).append('>');
                    i += 2;
                    continue;
                }
            }
            result.append(c);
            i++;
        }
        return result.toString();
    }
}
