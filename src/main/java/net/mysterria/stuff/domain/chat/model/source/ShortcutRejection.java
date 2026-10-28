package net.mysterria.stuff.domain.chat.model.source;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.mysterria.stuff.domain.chat.model.ParsedShortcut;

public enum ShortcutRejection {

    MISSING_ARGUMENTS {
        @Override
        public Component describe(ParsedShortcut parsed) {
            return Component.text("Usage: !" + parsed.getTypedAlias() + " "
                    + parsed.getShortcut().getArgumentHint(), NamedTextColor.RED);
        }
    },

    UNSUPPORTED_CHARACTERS {
        @Override
        public Component describe(ParsedShortcut parsed) {
            return Component.text("!" + parsed.getTypedAlias()
                    + " was not sent: the message contains unsupported characters.", NamedTextColor.RED);
        }
    };

    public abstract Component describe(ParsedShortcut parsed);
}
