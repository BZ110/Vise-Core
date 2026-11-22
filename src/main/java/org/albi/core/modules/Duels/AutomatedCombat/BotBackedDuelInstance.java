package org.albi.core.modules.Duels.AutomatedCombat;

import org.albi.core.Core;
import org.albi.core.modules.Duels.DuelInstance;
import org.albi.core.modules.Duels.helpers.Arena;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class BotBackedDuelInstance extends DuelInstance {

    private final BotSession session;
    private final Arena arena;
    private final Location motherLocation;

    public BotBackedDuelInstance(Core plugin, BotSession session, Arena arena, Location motherLocation) {
        super(plugin);
        this.session = session;
        this.arena = arena;
        this.motherLocation = motherLocation;
    }


    public BotSession getSession() {
        return session;
    }

    @Override
    public void death(Player p, Player killer) {
        session.handlePlayerDeath();
    }

    @Override
    public void leave(Player p) {
        session.handlePlayerLeave();
    }

    @Override
    public void disconnect(Player p) {
        session.handlePlayerLeave();
    }

    @Override
    public Arena getArena() {
        return arena;
    }

    @Override
    public Location getMotherLocation() {
        return motherLocation;
    }
}
