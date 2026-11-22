package org.albi.core.commands;

import org.albi.core.util.ConfigValues;

import org.albi.core.gui.Messages;

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
                    plugin.getMessageAPI().sendMessage(sender, Messages.text(sender, "messages.auto-restart-command.no-restart-is-currently-scheduled"));
                } else {
                    String timeStr = service.formatTime(service.getRemainingSeconds());
                    plugin.getMessageAPI().sendMessage(sender, Messages.text(sender, "messages.auto-restart-command.the-next-restart-is-in", timeStr));
                }
                break;

            case "cancel":
                service.cancelRestart();
                plugin.getMessageAPI().sendMessage(sender, Messages.text(sender, "messages.auto-restart-command.restart-cancelled-the-server-will-not-restart-automatically"));
                break;

            case "set":
                if (args.length < 2) {
                    plugin.getMessageAPI().sendMessage(sender, Messages.text(sender, "messages.auto-restart-command.usage-autorestart-set-time-e-g-1h-30m"));
                    return true;
                }
                int seconds = parseTime(args[1]);
                if (seconds <= 0) {
                    plugin.getMessageAPI().sendMessage(sender, Messages.text(sender, "messages.auto-restart-command.invalid-time-format-use-something-like-1h-30m"));
                    return true;
                }
                service.setRemainingSeconds(seconds);
                plugin.getMessageAPI().sendMessage(sender, Messages.text(sender, "messages.auto-restart-command.restart-scheduled-the-server-will-restart-in", service.formatTime(seconds)));
                break;

            case "now":
                int delay = nowDelay();
                service.setRemainingSeconds(delay);
                Messages.send(sender, delay == 10 ? "messages.auto-restart-command.restart-initiated-restarting-in-10-seconds"
                        : "messages.auto-restart-command.restart-initiated", delay);
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        plugin.getMessageAPI().sendMessage(sender, Messages.text(sender, "messages.auto-restart-command.send-help"));
        plugin.getMessageAPI().sendMessage(sender, Messages.text(sender, "messages.auto-restart-command.auto-restart-commands"));
        plugin.getMessageAPI().sendMessage(sender, Messages.text(sender, "messages.auto-restart-command.autorestart-time-see-time-until-restart"));
        plugin.getMessageAPI().sendMessage(sender, Messages.text(sender, "messages.auto-restart-command.autorestart-set-time-schedule-a-restart"));
        plugin.getMessageAPI().sendMessage(sender, Messages.text(sender, "messages.auto-restart-command.autorestart-cancel-cancel-any-scheduled-restart"));
        int delay = nowDelay();
        Messages.send(sender, delay == 10 ? "messages.auto-restart-command.autorestart-now-restart-in-10-seconds"
                : "messages.auto-restart-command.now-help", delay);
        plugin.getMessageAPI().sendMessage(sender, Messages.text(sender, "messages.auto-restart-command.send-help"));
    }

    private int nowDelay() {
        return ConfigValues.integer(plugin.getConfig(), "autorestart.now_delay_seconds", 10, 1, 600);
    }

    private int parseTime(String input) {
        try {
            if (input.endsWith("h")) return Math.multiplyExact(Integer.parseInt(input.substring(0, input.length() - 1)), 3600);
            if (input.endsWith("m")) return Math.multiplyExact(Integer.parseInt(input.substring(0, input.length() - 1)), 60);
            if (input.endsWith("s")) return Integer.parseInt(input.replace("s", ""));
            return Integer.parseInt(input);
        } catch (NumberFormatException | ArithmeticException e) {
            return -1;
        }
    }
}
