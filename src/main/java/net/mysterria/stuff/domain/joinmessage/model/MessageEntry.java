package net.mysterria.stuff.domain.joinmessage.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
public class MessageEntry {
    private final UUID uuid;
    private String name;
    private String join;
    private String quit;

    public boolean isPending() {
        return uuid == null;
    }

    public boolean isEmpty() {
        return join == null && quit == null;
    }
}
