package org.albi.core.commands;

import org.albi.core.gui.Messages;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.albi.core.Core;
import org.albi.core.util.ConfigValues;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StoreCommand extends BaseCommand {

    private final Core plugin;

    public StoreCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player)) return true;

        Player player = (Player) sender;

        if (!player.hasPermission("core.store")) {
            plugin.getMessageAPI().sendMessageLang(player, "no_permission");
            return true;
        }
        var store = ConfigValues.httpEndpoint(plugin.getConfig().getString("network.store_url", ""));
        if (store == null) {
            Messages.send(player, "messages.store-command.the-store-link-is-unavailable-on-this-server");
            return true;
        }
        TextComponent linkMsg = new TextComponent("§eSupport our server by clicking on this message and buying a rank.");
        linkMsg.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, store.toString()));
        linkMsg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§7Opens in your browser")));
        player.spigot().sendMessage(linkMsg);
        return true;
    }
}
