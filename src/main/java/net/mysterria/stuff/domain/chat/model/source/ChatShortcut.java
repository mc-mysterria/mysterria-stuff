package net.mysterria.stuff.domain.chat.model.source;

import lombok.Getter;

import java.util.List;

@Getter
public enum ChatShortcut {
    CHURCH("church", "cc", "<message>", "c", "cc", "church"),
    ORGANIZATION("organization", "oc", "<message>", "o", "oc", "org", "order"),
    LINEAGE("lineage", "coi lineage chat", "<message>", "lin", "lineage", "family"),
    LANDS("lands", "lands chat", "<message>", "l", "land", "lands"),
    NATIONS("nations", "nations chat", "<message>", "n", "nation", "nations"),
    PARTY("party", "p", "<message>", "p", "party", "dp", "dparty"),
    STAFF("staff", "staffchat", "<message>", "s", "sc", "staff"),
    PRIVATE_MESSAGE("message", "msg", "<player> <message>", "m", "msg", "dm", "pm", "w", "whisper", "tell"),
    REPLY("reply", "reply", "<message>", "r", "reply");

    private final String routeId;
    private final String providerCommand;
    private final String argumentHint;
    private final List<String> defaultAliases;

    ChatShortcut(String routeId, String providerCommand, String argumentHint, String... defaultAliases) {
        this.routeId = routeId;
        this.providerCommand = providerCommand;
        this.argumentHint = argumentHint;
        this.defaultAliases = List.of(defaultAliases);
    }
}
