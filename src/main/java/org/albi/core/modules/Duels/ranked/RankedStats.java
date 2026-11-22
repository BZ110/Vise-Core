package org.albi.core.modules.Duels.ranked;

import java.io.Serializable;

public class RankedStats implements Serializable {
    private static final long serialVersionUID = 1L;

    private int elo = 1000; // Starting Elo
    private int wins = 0;
    private int losses = 0;
    private int placementMatches = 0;
    public static int PLACEMENT_THRESHOLD = 5;

    public int getElo() {
        return elo;
    }

    public void setElo(int elo) {
        this.elo = elo;
    }

    public int getWins() {
        return wins;
    }

    public void incWins() {
        this.wins++;
    }

    public int getLosses() {
        return losses;
    }

    public void incLosses() {
        this.losses++;
    }

    public int getPlacementMatches() {
        return placementMatches;
    }

    public void incPlacementMatches() {
        this.placementMatches++;
    }

    public boolean isPlacementsFinished() {
        return placementMatches >= PLACEMENT_THRESHOLD;
    }

    public String getTierDisplay() {
        if (!isPlacementsFinished()) {
            return "§7Unranked (" + placementMatches + "/" + PLACEMENT_THRESHOLD + ")";
        }
        return RankedTier.getTierDisplay(elo);
    }
}
