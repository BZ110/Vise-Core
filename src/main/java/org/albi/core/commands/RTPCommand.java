package org.albi.core.commands;

import org.albi.core.gui.GuiService;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import org.albi.core.gui.Messages;

public class RTPCommand extends BaseCommand {

    private record Destination(String id, String target, int slot, Material icon, String name) {}

    private static final List<Destination> DEFAULTS = List.of(
            new Destination("plains-rtp", "Plains", 11, Material.GRASS_BLOCK, "§x§C§0§F§F§4§9§lPLAINS RTP"),
            new Destination("snowy-rtp", "Snowy", 13, Material.SNOW_BLOCK, "§x§F§F§5§C§7§1§lSNOWY RTP"),
            new Destination("desert-rtp", "Desert", 15, Material.SANDSTONE, "§x§F§F§C§3§7§C§lDESERT RTP"),
            new Destination("badlands-rtp", "Badlands", 21, Material.RED_SAND, "§x§F§F§C§3§7§C§lBADLANDS RTP"),
            new Destination("mushroom-rtp", "Mushroom", 23, Material.RED_MUSHROOM_BLOCK, "§x§F§F§C§3§7§C§lMUSHROOM RTP"));

    private final Core plugin;
    private final List<Destination> destinations;

    public RTPCommand(Core plugin) {
        this.plugin = plugin;
        this.destinations = loadDestinations();
    }

    private List<Destination> loadDestinations() {
        String path = "teleports.rtp.destinations";
        if (!plugin.getConfig().isList(path)) return DEFAULTS;
        var configured = plugin.getConfig().getList(path);
        var destinations = new ArrayList<Destination>();
        var ids = new HashSet<String>();
        for (var entry : plugin.getConfig().getMapList(path)) {
            String id = entry.get("id") instanceof String name ? name.toLowerCase(Locale.ROOT) : "";
            Object target = entry.get("target");
            if (!id.matches("[a-z0-9_-]{1,64}") || !(target instanceof String world)
                    || !world.matches("[a-zA-Z0-9_.:-]{1,64}") || !ids.add(id)) {
                plugin.getLogger().warning("[RTP] Ignoring a destination with an invalid or duplicate ID or target.");
                continue;
            }
            var existing = DEFAULTS.stream().filter(destination -> destination.id.equals(id)).findFirst();
            int fallbackSlot = existing.map(Destination::slot).orElse(Math.min(53, destinations.size()));
            int slot = entry.get("slot") instanceof Integer number && number >= 0 && number < 54 ? number : fallbackSlot;
            Material icon = existing.map(Destination::icon).orElse(Material.COMPASS);
            if (entry.get("material") instanceof String material) {
                var configuredIcon = Material.matchMaterial(material);
                if (configuredIcon != null && configuredIcon.isItem() && !configuredIcon.isAir()) icon = configuredIcon;
            }
            String name = entry.get("name") instanceof String label ? plugin.getMessageAPI().toColor(label)
                    : existing.map(Destination::name).orElse(world);
            destinations.add(new Destination(id, world, slot, icon, name));
        }
        return destinations.isEmpty() && !configured.isEmpty() ? DEFAULTS : List.copyOf(destinations);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player)) return true;

        String cmdName = cmd.getName().toLowerCase();
        int cooldown = plugin.getConfig().getInt("cooldowns." + cmdName, 3);
        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (ep.checkCooldown(cmdName)) {
            plugin.getMessageAPI().sendMessageLang(player, "cooldown");
            return true;
        } else {
            ep.addCooldown(cmdName, cooldown);
        }

        if (!player.hasPermission("core.rtp")) {
            plugin.getMessageAPI().sendMessageLang(player, "no_permission");
            return true;
        }

        if (destinations.isEmpty()) {
            Messages.send(player, "messages.rtp-command.no-destinations");
            return true;
        }
        openRTPMenu(player);
        return true;
    }

    private void openRTPMenu(Player p) {
        SGMenu menu = plugin.getMenus().create("travel.rtp", 4);

        ItemStack filler = nameItem(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), " ");
        for (int i = 0; i < 36; i++) menu.setButton(i, new SGButton(filler));

        for (Destination destination : destinations) {
            var button = new SGButton(nameItem(new ItemStack(destination.icon), destination.name)).withListener(event -> {
                p.closeInventory();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "xwr rtp " + destination.target + " " + p.getName());
            });
            GuiService.control(menu, destination.id, destination.slot, button);
        }

        GuiService.open(p, menu);
    }

    private ItemStack nameItem(ItemStack it, String name) {
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        it.setItemMeta(im);
        return it;
    }
}
