package org.albi.core.managers;

import org.albi.core.Core;
import org.albi.core.commands.*;
import org.albi.core.modules.Duels.ranked.RankedCommands;
import org.bukkit.command.CommandExecutor;

public class CommandManager {
    public CommandManager(Core plugin) {
        registerCommand(plugin, "bypass", new BypassCommand(plugin));
        registerCommand(plugin, "fly", new FlyCommand(plugin));
        registerCommand(plugin, "gma", new GMACommand(plugin));
        registerCommand(plugin, "gms", new GMSCommand(plugin));
        registerCommand(plugin, "gmsp", new GMSPCommand(plugin));
        registerCommand(plugin, "gmc", new GMCCommand(plugin));
        registerCommand(plugin, "vanish", new VanishCommand(plugin));
        registerCommand(plugin, "spawn", new SpawnCommand(plugin));
        registerCommand(plugin, "setspawn", new SetSpawnCommand(plugin));
        registerCommand(plugin, "setspawnitem", new SetSpawnItemCommand(plugin));
        registerCommand(plugin, "msg", new MsgCommand(plugin));
        registerCommand(plugin, "r", new ReplyCommand(plugin));
        registerCommand(plugin, "block", new BlockCommand(plugin));
        registerCommand(plugin, "unblock", new UnblockCommand(plugin));
        registerCommand(plugin, "rules", new RulesCommand(plugin));
        registerCommand(plugin, "lobbyplayers", new LobbyPlayersCommand(plugin));
        registerCommand(plugin, "travel", new TravelCommand(plugin));
        registerCommand(plugin, "health", new HealthCommand(plugin));
        registerCommand(plugin, "stats", new StatsCommand(plugin));
        registerCommand(plugin, "settings", new SettingsCommand(plugin));
        registerCommand(plugin, "goto", new GotoCommand(plugin));
        registerCommand(plugin, "setgoto", new SetGotoCommand(plugin));
        registerCommand(plugin, "store", new StoreCommand(plugin));
        registerCommand(plugin, "leaderboard", new LeaderboardCommand(plugin));
        registerCommand(plugin, "rtp", new RTPCommand(plugin));
        registerCommand(plugin, "tpa", new TpaCommand(plugin));
        registerCommand(plugin, "tpahere", new TpahereCommand(plugin));
        registerCommand(plugin, "tpaccept", new TpAcceptCommand(plugin));
        registerCommand(plugin, "back", new BackCommand(plugin));
        registerCommand(plugin, "armortrim", new ArmorTrimCommand(plugin));
        registerCommand(plugin, "profile", new ProfileCommand(plugin));
        registerCommand(plugin, "autorestart", new AutoRestartCommand(plugin));

        // Ranked Matchmaking Commands
        RankedCommands rankedCmd = new RankedCommands(plugin);
        registerCommand(plugin, "rankedlb", rankedCmd);
        registerCommand(plugin, "seegames", rankedCmd);
        registerCommand(plugin, "matchinfo", rankedCmd);
        registerCommand(plugin, "rankedreport", rankedCmd);
        registerCommand(plugin, "rankedadmin", new org.albi.core.modules.Duels.commands.RankedAdminCommand(plugin));
    }


    private void registerCommand(Core plugin, String commandName, CommandExecutor executor) {
        try {
            plugin.getLogger().info("[Core] Registering Command: /" + commandName);
            plugin.getCommand(commandName).setExecutor(executor);
        } catch (Exception e) {
            plugin.getLogger().severe("[Core] Could not register command: /" + commandName);
        }
    }
}
