package org.albi.core.modules.Party.managers;

import org.albi.core.Core;
import org.albi.core.modules.Party.PartyManager;
import org.albi.core.modules.Party.commands.*;
import org.bukkit.command.CommandExecutor;

public class PartyCommandManager {
    public PartyCommandManager(Core plugin, PartyManager partyManager) {
        // registerCommand(plugin, "example", new ExampleCommand(plugin, partyManager));

        registerCommand(plugin, "party", new PartyCommand(plugin, partyManager));
        registerCommand(plugin, "partyduelaccept", new PartyDuelAcceptCommand(plugin, partyManager));

    }

    private void registerCommand(Core plugin, String commandName, CommandExecutor executor) {
        try {
            plugin.getLogger().info("[Party] Registering Command: /" + commandName);
            plugin.getCommand(commandName).setExecutor(executor);
        } catch (Exception e) {
            plugin.getLogger().severe("[Party] Could not register command: /" + commandName);
        }
    }
}
