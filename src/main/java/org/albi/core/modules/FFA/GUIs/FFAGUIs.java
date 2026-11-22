package org.albi.core.modules.FFA.GUIs;

import org.albi.core.gui.GuiService;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Kit;
import org.albi.core.modules.FFA.helpers.FFAKitSetup;
import org.albi.core.modules.FFA.helpers.FFASpot;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FFAGUIs {

    private final Core plugin;
    public FFAGUIs(Core plugin) { this.plugin = plugin; }

    /** 1x9 menu of FFA kits that have a loaded world. */
    public void openKitMenu(Player p) {
        List<FFAKitSetup> setups = new ArrayList<>();
        for (FFAKitSetup s : org.albi.core.modules.FFA.FFAManager.stor.allSetups()) {
            if (s.getWorldName() != null && Bukkit.getWorld(s.getWorldName()) != null) setups.add(s);
        }
        SGMenu m = plugin.getMenus().create("ffa.kits", 1);

        int slot = 0;
        for (FFAKitSetup s : setups) {
            Kit k = DuelManager.stor.kits.stream()
                    .filter(kk -> kk.getName().equalsIgnoreCase(s.getKitName()))
                    .findFirst().orElse(null);
            if (k == null) continue;

            ItemStack it = k.getDisplayItem().convertToItem();
            ItemMeta itM = it.getItemMeta();
            if (itM != null) {
                if (itM.hasDisplayName()) {
                    itM.setDisplayName(itM.getDisplayName().split(" §8", 2)[0]);
                }

                int playing = 0;
                var w = Bukkit.getWorld(s.getWorldName());
                if (w != null) playing = w.getPlayers().size();

                List<String> lore = new ArrayList<>(1);
                lore.add(GuiService.text("ffaguis.open-kit-menu", playing));
                itM.setLore(lore);

                it.setItemMeta(itM);
            }

            int targetSlot = Math.min(slot++, 8);
            m.setButton(targetSlot, new SGButton(it).withListener((InventoryClickEvent e) -> {
                openSpotMenu(p, s);
            }));
            if (slot > 9) break; // 1x9 only
        }

        GuiService.open(p, m);
    }

    /** 1x9 menu of teleport spots within the chosen kit world. */
    public void openSpotMenu(Player p, FFAKitSetup setup) {
        SGMenu m = plugin.getMenus().create("ffa.spawns", 1);
        for (Map.Entry<Integer, FFASpot> e : setup.getSpots().entrySet()) {
            int idx = e.getKey();
            if (idx < 0 || idx > 8) continue;
            ItemStack it = e.getValue().getIcon().convertToItem();
            m.setButton(idx, new SGButton(it).withListener((InventoryClickEvent ev) -> {
                p.closeInventory();
                Location l = e.getValue().getLocation().convertToLocation();
                p.teleportAsync(l);

                Kit k = DuelManager.stor.kits.stream()
                        .filter(kk -> kk.getName().equalsIgnoreCase(setup.getKitName()))
                        .findFirst().orElse(null);
                if (k == null) return;

                plugin.resetPlayer(p, false);
                k.giveKit(p);

                ExtendedPlayer ep = plugin.getExtendedPlayer(p);
                if (ep != null && ep.isReady()) {
                    var mp = ep.getMainPlayer();
                    var edit = mp.getKitEdit(k.getName()); // null if never saved
                    if (edit != null && !edit.isEmpty()) {
                        edit.applyToIncludingOffhand(p); // rearrange to player's layout
                        p.updateInventory();
                    }

                    if (!k.isBuild()) p.setGameMode(GameMode.ADVENTURE);

                    ep.setLastFFAKitName(setup.getKitName());
                    ep.setLastFFAWorlName(setup.getWorldName());
                    ep.setLastFFADearthLocation(l);
                }
            }));
        }
        GuiService.open(p, m);
    }
}
