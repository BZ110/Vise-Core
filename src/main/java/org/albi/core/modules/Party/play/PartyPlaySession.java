package org.albi.core.modules.Party.play;

import org.albi.core.modules.Duels.helpers.Arena;
import org.albi.core.modules.Duels.helpers.Kit;

import java.util.*;

public final class PartyPlaySession {

    public enum Mode { TEAMS, FFA }

    public int teamOneSize;
    public int teamTwoSize;
    public Kit kitOne;
    public Kit kitTwo;
    public Arena arena;
    public int rounds = 1;
    public Mode mode = Mode.TEAMS;

    public final LinkedHashSet<UUID> bench = new LinkedHashSet<>();
    public final LinkedHashSet<UUID> teamOneMembers = new LinkedHashSet<>();
    public final LinkedHashSet<UUID> teamTwoMembers = new LinkedHashSet<>();
    public int poolPage = 0;
    public int teamPage = 0;
    public int activeTeam = 1;
    public int viewedTeam = 1;

    public void resetAssignments(Iterable<UUID> allMembers) {
        bench.clear();
        teamOneMembers.clear();
        teamTwoMembers.clear();
        for (UUID id : allMembers) bench.add(id);
        poolPage = 0;
        teamPage = 0;
        activeTeam = 1;
        viewedTeam = 1;
    }

    public Set<UUID> getTeamSet(int team) {
        return team == 2 ? teamTwoMembers : teamOneMembers;
    }

    public int getTeamLimit(int team) {
        return team == 2 ? teamTwoSize : teamOneSize;
    }

    public boolean hasRoom(int team) {
        return getTeamSet(team).size() < getTeamLimit(team);
    }

    public boolean assignToTeam(UUID id, int team) {
        if (id == null || !bench.contains(id) || !hasRoom(team)) return false;
        bench.remove(id);
        getTeamSet(team).add(id);
        return true;
    }

    public boolean moveBetweenTeams(UUID id) {
        if (id == null) return false;
        if (teamOneMembers.remove(id)) {
            if (hasRoom(2)) teamTwoMembers.add(id);
            else {
                teamOneMembers.add(id);
                return false;
            }
            return true;
        }
        if (teamTwoMembers.remove(id)) {
            if (hasRoom(1)) teamOneMembers.add(id);
            else {
                teamTwoMembers.add(id);
                return false;
            }
            return true;
        }
        return false;
    }

    public boolean returnToBench(UUID id) {
        if (id == null) return false;
        if (teamOneMembers.remove(id) || teamTwoMembers.remove(id)) {
            bench.add(id);
            return true;
        }
        return false;
    }

    public List<UUID> teamPageEntries() {
        List<UUID> list = new ArrayList<>(getTeamSet(viewedTeam));
        int from = Math.max(0, teamPage * 27);
        int to = Math.min(list.size(), from + 27);
        if (from >= list.size()) return List.of();
        return list.subList(from, to);
    }

    public List<UUID> poolPageEntries() {
        List<UUID> list = new ArrayList<>(bench);
        int from = Math.max(0, poolPage * 7);
        int to = Math.min(list.size(), from + 7);
        if (from >= list.size()) return List.of();
        return list.subList(from, to);
    }

    public List<UUID> everyoneInFfa() {
        List<UUID> list = new ArrayList<>(teamOneMembers);
        list.addAll(teamTwoMembers);
        list.addAll(bench);
        return list;
    }
}
