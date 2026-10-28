package net.mysterria.stuff.domain.joinmessage.listener;

import lombok.RequiredArgsConstructor;
import net.mysterria.stuff.domain.joinmessage.service.JoinMessageStore;
import net.mysterria.stuff.util.text.AdventureUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@RequiredArgsConstructor
public class JoinQuitMessageListener implements Listener {

    private final JoinMessageStore store;

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String firstJoin = store.firstJoinMessageFor(player);
        if (!player.hasPlayedBefore() && firstJoin != null) {
            event.joinMessage(AdventureUtil.parseUniversal(firstJoin));
            return;
        }
        String message = store.resolveJoinMessage(player);
        event.joinMessage(message == null ? null : AdventureUtil.parsePlayerText(message));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        String message = store.resolveQuitMessage(event.getPlayer());
        event.quitMessage(message == null ? null : AdventureUtil.parsePlayerText(message));
    }
}
