package org.albi.core.modules.Duels.commands;

import org.albi.core.gui.Messages;

import org.albi.core.gui.GuiService;

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
            Messages.send(sender, "messages.kit-editor-command.players-only");
            return true;
        }

        SGMenu menu = plugin.getMenus().create("kits.editor", 1);
        GuiService.control(menu, "duel-kit-editor", 2, button(Material.DIAMOND_SWORD, GuiService.text("kit-editor-command.duel-kit-editor"),
                List.of(GuiService.text("kit-editor-command.edit-saved-layouts-for-duel-kits"), GuiService.text("kit-editor-command.click-to-continue")),
                e -> gui.openDuelKitMenu(p)));
        GuiService.control(menu, "ffa-kit-editor", 4, button(Material.IRON_AXE, GuiService.text("kit-editor-command.ffa-kit-editor"),
                List.of(GuiService.text("kit-editor-command.edit-saved-layouts-for-ffa-kits"), GuiService.text("kit-editor-command.click-to-continue")),
                e -> gui.openKitMenu(p)));
        GuiService.control(menu, "custom-kit-editor", 6, button(Material.BOOK, GuiService.text("kit-editor-command.custom-kit-editor"),
                List.of(GuiService.text("kit-editor-command.open-your-custom-kit-browser"), GuiService.text("kit-editor-command.click-to-continue")),
                e -> p.performCommand("customkit")));
        GuiService.open(p, menu);
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
