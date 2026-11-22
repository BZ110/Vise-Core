package org.albi.core.modules.CustomKit.commands;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.data.StatManagement;
import org.albi.core.modules.CustomKit.GUIs.CopyKitSelectSlotGUI;
import org.albi.core.modules.CustomKit.helpers.CustomKit;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CopyKitCommand extends BaseCommand {

    private final Core plugin;
    public CopyKitCommand(Core plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command cmd,
                             String label,
                             String[] args) {

        if (!(sender instanceof Player player)) return true;

        if (args.length < 1) {
            player.sendMessage("§cUsage: /copykit <code>");
            return true;
        }
        String code = args[0].toUpperCase();

        // lobby‑world check (same as /customkit)
        World lobby = Bukkit.getWorld(plugin.getConfig().getString("lobby_world","world"));
        if (player.getWorld() != lobby) {
            plugin.getMessageAPI().sendMessageLang(player, "not_here");
            return true;
        }

        /* ----------------------------------------------------------------
         *  Run the Redis access asynchronously so we don't block main
         * -------------------------------------------------------------- */
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {


            CustomKit imported = StatManagement.getRedis().withJedis(jedis -> {
                String json = jedis.get("sharedkits:getkit:" + code);
                if (json == null) return null;


                return CustomKit.fromJson(json);
            });

            /* --------------------------------------------
             *  Now switch back to main thread for Bukkit
             * ------------------------------------------ */
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (imported == null) {
                    player.sendMessage("§cNo kit found with that code.");
                    return;
                }
                new CopyKitSelectSlotGUI(plugin, imported).open(player);
            });
        });

        return true;
    }
}
