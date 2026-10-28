package net.mysterria.stuff.domain.chat.type;

public interface ChatShortcutModule extends AutoCloseable {

    void reload();

    @Override
    void close();
}
