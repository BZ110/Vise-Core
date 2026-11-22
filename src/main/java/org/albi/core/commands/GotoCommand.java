
package org.albi.core.commands;

import org.albi.core.gui.Messages;

import org.albi.core.gui.GuiService;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.serializable.SerializableItem;
import org.albi.core.serializable.SerializableLocation;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class GotoCommand extends BaseCommand {

    private final Core plugin;
    public GotoCommand(Core plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        // Check for world.
        World world = player.getWorld();
        World world2 = Bukkit.getWorld(plugin.getConfig().getString("lobby_world", "world"));

        if (world != world2) {
            plugin.getMessageAPI().sendMessageLang(player, "not_here");
            return true;
        }

        Map<Integer, SerializableLocation> locs = (Map<Integer, SerializableLocation>) plugin.getData().get("gotoLocations:73b35c83-19fa-46d1-b6fe-e8ea2f144c72");
        Map<Integer, SerializableItem> icons = (Map<Integer, SerializableItem>) plugin.getData().get("gotoIcons:73b35c83-19fa-46d1-b6fe-e8ea2f144c72");

        if (locs == null || icons == null || locs.isEmpty() || icons.isEmpty()) {
            Messages.send(player, "messages.goto-command.no-goto-points-saved");
            return true;
        }

        SGMenu menu = plugin.getMenus().create("travel.goto", 6);
        for (int i = 0; i <= 53; i++) {
            if (!locs.containsKey(i) || !icons.containsKey(i)) continue;
            SerializableLocation loc = locs.get(i);
            SerializableItem icon = icons.get(i);

            if (loc != null && icon != null) {
                menu.setButton(i, new SGButton(icon.convertToItem()).withListener((InventoryClickEvent e) -> {
                    player.closeInventory();
                    player.teleportAsync(loc.convertToLocation());
                }));
            }
        }

        GuiService.open(player, menu);
        return true;
    }
}
