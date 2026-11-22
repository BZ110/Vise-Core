package org.albi.core.commands;

import org.albi.core.Core;
import org.albi.core.util.AutoRestartService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class AutoRestartCommand implements CommandExecutor {

    private final Core plugin;

    public AutoRestartCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("core.autorestart")) {
            plugin.getMessageAPI().sendMessageLang(sender, "no_permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        AutoRestartService service = plugin.getAutoRestartService();
        String sub = args[0].toLowerCase();

        switch (sub) {
            case "time":
            case "status":
                if (service.getRemainingSeconds() < 0) {
                    plugin.getMessageAPI().sendMessage(sender, "&cNo restart is currently scheduled.");
                } else {
                    String timeStr = service.formatTime(service.getRemainingSeconds());
                    plugin.getMessageAPI().sendMessage(sender, "&7The next restart is in &d" + timeStr + "&7.");
                }
                break;

            case "cancel":
                service.cancelRestart();
                plugin.getMessageAPI().sendMessage(sender, "&c&lRESTART CANCELLED &7The server will not restart automatically.");
                break;

            case "set":
                if (args.length < 2) {
                    plugin.getMessageAPI().sendMessage(sender, "&cUsage: /autorestart set <time> (e.g., 1h, 30m, 10s)");
                    return true;
                }
                int seconds = parseTime(args[1]);
                if (seconds <= 0) {
                    plugin.getMessageAPI().sendMessage(sender, "&cInvalid time format. Use something like 1h, 30m, or 100s.");
                    return true;
                }
                service.setRemainingSeconds(seconds);
                plugin.getMessageAPI().sendMessage(sender, "&a&lRESTART SCHEDULED &7The server will restart in &d" + service.formatTime(seconds) + "&7.");
                break;

            case "now":
                service.setRemainingSeconds(10);
                plugin.getMessageAPI().sendMessage(sender, "&c&lRESTART INITIATED &7Restarting in 10 seconds...");
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        plugin.getMessageAPI().sendMessage(sender, "&8&m----------------------------------------");
        plugin.getMessageAPI().sendMessage(sender, "&d&lAutoRestart Commands");
        plugin.getMessageAPI().sendMessage(sender, "&7/autorestart time &8- &fSee time until restart");
        plugin.getMessageAPI().sendMessage(sender, "&7/autorestart set <time> &8- &fSchedule a restart");
        plugin.getMessageAPI().sendMessage(sender, "&7/autorestart cancel &8- &fCancel any scheduled restart");
        plugin.getMessageAPI().sendMessage(sender, "&7/autorestart now &8- &fRestart in 10 seconds");
        plugin.getMessageAPI().sendMessage(sender, "&8&m----------------------------------------");
    }

    private int parseTime(String input) {
        try {
            if (input.endsWith("h")) return Integer.parseInt(input.replace("h", "")) * 3600;
            if (input.endsWith("m")) return Integer.parseInt(input.replace("m", "")) * 60;
            if (input.endsWith("s")) return Integer.parseInt(input.replace("s", ""));
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
