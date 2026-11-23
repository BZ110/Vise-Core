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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.albi.core.data.SharedState;

public class CopyKitCommand extends BaseCommand {

    private final Core plugin;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
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
        if (!pending.add(player.getUniqueId())) return true;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> readSharedKit(shared, player, code, 0));

        return true;
    }

    private void readSharedKit(SharedState shared, Player player, String code, int attempt) {
        String json = shared.readKit(code);
        if (json == null && attempt < 10 && (attempt > 0 || shared.requestRedisKit(code))) {
            if (attempt == 0) plugin.getServer().getScheduler().runTask(plugin,
                    () -> Messages.send(player, "messages.copy-kit-command.asking-a-peer"));
            plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin,
                    () -> readSharedKit(shared, player, code, attempt + 1), 20);
            return;
        }
        CustomKit kit = null;
        try {
            if (json != null) kit = CustomKit.fromJson(json);
        } catch (com.google.gson.JsonParseException invalid) {
            plugin.getLogger().warning("[Kit Sharing] A saved share code contains invalid kit data.");
        }
        var imported = kit;
        boolean available = shared.isKitBackendAvailable(code);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            pending.remove(player.getUniqueId());
            if (!player.isOnline()) return;
            if (imported == null) {
                Messages.send(player, available ? "messages.copy-kit-command.no-kit-found-with-that-code"
                        : "messages.copy-kit-command.storage-unavailable");
                return;
            }
            if (!player.getWorld().getName().equalsIgnoreCase(plugin.getConfig().getString("lobby_world", "world"))
                    || plugin.getExtendedPlayer(player).getPlayerDuelInstance() != null) return;
            new CopyKitSelectSlotGUI(plugin, imported).open(player);
        });
    }
}
