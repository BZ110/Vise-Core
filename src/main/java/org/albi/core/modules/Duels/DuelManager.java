package org.albi.core.modules.Duels;

import org.albi.core.Core;
import org.albi.core.modules.Duels.AutomatedCombat.Manager;
import org.albi.core.modules.Duels.queue.DuelQueueModule;
import org.albi.core.modules.Duels.queue.RankedQueueModule;
import org.albi.core.modules.Duels.queue.global.GlobalMatchmaking;
import org.albi.core.modules.Duels.polar.PolarArenaManager;
import org.albi.core.modules.Module;
import org.albi.core.modules.Duels.helpers.DuelStorage;
import org.albi.core.modules.Duels.managers.CommandManager;
import org.albi.core.modules.Duels.managers.EventManager;
import org.albi.core.modules.Duels.ranked.RankedCommands;
import org.albi.core.modules.Duels.spectating.DuelSpectators;
import org.bukkit.entity.Player;

public final class DuelManager extends Module {

    public static SchematicManager schemManager;
    public static DuelStorage stor;
    public static PolarArenaManager polarArenaManager;
    private static DuelQueueModule duelQueueModule;
    private static RankedQueueModule rankedQueueModule;
    private static GlobalMatchmaking matchmaking;
    private static DuelSpectators spectators;

    public DuelManager(Core plugin) {
        super(plugin);
    }

    @Override
    public void init() {

        getPlugin().getLogger().warning("[Duels] Initializing");

        getPlugin().getLogger().info("[Duels] Loading Schematic Manager (Static Context)");
        schemManager = new SchematicManager(getPlugin());

        getPlugin().getLogger().info("[Duels] Loading DuelStorage (if not exists, create)");
        stor = (DuelStorage) getPlugin().getData().get("duelStorage");
        if (stor == null) {
            stor = new DuelStorage();
            getPlugin().getData().set("duelStorage", stor); // Set the reference to it.
        }

        getPlugin().getLogger().info("[Duels] Loading Polar Arena Manager");
        polarArenaManager = new PolarArenaManager(getPlugin());
        if (getPlugin().getConfig().getBoolean("use_polar_worlds") && !polarArenaManager.isEnabled()) {
            getPlugin().getLogger().warning("Polar arenas require PolarPaper. Schematic arenas can still be used.");
        }
        polarArenaManager.bootstrapStoredArenas();

        getPlugin().getLogger().info("[Duels] Loading Event Manager");
        new EventManager(getPlugin());

        getPlugin().getLogger().info("[Duels] Loading Command Manager");
        new CommandManager(getPlugin());

        Manager.initialize(getPlugin());

        getPlugin().getLogger().info("[Duels] Loading Slash Queue Manager");
        duelQueueModule = new DuelQueueModule(getPlugin());

        getPlugin().getLogger().info("[Duels] Loading Ranked Queue Module");
        rankedQueueModule = new RankedQueueModule(getPlugin());
        matchmaking = GlobalMatchmaking.create(getPlugin());
        spectators = new DuelSpectators(getPlugin(), getPlugin().getConfig().getBoolean("network.spectating.enabled", true) ? matchmaking : null);

        // Register Ranked Commands
        RankedCommands rankedCommands = new RankedCommands(getPlugin());
        getPlugin().getCommand("rankedlb").setExecutor(rankedCommands);
        getPlugin().getCommand("matchinfo").setExecutor(rankedCommands);
        getPlugin().getCommand("seegames").setExecutor(rankedCommands);
    }

    public static RankedQueueModule getRankedQueueModule() {
        return rankedQueueModule;
    }

    public static GlobalMatchmaking getMatchmaking() {
        return matchmaking;
    }

    public static DuelSpectators getSpectators() {
        return spectators;
    }

    public static boolean canStartLocalDuel(Core plugin, Player player) {
        var profile = plugin.getExtendedPlayer(player);
        return profile.isReady() && profile.getPlayerDuelInstance() == null && !profile.isSpectating()
                && (spectators == null || !spectators.busy(player));
    }

    public static void leaveQueues(Player player) {
        if (duelQueueModule != null) duelQueueModule.leaveQueue(player);
        if (rankedQueueModule != null) rankedQueueModule.leaveQueue(player);
    }

    public static void shutdownMatchmaking() {
        if (matchmaking != null) matchmaking.close();
        if (rankedQueueModule != null) rankedQueueModule.close();
        matchmaking = null;
        spectators = null;
    }

    public static DuelQueueModule getDuelQueueModule() {
        return duelQueueModule;
    }
}
