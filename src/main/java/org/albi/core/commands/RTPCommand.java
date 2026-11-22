package org.albi.core.commands;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.commands.BaseCommand;
import org.albi.core.data.ExtendedPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class RTPCommand extends BaseCommand {

    private final Core plugin;

    public RTPCommand(Core plugin) {
        this.plugin = plugin;
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

        openRTPMenu(player);
        return true;
    }

    private void openRTPMenu(Player p) {
        SGMenu menu = plugin.getGUI().create("§bRTP", 4);

        ItemStack filler = nameItem(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), " ");
        for (int i = 0; i < 36; i++) menu.setButton(i, new SGButton(filler));

        menu.setButton(11, biomeButton("§x§C§0§F§F§4§9§lPLAINS RTP", Material.GRASS_BLOCK, "Plains", p));
        menu.setButton(13, biomeButton("§x§F§F§5§C§7§1§lSNOWY RTP", Material.SNOW_BLOCK, "Snowy", p));
        menu.setButton(15, biomeButton("§x§F§F§C§3§7§C§lDESERT RTP", Material.SANDSTONE, "Desert", p));
        menu.setButton(21, biomeButton("§x§F§F§C§3§7§C§lBADLANDS RTP", Material.RED_SAND, "Badlands", p));
        menu.setButton(23, biomeButton("§x§F§F§C§3§7§C§lMUSHROOM RTP", Material.RED_MUSHROOM_BLOCK, "Mushroom", p));


        p.openInventory(menu.getInventory());
    }

    private SGButton biomeButton(String name, Material mat, String biome, Player p) {
        ItemStack item = nameItem(new ItemStack(mat), name);
        return new SGButton(item).withListener((InventoryClickEvent e) -> {
            p.closeInventory();
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "xwr rtp " + biome + " " + p.getName());
        });
    }

    private ItemStack nameItem(ItemStack it, String name) {
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        it.setItemMeta(im);
        return it;
    }
}
