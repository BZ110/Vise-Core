package org.albi.core.modules.Duels.helpers;

import org.albi.core.modules.Duels.DuelInstance;
import org.bukkit.entity.Player;

public final class PlayerDuelInstance {
    Player player;
    String status;
    DuelInstance duelInstance;
    Kit kit;
    Player[] teammates;
    Player[] opponents;
    FlexibleInventory flexibleInventory;
    int team;

    int mendingRng;

    public void clear() {
        this.player = null;
        this.status = null;
        this.duelInstance = null;
        this.kit = null;
        this.mendingRng = 0;
    }

    public String getStatus() {
        return status;
    }

    public Player getPlayer() {
        return player;
    }

    public Player[] getTeammates() {
        return teammates;
    }

    public Player[] getOpponents() {
        return opponents;
    }

    public FlexibleInventory getFlexibleInventory() {
        return flexibleInventory;
    }

    public Kit getKit() {
        return kit;
    }

    public int getTeam() {
        return team;
    }

    public DuelInstance getDuelInstance() {
        return duelInstance;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setDuelInstance(DuelInstance duelInstance) {
        this.duelInstance = duelInstance;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public void setFlexibleInventory(FlexibleInventory flexibleInventory) {
        this.flexibleInventory = flexibleInventory;
    }

    public void setTeam(int team) {
        this.team = team;
    }

    public void setKit(Kit kit) {
        this.kit = kit;
    }

    public void setOpponents(Player[] opponents) {
        this.opponents = opponents;
    }

    public void setTeammates(Player[] teammates) {
        this.teammates = teammates;
    }

    public int getMendingRng() {
        return mendingRng;
    }

    public void setMendingRng(int mendingRng) {
        this.mendingRng = mendingRng;
    }
}
