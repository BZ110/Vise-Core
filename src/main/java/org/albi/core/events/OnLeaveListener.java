package org.albi.core.events;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.Duels.DuelInstance;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class OnLeaveListener implements Listener {

    private final Core plugin;

    public OnLeaveListener(Core plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        ExtendedPlayer ep = plugin.getCache().getExtendedPlayer(event.getPlayer());
        if (ep == null) return;

        event.setQuitMessage(null);

        // Handle duels first if needed
        if (ep.getPlayerDuelInstance() != null) {
            DuelInstance di = ep.getPlayerDuelInstance().getDuelInstance();
            if (di != null) {
                di.leave(event.getPlayer());
            }
        }

        ep.freePlayer(); // Save + cleanup
        plugin.getCache().removeExtendedPlayer(event.getPlayer()); // Final cleanup
    }

}
