package org.albi.core.modules.Duels;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.util.ConfigValues;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.data.MainPlayer;
import org.albi.core.modules.CustomKit.helpers.CustomKit;
import org.albi.core.modules.Duels.helpers.*;
import org.albi.core.modules.Duels.polar.PolarArenaReservation;
import org.albi.core.modules.Duels.ranked.RankedStats;
import org.albi.core.modules.Duels.ranked.RankedMatchRecord;
import org.albi.core.modules.Duels.ranked.MatchTracker;
import org.albi.core.modules.Duels.ranked.EloCalculator;
import org.albi.core.util.TitleFx;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;

/**
 * One running best‑of‑N duel.
 *
 * <p>
 * Responsibilities kept here:
 * <ul>
 * <li>Tracking score and deciding who wins a round</li>
 * <li>Finishing the match when a team reaches the needed rounds</li>
 * <li>Handling players that leave or change world mid‑duel</li>
 * <li>High‑level flow‑control; low‑level tasks live in helpers</li>
 * </ul>
 */
public class DuelInstance implements Listener {

    // ──────────────────────────────────────────────────────────────────────
    // Immutable duel settings
    // ──────────────────────────────────────────────────────────────────────
    private Player[] teamOne;
    private Player[] teamTwo;
    private final UUID spectateId = UUID.randomUUID();
    private Map<UUID, String> returnServers = Map.of();
    public List<Player> spectators = new ArrayList<>();

    private Arena arena;
    private Kit kitTeamOne;
    private Kit kitTeamTwo;
    private int maxRounds;

    // ──────────────────────────────────────────────────────────────────────
    // Mutable runtime state
    // ──────────────────────────────────────────────────────────────────────
    private int roundsWonTeamOne = 0;
    private int roundsWonTeamTwo = 0;
    private boolean finished = false; // becomes true on match end
    private boolean canceled = false; // becomes true if force‑quit
    private boolean isRanked = false;
    private boolean telemetry = false;
    private RankedMatchRecord matchRecord;

    int teamOneLengthBegin = 0;
    int teamTwoLengthBegin = 0;

    // -------------------------------------------------------------------------
    // Team spawn locations (set these right after queueing the schematic)
    // -------------------------------------------------------------------------
    private Location teamLocOne;
    private Location teamLocTwo;

    private Location motherLocation;
    private boolean usePreparedPersonalKits;
    private java.util.concurrent.CompletableFuture<Boolean> queueArenaReady;
    private boolean queueArenaInPlay;
    private PolarArenaReservation polarReservation;
    private PolarArenaReservation nextPolarReservation;

    private final Core plugin;
    private final Map<UUID, Long> roundStartTime = new HashMap<>();
    private java.util.function.Consumer<Integer> onFinishCallback;
    private java.util.function.Consumer<Player> onLeaveCallback;

    public DuelInstance(Core plugin) {
        this.plugin = plugin;
    }

    public DuelInstance onFinish(java.util.function.Consumer<Integer> callback) {
        this.onFinishCallback = callback;
        return this;
    }

    public DuelInstance onLeave(java.util.function.Consumer<Player> callback) {
        this.onLeaveCallback = callback;
        return this;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Static Methods
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Merges 2 player arrays into one.
     * 
     * @param a The first array.
     * @param b The second array.
     * @return The merged array.
     */
    public static Player[] merge(Player[] a, Player[] b) {
        Player[] merged = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, merged, a.length, b.length);
        return merged;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Main Methods
    // ──────────────────────────────────────────────────────────────────────
    /**
     * Prepare the necessary variables to start the duel.
     * 
     * @param teamOne    The first team.
     * @param teamTwo    The second team.
     * @param arena      The arena that they will play on.
     * @param kitTeamOne The kit that the first team will use.
     * @param kitTeamTwo The kit that the second team will use.
     * @param maxRounds  The maximum amount of rounds.
     * @return This DuelInstance, so you can do .initialize() after if needed.
     */
    public DuelInstance prepare(Player[] teamOne,
            Player[] teamTwo,
            Arena arena,
            Kit kitTeamOne,
            Kit kitTeamTwo,
            int maxRounds) {
        this.teamOne = teamOne;
        this.teamTwo = teamTwo;
        this.arena = arena;
        this.kitTeamOne = kitTeamOne;
        this.kitTeamTwo = kitTeamTwo;
        this.maxRounds = maxRounds;
        return this;
    }

    public DuelInstance setRanked(boolean ranked) {
        this.isRanked = ranked;
        return this;
    }

    public DuelInstance setTelemetry(boolean telemetry) {
        this.telemetry = telemetry;
        return this;
    }

    public DuelInstance usePreparedPersonalKits() {
        usePreparedPersonalKits = true;
        return this;
    }

    public void returnPlayersTo(Map<UUID, String> sourceServers) {
        returnServers = Map.copyOf(sourceServers);
    }

    public UUID spectateId() {
        return spectateId;
    }

    public boolean isWatchable() {
        return !finished && !canceled && teamLocOne != null && teamLocTwo != null;
    }

    public static int availableQueueSlots(Core plugin, Arena arena) {
        if (arena == null || arena.deactivated || arena.getName() == null
                || arena.getSpawn1() == null || arena.getSpawn1().length < 5
                || arena.getSpawn2() == null || arena.getSpawn2().length < 5
                || arena.getCornerParameters() == null || arena.getCornerParameters().length < 4) return 0;
        if (arena.isPolarArena()) {
            return DuelManager.polarArenaManager == null ? 0 : DuelManager.polarArenaManager.availableCopies(arena);
        }
        if (arena.getMaxPerLine() <= 0 || arena.getMaxArenas() <= 0 || arena.getSchematic() == null
                || plugin.getServer().getWorld(arena.getName() + "-duel") == null) return 0;
        File file = getSchematicFile(plugin, arena);
        if (!file.isFile() || !DuelManager.schemManager.canReserve(file)) return 0;
        var used = GlobalDuelCache.usedArenas.get(arena);
        return Math.max(0, arena.getMaxArenas() - (used == null ? 0 : used.size()));
    }

    /** Holds a slot while queued players connect to this Paper instance. */
    public boolean reserveQueueArena(Arena candidate) {
        if (motherLocation != null || availableQueueSlots(plugin, candidate) == 0) return false;
        arena = candidate;
        if (candidate.isPolarArena()) {
            polarReservation = DuelManager.polarArenaManager.reservePreloadedArena(arena);
            if (polarReservation == null) return false;
            motherLocation = polarReservation.getMotherLocation();
            queueArenaReady = java.util.concurrent.CompletableFuture.completedFuture(true);
        } else {
            motherLocation = findArenaSpot(arena);
            if (motherLocation == null) return false;
            queueArenaReady = DuelManager.schemManager.enqueueTrackedSchematic(motherLocation, getSchematicFile(arena), true);
        }
        return true;
    }

    public java.util.concurrent.CompletableFuture<Boolean> queueArenaReady() {
        return queueArenaReady;
    }

    public boolean hasLoadedQueueArena() {
        return motherLocation != null && motherLocation.isWorldLoaded();
    }

    public void releaseQueueArena() {
        if (queueArenaReady != null && !queueArenaReady.isDone()) {
            // A slot under an outstanding paste stays owned until the paste stops writing.
            queueArenaReady.whenComplete((ready, failure) -> releaseQueueArena());
            return;
        }
        if (polarReservation != null && queueArenaReady != null && !queueArenaInPlay) {
            DuelManager.polarArenaManager.returnUnusedArena(polarReservation);
            polarReservation = null;
        } else {
            freeArena();
        }
        motherLocation = null;
        queueArenaReady = null;
    }

    /** Clears only state installed by this duel if an exception interrupts its start. */
    public void abortQueueStart() {
        canceled = true;
        GlobalDuelCache.availableInstances.remove(this);
        for (Player player : merge(teamOne, teamTwo)) {
            ExtendedPlayer profile = plugin.getExtendedPlayer(player);
            var current = profile == null ? null : profile.getPlayerDuelInstance();
            if (current != null && current.getDuelInstance() == this) {
                profile.setPlayerDuelInstance(null);
                plugin.teleportToLobby(player);
            }
        }
    }

    private boolean reserveArena() {
        if (arena == null) return false;
        if (arena.isPolarArena()) {
            if (!usingPolarArena()) return false;
            polarReservation = DuelManager.polarArenaManager.reserveArena(arena);
            if (polarReservation != null) motherLocation = polarReservation.getMotherLocation();
        } else {
            motherLocation = findArenaSpot(arena);
        }
        return motherLocation != null;
    }

    public int configuredCountdownSeconds() {
        var settings = plugin.getDuelSettings().countdown();
        return isRanked ? settings.rankedSeconds() : settings.startSeconds();
    }

    public void initialize() {
        initialize(configuredCountdownSeconds(), false);
    }

    /**
     * Prepares everything, and starts the duel.
     */
    public void initialize(int secondsToCount, boolean isPk) {
        startPrepared(secondsToCount, isPk);
    }

    public boolean startPrepared(int secondsToCount, boolean isPk) {
        final Player[][] merged = { merge(teamOne, teamTwo) };

        // SAFETY CHECK: Is anyone in a duel?
        for (Player p : merged[0]) {
            ExtendedPlayer ep = plugin.getExtendedPlayer(p);
            PlayerDuelInstance duelData = ep == null ? null : ep.getPlayerDuelInstance();
            if (ep == null || (duelData != null && duelData.getStatus() != null)) {
                for (Player pp : merged[0]) {
                    plugin.getMessageAPI().sendMessageLang(pp, "duel_in");
                }
                releaseQueueArena();
                return false;
            }
        }

        if (motherLocation == null && !reserveArena()) {
            for (Player pp : merged[0]) {
                plugin.getMessageAPI().sendMessageLang(pp, "duel_no_arena");
            }
            return false;
        }
        createAndStartMatchRecord();
        queueArenaInPlay = queueArenaReady != null;
        if (!usingPolarArena() && !queueArenaInPlay) {
            DuelManager.schemManager.enqueueSchematic(motherLocation, getSchematicFile(arena), true);
        }

        // Set the spawnable locations.
        World world = motherLocation.getWorld();
        teamLocOne = buildSpawn(world, arena.getSpawn1());
        teamLocTwo = buildSpawn(world, arena.getSpawn2());

        // Give everyone a PlayerDuelInstance
        List<String> stringList = plugin.getConfig().getStringList("mending_rngs");

        for (Player p : merged[0]) {
            ExtendedPlayer ep = plugin.getExtendedPlayer(p);
            PlayerDuelInstance pdI = new PlayerDuelInstance();
            pdI.setPlayer(p);
            pdI.setStatus("Countdown");
            pdI.setDuelInstance(this);

            if (isOnTeam(p, teamOne)) {
                pdI.setMendingRng(0);
                for (String str : stringList) {
                    if (str.equalsIgnoreCase(kitTeamOne.getName())) {
                        pdI.setMendingRng(7);
                    }
                }

                /* per‑player kit? */
                if (isPk && !usePreparedPersonalKits) {
                    Kit personal = personalKitFor(p, ep);
                    pdI.setKit(personal);
                } else {
                    pdI.setKit(kitTeamOne);
                }

                pdI.setTeam(1);
                pdI.setTeammates(teamOne);
                pdI.setOpponents(teamTwo);

            } else { // team two
                for (String str : stringList) {
                    if (str.equalsIgnoreCase(kitTeamTwo.getName())) {
                        pdI.setMendingRng(7);
                    }
                }
                if (isPk && !usePreparedPersonalKits) {
                    Kit personal = personalKitFor(p, ep);
                    pdI.setKit(personal);
                } else {
                    pdI.setKit(kitTeamTwo);
                }

                pdI.setTeam(2);
                pdI.setTeammates(teamTwo);
                pdI.setOpponents(teamOne);
            }

            ep.setPlayerDuelInstance(pdI);
        }

        // --- RANKED INTRO & STAKES ---
        if (isRanked && teamOne.length > 0 && teamTwo.length > 0) {
            Player p1 = teamOne[0];
            Player p2 = teamTwo[0];
            var main1 = plugin.getExtendedPlayer(p1).getMainPlayer();
            var main2 = plugin.getExtendedPlayer(p2).getMainPlayer();
            var rs1 = main1.getKitRankedStats(kitTeamOne.getName());
            var rs2 = main2.getKitRankedStats(kitTeamOne.getName());

            var stakes1 = org.albi.core.modules.Duels.ranked.EloCalculator.calculateStakes(rs1.getElo(), rs2.getElo(), !rs1.isPlacementsFinished());
            var stakes2 = org.albi.core.modules.Duels.ranked.EloCalculator.calculateStakes(rs2.getElo(), rs1.getElo(), !rs2.isPlacementsFinished());

            String msgMatch = "\n§6§lMATCH §8» §f" + p1.getName() + " §8[§7" + rs1.getTierDisplay() + " §f" + rs1.getElo() + "§8] §7vs §f" + p2.getName() + " §8[§7" + rs2.getTierDisplay() + " §f" + rs2.getElo() + "§8]";
            String msgStakes = "§6§lSTAKES §8» §a+" + stakes1.win + " §7/ §c-" + stakes1.loss + " ELO\n";
            
            for (Player p : merged[0]) {
                p.sendMessage(msgMatch);
                p.sendMessage(msgStakes);
                p.playSound(p, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.0f);
                p.playSound(p, Sound.BLOCK_ANVIL_USE, 0.8f, 0.8f);
            }
        }

        final boolean isPkFinal = isPk;
        for (Player p : merge(teamOne, teamTwo)) {
            plugin.resetPlayer(p);

            Kit kitToGive = isPkFinal
                    ? plugin.getExtendedPlayer(p).getPlayerDuelInstance().getKit()
                    : (isOnTeam(p, teamOne) ? kitTeamOne : kitTeamTwo);

            Location dest = isOnTeam(p, teamOne) ? teamLocOne : teamLocTwo;
            // Apply kit AFTER the async teleport completes so cross-world
            // player data saving doesn't wipe the inventory we just set.
            p.teleportAsync(dest)
                    .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!canceled) plugin.applyPossibleKit(p, kitToGive);
                    }));
        }

        // -------------------------------------------------------------------------
        // 10‑second tick‑level countdown with position lock
        // -------------------------------------------------------------------------

        clearEntitiesInArena(motherLocation, arena);
        teamOneLengthBegin = teamOne.length;
        teamTwoLengthBegin = teamTwo.length;
        new BukkitRunnable() {
            int ticksLeft = secondsToCount * 20; // 10 s × 20 ticks
            boolean skipped = false;
            boolean firstToggleSoundPlayed = false;
            java.util.Set<UUID> readyToggles = new java.util.HashSet<>();
            java.util.Map<UUID, Boolean> lastSneak = new java.util.HashMap<>();

            @Override
            public void run() {
                if (canceled || finished) {
                    cancel();
                    return;
                }

                // ----- keep players anchored each tick --------------------------
                for (Player p : teamOne) {
                    if (plugin.getExtendedPlayer(p) == null ||
                            plugin.getExtendedPlayer(p).getPlayerDuelInstance() == null ||
                            plugin.getExtendedPlayer(p).getPlayerDuelInstance().getStatus().equals("Cancel")) {
                        emergencyCancel();
                        cancel();
                        return;
                    }
                    try {
                        if (teamLocOne != null && teamLocOne.isWorldLoaded()
                                && p.getWorld().equals(teamLocOne.getWorld())
                                && p.getLocation().distanceSquared(teamLocOne) > 0.05) {
                            p.teleportAsync(teamLocOne);
                        }
                    } catch (Exception ignored) {
                    }
                }
                for (Player p : teamTwo) {
                    if (plugin.getExtendedPlayer(p) == null ||
                            plugin.getExtendedPlayer(p).getPlayerDuelInstance() == null ||
                            plugin.getExtendedPlayer(p).getPlayerDuelInstance().getStatus().equals("Cancel")) {
                        emergencyCancel();
                        cancel();
                        return;
                    }
                    try {
                        if (teamLocTwo != null && teamLocTwo.isWorldLoaded()
                                && p.getWorld().equals(teamLocTwo.getWorld())
                                && p.getLocation().distanceSquared(teamLocTwo) > 0.05) {
                            p.teleportAsync(teamLocTwo);
                        }
                    } catch (Exception ignored) {
                    }
                }

                List<Player> curList = java.util.Arrays.asList(merge(teamOne, teamTwo));
                for (Player p : plugin.getDuelSettings().countdown().readyUp() ? curList : java.util.List.<Player>of()) {
                    boolean sneaks = p.isSneaking();
                    boolean was = lastSneak.getOrDefault(p.getUniqueId(), false);
                    if (sneaks && !was) {
                        boolean wasReady = readyToggles.contains(p.getUniqueId());
                        if (wasReady)
                            readyToggles.remove(p.getUniqueId());
                        else {
                            readyToggles.add(p.getUniqueId());
                            if (!firstToggleSoundPlayed) {
                                firstToggleSoundPlayed = true;
                                for (Player all : curList) {
                                    all.playSound(all, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.5f, 1.2f);
                                }
                            }
                        }
                    }
                    lastSneak.put(p.getUniqueId(), sneaks);

                    p.sendActionBar(Messages.text(p, "messages.match-hud.skip-countdown", readyToggles.size(), curList.size()));
                }

                if (!skipped && readyToggles.size() >= curList.size() && !curList.isEmpty()) {
                    skipped = true;
                    ticksLeft = 0;
                    for (Player p : curList) {
                        p.sendTitle(Messages.text(p, "messages.duel-instance.skipped"), Messages.text(p, "messages.duel-instance.everyone-readied-up-starting-now"), 0, 20, 0);
                    }
                }

                // ----- per‑second title update ----------------------------------
                if (ticksLeft % 20 == 0) {
                    int secondsLeft = ticksLeft / 20;
                    merged[0] = merge(teamOne, teamTwo);
                    for (Player p : merged[0]) {
                        p.playSound(p, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                        p.sendTitle(
                                plugin.getMessageAPI().toColorLang(p, "duel_title"),
                                plugin.getMessageAPI().toColorLang(p, "duel_subtitle", new String[] {
                                        "" + getTeamLength(p),
                                        "" + getEnemyLength(p),
                                        "" + secondsLeft
                                }),
                                0, 20, 0);
                    }
                }

                // ----- TIER AURAS (Ranked Only) -----
                if (isRanked && ticksLeft % 2 == 0) {
                    for (Player p : curList) {
                        spawnTierAura(p);
                    }
                }

                // ----- In-Game Action Bar (Post-countdown or during) -----
                if (isRanked) {
                    if (ticksLeft <= 0) {
                        updateRankedHUD();
                    }
                }

                // ----- timer finished -------------------------------------------
                if (ticksLeft-- <= 0) {
                    merged[0] = merge(teamOne, teamTwo);
                    for (Player p : merged[0]) {
                        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
                        PlayerDuelInstance pdi = ep.getPlayerDuelInstance();
                        pdi.setFlexibleInventory(
                                new FlexibleInventory().captureInventory(p));
                    }
                    cancel();
                    startRound(skipped); // enable PvP
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // start immediately, run every tick
        return true;
    }

    /**
     * This method indicates that a team has won a round.
     * 
     * @param team The team that won the round in an integer format. (Team 1 or 2)
     */
    public void finishRound(int team) {

        if (team == 1)
            ++roundsWonTeamOne;
        else if (team == 2)
            ++roundsWonTeamTwo;
        else
            return;

        // arena rebuild if needed
        boolean build = false;
        for (Player p : merge(teamOne, teamTwo)) {
            if (!build)
                build = plugin.getExtendedPlayer(p)
                        .getPlayerDuelInstance().getKit().isBuild();
            p.playSound(p, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.5f);
        }
        boolean buildx = (kitTeamOne.isBuild() || kitTeamTwo.isBuild());
        boolean pk = (kitTeamOne.isPk() || kitTeamTwo.isPk());
        if (usingPolarArena() && (buildx || pk)) {
            nextPolarReservation = DuelManager.polarArenaManager.reserveArena(arena);
        } else if (build) {
            DuelManager.schemManager.enqueueSchematic(
                    motherLocation, getSchematicFile(arena), true);
        }

        /* ── status swap + HP recap ───────────────────────────── */
        Player[] fighters = merge(teamOne, teamTwo);
        List<Player> alive = new ArrayList<>();
        for (Player p : fighters) {
            ExtendedPlayer ep = plugin.getExtendedPlayer(p);
            if ("InGame".equals(ep.getPlayerDuelInstance().getStatus()))
                alive.add(p);
            ep.getPlayerDuelInstance().setStatus("Countdown");
            p.getInventory().clear();
        }
        for (Player t : alive) {
            broadcast("duel_hp", t.getName(),
                    String.format("%.1f", t.getHealth()));
        }

        /* ── rounds left message ─────────────────────────────── */
        String colour = team == 1 ? "&cRed" : "&9Blue";
        int left = team == 1 ? maxRounds - roundsWonTeamOne
                : maxRounds - roundsWonTeamTwo;

        if (left == 0) {
            finish(team);
            return;
        }

        finishTelemetry(team);
        broadcast("duel_round_left",
                String.valueOf(team), String.valueOf(left), colour);

        clearEntitiesInArena(motherLocation, arena);

        /* ── inter-round countdown ───────────────────── */
        new BukkitRunnable() {
            int ticks = plugin.getDuelSettings().countdown().betweenRoundsSeconds() * 20;
            boolean skipped = false;
            boolean firstToggleSoundPlayed = false;
            java.util.Set<UUID> readyToggles = new java.util.HashSet<>();
            java.util.Map<UUID, Boolean> lastSneak = new java.util.HashMap<>();

            public void run() {
                if (canceled || finished) {
                    cancel();
                    return;
                }

                Player[] currentPlayers = merge(teamOne, teamTwo);
                List<Player> curList = java.util.Arrays.asList(currentPlayers);
                for (Player p : plugin.getDuelSettings().countdown().readyUp() ? curList : java.util.List.<Player>of()) {
                    boolean sneaks = p.isSneaking();
                    boolean was = lastSneak.getOrDefault(p.getUniqueId(), false);
                    if (sneaks && !was) {
                        boolean wasReady = readyToggles.contains(p.getUniqueId());
                        if (wasReady)
                            readyToggles.remove(p.getUniqueId());
                        else {
                            readyToggles.add(p.getUniqueId());
                            if (!firstToggleSoundPlayed) {
                                firstToggleSoundPlayed = true;
                                for (Player all : curList) {
                                    all.playSound(all, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.5f, 1.2f);
                                }
                            }
                        }
                    }
                    lastSneak.put(p.getUniqueId(), sneaks);

                    p.sendActionBar(Messages.text(p, "messages.match-hud.skip-countdown", readyToggles.size(), curList.size()));
                }

                // Keep the early portion of the reset available for the next arena copy.
                if (!skipped && readyToggles.size() >= curList.size() && !curList.isEmpty()
                        && ticks <= plugin.getDuelSettings().countdown().betweenRoundsReadyWindowSeconds() * 20) {
                    skipped = true;
                    ticks = 0;
                    for (Player p : currentPlayers) {
                        p.sendTitle(Messages.text(p, "messages.duel-instance.skipped"), Messages.text(p, "messages.duel-instance.everyone-readied-up-starting-now"), 0, 20, 10);
                    }
                }

                if (ticks % 20 == 0) {
                    for (Player p : merge(teamOne, teamTwo)) {
                        p.playSound(p, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                    }
                    if (ticks != 0)
                        broadcast("duel_count", String.valueOf((ticks / 20)));
                }

                // Call this wherever you were sending the titles
                {
                    int t1 = getRoundsWonTeamOne();
                    int t2 = getRoundsWonTeamTwo();

                    Player[] winners = (team == 1) ? teamOne : teamTwo;
                    Player[] losers = (team == 1) ? teamTwo : teamOne;

                    boolean prelude = ticks > 40; // 2 seconds = 40 ticks
                    String seconds = String.valueOf(Math.max(0, ticks / 20));
                    if (ticks != 0) {
                        if (prelude && (ticks % 20 == 0)) {
                            // Show "Round Won/Lost!"
                            sendTitleTo(winners, "§a§lRound Won!",
                                    "§7Score: §a" + (team == 1 ? t1 : t2) + " §7- §c" + (team == 1 ? t2 : t1));
                            sendTitleTo(losers, "§c§lRound Lost!",
                                    "§7Score: §a" + (team == 1 ? t2 : t1) + " §7- §c" + (team == 1 ? t1 : t2));
                        } else if (ticks % 20 == 0) {
                            // Show countdown
                            sendTitleTo(winners, "§d§l" + seconds,
                                    "§7Score: §a" + (team == 1 ? t1 : t2) + " §7- §c" + (team == 1 ? t2 : t1));
                            sendTitleTo(losers, "§d§l" + seconds,
                                    "§7Score: §a" + (team == 1 ? t2 : t1) + " §7- §c" + (team == 1 ? t1 : t2));
                        }
                    }

                }

                if (ticks-- == 0) {

                    boolean build = (kitTeamOne.isBuild() || kitTeamTwo.isBuild());
                    boolean pk = (kitTeamOne.isPk() || kitTeamTwo.isPk());
                    if (usingPolarArena() && nextPolarReservation != null && (build || pk)) {
                        switchToNextPolarReservation();
                    }

                    clearEntitiesInArena(motherLocation, arena);
                    createAndStartMatchRecord(); // NEW ROUND READY
                    for (Player p : fighters) {

                        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
                        PlayerDuelInstance pdi = ep.getPlayerDuelInstance();

                        pdi.setStatus("InGame");

                        Location dest = pdi.getTeam() == 1
                                ? teamLocOne
                                : teamLocTwo;

                        // Apply inventory AFTER the async teleport completes so
                        // cross-world player data saving doesn't wipe items.
                        p.teleportAsync(dest).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.resetPlayer(p);
                            pdi.getFlexibleInventory().giveInventory(p);
                            TitleFx.playFight(plugin, p, "");
                            applyEffects(p, pdi.getKit());
                        }));
                    }

                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Finishes the game, announces the winner, waits 3 s, then returns everyone
     * to the lobby and frees the arena.
     * 
     * @param team 1 = red, 2 = blue.
     */
    void finish(int team) {
        if (finished)
            return;
        finished = true;
        // canceled = true; // FIX: Only cancel if explicitly aborted, not on normal
        // finish
        GlobalDuelCache.availableInstances.remove(this);

        String winCol = team == 1 ? "&cRed" : "&9Blue";
        String loseCol = team == 2 ? "&cRed" : "&9Blue";
        int winR = team == 1 ? roundsWonTeamOne : roundsWonTeamTwo;
        int loseR = team == 2 ? roundsWonTeamOne : roundsWonTeamTwo;

        clearEntitiesInArena(motherLocation, arena);

        // ELO CALCULATION FOR TITLES
        int oldElo1 = 0, newElo1 = 0, oldElo2 = 0, newElo2 = 0;
        String tier1 = "", tier2 = "";
        if (isRanked && teamOne.length > 0 && teamTwo.length > 0) {
            var rs1 = plugin.getExtendedPlayer(teamOne[0]).getMainPlayer().getKitRankedStats(kitTeamOne.getName());
            var rs2 = plugin.getExtendedPlayer(teamTwo[0]).getMainPlayer().getKitRankedStats(kitTeamOne.getName());
            oldElo1 = rs1.getElo();
            oldElo2 = rs2.getElo();
            tier1 = rs1.getTierDisplay();
            tier2 = rs2.getTierDisplay();

            newElo1 = org.albi.core.modules.Duels.ranked.EloCalculator.calculate(oldElo1, oldElo2, team == 1, !rs1.isPlacementsFinished());
            newElo2 = org.albi.core.modules.Duels.ranked.EloCalculator.calculate(oldElo2, oldElo1, team == 2, !rs2.isPlacementsFinished());
        }

        /* stats + immediate announcement */
        for (Player p : merge(teamOne, teamTwo)) {
            p.playSound(p, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
            boolean win = (team == 1 && isOnTeam(p, teamOne)) ||
                    (team == 2 && isOnTeam(p, teamTwo));
            plugin.resetPlayer(p, false);

            if (isRanked) {
                int oldE = isOnTeam(p, teamOne) ? oldElo1 : oldElo2;
                int newE = isOnTeam(p, teamOne) ? newElo1 : newElo2;
                String tier = isOnTeam(p, teamOne) ? tier1 : tier2;
                String c = win ? "§a" : "§c";

                if (win) {
                    p.sendTitle(Messages.text(p, "messages.duel-instance.victory"), Messages.text(p, "messages.duel-instance.finish", tier, c, oldE, c, newE), 0, 60, 20);
                    p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    p.getWorld().strikeLightningEffect(p.getLocation()); // Winner lightning
                    addWin(p);
                } else {
                    p.sendTitle(Messages.text(p, "messages.duel-instance.defeat"), Messages.text(p, "messages.duel-instance.finish", tier, c, oldE, c, newE), 0, 60, 20);
                    addLoss(p);
                }
            } else {
                if (win) {
                    p.sendTitle(Messages.text(p, "messages.duel-instance.you-won"), Messages.text(p, "messages.duel-instance.score", winR, loseR), 0, 20, 10);
                    addWin(p);
                } else {
                    p.sendTitle(Messages.text(p, "messages.duel-instance.you-lost"), Messages.text(p, "messages.duel-instance.score", loseR, winR), 0, 20, 10);
                    addLoss(p);
                }
            }

            addTimePlayed(p);
            plugin.getExtendedPlayer(p).getPlayerDuelInstance()
                    .setStatus("Countdown");
        }

        // RANKED RECAP BROADCAST
        if (isRanked && teamOne.length > 0 && teamTwo.length > 0) {
            Player winnerP = (team == 1) ? teamOne[0] : teamTwo[0];
            int winOld = (team == 1) ? oldElo1 : oldElo2;
            int winNew = (team == 1) ? newElo1 : newElo2;
            String winTier = (team == 1) ? tier1 : tier2;

            String recap = "\n§6§lRECAP §8» §f" + winnerP.getName() + " §8(§7" + winNew + "§8)\n" +
                          "§7Elo Progress: §a" + winOld + " §8» §a" + winNew + " §8(§7" + winTier + "§8)\n";
            broadcastRecap(recap);
        }

        if (isRanked && !canceled) {
            handleRankedResults(team);
        }
        if (!canceled) finishTelemetry(team);

        broadcast("duel_over", winCol, loseCol,
                String.valueOf(winR), String.valueOf(loseR));

        if (onFinishCallback != null) {
            onFinishCallback.accept(team);
        }

        /* delayed cleanup */
        new BukkitRunnable() {
            int ticks = plugin.getDuelSettings().countdown().returnDelayTicks();

            public void run() {
                if (canceled) {
                    cancel();
                    return;
                }
                if (--ticks > 0)
                    return;

                for (Player p : merge(teamOne, teamTwo)) {
                    try {
                        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
                        if (ep != null)
                            ep.setPlayerDuelInstance(null);
                        returnFighterToLobby(p);
                    } catch (Exception ignored) {
                    }
                }
                sendSpectatorsHome();
                freeArena();
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * A player has left, factor it in and check if a game win is needed.
     * 
     * @param p The player that has left.
     */
    public void leave(Player p) {
        leave(p, true);
    }

    public void disconnect(Player p) {
        leave(p, false);
    }

    private void leave(Player p, boolean returnToLobby) {
        int team = 0;
        if (isOnTeam(p, teamOne))
            team = 1;
        else if (isOnTeam(p, teamTwo))
            team = 2;

        if (onLeaveCallback != null) {
            try {
                onLeaveCallback.accept(p);
            } catch (Exception ignored) {
            }
        }

        for (Player pp : merge(teamOne, teamTwo)) {
            if (!pp.getUniqueId().equals(p.getUniqueId())) {
                plugin.getMessageAPI().sendMessageLang(pp, "duel_leave", new String[] {
                        p.getName()
                });
            }
        }

        if (team == 1 && teamOne.length <= 1) {
            finish(2);
        } else if (team == 2 && teamTwo.length <= 1) {
            finish(1);
        }

        if (plugin.getExtendedPlayer(p) != null) {
            plugin.getExtendedPlayer(p).setPlayerDuelInstance(null);
        }

        if (returnToLobby) {
            try {
                returnFighterToLobby(p);
            } catch (Exception ignored) {
            }
        }

        removeFromTeam(p);
    }

    private void returnFighterToLobby(Player player) {
        var spectatorService = DuelManager.getSpectators();
        if (spectatorService != null) {
            spectatorService.sendToLobby(player, returnServers.get(player.getUniqueId()));
        } else if (player.isOnline()) {
            plugin.resetPlayer(player);
            plugin.teleportToLobby(player);
        }
    }

    private void handleRankedResults(int winningTeam) {
        Player winner = null;
        if (winningTeam == 1 && teamOne.length > 0)
            winner = teamOne[0];
        else if (winningTeam == 2 && teamTwo.length > 0)
            winner = teamTwo[0];

        Player loser = null;
        if (winningTeam == 1 && teamTwo.length > 0)
            loser = teamTwo[0];
        else if (winningTeam == 2 && teamOne.length > 0)
            loser = teamOne[0];

        if (winner == null || loser == null)
            return;

        MainPlayer mpWin = plugin.getExtendedPlayer(winner).getMainPlayer();
        MainPlayer mpLose = plugin.getExtendedPlayer(loser).getMainPlayer();

        String kitName = kitTeamOne.getName();
        RankedStats rsWin = mpWin.getKitRankedStats(kitName);
        RankedStats rsLose = mpLose.getKitRankedStats(kitName);
        RankedStats rsWinOverall = mpWin.getOverallRankedStats();
        RankedStats rsLoseOverall = mpLose.getOverallRankedStats();

        int oldEloWin = rsWin.getElo();
        int oldEloLose = rsLose.getElo();

        // Kit Elo Update
        int newEloWin = EloCalculator.calculate(oldEloWin, oldEloLose, true, !rsWin.isPlacementsFinished());
        int newEloLose = EloCalculator.calculate(oldEloLose, oldEloWin, false, !rsLose.isPlacementsFinished());

        rsWin.setElo(newEloWin);
        rsWin.incWins();
        rsWin.incPlacementMatches();
        rsLose.setElo(newEloLose);
        rsLose.incLosses();
        rsLose.incPlacementMatches();

        // Overall Elo Update
        int oldOvW = rsWinOverall.getElo();
        int oldOvL = rsLoseOverall.getElo();
        int newOvW = EloCalculator.calculate(oldOvW, oldOvL, true, !rsWinOverall.isPlacementsFinished());
        int newOvL = EloCalculator.calculate(oldOvL, oldOvW, false, !rsLoseOverall.isPlacementsFinished());

        rsWinOverall.setElo(newOvW);
        rsWinOverall.incWins();
        rsWinOverall.incPlacementMatches();
        rsLoseOverall.setElo(newOvL);
        rsLoseOverall.incLosses();
        rsLoseOverall.incPlacementMatches();

        mpWin.markRankedDirty();
        mpLose.markRankedDirty();

        String winMsg = "§a§lRANKED! §7Elo: §f" + oldEloWin + " §8» §a" + newEloWin + " §7(+" + (newEloWin - oldEloWin)
                + ")";
        String loseMsg = "§c§lRANKED! §7Elo: §f" + oldEloLose + " §8» §c" + newEloLose + " §7("
                + (newEloLose - oldEloLose) + ")";

        winner.sendMessage(winMsg);
        loser.sendMessage(loseMsg);

        if (matchRecord != null) {
            matchRecord.setWinner(winner.getUniqueId());
            if (winner.getUniqueId().equals(matchRecord.getPlayer1())) {
                matchRecord.setEloChange1(newEloWin - oldEloWin);
                matchRecord.setEloChange2(newEloLose - oldEloLose);
            } else {
                matchRecord.setEloChange1(newEloLose - oldEloLose);
                matchRecord.setEloChange2(newEloWin - oldEloWin);
            }
        }
    }

    /**
     * A player has died, factor it in and check if a round win is needed.
     * 
     * @param p The player that has died.
     */
    public void death(Player p, Player killer) {
        p.setGameMode(GameMode.SPECTATOR);

        p.playSound(p, Sound.ENTITY_VILLAGER_DEATH, 1, 1);
        p.playSound(p, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
        if (killer != null && killer != p) {
            killer.playSound(killer, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
            p.playSound(p, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.5f);
        }

        plugin.getExtendedPlayer(p).getPlayerDuelInstance().setStatus("Dead");
        addDeath(p);
        if (killer != p)
            addKill(killer);
        for (Player pp : merge(teamOne, teamTwo)) {

            if (killer != null && !killer.getUniqueId().equals(p.getUniqueId()))
                plugin.getMessageAPI().sendMessageLang(pp, "duel_kill", new String[] {
                        p.getName(),
                        killer.getName()
                });
            else
                plugin.getMessageAPI().sendMessageLang(pp, "duel_kill_themself", new String[] {
                        p.getName()
                });
        }

        if (!killer.getUniqueId().equals(p.getUniqueId()) && killer != null && p != null) {
            MainPlayer mp = plugin.getExtendedPlayer(killer).getMainPlayer();

            if (mp != null)
                mp.addXp(1);

            plugin.getMessageAPI().sendMessageLang(
                    killer,
                    "duel_death_xp");
        }

        long alive1 = Arrays.stream(teamOne)
                .filter(pp -> !"Dead".equals(plugin.getExtendedPlayer(pp).getPlayerDuelInstance().getStatus())).count();
        long alive2 = Arrays.stream(teamTwo)
                .filter(pp -> !"Dead".equals(plugin.getExtendedPlayer(pp).getPlayerDuelInstance().getStatus())).count();

        if (alive1 == 0)
            finishRound(2);
        else if (alive2 == 0)
            finishRound(1);

    }

    // ──────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ──────────────────────────────────────────────────────────────────────
    private void startRound() {
        startRound(false);
    }

    private void startRound(boolean wasSkipped) {
        Player[] merged = merge(teamOne, teamTwo);

        for (Player p : merged) {
            if (!wasSkipped) {
                TitleFx.playFight(plugin, p, "");
            }

            var pdi = plugin.getExtendedPlayer(p).getPlayerDuelInstance();
            pdi.setStatus("InGame");

            addRoundPlay(p);
            roundStartTime.put(p.getUniqueId(), System.currentTimeMillis());

            /* Apply metadata effects */
            applyEffects(p, pdi.getKit());
        }

        GlobalDuelCache.availableInstances.add(this);
        if (isRanked) {
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    if (finished || canceled) {
                        cancel();
                        return;
                    }
                    updateRankedHUD();
                }
            }.runTaskTimer(plugin, 20L, 20L);
        }
    }

    // EMERGENCY SHUT‑DOWN
    private void emergencyCancel() {
        if (canceled)
            return;
        canceled = true;
        clearEntitiesInArena(motherLocation, arena);
        GlobalDuelCache.availableInstances.remove(this);

        for (Player p : merge(teamOne, teamTwo)) {
            ExtendedPlayer ep = plugin.getExtendedPlayer(p);
            ep.setPlayerDuelInstance(null);
            returnFighterToLobby(p);
            plugin.getMessageAPI().sendMessageLang(p, "duel_cancelled");
        }

        sendSpectatorsHome();
        freeArena();
    }

    private boolean usingPolarArena() {
        return DuelManager.polarArenaManager != null && DuelManager.polarArenaManager.isPolarArena(arena);
    }

    private Location buildSpawn(World world, double[] rel) {
        if (world == null || rel == null || rel.length < 5)
            return null;
        if (usingPolarArena()) {
            return new Location(world, rel[0], rel[1], rel[2], (float) rel[3], (float) rel[4]);
        }
        return new Location(
                world,
                motherLocation.getX() + rel[0],
                motherLocation.getY() + rel[1],
                motherLocation.getZ() + rel[2],
                (float) (motherLocation.getYaw() + rel[3]),
                (float) (motherLocation.getPitch() + rel[4]));
    }

    private void switchToNextPolarReservation() {
        if (nextPolarReservation == null)
            return;
        PolarArenaReservation old = polarReservation;
        polarReservation = nextPolarReservation;
        nextPolarReservation = null;
        motherLocation = polarReservation.getMotherLocation();
        World world = motherLocation.getWorld();
        teamLocOne = buildSpawn(world, arena.getSpawn1());
        teamLocTwo = buildSpawn(world, arena.getSpawn2());
        var spectatorService = DuelManager.getSpectators();
        if (spectatorService != null) spectatorService.moveToNextRound(this);
        if (old != null) {
            DuelManager.polarArenaManager.releaseArena(old, true);
        }
    }

    // Release the motherLocation back to the available pool
    private void freeArena() {
        if (arena == null || motherLocation == null)
            return;

        if (polarReservation != null || nextPolarReservation != null || arena.isPolarArena()) {
            if (polarReservation != null) {
                DuelManager.polarArenaManager.releaseArena(polarReservation, true);
                polarReservation = null;
            }
            if (nextPolarReservation != null) {
                DuelManager.polarArenaManager.releaseArena(nextPolarReservation, true);
                nextPolarReservation = null;
            }
            return;
        }

        List<Location> used = GlobalDuelCache.usedArenas.get(arena);
        if (used != null)
            used.remove(motherLocation);
    }

    private Location findArenaSpot(Arena arena) {

        World world = plugin.getServer().getWorld(arena.getName() + "-duel");

        if (world == null) {
            plugin.getLogger().severe("[Duels] World \"" + arena.getName() + "-duel\" not loaded!");
            return null;
        }

        if (!GlobalDuelCache.availableArenas.containsKey(arena)) {
            // It must not be in "used" array either.
            GlobalDuelCache.availableArenas.put(arena, new ArrayList<>());
            GlobalDuelCache.usedArenas.put(arena, new ArrayList<>());
        }

        ArrayList<Location> available = GlobalDuelCache.availableArenas.get(arena);
        ArrayList<Location> used = GlobalDuelCache.usedArenas.get(arena);

        // With the epic help of CHATGPT!
        if (available.isEmpty()) {
            int maxArenas = arena.getMaxArenas();
            int zOffset = arena.getOffsetZAxis();
            int xOffset = arena.getOffsetXAxis();
            int maxPerLine = arena.getMaxPerLine();
            if (maxPerLine <= 0) return null;

            Location base = new Location(world, 0, 100, 0);

            for (int i = 0; i < maxArenas; i++) {
                int row = i / maxPerLine;
                int col = i % maxPerLine;

                Location arenaSpot = base.clone().add(
                        row * xOffset,
                        0,
                        col * zOffset);

                available.add(arenaSpot);
            }
        }

        for (Location potential : available) {
            if (!used.contains(potential)) {
                used.add(potential);
                return potential;
            }
        }
        return null;
    }

    /*
     * ───────────────────────────────────────────────────────────────────────────
     */
    /* Team‑size helpers */
    /*
     * ───────────────────────────────────────────────────────────────────────────
     */
    private boolean isOnTeam(Player target, Player[] team) {
        UUID id = target.getUniqueId();
        for (Player p : team) {
            if (p.getUniqueId().equals(id))
                return true;
        }
        return false;
    }

    /** How many players are on the caller’s own team? */
    private int getTeamLength(Player player) {
        if (isOnTeam(player, teamOne))
            return teamOne.length;
        if (isOnTeam(player, teamTwo))
            return teamTwo.length;
        return 0; // not in this duel (shouldn’t happen)
    }

    /** How many players are on the opposing team? */
    private int getEnemyLength(Player player) {
        if (isOnTeam(player, teamOne))
            return teamTwo.length;
        if (isOnTeam(player, teamTwo))
            return teamOne.length;
        return 0;
    }

    /**
     * The schematic file.
     * 
     * @param arena The arena you want the schematic file to.
     * @return Parsed schematic file.
     */
    private File getSchematicFile(Arena arena) {
        return getSchematicFile(plugin, arena);
    }

    private static File getSchematicFile(Core plugin, Arena arena) {
        return new File(plugin.getDataFolder(),
                "schems" + File.separator + "duels" + File.separator + arena.getSchematic());
    }

    /**
     * Clears all non-player entities within the arena boundaries.
     *
     * @param base  The mother location (base) of the arena.
     * @param arena The arena configuration which holds corner parameters.
     */
    private void clearEntitiesInArena(Location base, Arena arena) {
        if (base == null || arena == null)
            return;

        try {
            if (!base.isWorldLoaded())
                return;
        } catch (Exception ignored) {
        }

        double[] params = arena.getCornerParameters();
        if (params == null || params.length < 4)
            return;

        double minX = usingPolarArena() ? Math.min(params[0], params[2]) : base.getX() + params[0];
        double minZ = usingPolarArena() ? Math.min(params[1], params[3]) : base.getZ() + params[1];
        double maxX = usingPolarArena() ? Math.max(params[0], params[2]) : base.getX() + params[2];
        double maxZ = usingPolarArena() ? Math.max(params[1], params[3]) : base.getZ() + params[3];

        World world = base.getWorld();
        if (world == null)
            return;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Player)
                continue; // Skip players.
            Location loc = entity.getLocation();
            double x = loc.getX();
            double z = loc.getZ();
            if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                entity.remove();
            }
        }
    }

    /**
     * Remove a player from their team array.
     *
     * @param target the player to remove
     * @return 1 if the player was in team One, 2 if in team Two, 0 if in neither
     */
    private int removeFromTeam(Player target) {
        UUID id = target.getUniqueId();

        // ── team one ───────────────────────────────────────────────────────
        for (int i = 0; i < teamOne.length; i++) {
            if (teamOne[i].getUniqueId().equals(id)) {
                teamOne = shrinkArray(teamOne, i); // remove index i
                return 1;
            }
        }

        // ── team two ───────────────────────────────────────────────────────
        for (int i = 0; i < teamTwo.length; i++) {
            if (teamTwo[i].getUniqueId().equals(id)) {
                teamTwo = shrinkArray(teamTwo, i);
                return 2;
            }
        }
        return 0; // not found
    }

    /* utility: returns a new array with element at idx removed */
    private static Player[] shrinkArray(Player[] src, int idx) {
        Player[] dst = new Player[src.length - 1];
        System.arraycopy(src, 0, dst, 0, idx); // before idx
        System.arraycopy(src, idx + 1, dst, idx, src.length - idx - 1); // after idx
        return dst;
    }

    // GETTERS

    public int amountOfPlayers() {
        return teamOne.length + teamTwo.length;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public Arena getArena() {
        return arena;
    }

    public Kit getKitTeamOne() {
        return kitTeamOne;
    }

    public Kit getKitTeamTwo() {
        return kitTeamTwo;
    }

    public Location getMotherLocation() {
        return motherLocation;
    }

    public World getActiveWorld() {
        return motherLocation != null ? motherLocation.getWorld() : null;
    }

    public Player[] getTeamOnePlayers() {
        return teamOne == null ? new Player[0] : Arrays.copyOf(teamOne, teamOne.length);
    }

    public Player[] getTeamTwoPlayers() {
        return teamTwo == null ? new Player[0] : Arrays.copyOf(teamTwo, teamTwo.length);
    }

    public boolean isOnRedTeam(Player player) {
        return player != null && teamOne != null && isOnTeam(player, teamOne);
    }

    public boolean isOnBlueTeam(Player player) {
        return player != null && teamTwo != null && isOnTeam(player, teamTwo);
    }

    public boolean isSpectating(Player player) {
        if (player == null)
            return false;
        UUID targetId = player.getUniqueId();
        for (Player spectator : spectators) {
            if (spectator != null && spectator.getUniqueId().equals(targetId)) {
                return true;
            }
        }
        return false;
    }

    private Kit personalKitFor(Player p, ExtendedPlayer ep) {
        MainPlayer mp = ep.getMainPlayer();
        if (mp == null)
            return kitTeamOne; // very unlikely fallback

        int slot = ep.getSelectedKit().getOrDefault(p.getUniqueId(), 1);
        CustomKit ck = mp.getKits()[slot];
        if (ck == null)
            return kitTeamOne; // fallback again

        Kit k = ck.toKit();
        k.setPk(false); // prevent recursion
        return k;
    }

    /**
     * Builds a game‑specific stat key like "Axe 1v1" or "Custom Mismatch".
     */
    private String buildStatName(Player p) {
        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep == null)
            return "Unknown";

        PlayerDuelInstance pdi = ep.getPlayerDuelInstance();
        Kit kit = pdi != null ? pdi.getKit() : null;
        if (kitTeamOne == null || kitTeamTwo == null)
            kit = null;
        boolean customMode = (kit == null) || kit.isPk() || kit.getName().toLowerCase().startsWith("custom");

        String base = customMode ? "Custom" : capFirst(kit.getName());
        return base + " " + teamSizeLabel();
    }

    private String capFirst(String in) {
        if (in == null || in.isEmpty())
            return "";
        in = in.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(in.charAt(0)) + in.substring(1);
    }

    private String teamSizeLabel() {
        int a = teamOneLengthBegin;
        int b = teamTwoLengthBegin;
        return ((a == b && a < 5 && a > 0) ? a + "v" + b : "Mismatch");
    }

    /* ─────────────────────────────────────────────────────── */
    /* Gameplay stats are updated before the quit or shutdown snapshot. */
    /* ─────────────────────────────────────────────────────── */

    private void addWin(Player p) {
        String stat = buildStatName(p);
        var data = plugin.getCache().getExtendedPlayer(p);
        if (data != null && data.getStat() != null) data.getStat().addWin(stat);
    }

    private void addLoss(Player p) {
        String stat = buildStatName(p);
        var data = plugin.getCache().getExtendedPlayer(p);
        if (data != null && data.getStat() != null) data.getStat().resetWinStreak(stat);
    }

    private void addDeath(Player p) {
        String stat = buildStatName(p);
        var data = plugin.getCache().getExtendedPlayer(p);
        if (data != null && data.getStat() != null) data.getStat().addDeath(stat);
    }

    /** Adds just one win‑streak increment without counting as a win. */
    private void addWinstreak(Player p) {
        String stat = buildStatName(p);
        var data = plugin.getCache().getExtendedPlayer(p);
        if (data != null && data.getStat() != null) data.getStat().addWin(stat); // addWin already increases streak internally
    }

    private void addRoundPlay(Player p) {
        String stat = buildStatName(p);
        var data = plugin.getCache().getExtendedPlayer(p);
        if (data != null && data.getStat() != null) data.getStat().addRound(stat);
    }

    private void addTimePlayed(Player p) {
        long start = roundStartTime.getOrDefault(p.getUniqueId(), System.currentTimeMillis());
        int seconds = (int) ((System.currentTimeMillis() - start) / 1000);
        if (seconds <= 0)
            return;
        String stat = buildStatName(p);
        var data = plugin.getCache().getExtendedPlayer(p);
        if (data != null && data.getStat() != null) data.getStat().addTime(stat, seconds);
    }

    private void addKill(Player p) {
        String stat = buildStatName(p);
        var data = plugin.getCache().getExtendedPlayer(p);
        if (data != null && data.getStat() != null) data.getStat().addKill(stat);
    }

    /** Sends a lang-keyed message to fighters **and** spectators. */
    private void broadcast(String langKey, String... placeholders) {
        Player[] everyone = getEveryone();
        String activeWorld = getActiveWorldName();
        for (Player p : everyone) {
            if (isPlayerInActiveWorld(p, activeWorld)) {
                plugin.getMessageAPI().sendMessageLang(p, langKey, placeholders);
            }
        }
    }

    private void broadcast(String message) {
        Player[] everyone = getEveryone();
        String activeWorld = getActiveWorldName();
        for (Player p : everyone) {
            if (isPlayerInActiveWorld(p, activeWorld)) {
                p.sendMessage(plugin.getMessageAPI().toColor(p, message));
            }
        }
    }

    private void broadcastTelemetryLink() {
        if (matchRecord == null)
            return;
        String id = matchRecord.getMatchId();
        String template = plugin.getConfig().getString("ranked.report_url_template", "");
        var url = ConfigValues.httpEndpoint(template.replace("{match_id}", id));
        String text = "§7Match ID: §f" + id;


        Player[] everyone = getEveryone();
        String activeWorld = getActiveWorldName();
        for (Player p : everyone) {
            if (!isPlayerInActiveWorld(p, activeWorld)) continue;
            if (url == null) {
                p.sendMessage(text);
            } else {
                plugin.getMessageAPI().sendUrlMessage(p, Messages.text(p, "messages.duel-instance.view-match", text), url.toString());
            }
        }
    }

    private void finishTelemetry(int winningTeam) {
        if (matchRecord == null) return;
        Player[] winners = winningTeam == 1 ? teamOne : teamTwo;
        if (winners.length > 0) matchRecord.setWinner(winners[0].getUniqueId());
        MatchTracker.get(plugin).stopTracking(matchRecord);
        broadcastTelemetryLink();
        matchRecord = null;
    }

    private Player[] getEveryone() {
        Player[] fighters = merge(teamOne, teamTwo);
        Player[] specs = spectators.toArray(new Player[0]);
        return merge(fighters, specs);
    }

    private String getActiveWorldName() {
        try {
            if (motherLocation != null && motherLocation.isWorldLoaded()) {
                return motherLocation.getWorld().getName();
            }
        } catch (Exception ignored) {
        }
        return arena != null ? arena.getName() + "-duel" : "unknown";
    }

    private boolean isPlayerInActiveWorld(Player p, String activeWorld) {
        return p.getWorld().getName().equals(activeWorld);
    }

    public void addSpectator(Player p) {
        if (!spectators.contains(p)) {
            spectators.add(p);
        }
        p.setGameMode(GameMode.SPECTATOR);
    }

    public void removeSpectator(Player player) {
        spectators.remove(player);
    }

    private void createAndStartMatchRecord() {
        int maxTelemetryRounds = ConfigValues.integer(plugin.getConfig(), "duels.telemetry.max_round_target", 3, 1, 100);
        boolean validForTelemetry = teamOne.length == 1 && teamTwo.length == 1 && maxRounds <= maxTelemetryRounds;
        boolean enabled = plugin.getConfig().getBoolean(isRanked ? "ranked.telemetry.enabled" : "duels.telemetry.enabled", true);
        if (enabled && (isRanked || telemetry) && validForTelemetry) {
            matchRecord = new RankedMatchRecord(
                    teamOne[0].getUniqueId(),
                    teamTwo[0].getUniqueId(),
                    teamOne[0].getName(),
                    teamTwo[0].getName(),
                    kitTeamOne.getName());
            MatchTracker.get(plugin).startTracking(matchRecord);
        } else if (telemetry) {
            this.telemetry = false;
        }
    }

    /** Resets every spectator, teleports them to lobby, and purges the list. */
    private void sendSpectatorsHome() {
        var spectatorService = DuelManager.getSpectators();
        if (spectatorService != null) spectatorService.closeMatch(this);
        for (Player spec : spectators) {
            ExtendedPlayer ep = plugin.getExtendedPlayer(spec);
            if (ep != null)
                ep.clearSpectatorStatus();
            plugin.resetPlayer(spec);
            plugin.teleportToLobby(spec);
        }
        spectators.clear();
    }

    private static void sendTitleTo(Player[] players, String title, String subtitle) {
        for (Player p : players) {
            p.sendTitle(title, subtitle, 0, 20, 10);
        }
    }

    private void applyEffects(Player p, Kit kit) {

        if (kit == null)
            return;

        Object raw = kit.getMetaData().get("effects");
        if (!(raw instanceof Iterable<?> list))
            return;

        for (Object obj : list) {

            if (!(obj instanceof org.albi.core.data.EffectMeta meta))
                continue;

            try {
                var type = meta.getEffect();

                if (type == null)
                    continue;

                p.addPotionEffect(
                        new org.bukkit.potion.PotionEffect(
                                type,
                                meta.getDuration() * 20,
                                meta.getAmplifier(),
                                meta.isAmbient(),
                                true,
                                true));

            } catch (Exception ignored) {
            }
        }
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isRanked() {
        return isRanked;
    }

    public Location getTeamLocOne() {
        return teamLocOne;
    }

    public Location getTeamLocTwo() {
        return teamLocTwo;
    }

    public int getRoundsWonTeamOne() {
        return roundsWonTeamOne;
    }

    public int getRoundsWonTeamTwo() {
        return roundsWonTeamTwo;
    }

    public int getScore(int team) {
        if (team == 1)
            return getRoundsWonTeamOne();
        else
            return getRoundsWonTeamTwo();
    }

    public int getTimeRunning() {
        long earliestStart = roundStartTime.values().stream()
                .min(Long::compare)
                .orElse(0L);
        if (earliestStart == 0L)
            return 0;
        return (int) ((System.currentTimeMillis() - earliestStart) / 1000);
    }

    private void spawnTierAura(Player p) {
        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep == null) return;
        var rs = ep.getMainPlayer().getKitRankedStats(kitTeamOne.getName());
        var tier = org.albi.core.modules.Duels.ranked.RankedTier.getTier(rs.getElo());
        
        Location loc = p.getLocation().add(0, 0.1, 0);
        World world = loc.getWorld();
        if (world == null) return;

        switch (tier) {
            case BRONZE:
                world.spawnParticle(Particle.ASH, loc, 3, 0.3, 0.1, 0.3, 0.02);
                break;
            case SILVER:
                world.spawnParticle(Particle.END_ROD, loc, 1, 0.2, 0.1, 0.2, 0.01);
                break;
            case GOLD:
                world.spawnParticle(Particle.FLAME, loc, 2, 0.3, 0.1, 0.3, 0.02);
                break;
            case PLATINUM:
                world.spawnParticle(Particle.GLOW, loc, 3, 0.3, 0.1, 0.3, 0.02);
                break;
            case DIAMOND:
            case MASTER:
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 2, 0.3, 0.1, 0.3, 0.02);
                break;
            case GRANDMASTER:
            case CHAMPION:
                world.spawnParticle(Particle.DRAGON_BREATH, loc, 3, 0.3, 0.1, 0.3, 0.02);
                world.spawnParticle(Particle.FLAME, loc, 1, 0.3, 0.1, 0.3, 0.02);
                break;
        }
    }

    private void updateRankedHUD() {
        if (!isRanked || teamOne.length == 0 || teamTwo.length == 0) return;
        
        Player p1 = teamOne[0];
        Player p2 = teamTwo[0];
        var main1 = plugin.getExtendedPlayer(p1).getMainPlayer();
        var main2 = plugin.getExtendedPlayer(p2).getMainPlayer();
        var rs1 = main1.getKitRankedStats(kitTeamOne.getName());
        var rs2 = main2.getKitRankedStats(kitTeamOne.getName());
        
        var stak1 = org.albi.core.modules.Duels.ranked.EloCalculator.calculateStakes(rs1.getElo(), rs2.getElo(), !rs1.isPlacementsFinished());
        var stak2 = org.albi.core.modules.Duels.ranked.EloCalculator.calculateStakes(rs2.getElo(), rs1.getElo(), !rs2.isPlacementsFinished());
        
        String time = formatTime(getTimeRunning());
        
        // Update fighters
        p1.sendActionBar(Messages.text(p1, "messages.duel-instance.vs-stakes", rs1.getElo(), rs2.getElo(), time, stak1.win, stak1.loss));
        p2.sendActionBar(Messages.text(p2, "messages.duel-instance.vs-stakes", rs2.getElo(), rs1.getElo(), time, stak2.win, stak2.loss));
        
        // Update spectators (P1 perspective)
        for (Player spec : spectators) {
            spec.sendActionBar(Messages.text(spec, "messages.duel-instance.spec-vs-stakes", rs1.getElo(), rs2.getElo(), time, stak1.win, stak1.loss));
        }
    }

    private void sendRankedActionBar(Player p) {
        // Redundant with updateRankedHUD, callers should use updateRankedHUD instead.
        updateRankedHUD();
    }

    private void broadcastRecap(String msg) {
        for (Player p : merge(teamOne, teamTwo)) {
            p.sendMessage(msg);
        }
        for (Player spec : spectators) {
            spec.sendMessage(msg);
        }
    }

    private String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
