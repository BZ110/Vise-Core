package org.albi.core.commands;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.serializable.SerializableItem;
import org.albi.core.serializable.SerializableLocation;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

public class SetGotoCommand extends BaseCommand {

    private final Core plugin;
    public SetGotoCommand(Core plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length < 1) {
            player.sendMessage("§cUsage: /setgoto <slot>");
            return true;
        }

        int slot;
        try { slot = Integer.parseInt(args[0]); }
        catch (NumberFormatException ex) {
            player.sendMessage("§cSlot must be a number between 0 and 53");
            return true;
        }
        if (slot < 0 || slot > 53) {
            player.sendMessage("§cSlot must be between 0 and 53");
            return true;
        }

        Map<Integer, SerializableLocation> locs = (Map<Integer, SerializableLocation>) plugin.getData().get("gotoLocations:73b35c83-19fa-46d1-b6fe-e8ea2f144c72");
        Map<Integer, SerializableItem> icons = (Map<Integer, SerializableItem>) plugin.getData().get("gotoIcons:73b35c83-19fa-46d1-b6fe-e8ea2f144c72");
        if (locs == null) locs = new HashMap<>();
        if (icons == null) icons = new HashMap<>();

        locs.put(slot, new SerializableLocation(player.getLocation()));

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            item = new ItemStack(Material.ENDER_PEARL);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§bGoto Point " + slot);
            item.setItemMeta(meta);
        }

        icons.put(slot, new SerializableItem(item));

        plugin.getData().set("gotoLocations:73b35c83-19fa-46d1-b6fe-e8ea2f144c72", locs);
        plugin.getData().set("gotoIcons:73b35c83-19fa-46d1-b6fe-e8ea2f144c72", icons);
        player.sendMessage("§aSet Goto point in slot §e" + slot);
        return true;
    }
}

