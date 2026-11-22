package org.albi.core.modules.Duels.commands;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.modules.FFA.GUIs.FFAKitEditorGUI;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class KitEditorCommand implements CommandExecutor {
    private final Core plugin;
    private final FFAKitEditorGUI gui;

    public KitEditorCommand(Core plugin) {
        this.plugin = plugin;
        this.gui = new FFAKitEditorGUI(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        SGMenu menu = plugin.getGUI().create("§6Kit Editor", 1);
        menu.setButton(2, button(Material.DIAMOND_SWORD, "§dDuel Kit Editor",
                List.of("§7Edit saved layouts for duel kits.", "§fClick to continue."),
                e -> gui.openDuelKitMenu(p)));
        menu.setButton(4, button(Material.IRON_AXE, "§bFFA Kit Editor",
                List.of("§7Edit saved layouts for FFA kits.", "§fClick to continue."),
                e -> gui.openKitMenu(p)));
        menu.setButton(6, button(Material.BOOK, "§eCustom Kit Editor",
                List.of("§7Open your custom kit browser.", "§fClick to continue."),
                e -> p.performCommand("customkit")));
        p.openInventory(menu.getInventory());
        return true;
    }

    private SGButton button(Material type, String name, List<String> lore, java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> action) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return new SGButton(item).withListener(action::accept);
    }
}
