package net.mysterria.stuff.config.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.mysterria.stuff.util.text.AdventureUtil;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class TokenSettings {
    private String itemName;
    private List<String> itemLore;
    private Map<String, String> messages;

    public Component message(String key, String... placeholders) {
        String text = messages.get(key);
        if (text == null) {
            throw new IllegalStateException("Missing token message: " + key);
        }
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            text = text.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return AdventureUtil.parseItemText(text);
    }
}
