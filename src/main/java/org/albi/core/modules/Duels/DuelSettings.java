package org.albi.core.modules.Duels;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

import static org.albi.core.util.ConfigValues.integer;
import static org.albi.core.util.ConfigValues.integers;

public record DuelSettings(Countdown countdown, List<Integer> rounds, int defaultRounds) {
    private static final List<Integer> DEFAULT_ROUNDS = List.of(1, 2, 3, 4, 5, 10, 15, 18, 20);

    public record Countdown(int startSeconds, int rankedSeconds, int betweenRoundsSeconds, int returnDelayTicks,
                            boolean readyUp, int betweenRoundsReadyWindowSeconds) {}

    public static DuelSettings from(ConfigurationSection config) {
        int selectedRounds = integer(config, "duels.default_rounds", 1, 1, 100);
        var configuredRounds = integers(config, "duels.rounds", DEFAULT_ROUNDS, 1, 100);
        if (configuredRounds.isEmpty()) configuredRounds = DEFAULT_ROUNDS;
        return new DuelSettings(new Countdown(
                integer(config, "duels.countdown.start_seconds", 10, 0, 120),
                integer(config, "ranked.countdown_seconds", 10, 0, 120),
                integer(config, "duels.countdown.between_rounds_seconds", 5, 0, 120),
                integer(config, "duels.countdown.return_delay_ticks", 60, 0, 2400),
                config.getBoolean("duels.countdown.ready_up", true),
                integer(config, "duels.countdown.between_rounds_ready_window_seconds", 3, 0, 120)),
                configuredRounds, selectedRounds);
    }
}
