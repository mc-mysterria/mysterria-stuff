package net.mysterria.stuff.features.chat;

public interface ChatAliasIntegration extends AutoCloseable {

    void reload();

    /**
     * Whether this raw chat line ({@code !<alias> ...}) is one of the typed shortcuts, so a relay
     * such as DiscordSRV can leave it out of public channels.
     */
    boolean isShortcut(String rawMessage);

    @Override
    void close();
}
