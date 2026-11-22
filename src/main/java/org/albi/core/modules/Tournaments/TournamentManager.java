package org.albi.core.modules.Tournaments;

import org.albi.core.gui.Messages;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.albi.core.Core;
import org.albi.core.util.ConfigValues;
import org.albi.core.modules.Duels.DuelInstance;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Arena;
import org.albi.core.modules.Module;
import org.albi.core.modules.Tournaments.commands.TournamentCommand;
import org.albi.core.modules.Tournaments.helpers.TournamentSession;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TournamentManager extends Module {

    private static TournamentManager instance;
    private ChallongeAPI challongeAPI;

    // Active tournament sessions mapped by their unique Challonge URL prefix
    private final Map<String, TournamentSession> activeTournaments = new ConcurrentHashMap<>();
    private BukkitRunnable pollingTask;

    public TournamentManager(Core plugin) {
        super(plugin);
    }

    public static TournamentManager getInstance() {
        return instance;
    }

    public ChallongeAPI getAPI() {
        return challongeAPI;
    }

    public Map<String, TournamentSession> getActiveTournaments() {
        return activeTournaments;
    }

    @Override
    public void init() {
        getPlugin().getLogger().info("[Tournaments] Initializing");
        String apiKey = ConfigValues.envOrYaml(getPlugin().getConfig(),
                "tournaments.challonge_api_key", "VISE_CHALLONGE_API_KEY");
        String username = ConfigValues.envOrYaml(getPlugin().getConfig(),
                "tournaments.challonge_username", "VISE_CHALLONGE_USERNAME");

        if (apiKey.isBlank() || apiKey.equals("YourAPIKey") || username.isBlank() || username.equals("YourUsername")) {
            getPlugin().getLogger().warning("[Tournaments] Set the Challonge username and API key to enable tournaments.");
            return;
        }
        instance = this;
        challongeAPI = new ChallongeAPI(apiKey, username);
        getPlugin().getCommand("tournament").setExecutor(new TournamentCommand(getPlugin(), this));
        getPlugin().getCommand("next").setExecutor(new TournamentCommand(getPlugin(), this)); // same executor handles it

        startPollingTask();
    }

    private void startPollingTask() {
        pollingTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (TournamentSession session : activeTournaments.values()) {
                    if (session.getState() == TournamentSession.State.PLAYING) {
                        pollMatchesForSession(session);
                    }
                }
            }
        };
        pollingTask.runTaskTimerAsynchronously(getPlugin(), 200L, 200L); // 10 seconds
    }

    private void pollMatchesForSession(TournamentSession session) {
        challongeAPI.getMatches(session.getTournamentUrl()).thenAccept(matchesJson -> {
            boolean allCurrentRoundCompleted = true;
            boolean anyMatchProcessed = false;

            for (JsonElement element : matchesJson) {
                JsonObject matchAttr = element.getAsJsonObject().getAsJsonObject("match");
                long matchId = matchAttr.get("id").getAsLong();
                String state = matchAttr.get("state").getAsString();
                int round = matchAttr.get("round").getAsInt();

                // If this match is in the active round but not complete, it delays the next round
                if (round == session.getCurrentRound() && !state.equals("complete")) {
                    allCurrentRoundCompleted = false;
                }

                if (state.equals("open") && round == session.getCurrentRound()) {
                    if (session.getActiveDuels().containsKey(matchId) || session.getCompletedMatches().contains(matchId)) continue; // already active or complete

                    JsonElement p1Element = matchAttr.get("player1_id");
                    JsonElement p2Element = matchAttr.get("player2_id");

                    if (p1Element == null || p1Element.isJsonNull() || p2Element == null || p2Element.isJsonNull()) {
                        continue; // Waiting for opponent to advance
                    }

                    long player1Id = p1Element.getAsLong();
                    long player2Id = p2Element.getAsLong();

                    List<UUID> team1 = session.getTeams().get(player1Id);
                    List<UUID> team2 = session.getTeams().get(player2Id);

                    if (team1 != null && team2 != null) {
                        anyMatchProcessed = true;
                        Bukkit.getScheduler().runTask(getPlugin(), () -> {
                            startMatch(session, matchId, team1, team2);
                        });
                    }
                }
            }

            // Move to next round if all matches in current round are complete
            // Initial round is 1, when all round 1 matches complete -> we move to round 2
            if (allCurrentRoundCompleted && session.getCurrentRound() > 0) {
                if (matchesJson.size() > 0) { // If there's actually a tournament
                    // Find max round to see if we reached the end
                    int maxRound = 1;
                    int completedCount = 0;
                    for (JsonElement element : matchesJson) {
                        JsonObject matchAttr = element.getAsJsonObject().getAsJsonObject("match");
                        maxRound = Math.max(maxRound, matchAttr.get("round").getAsInt());
                        if (matchAttr.get("state").getAsString().equals("complete")) completedCount++;
                    }

                    if (completedCount == matchesJson.size() && matchesJson.size() > 0) {
                        session.setState(TournamentSession.State.FINISHED);
                        Bukkit.getScheduler().runTask(getPlugin(), () -> {
                            for (UUID id : session.getParticipants()) {
                                Player p = Bukkit.getPlayer(id);
                                if (p != null) {
                                    p.sendTitle(Messages.text(p, "messages.tournament-manager.tournament-finished"), Messages.text(p, "messages.tournament-manager.thanks-for-playing"), 10, 60, 10);
                                    getPlugin().resetPlayer(p);
                                    getPlugin().teleportToLobby(p);
                                }
                            }
                            activeTournaments.remove(session.getTournamentUrl());
                        });
                        challongeAPI.concludeTournament(session.getTournamentUrl());
                    } else if (session.getCurrentRound() < maxRound) {
                        session.setCurrentRound(session.getCurrentRound() + 1);
                        Bukkit.getScheduler().runTask(getPlugin(), () -> {
                            for (UUID id : session.getParticipants()) {
                                Player p = Bukkit.getPlayer(id);
                                if (p != null) {
                                    getPlugin().getMessageAPI().sendMessageLang(p, "tournament_round", new String[]{String.valueOf(session.getCurrentRound())});
                                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                                }
                            }
                        });
                    }
                }
            }
        });
    }

    private void startMatch(TournamentSession session, long matchId, List<UUID> team1, List<UUID> team2) {
        List<Player> team1Players = new ArrayList<>();
        for (UUID id : team1) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                team1Players.add(p);
            }
        }
        
        List<Player> team2Players = new ArrayList<>();
        for (UUID id : team2) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                team2Players.add(p);
            }
        }

        Player[] pT1 = team1Players.toArray(new Player[0]);
        Player[] pT2 = team2Players.toArray(new Player[0]);

        // Auto-win if a team is completely offline/left
        if (pT1.length == 0 && pT2.length > 0) {
            long winnerId = findTeamId(session, team2);
            session.getCompletedMatches().add(matchId);
            challongeAPI.updateMatch(session.getTournamentUrl(), matchId, "0-1", winnerId);
            return;
        } else if (pT2.length == 0 && pT1.length > 0) {
            long winnerId = findTeamId(session, team1);
            session.getCompletedMatches().add(matchId);
            challongeAPI.updateMatch(session.getTournamentUrl(), matchId, "1-0", winnerId);
            return;
        } else if (pT1.length == 0 && pT2.length == 0) {
            // Both empty, randomly pick team 1 as winner so bracket advances
            long winnerId = findTeamId(session, team1);
            session.getCompletedMatches().add(matchId);
            challongeAPI.updateMatch(session.getTournamentUrl(), matchId, "1-0", winnerId);
            return;
        }

        // Find arena
        List<Arena> validArenas = DuelManager.stor.getAllArenas();
        Arena selectedArena = validArenas.isEmpty() ? null : validArenas.get(new Random().nextInt(validArenas.size()));

        if (selectedArena == null) {
            getPlugin().getLogger().warning("No arena found for tournament match! " + session.getTournamentUrl());
            return;
        }

        DuelInstance duel = new DuelInstance(getPlugin())
                .prepare(pT1, pT2, selectedArena, session.getKit(), session.getKit(), 1)
                .onFinish(winningTeam -> handleMatchFinish(session, matchId, winningTeam, team1, team2))
                .onLeave(leavingPlayer -> {
                    // Logic to handle someone leaving a 2v2 mid-match.
                    getPlugin().getMessageAPI().sendMessageLang(leavingPlayer, "tournament_kick");
                    session.getParticipants().remove(leavingPlayer.getUniqueId());

                    // The duel instance continues with the rest of the players. If team hits 0, onFinish gets called by DuelInstance.
                });

        session.getActiveDuels().put(matchId, duel);
        duel.initialize(ConfigValues.integer(getPlugin().getConfig(), "tournaments.countdown_seconds", 5, 0, 120), false);
    }

    private void handleMatchFinish(TournamentSession session, long matchId, int winningTeam, List<UUID> team1, List<UUID> team2) {
        session.getActiveDuels().remove(matchId);
        session.getCompletedMatches().add(matchId);

        // Figure out the local team
        List<UUID> winLocalTeam = (winningTeam == 1) ? team1 : team2;
        List<UUID> loseLocalTeam = (winningTeam == 1) ? team2 : team1;

        long challongeWinnerId = findTeamId(session, winLocalTeam);
        String scores = winningTeam == 1 ? "1-0" : "0-1";

        challongeAPI.updateMatch(session.getTournamentUrl(), matchId, scores, challongeWinnerId)
                .thenAccept(success -> {
                    if (!success) {
                        getPlugin().getLogger().warning("Failed to report match " + matchId + " to challonge!");
                    }
                });

        // Auto-spectate or return to lobby
        Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            for (UUID id : winLocalTeam) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    getPlugin().teleportToLobby(p);
                    autoSpectate(p, session);
                }
            }
            for (UUID id : loseLocalTeam) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    session.getParticipants().remove(id); // Eliminated
                    getPlugin().teleportToLobby(p);
                }
            }
        }, 60L); // wait 3 sec after finish which matches duel cleanup
    }

    private long findTeamId(TournamentSession session, List<UUID> team) {
        for (Map.Entry<Long, List<UUID>> entry : session.getTeams().entrySet()) {
            if (entry.getValue().equals(team)) {
                return entry.getKey();
            }
        }
        return -1;
    }

    public boolean isInTournament(Player player) {
        for (TournamentSession session : activeTournaments.values()) {
            if (session.getParticipants().contains(player.getUniqueId())) return true;
        }
        return false;
    }

    public TournamentSession getSession(Player player) {
        for (TournamentSession session : activeTournaments.values()) {
            if (session.getParticipants().contains(player.getUniqueId())) return session;
        }
        return null;
    }

    public void autoSpectate(Player player, TournamentSession session) {
        if (session == null || session.getState() != TournamentSession.State.PLAYING) return;
        if (session.getActiveDuels().isEmpty()) return;

        // Pick a random active duel
        List<Long> activeMatchIds = new ArrayList<>(session.getActiveDuels().keySet());
        if (!activeMatchIds.isEmpty()) {
            long randomMatchId = activeMatchIds.get(new Random().nextInt(activeMatchIds.size()));
            
            // We need to find the participant/players for this match from challonge...
            // But an easier way is just to guess from the online participants who is currently 'InGame'
            for (UUID id : session.getParticipants()) {
                if (id.equals(player.getUniqueId())) continue;
                Player other = Bukkit.getPlayer(id);
                if (other != null && other.isOnline()) {
                    org.albi.core.data.ExtendedPlayer ep = getPlugin().getExtendedPlayer(other);
                    if (ep != null && ep.getPlayerDuelInstance() != null && "InGame".equals(ep.getPlayerDuelInstance().getStatus())) {
                        player.performCommand("spectate " + other.getName());
                        getPlugin().getMessageAPI().sendMessageLang(player, "tournament_spectate_next");
                        return;
                    }
                }
            }
            
            Messages.send(player, "messages.tournament-manager.no-active-matches-found-to-spectate-right-now");
        }
    }

    public void autoSpectateNext(Player player) {
        TournamentSession session = getSession(player);
        if (session == null || session.getState() != TournamentSession.State.PLAYING) return;

        // Reset them slightly
        player.setGameMode(GameMode.SURVIVAL);
        autoSpectate(player, session);
    }
}
