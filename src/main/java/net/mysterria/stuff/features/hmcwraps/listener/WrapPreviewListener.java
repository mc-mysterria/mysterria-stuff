package net.mysterria.stuff.features.hmcwraps.listener;

import de.skyslycer.hmcwraps.HMCWraps;
import de.skyslycer.hmcwraps.serialization.wrap.Wrap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.mysterria.stuff.features.hmcwraps.UniversalTokenManager;
import net.mysterria.stuff.utils.AdventureUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles wrap preview functionality using HMCWraps preview system
 */
public class WrapPreviewListener implements Listener {

    private final UniversalTokenManager manager;
    private final HMCWraps hmcWraps;
    private final Map<UUID, PreviewSession> activePreviews;

    public WrapPreviewListener(HMCWraps hmcWraps) {
        this.manager = UniversalTokenManager.getInstance();
        this.hmcWraps = hmcWraps;
        this.activePreviews = new HashMap<>();
    }

    /**
     * Start a preview session for a player
     * @param player The player
     * @param wrap The wrap to preview
     * @param returnGui Runnable to reopen the GUI after preview
     */
    public void startPreview(Player player, Wrap wrap, Runnable returnGui) {
        UUID playerId = player.getUniqueId();

        activePreviews.put(playerId, new PreviewSession(wrap, returnGui));

        String wrapName = wrap.getName();
        String legacyWrapName = AdventureUtil.convertMiniMessageToLegacy(wrapName);

        player.sendMessage(manager.getMessage("preview-started", "wrap", legacyWrapName));

        hmcWraps.getPreviewManager().create(player, this::endPreview, wrap);
    }

    /**
     * End preview for a player and reopen the GUI
     */
    public void endPreview(Player player) {
        UUID playerId = player.getUniqueId();
        PreviewSession session = activePreviews.remove(playerId);

        if (session != null && session.returnGui != null) {
            Bukkit.getScheduler().runTaskLater(manager.getPlugin(), session.returnGui, 5L);
        }
    }

    private record PreviewSession(Wrap wrap, Runnable returnGui) {}
}
