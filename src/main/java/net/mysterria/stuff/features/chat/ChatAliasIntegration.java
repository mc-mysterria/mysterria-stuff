package net.mysterria.stuff.features.chat;

public interface ChatAliasIntegration extends AutoCloseable {

    void reload();

    boolean routesCohortShortcut(String message);

    @Override
    void close();
}
