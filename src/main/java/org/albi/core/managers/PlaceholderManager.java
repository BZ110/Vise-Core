package org.albi.core.managers;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.data.MainPlayer;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.AutomatedCombat.BotBackedDuelInstance;
import org.albi.core.modules.Party.play.PartyFFAInstance;
import org.albi.core.modules.LavaRising.LavaRisingManager;
import org.albi.core.modules.Duels.DuelInstance;
import org.albi.core.modules.Duels.helpers.GlobalDuelCache;
import org.albi.core.modules.Duels.helpers.Kit;
import org.albi.core.modules.Duels.helpers.PlayerDuelInstance;
import org.albi.core.modules.Friends.FriendsManager;
import org.albi.core.modules.ModuleManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlaceholderManager extends PlaceholderExpansion implements Relational {

    private static final long FRIEND_TAB_CACHE_TTL_MS = 1000L;
    private static final long FRIEND_TAB_CACHE_CLEANUP_MS = 30_000L;

    private final Core plugin;
    private final Map<UUID, CachedFriendTabSnapshot> friendTabCache = new ConcurrentHashMap<>();
    private volatile long lastFriendTabCacheCleanupAt = 0L;

    public PlaceholderManager(Core plugin) {
        this.plugin = plugin;
    }

    /* basic meta */
    @Override
    public @NotNull String getIdentifier() {
        return "core";
    }

    @Override
    public @NotNull String getAuthor() {
        return "CriticalKing";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    /* main hook */
    @Override
    public @Nullable String onRequest(OfflinePlayer p, @NotNull String id) {

        String key = id.toLowerCase();
        switch (key) {

            /* static config values */
            case "server":
                return plugin.getConfig().getString("region").toUpperCase() + "-"
                        + plugin.getConfig().getString("id_number");

            case "ip":
                return plugin.getConfig().getString("ip");

            case "name":
                return plugin.getConfig().getString("name");

            /* ───── dynamic values that depend on ExtendedPlayer ───── */
            case "xp":
            case "level": {
                ExtendedPlayer ep = plugin.getExtendedPlayer(p.getPlayer());

                /* not cached or still loading → show placeholder */
                if (ep == null || !ep.isReady()) {
                    return "Loading...";
                }

                if (key.equals("xp"))
                    return String.valueOf(ep.getMainPlayer().xp());
                else
                    return String.valueOf(ep.getMainPlayer().level());
            }

            /* duel prefix (optional) */
            case "duel-prefix": {
                ExtendedPlayer ep = plugin.getExtendedPlayer(p.getPlayer());
                if (ep == null || !ep.isReady())
                    return "";

                PlayerDuelInstance inst = ep.getPlayerDuelInstance();
                if (inst == null)
                    return "";

                int team = inst.getTeam();
                if (team == 1)
                    return plugin.getMessageAPI().toColorLang(p.getPlayer(), "duel_prefix_team_one");
                else
                    return plugin.getMessageAPI().toColorLang(p.getPlayer(), "duel_prefix_team_two");
            }

            case "my-color": {
                ExtendedPlayer ep = plugin.getExtendedPlayer(p.getPlayer());
                if (ep == null || !ep.isReady())
                    return "";

                PlayerDuelInstance inst = ep.getPlayerDuelInstance();
                if (inst == null)
                    return "";

                int team = inst.getTeam();
                if (team == 1)
                    return plugin.getMessageAPI().toColorLang(p.getPlayer(), "duel_prefix_team_one_color");
                else
                    return plugin.getMessageAPI().toColorLang(p.getPlayer(), "duel_prefix_team_two_color");
            }

            case "rank-w-color-g": {
                ExtendedPlayer ep = plugin.getExtendedPlayer(p.getPlayer());
                if (ep == null || !ep.isReady())
                    return "";
                PlayerDuelInstance pdi = ep.getPlayerDuelInstance();
                if (pdi == null || pdi.getDuelInstance() == null || !pdi.getDuelInstance().isRanked())
                    return "";

                org.albi.core.modules.Duels.ranked.RankedStats rs = ep.getMainPlayer()
                        .getKitRankedStats(pdi.getDuelInstance().getKitTeamOne().getName());
                org.albi.core.modules.Duels.ranked.RankedTier tier = org.albi.core.modules.Duels.ranked.RankedTier
                        .getTier(rs.getElo());
                return tier.getColor().toString() + rs.getElo() + " ";
            }

            case "dueling": {
                // Access available instances
                ArrayList<DuelInstance> instances = GlobalDuelCache.availableInstances;

                for (DuelInstance instance : instances) {
                    if (instance.isCanceled() || instance.isFinished()) {
                        instances.remove(instance);
                    }

                }
                int players = plugin.playersInDuel;
                return "" + players;
            }

            case "indics": {
                Player player = p.getPlayer();
                ExtendedPlayer ep = plugin.getExtendedPlayer(player);
                if (ep == null || !ep.isReady())
                    return "";

                double hp = Math.round(player.getHealth() * 100.0) / 100.0;

                // --- FFA CHECK ---
                String currentWorld = player.getWorld().getName();
                List<String> ffaWorlds = plugin.getConfig().getStringList("ffa_worlds");

                if (ffaWorlds.contains(currentWorld)) {
                    String kitName = ep.getLastFFAKitName();
                    if (kitName == null)
                        return "";

                    Kit kit = getFFAKit(kitName);
                    if (kit != null && kit.isHealthIndics()) {
                        return " &7(&c❤" + hp + "&7)";
                    }
                    return "";
                }

                // --- DUEL CHECK ---
                PlayerDuelInstance pdi = ep.getPlayerDuelInstance();
                if (pdi == null)
                    return "";

                Kit kit = pdi.getKit();
                if (kit != null && kit.isHealthIndics()) {
                    return " &7(&c❤" + hp + "&7)";
                }

                return "";
            }

            case "max-up": {
                ExtendedPlayer ep = plugin.getExtendedPlayer(p.getPlayer());
                if (ep == null)
                    return "0";

                MainPlayer mp = ep.getMainPlayer();
                if (mp == null)
                    return "0";

                return "" + (10 + (mp.level() - 1) * 10);
            }

            case "displaytab": {
                ExtendedPlayer ep = plugin.getExtendedPlayer(p.getPlayer());
                if (ep == null || ep.getMainPlayer() == null)
                    return "global";

                MainPlayer mp = ep.getMainPlayer();

                return ep.getDisplayTab() == 1 ? "friends" : "global";
            }

            case "display-duel-world": {
                Player player = p.getPlayer();
                if (player == null)
                    return "";
                return isDuelWorld(player.getWorld()) ? player.getWorld().getName() : "";
            }

            case "display-duel-world_players_red_team": {
                Player player = p.getPlayer();
                return player != null && isPlayerInDisplayedDuelWorldOnTeam(player, 1) ? "true" : "false";
            }

            case "display-duel-world_players_blue_team": {
                Player player = p.getPlayer();
                return player != null && isPlayerInDisplayedDuelWorldOnTeam(player, 2) ? "true" : "false";
            }

            case "display-duel-world_players_spectator": {
                Player player = p.getPlayer();
                return player != null && isPlayerDisplayedDuelWorldSpectator(player) ? "true" : "false";
            }

            case "spectators_watching": {
                Player player = p.getPlayer();
                DuelInstance duel = resolveVisibleDuel(player);
                return duel == null ? "0" : String.valueOf(countVisibleSpectators(duel));
            }

            case "red_team": {
                Player player = p.getPlayer();
                if (player == null)
                    return "false";
                if (isActiveFighterOnTeam(player, 1))
                    return "true";
                // Spectator support: resolve team from who they're watching
                ExtendedPlayer epSpec = plugin.getExtendedPlayer(player);
                if (epSpec != null && epSpec.isSpectating()) {
                    int specTeam = resolveSpectatorTeam(player);
                    return specTeam == 1 ? "true" : "false";
                }
                return "false";
            }

            case "blue_team": {
                Player player = p.getPlayer();
                if (player == null)
                    return "false";
                if (isActiveFighterOnTeam(player, 2))
                    return "true";
                // Spectator support: resolve team from who they're watching
                ExtendedPlayer epSpec = plugin.getExtendedPlayer(player);
                if (epSpec != null && epSpec.isSpectating()) {
                    int specTeam = resolveSpectatorTeam(player);
                    return specTeam == 2 ? "true" : "false";
                }
                return "false";
            }

            // ───────── LAVA RISING PLACEHOLDERS ─────────

            case "lava_active": {
                var inst = LavaRisingManager.getActiveInstance();
                return inst != null && inst.isActive() ? "true" : "false";
            }

            case "lava_seconds": {
                var inst = LavaRisingManager.getActiveInstance();
                return inst != null ? String.valueOf(inst.getSecondsElapsed()) : "0";
            }

            case "lava_time": {
                var inst = LavaRisingManager.getActiveInstance();
                return inst != null ? fmt(inst.getSecondsElapsed()) : "0s";
            }

            case "lava_y": {
                var inst = LavaRisingManager.getActiveInstance();
                return inst != null ? String.valueOf(inst.getCurrentLavaY()) : "-63";
            }

            case "lava_arena": {
                var inst = LavaRisingManager.lavaRisingListener.getActiveInstance();
                return inst != null ? inst.getArena().getName() : "None";
            }

            case "lava_players": {
                var inst = LavaRisingManager.getActiveInstance();
                return inst != null ? String.valueOf(inst.getWorld().getPlayers().size()) : "0";
            }

            case "lava_remaining": {
                // grab the configured world name, defaulting to “lavaevent” if the key is
                // missing
                String worldName = plugin.getConfig()
                        .getString("lava_rising.lava_rising_world", "lavaevent");

                // look up the world once; handle the null case safely
                World lavaWorld = Bukkit.getWorld(worldName);
                if (lavaWorld == null) {
                    plugin.getLogger().warning("[LavaRising] World '" + worldName + "' not loaded.");
                    return "0"; // or handle however you like
                }

                // count living players (non‑spectators) with the stream API
                long alive = lavaWorld.getPlayers().stream()
                        .filter(p222 -> p222.getGameMode() != GameMode.SPECTATOR)
                        .count();

                return alive + "";
            }

            case "duel-score-yours": {
                Player player = p.getPlayer();
                if (player == null)
                    return "";
                ExtendedPlayer ep = plugin.getExtendedPlayer(player);
                if (ep == null)
                    return "";

                PlayerDuelInstance pDI = ep.getPlayerDuelInstance();
                if (pDI != null) {
                    DuelInstance di = pDI.getDuelInstance();
                    return "" + di.getScore(pDI.getTeam());
                }

                // Spectator fallback: use perspective of who they're watching
                if (ep.isSpectating()) {
                    DuelInstance duel = resolveVisibleDuel(player);
                    if (duel != null) {
                        int team = resolveSpectatorTeam(player);
                        return "" + duel.getScore(team != 0 ? team : 1);
                    }
                }
                return "";
            }

            case "duel-score-other": {
                Player player = p.getPlayer();
                if (player == null)
                    return "";
                ExtendedPlayer ep = plugin.getExtendedPlayer(player);
                if (ep == null)
                    return "";

                PlayerDuelInstance pDI = ep.getPlayerDuelInstance();
                if (pDI != null) {
                    DuelInstance di = pDI.getDuelInstance();
                    return "" + di.getScore(pDI.getTeam() == 1 ? 2 : 1);
                }

                // Spectator fallback
                if (ep.isSpectating()) {
                    DuelInstance duel = resolveVisibleDuel(player);
                    if (duel != null) {
                        int team = resolveSpectatorTeam(player);
                        return "" + duel.getScore(team == 1 ? 2 : 1);
                    }
                }
                return "";
            }

            case "duel-time-formatted": {
                Player player = p.getPlayer();
                if (player == null)
                    return "";
                ExtendedPlayer ep = plugin.getExtendedPlayer(player);
                if (ep == null)
                    return "";

                PlayerDuelInstance pDI = ep.getPlayerDuelInstance();
                if (pDI != null) {
                    DuelInstance di = pDI.getDuelInstance();
                    if (di instanceof PartyFFAInstance partyFFA) {
                        return fmt(partyFFA.getElapsedSeconds());
                    }
                    return fmt(di.getTimeRunning());
                }

                // Spectator fallback
                if (ep.isSpectating()) {
                    DuelInstance duel = resolveVisibleDuel(player);
                    if (duel != null) {
                        if (duel instanceof PartyFFAInstance partyFFA) {
                            return fmt(partyFFA.getElapsedSeconds());
                        }
                        return fmt(duel.getTimeRunning());
                    }
                }
                return "";
            }

            case "bot-in-match": {
                ExtendedPlayer ep = plugin.getExtendedPlayer(p.getPlayer());
                if (ep == null || ep.getPlayerDuelInstance() == null)
                    return "no";
                return ep.getPlayerDuelInstance().getDuelInstance() instanceof BotBackedDuelInstance ? "yes" : "no";
            }

            case "partyffa-in-match": {
                ExtendedPlayer ep = plugin.getExtendedPlayer(p.getPlayer());
                if (ep == null || ep.getPlayerDuelInstance() == null)
                    return "no";
                return ep.getPlayerDuelInstance().getDuelInstance() instanceof PartyFFAInstance ? "yes" : "no";
            }

            case "bot-score-player": {
                var session = getBotSession(p.getPlayer());
                return session == null ? "0" : String.valueOf(session.getPlayerRoundsWon());
            }

            case "bot-score-bot": {
                var session = getBotSession(p.getPlayer());
                return session == null ? "0" : String.valueOf(session.getBotRoundsWon());
            }

            case "bot-round": {
                var session = getBotSession(p.getPlayer());
                return session == null ? "0" : String.valueOf(session.getCurrentRound());
            }

            case "bot-player-hp": {
                var session = getBotSession(p.getPlayer());
                return session == null ? "0.0" : String.format("%.1f", session.getPlayerHealth());
            }

            case "bot-hp": {
                var session = getBotSession(p.getPlayer());
                return session == null ? "0.0" : String.format("%.1f", session.getBotHealth());
            }

            case "partyffa-kills": {
                var partyFFA = getPartyFFA(p.getPlayer());
                return partyFFA == null ? "0" : String.valueOf(partyFFA.getKills(p.getPlayer().getUniqueId()));
            }

            case "partyffa-remaining": {
                var partyFFA = getPartyFFA(p.getPlayer());
                return partyFFA == null ? "0" : String.valueOf(partyFFA.getRemainingPlayers());
            }

            case "partyffa-time": {
                var partyFFA = getPartyFFA(p.getPlayer());
                return partyFFA == null ? "0s" : fmt(partyFFA.getElapsedSeconds());
            }

            case "duel-in-world": {
                World w = p.getPlayer().getWorld();

                if (w.getName().toLowerCase().endsWith("-duel")) {
                    return "yes";
                } else
                    return "no";
            }

            // ───────── RANKED PLACEHOLDERS ─────────
            case "ranked_elo": {
                ExtendedPlayer ep = plugin.getExtendedPlayer(p.getPlayer());
                return (ep == null || !ep.isReady()) ? "0"
                        : String.valueOf(ep.getMainPlayer().getOverallRankedStats().getElo());
            }

            case "ranked_tier": {
                ExtendedPlayer ep = plugin.getExtendedPlayer(p.getPlayer());
                return (ep == null || !ep.isReady()) ? "Loading..."
                        : ep.getMainPlayer().getOverallRankedStats().getTierDisplay();
            }

            case "ranked_placement_left": {
                ExtendedPlayer ep = plugin.getExtendedPlayer(p.getPlayer());
                if (ep == null || !ep.isReady())
                    return "5";
                int matches = ep.getMainPlayer().getOverallRankedStats().getPlacementMatches();
                int threshold = org.albi.core.modules.Duels.ranked.RankedStats.PLACEMENT_THRESHOLD;
                return String.valueOf(Math.max(0, threshold - matches));
            }

            case "ranked_in_queue": {
                var mod = org.albi.core.modules.Duels.DuelManager.getRankedQueueModule();
                return mod == null ? "0" : String.valueOf(mod.getTotalQueued());
            }

            case "ranked_tier_icon": {
                ExtendedPlayer ep = plugin.getExtendedPlayer(p.getPlayer());
                return (ep == null || !ep.isReady()) ? ""
                        : ep.getMainPlayer().getOverallRankedStats().getTierDisplay().substring(0, 2);
            }

            // DEPRECATED
            // WILL REMOVE IN NEXT MAINTENANCE
            case "ffa-indic": {
                ExtendedPlayer ep = plugin.getExtendedPlayer(p.getPlayer());
                if (ep == null || !ep.isReady())
                    return "";

                // Must be in FFA world
                String currentWorld = p.getPlayer().getWorld().getName();
                List<String> ffaWorlds = plugin.getConfig().getStringList("ffa_worlds");
                if (!ffaWorlds.contains(currentWorld))
                    return "";

                // Must have kit + allow indicators
                String kitName = ep.getLastFFAKitName();
                if (kitName == null)
                    return "";

                Kit kit = getFFAKit(kitName); // adjust to how you fetch kits
                if (kit == null || !kit.isHealthIndics())
                    return "";

                // Build indicator
                double hp = (double) Math.round(Objects.requireNonNull(p.getPlayer()).getHealth() * 100.0) / 100.0;
                return " &7(&c❤" + hp + "&7)";
            }

            case "ffa-kills": {
                ExtendedPlayer ep = plugin.getExtendedPlayer(p.getPlayer());
                if (ep == null || !ep.isReady())
                    return "0";

                String kitName = ep.getLastFFAKitName();
                if (kitName == null)
                    return "0";

                return String.valueOf(ep.getMainPlayer().getFFAKills(kitName));
            }

            case "ffa-deaths": {
                ExtendedPlayer ep = plugin.getExtendedPlayer(p.getPlayer());
                if (ep == null || !ep.isReady())
                    return "0";

                String kitName = ep.getLastFFAKitName();
                if (kitName == null)
                    return "0";

                return String.valueOf(ep.getMainPlayer().getFFADeaths(kitName));
            }

            case "ffa-ws": { // win streak
                ExtendedPlayer ep = plugin.getExtendedPlayer(p.getPlayer());
                if (ep == null || !ep.isReady())
                    return "0";

                String kitName = ep.getLastFFAKitName();
                if (kitName == null)
                    return "0";

                return String.valueOf(ep.getMainPlayer().getFFAWinstreak(kitName));
            }

            case "ffa-kdr": {
                ExtendedPlayer ep = plugin.getExtendedPlayer(p.getPlayer());
                if (ep == null || !ep.isReady())
                    return "0.00";

                String kitName = ep.getLastFFAKitName();
                if (kitName == null)
                    return "0.00";

                int kills = ep.getMainPlayer().getFFAKills(kitName);
                int deaths = ep.getMainPlayer().getFFADeaths(kitName);

                if (deaths == 0)
                    return kills > 0 ? String.valueOf(kills) : "0.00";
                double kdr = (double) kills / deaths;
                return String.format("%.2f", kdr);
            }

            case "ffa-in-world": {
                ExtendedPlayer ep = plugin.getExtendedPlayer(p.getPlayer());
                if (ep == null || !ep.isReady())
                    return "no";

                String currentWorld = p.getPlayer().getWorld().getName();
                List<String> ffaWorlds = plugin.getConfig().getStringList("ffa_worlds");

                return ffaWorlds.contains(currentWorld) ? "yes" : "no";
            }

            case "party_in_party": {
                if (p.getPlayer() == null || plugin.getPartyManager() == null)
                    return "no";
                return plugin.getPartyManager().isInParty(p.getPlayer()) ? "yes" : "no";
            }

            case "party_size": {
                if (p.getPlayer() == null || plugin.getPartyManager() == null)
                    return "0";
                var party = plugin.getPartyManager().getParty(p.getPlayer());
                return party == null ? "0" : String.valueOf(party.getMembers().size());
            }

            case "party_max": {
                if (p.getPlayer() == null || plugin.getPartyManager() == null)
                    return "0";
                var party = plugin.getPartyManager().getParty(p.getPlayer());
                return party == null ? "0" : String.valueOf(party.getMax());
            }

            case "party_leader": {
                if (p.getPlayer() == null || plugin.getPartyManager() == null)
                    return "";
                var party = plugin.getPartyManager().getParty(p.getPlayer());
                if (party == null)
                    return "";
                String name = Bukkit.getOfflinePlayer(party.getLeader()).getName();
                return name == null ? "Unknown" : name;
            }

            case "party_shuffle": {
                if (p.getPlayer() == null || plugin.getPartyManager() == null)
                    return "off";
                var party = plugin.getPartyManager().getParty(p.getPlayer());
                return party == null ? "off" : (party.isShuffle() ? "on" : "off");
            }

            case "party_autobalance": {
                if (p.getPlayer() == null || plugin.getPartyManager() == null)
                    return "off";
                var party = plugin.getPartyManager().getParty(p.getPlayer());
                return party == null ? "off" : (party.isAutoBalance() ? "on" : "off");
            }

            case "party_line_1":
            case "party_line_2":
            case "party_line_3":
            case "party_line_4":
            case "party_line_5": {
                if (p.getPlayer() == null || plugin.getPartyManager() == null)
                    return "";
                var party = plugin.getPartyManager().getParty(p.getPlayer());
                if (party == null)
                    return "";
                List<UUID> ids = new ArrayList<>(party.getMembers());
                ids.sort((a, b) -> {
                    if (a.equals(party.getLeader()))
                        return -1;
                    if (b.equals(party.getLeader()))
                        return 1;
                    String an = Bukkit.getOfflinePlayer(a).getName();
                    String bn = Bukkit.getOfflinePlayer(b).getName();
                    if (an == null)
                        an = a.toString();
                    if (bn == null)
                        bn = b.toString();
                    return an.compareToIgnoreCase(bn);
                });
                int idx = Integer.parseInt(key.substring("party_line_".length())) - 1;
                if (idx < 0 || idx >= ids.size())
                    return "";
                UUID id2 = ids.get(idx);
                String name = Bukkit.getOfflinePlayer(id2).getName();
                if (name == null)
                    name = id2.toString().substring(0, 8);
                return (id2.equals(party.getLeader()) ? "&6★ &f" : "&7• &f") + name;
            }

            case "friends_online": {
                if (p.getPlayer() == null)
                    return "0";
                FriendsManager friends = ModuleManager.getModule(FriendsManager.class);
                return friends == null ? "0" : String.valueOf(friends.getOnlineFriends(p.getPlayer()));
            }

            case "friends_total": {
                ExtendedPlayer ep = plugin.getExtendedPlayer(p.getPlayer());
                if (ep == null || ep.getMainPlayer() == null)
                    return "0";
                return String.valueOf(ep.getMainPlayer().getFriends().size());
            }

            case "friends_pending": {
                if (p.getPlayer() == null)
                    return "0";
                FriendsManager friends = ModuleManager.getModule(FriendsManager.class);
                return friends == null ? "0" : String.valueOf(friends.getPendingCount(p.getPlayer()));
            }

            case "status": {
                Player player = p.getPlayer();
                if (player == null)
                    return "";
                ExtendedPlayer ep = plugin.getExtendedPlayer(player);
                if (ep != null && ep.isLavaRisingEvent())
                    return "💥";
                if (ep != null && ep.getPlayerDuelInstance() != null)
                    return "🗡";
                String world = player.getWorld().getName();
                if (plugin.getConfig().getStringList("c_worlds").contains(world)
                        || plugin.getConfig().getStringList("ffa_worlds").contains(world))
                    return "⛰";
                if (world.equalsIgnoreCase(plugin.getConfig().getString("lobby_world", "world")))
                    return "✨";
                return "";
            }

            case "friends_tab_count": {
                Player player = p.getPlayer();
                if (player == null)
                    return "0";
                return String.valueOf(getFriendTabSnapshot(player).size());
            }

            case "friends_tab_header": {
                Player player = p.getPlayer();
                if (player == null)
                    return "&f☺ Friends (&b0&f)";
                int count = getFriendTabSnapshot(player).size();
                return "&f☺ Friends (&b" + count + "&f)";
            }

            /* unknown placeholder */
            default: {
                Player player = p.getPlayer();
                if (player == null)
                    return "";

                if (key.startsWith("friends_tab_line_")) {
                    int index = parseOneBasedIndex(key, "friends_tab_line_");
                    if (index < 0)
                        return "";
                    return getFriendTabSnapshot(player).displayLineAt(index);
                }

                if (key.startsWith("friends_tab_lineskinuuid_")) {
                    int index = parseOneBasedIndex(key, "friends_tab_lineskinuuid_");
                    if (index < 0)
                        return "";
                    return getFriendTabSnapshot(player).skinUuidAt(index);
                }

                if (key.startsWith("friends_tab_lineping_")) {
                    int index = parseOneBasedIndex(key, "friends_tab_lineping_");
                    if (index < 0)
                        return "";
                    return getFriendTabSnapshot(player).pingAt(index);
                }

                if (key.startsWith("friend-line_get-real-name_")) {
                    int index = parseOneBasedIndex(key, "friend-line_get-real-name_");
                    if (index < 0)
                        return "";
                    return getFriendTabSnapshot(player).realNameAt(index);
                }

                if (key.startsWith("friend-line_get-real-ping_")) {
                    int index = parseOneBasedIndex(key, "friend-line_get-real-ping_");
                    if (index < 0)
                        return "";
                    return getFriendTabSnapshot(player).pingAt(index);
                }
                return "";
            }
        }

    }

    @Override
    public String onPlaceholderRequest(Player viewer, Player target, String identifier) {
        if (viewer == null || target == null)
            return "";

        if (identifier.equalsIgnoreCase("duel-color")) {
            ExtendedPlayer viewerEp = plugin.getExtendedPlayer(viewer);
            if (viewerEp == null || viewerEp.getPlayerDuelInstance() == null) {
                return "";
            }
            ExtendedPlayer targetEp = plugin.getExtendedPlayer(target);
            if (targetEp == null || targetEp.getPlayerDuelInstance() == null) {
                return "";
            }
            if (viewerEp.getPlayerDuelInstance().getDuelInstance() != targetEp.getPlayerDuelInstance()
                    .getDuelInstance()) {
                return "";
            }
            int targetTeam = targetEp.getPlayerDuelInstance().getTeam();
            if (targetTeam == 1)
                return plugin.getMessageAPI().toColorLang(target, "duel_prefix_team_one_color");
            else
                return plugin.getMessageAPI().toColorLang(target, "duel_prefix_team_two_color");
        }

        if (identifier.equalsIgnoreCase("friend")) {
            FriendsManager friends = ModuleManager.getModule(FriendsManager.class);
            return friends != null && friends.isFriends(viewer, target) ? "&6⭐ " : "";
        }
        if (identifier.equalsIgnoreCase("playerSharesDuelWorld")) {
            if (viewer.getName().equals(target.getName()))
                return "true";
            return sharesActiveDuelWorld(viewer, target) ? "true" : "false";
        }
        return "";
    }

    private String fmt(int s) {
        int d = s / 86400;
        s %= 86400;
        int h = s / 3600;
        s %= 3600;
        int m = s / 60;
        s %= 60;

        StringBuilder b = new StringBuilder();
        if (d > 0)
            b.append(d).append("d ");
        if (h > 0)
            b.append(h).append("h ");
        if (m > 0)
            b.append(m).append("m ");
        if (s > 0 || b.length() == 0)
            b.append(s).append("s");

        return b.toString().trim();
    }

    private Kit getFFAKit(String kitName) {
        if (DuelManager.stor == null) return null;
        return DuelManager.stor.kits.stream()
                .filter(k -> k.getName().equalsIgnoreCase(kitName))
                .findFirst()
                .orElse(null);
    }

    private BotBackedDuelInstance getBotDuel(Player player) {
        if (player == null)
            return null;
        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (ep == null || ep.getPlayerDuelInstance() == null)
            return null;
        DuelInstance duel = ep.getPlayerDuelInstance().getDuelInstance();
        return duel instanceof BotBackedDuelInstance bot ? bot : null;
    }

    private org.albi.core.modules.Duels.AutomatedCombat.BotSession getBotSession(Player player) {
        BotBackedDuelInstance bot = getBotDuel(player);
        return bot == null ? null : bot.getSession();
    }

    private PartyFFAInstance getPartyFFA(Player player) {
        if (player == null)
            return null;
        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (ep == null || ep.getPlayerDuelInstance() == null)
            return null;
        DuelInstance duel = ep.getPlayerDuelInstance().getDuelInstance();
        return duel instanceof PartyFFAInstance partyFFA ? partyFFA : null;
    }

    private List<String> buildFriendTabEntries(Player viewer) {
        return getFriendTabSnapshot(viewer).displayLines();
    }

    private List<String> buildFriendTabUUIDEntries(Player viewer) {
        return getFriendTabSnapshot(viewer).skinUuids();
    }

    private List<String> buildFriendTabPingEntries(Player viewer) {
        return getFriendTabSnapshot(viewer).pings();
    }

    private List<String> buildFriendTabRealNameEntries(Player viewer) {
        return getFriendTabSnapshot(viewer).realNames();
    }

    private FriendTabSnapshot getFriendTabSnapshot(Player viewer) {
        if (viewer == null)
            return FriendTabSnapshot.EMPTY;

        long now = System.currentTimeMillis();
        UUID viewerId = viewer.getUniqueId();
        CachedFriendTabSnapshot cached = friendTabCache.get(viewerId);
        if (cached != null && (now - cached.createdAtMillis()) < FRIEND_TAB_CACHE_TTL_MS) {
            return cached.snapshot();
        }

        FriendTabSnapshot snapshot = buildFreshFriendTabSnapshot(viewer);
        friendTabCache.put(viewerId, new CachedFriendTabSnapshot(now, snapshot));
        cleanupExpiredFriendTabSnapshots(now);
        return snapshot;
    }

    private FriendTabSnapshot buildFreshFriendTabSnapshot(Player viewer) {
        ExtendedPlayer ep = plugin.getExtendedPlayer(viewer);
        if (ep == null || !ep.isReady())
            return FriendTabSnapshot.EMPTY;

        MainPlayer mp = ep.getMainPlayer();
        if (mp == null)
            return FriendTabSnapshot.EMPTY;

        List<FriendTabRow> rows = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();

        for (UUID id : mp.getFriends()) {
            if (id == null || !seen.add(id))
                continue;

            Player online = Bukkit.getPlayer(id);
            boolean isOnline = online != null && online.isOnline();

            String realName = isOnline ? online.getName() : resolveOfflineName(id);
            if (realName == null || realName.isBlank()) {
                realName = id.toString().substring(0, 8);
            }

            rows.add(new FriendTabRow(
                    realName,
                    (isOnline ? "&a" : "&8") + realName,
                    id.toString().replace("-", ""),
                    isOnline ? String.valueOf(Math.max(0, online.getPing())) : "-1",
                    isOnline));
        }

        rows.sort(
                Comparator
                        .comparing(FriendTabRow::online).reversed()
                        .thenComparing(FriendTabRow::realName, String.CASE_INSENSITIVE_ORDER));

        return new FriendTabSnapshot(List.copyOf(rows));
    }

    private String resolveOfflineName(UUID id) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
        String name = offline.getName();
        if (name != null && !name.isBlank())
            return name;

        Player online = Bukkit.getPlayer(id);
        return online != null ? online.getName() : null;
    }

    private void cleanupExpiredFriendTabSnapshots(long now) {
        if ((now - lastFriendTabCacheCleanupAt) < FRIEND_TAB_CACHE_CLEANUP_MS)
            return;
        lastFriendTabCacheCleanupAt = now;
        friendTabCache.entrySet().removeIf(entry -> {
            CachedFriendTabSnapshot cached = entry.getValue();
            if (cached == null)
                return true;
            if ((now - cached.createdAtMillis()) > FRIEND_TAB_CACHE_CLEANUP_MS)
                return true;
            Player online = Bukkit.getPlayer(entry.getKey());
            return online == null || !online.isOnline();
        });
    }

    private int parseOneBasedIndex(String key, String prefix) {
        try {
            return Integer.parseInt(key.substring(prefix.length())) - 1;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private DuelInstance resolveVisibleDuel(Player player) {
        if (player == null)
            return null;

        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (ep != null) {
            PlayerDuelInstance pdi = ep.getPlayerDuelInstance();
            if (pdi != null && pdi.getDuelInstance() != null) {
                return pdi.getDuelInstance();
            }
            if (ep.isSpectating()) {
                World spectatedWorld = ep.getSpectatingBase() != null ? ep.getSpectatingBase().getWorld()
                        : player.getWorld();
                DuelInstance duel = findDuelByWorld(spectatedWorld);
                if (duel != null)
                    return duel;
            }
        }

        return findDuelByWorld(player.getWorld());
    }

    private DuelInstance findDuelByWorld(World world) {
        if (world == null)
            return null;
        for (DuelInstance duel : GlobalDuelCache.availableInstances) {
            if (duel == null || duel.isCanceled() || duel.isFinished())
                continue;
            World activeWorld = duel.getActiveWorld();
            if (activeWorld != null && activeWorld.equals(world)) {
                return duel;
            }
        }
        return null;
    }

    private int countVisibleSpectators(DuelInstance duel) {
        if (duel == null)
            return 0;
        World activeWorld = duel.getActiveWorld();
        int count = 0;
        for (Player spectator : duel.spectators) {
            if (spectator == null || !spectator.isOnline())
                continue;
            ExtendedPlayer spectatorData = plugin.getExtendedPlayer(spectator);
            if (spectatorData == null || !spectatorData.isSpectating())
                continue;
            World spectatedWorld = spectatorData.getSpectatingBase() != null
                    ? spectatorData.getSpectatingBase().getWorld()
                    : spectator.getWorld();
            if (activeWorld != null && activeWorld.equals(spectatedWorld)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Resolves which team a spectator is watching from.
     * Uses Bukkit's getSpectatorTarget() to see who they're spectating,
     * then returns that player's team (1 or 2). Falls back to team 1
     * if the spectator isn't watching anyone specific.
     */
    private int resolveSpectatorTeam(Player spectator) {
        if (spectator == null)
            return 0;

        // Check if the spectator is directly watching a player
        org.bukkit.entity.Entity target = spectator.getSpectatorTarget();
        if (target instanceof Player targetPlayer) {
            ExtendedPlayer targetEp = plugin.getExtendedPlayer(targetPlayer);
            if (targetEp != null) {
                PlayerDuelInstance targetPdi = targetEp.getPlayerDuelInstance();
                if (targetPdi != null) {
                    return targetPdi.getTeam();
                }
            }
        }

        // Fallback: find the duel and check which team any nearby player is on
        DuelInstance duel = resolveVisibleDuel(spectator);
        if (duel != null) {
            // Default to team 1 (red) perspective when not watching anyone specific
            return 1;
        }
        return 0;
    }

    private boolean isActiveFighterOnTeam(Player player, int team) {
        if (player == null)
            return false;
        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (ep == null || ep.isSpectating())
            return false;
        PlayerDuelInstance pdi = ep.getPlayerDuelInstance();
        if (pdi == null || pdi.getDuelInstance() == null)
            return false;
        World activeWorld = pdi.getDuelInstance().getActiveWorld();
        return activeWorld != null && activeWorld.equals(player.getWorld()) && pdi.getTeam() == team;
    }

    private boolean sharesActiveDuelWorld(Player viewer, Player target) {
        if (viewer == null || target == null)
            return false;
        if (viewer.getUniqueId().equals(target.getUniqueId()))
            return false;

        ExtendedPlayer viewerData = plugin.getExtendedPlayer(viewer);
        ExtendedPlayer targetData = plugin.getExtendedPlayer(target);
        if (viewerData == null || targetData == null)
            return false;
        if (viewerData.isSpectating() || targetData.isSpectating())
            return false;

        PlayerDuelInstance viewerDuel = viewerData.getPlayerDuelInstance();
        PlayerDuelInstance targetDuel = targetData.getPlayerDuelInstance();
        if (viewerDuel == null || targetDuel == null)
            return false;
        if (viewerDuel.getDuelInstance() == null || viewerDuel.getDuelInstance() != targetDuel.getDuelInstance())
            return false;

        World activeWorld = viewerDuel.getDuelInstance().getActiveWorld();
        return activeWorld != null && activeWorld.equals(viewer.getWorld()) && activeWorld.equals(target.getWorld());
    }

    private boolean isDuelWorld(World world) {
        if (world == null)
            return false;
        String name = world.getName().toLowerCase(Locale.ROOT);
        return name.endsWith("-duel") || name.startsWith("p.");
    }

    private boolean isPlayerInDisplayedDuelWorldOnTeam(Player player, int team) {
        if (player == null || !isDuelWorld(player.getWorld()))
            return false;

        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (ep == null)
            return false;

        PlayerDuelInstance pdi = ep.getPlayerDuelInstance();
        if (pdi == null || pdi.getTeam() != team)
            return false;

        DuelInstance duel = pdi.getDuelInstance();
        if (duel == null)
            return false;

        World duelWorld = duel.getActiveWorld();
        return duelWorld != null && duelWorld.equals(player.getWorld());
    }

    private boolean isPlayerDisplayedDuelWorldSpectator(Player player) {
        if (player == null || !isDuelWorld(player.getWorld()))
            return false;

        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (ep == null || !ep.isSpectating())
            return false;
        if (ep.getSpectatingBase() == null || ep.getSpectatingBase().getWorld() == null)
            return false;

        return ep.getSpectatingBase().getWorld().equals(player.getWorld());
    }

    private record CachedFriendTabSnapshot(long createdAtMillis, FriendTabSnapshot snapshot) {
    }

    private record FriendTabRow(String realName, String displayLine, String skinUuid, String ping, boolean online) {
    }

    private record FriendTabSnapshot(List<FriendTabRow> rows) {
        private static final FriendTabSnapshot EMPTY = new FriendTabSnapshot(List.of());

        private int size() {
            return rows.size();
        }

        private String displayLineAt(int index) {
            return valueAt(index, FriendTabRow::displayLine, "");
        }

        private String realNameAt(int index) {
            return valueAt(index, FriendTabRow::realName, "");
        }

        private String skinUuidAt(int index) {
            return valueAt(index, FriendTabRow::skinUuid, "");
        }

        private String pingAt(int index) {
            return valueAt(index, FriendTabRow::ping, "-1");
        }

        private List<String> displayLines() {
            return rows.stream().map(FriendTabRow::displayLine).toList();
        }

        private List<String> realNames() {
            return rows.stream().map(FriendTabRow::realName).toList();
        }

        private List<String> skinUuids() {
            return rows.stream().map(FriendTabRow::skinUuid).toList();
        }

        private List<String> pings() {
            return rows.stream().map(FriendTabRow::ping).toList();
        }

        private String valueAt(int index, java.util.function.Function<FriendTabRow, String> extractor,
                String fallback) {
            if (index < 0 || index >= rows.size())
                return fallback;
            String value = extractor.apply(rows.get(index));
            return value == null ? fallback : value;
        }
    }

}
