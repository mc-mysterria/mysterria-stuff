package net.mysterria.stuff.domain.chat.model;

import lombok.Data;
import net.mysterria.stuff.domain.chat.model.source.ChatShortcut;
import net.mysterria.stuff.domain.chat.model.source.ShortcutRejection;

@Data
public class ParsedShortcut {
    private final ChatShortcut shortcut;
    private final String typedAlias;
    private final String payload;
    private final ShortcutRejection rejection;
}
