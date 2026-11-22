package org.albi.core.modules.Duels.commands;

import org.albi.core.Core;
import org.albi.core.modules.Duels.DuelManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public class SpectateCommand extends BaseCommand {
    private final Core plugin;

    public SpectateCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;
        if (args.length != 1) {
            plugin.getMessageAPI().sendMessageLang(player, "duels_duel_specify");
            return true;
        }

        String key = command.getName().toLowerCase(Locale.ROOT);
        var profile = plugin.getExtendedPlayer(player);
        if (profile.checkCooldown(key)) {
            plugin.getMessageAPI().sendMessageLang(player, "cooldown");
            return true;
        }
        profile.addCooldown(key, plugin.getConfig().getInt("cooldowns." + key, 3));
        DuelManager.getSpectators().spectate(player, args[0]);
        return true;
    }
}
