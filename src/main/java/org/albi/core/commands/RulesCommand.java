package org.albi.core.commands;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.serializable.SerializableSpawnInventory;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public class RulesCommand extends BaseCommand {

    private final Core plugin;

    public RulesCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        // ─────────────────────────────────────────────
        //  Player & permission checks
        // ─────────────────────────────────────────────
        if (!(sender instanceof Player player)) {
            plugin.getMessageAPI().sendMessageLang(sender, "players_only");
            return true;
        }

        String cmdName = command.getName().toLowerCase();
        int secondsForCooldown = plugin.getConfig().getInt("cooldowns." + cmdName, 3);
        ExtendedPlayer eP = plugin.getExtendedPlayer(player);
        if(eP.checkCooldown(cmdName)) {
            plugin.getMessageAPI().sendMessageLang(player, "cooldown");
            return true;
        } else {
            eP.addCooldown(cmdName, secondsForCooldown);
        }

        if (!player.hasPermission("core.rules")) {
            plugin.getMessageAPI().sendMessageLang(player, "no_permission");
            return true;
        }

        plugin.getMessageAPI().sendMessageLang(player, "rules");

        return true;
    }
}
