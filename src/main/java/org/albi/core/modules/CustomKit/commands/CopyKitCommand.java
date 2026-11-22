package org.albi.core.modules.CustomKit.commands;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.data.StatManagement;
import org.albi.core.modules.CustomKit.GUIs.CopyKitSelectSlotGUI;
import org.albi.core.modules.CustomKit.helpers.CustomKit;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

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
            Messages.send(player, "messages.copy-kit-command.usage-copykit-code");
            return true;
        }
        String code = args[0].toUpperCase(Locale.ROOT);

        // lobby‑world check (same as /customkit)
        World lobby = Bukkit.getWorld(plugin.getConfig().getString("lobby_world","world"));
        if (player.getWorld() != lobby) {
            plugin.getMessageAPI().sendMessageLang(player, "not_here");
            return true;
        }

        var shared = StatManagement.getSharedState();
        if (!shared.isEnabled()) {
            Messages.send(player, "messages.copy-kit-command.storage-unavailable");
            return true;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String json = shared.readKit(code);
            CustomKit imported = json == null ? null : CustomKit.fromJson(json);
            boolean available = shared.isKitBackendAvailable(code);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (imported == null) {
                    Messages.send(player, available ? "messages.copy-kit-command.no-kit-found-with-that-code"
                            : "messages.copy-kit-command.storage-unavailable");
                    return;
                }
                new CopyKitSelectSlotGUI(plugin, imported).open(player);
            });
        });

        return true;
    }
}
