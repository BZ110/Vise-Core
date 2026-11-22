package org.albi.core.modules.FFA.commands;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.modules.FFA.GUIs.FFAKitEditorGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class FFAKitEditorCommand implements CommandExecutor {

    private final Core plugin;
    private final FFAKitEditorGUI gui;

    public FFAKitEditorCommand(Core plugin) {
        this.plugin = plugin;
        this.gui = new FFAKitEditorGUI(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Messages.send(sender, "messages.ffakit-editor-command.players-only");
            return true;
        }
        // Open the same 1x9 kit chooser as /ffa, but it leads to the editor workflow
        gui.openKitMenu(p);
        return true;
    }
}
