package org.albi.core.modules.CustomKit.GUIs;

import org.albi.core.gui.Messages;

import org.albi.core.gui.GuiService;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.data.MainPlayer;
import org.albi.core.modules.CustomKit.helpers.CustomKit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class CopyKitSelectSlotGUI {

    private final Core plugin;
    private final CustomKit importKit;

    public CopyKitSelectSlotGUI(Core plugin, CustomKit importKit) {
        this.plugin = plugin;
        this.importKit = importKit;
    }

    /** Opens the choose‑a‑slot screen. */
    public void open(Player p) {

        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep == null) return;

        MainPlayer mp = ep.getMainPlayer();
        if (mp == null) return;

        CustomKit[] kits = mp.getKits();
        if (kits == null) return;

        SGMenu menu = plugin.getMenus().create("custom-kits.copy", 6);

        /* black glass border (top+bottom rows) */
        ItemStack border = nameItem(new ItemStack(Material.BLACK_STAINED_GLASS_PANE), " ");
        for (int i = 0; i < 9; i++) {
            menu.setButton(i,       new SGButton(border));
            menu.setButton(i + 45,  new SGButton(border));
        }

        /* templates */
        ItemStack locked = nameItem(new ItemStack(Material.BEDROCK), "§8Locked Slot");
        ItemStack free   = nameItem(new ItemStack(Material.PAPER),   "§aClick to save");

        /* 36 kit slots laid out exactly like displayMain() */
        for (int i = 0; i < 36; i++) {
            i++;
            ItemStack icon;
            SGButton  btn;
            int guiSlot = i + 8; // same offset (skip top border row)

            if (kits[i] != null) {
                icon = locked;
                btn  = new SGButton(icon); // no listener
            } else {
                icon = free;
                final int target = i;
                btn  = new SGButton(icon)
                        .withListener((InventoryClickEvent e) -> {
                            // deep‑copy the kit so edits don’t affect others
                            CustomKit clone = CustomKit.fromJson(importKit.toJson());
                            kits[target] = clone;
                            mp.setKit(target, clone);             // mark MP dirty
                            Messages.send(p, "messages.copy-kit-select-slot-gui.copied-kit-into-slot", target);
                            p.closeInventory();
                        });
            }
            menu.setButton(guiSlot, btn);
            i--;
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
