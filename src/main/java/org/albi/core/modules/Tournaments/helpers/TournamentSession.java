package org.albi.core.modules.Tournaments.helpers;

import org.albi.core.modules.Duels.DuelInstance;
import org.albi.core.modules.Duels.helpers.Kit;
import org.bukkit.entity.Player;

import java.util.*;

public class TournamentSession {

    public enum State {
        WAITING, PLAYING, FINISHED
    }

    private final String tournamentUrl;
    private final Kit kit;
    private final int teamSize;
    private final int maxTeams;

    private State state = State.WAITING;
    private int currentRound = 0;

    // UUIDs of all registered players
    private final Set<UUID> participants = new HashSet<>();

    // Map participant ID from Challonge to a List of player UUIDs (the team)
    private final Map<Long, List<UUID>> teams = new HashMap<>();

    // Map match ID from Challonge to a running DuelInstance
    private final Map<Long, DuelInstance> activeDuels = new HashMap<>();

    // Record of matches that have been fully resolved
    private final Set<Long> completedMatches = new HashSet<>();

    public TournamentSession(String tournamentUrl, Kit kit, int teamSize, int maxTeams) {
        this.tournamentUrl = tournamentUrl;
        this.kit = kit;
        this.teamSize = teamSize;
        this.maxTeams = maxTeams;
    }

    public String getTournamentUrl() {
        return tournamentUrl;
    }

    public Kit getKit() {
        return kit;
    }

    public int getTeamSize() {
        return teamSize;
    }

    public int getMaxTeams() {
        return maxTeams;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Set<UUID> getParticipants() {
        return participants;
    }

    public Map<Long, List<UUID>> getTeams() {
        return teams;
    }

    public Map<Long, DuelInstance> getActiveDuels() {
        return activeDuels;
    }

    public Set<Long> getCompletedMatches() {
        return completedMatches;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }
}
