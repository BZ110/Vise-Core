package org.albi.core.modules.Duels.ranked;

public class EloCalculator {

    public static final int DEFAULT_K = 32;
    public static final int PLACEMENT_K = 64;

    /**
     * Calculate the new Elo for a player.
     */
    public static int calculate(int elo, int opponentElo, boolean win, boolean placement) {
        double expected = 1.0 / (1.0 + Math.pow(10, (opponentElo - elo) / 400.0));
        double actual = win ? 1.0 : 0.0;
        int k = placement ? PLACEMENT_K : DEFAULT_K;

        int newElo = (int) Math.round(elo + k * (actual - expected));
        return Math.max(0, newElo);
    }

    public static Stakes calculateStakes(int elo, int opponentElo, boolean placement) {
        int winElo = calculate(elo, opponentElo, true, placement);
        int lossElo = calculate(elo, opponentElo, false, placement);

        int winChange = winElo - elo;
        int lossChange = elo - lossElo;

        String intensity = "ME";
        int maxChange = Math.max(winChange, lossChange);
        if (maxChange >= 45) intensity = "HI";
        else if (maxChange <= 15) intensity = "LO";

        return new Stakes(winChange, lossChange, intensity);
    }

    public static class Stakes {
        public final int win;
        public final int loss;
        public final String intensity;

        public Stakes(int win, int loss, String intensity) {
            this.win = win;
            this.loss = loss;
            this.intensity = intensity;
        }
    }
}
