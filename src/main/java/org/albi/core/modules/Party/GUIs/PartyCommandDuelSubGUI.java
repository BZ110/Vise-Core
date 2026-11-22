package org.albi.core.modules.Party.GUIs;

import org.albi.core.gui.GuiService;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Arena;
import org.albi.core.modules.Duels.helpers.DuelKitPageCategory;
import org.albi.core.modules.Duels.helpers.DuelPreference;
import org.albi.core.modules.Duels.helpers.Kit;
import org.albi.core.modules.Party.PartyManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class PartyCommandDuelSubGUI {

    private final Core plugin;
    private final PartyManager party;

    public PartyCommandDuelSubGUI(Core pl, PartyManager pm) { plugin = pl; party = pm; }

    public void openKitGUI(Player initiator, Player opponent) { openKit(initiator, opponent, true, -1); }

    private Map<Integer, DuelKitPageCategory> getPageCategories() {
        Map<Integer, DuelKitPageCategory> cats = (Map<Integer, DuelKitPageCategory>) plugin.getData().get("duelKitPageCategories");
        return cats == null ? Collections.emptyMap() : cats;
    }

    private void openCategory(Player p, Player opp, boolean quick) {
        SGMenu menu = plugin.getMenus().create("party.challenge-categories", 3);
        for (Map.Entry<Integer, DuelKitPageCategory> entry : getPageCategories().entrySet()) {
            DuelKitPageCategory cat = entry.getValue();
            if (cat == null || cat.getDisplayItem() == null) continue;
            ItemStack icon = cat.getDisplayItem().convertToItem();
            if (icon == null || icon.getType().isAir()) continue;
            ItemMeta meta = icon.getItemMeta();
            List<String> lore = meta != null && meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(" ");
            lore.add(GuiService.text("party-command-duel-sub-gui.open-page", cat.getPage()));
            if (meta != null) {
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            int slot = Math.max(0, Math.min(26, cat.getGuiSlot()));
            menu.setButton(slot, new SGButton(icon).withListener(e -> openKit(p, opp, quick, cat.getPage())));
        }
        GuiService.open(p, menu);
    }

    private void request(Player a, Player b) {
        ExtendedPlayer ea = plugin.getExtendedPlayer(a), eb = plugin.getExtendedPlayer(b);
        if (eb.isReady() && eb.getMainPlayer().getBlockedList().contains(a.getUniqueId())) {
            plugin.getMessageAPI().sendMessageLang(a, "duel_blocked");
            return;
        }
        if (!(party.isLeader(a) && party.isLeader(b))) {
            plugin.getMessageAPI().sendMessageLang(a, "duel_blocked");
            return;
        }

        DuelPreference pref = ea.prefsFor(b.getUniqueId());
        Kit kit = pref.getKit();
        int rounds = pref.getRounds();
        plugin.getMessageAPI().sendMessageLang(b, "party_duel_request",
                new String[]{a.getName(), kit.getDisplayItem().convertToItem().getItemMeta().getDisplayName(), String.valueOf(rounds)});
        plugin.getMessageAPI().sendMessageLang(a, "duel_sent");
        plugin.getMessageAPI().sendCommandMessageLang(b, "party_duel_accept", "/partyduelaccept " + a.getName());
    }

    private void setting(Player p, Player opp) {
        DuelPreference pref = plugin.getExtendedPlayer(p).prefsFor(opp.getUniqueId());
        Kit kit = pref.getKit();
        Arena ar = pref.getArena();
        int rd = pref.getRounds();

        SGMenu menu = plugin.getMenus().create("party.challenge", 3);
        GuiService.control(menu, "current-kit-choice", 10, new SGButton(withLore(kit.getDisplayItem().convertToItem(), "§7Current kit choice.", "§fClick to change."))
                .withListener(e -> {
                    if (getPageCategories().isEmpty()) openKit(p, opp, false, 0);
                    else openCategory(p, opp, false);
                }));
        GuiService.control(menu, "current-arena-choice", 12, new SGButton(withLore(ar.getDisplayItem().convertToItem(), "§7Current arena choice.", "§fClick to change."))
                .withListener(e -> openArena(p, opp, false)));
        GuiService.control(menu, "first-to", 14, new SGButton(button(Material.PAPER, GuiService.text("party-command-duel-sub-gui.first-to", rd),
                List.of(GuiService.text("party-command-duel-sub-gui.pick-the-round-target"), GuiService.text("party-command-duel-sub-gui.click-to-change"))))
                .withListener(e -> rounds(p, opp)));
        GuiService.control(menu, "send-party-duel", 16, new SGButton(button(Material.LIME_CONCRETE, GuiService.text("party-command-duel-sub-gui.send-party-duel"),
                List.of(GuiService.text("party-command-duel-sub-gui.everything-is-ready"), GuiService.text("party-command-duel-sub-gui.click-to-send-the-duel-request"))))
                .withListener(e -> { request(p, opp); p.closeInventory(); }));
        GuiService.open(p, menu);
    }

    private void openArena(Player p, Player opp, boolean quick) {
        SGMenu menu = plugin.getMenus().create("party.challenge-arenas", 5);
        int off = 0;
        for (Arena a : DuelManager.stor.arenas) {
            if(a.deactivated) continue;
            menu.setButton(off++, new SGButton(withLore(a.getDisplayItem().convertToItem(), "§7Click to select this arena."))
                    .withListener(e -> {
                        DuelPreference pref = plugin.getExtendedPlayer(p).prefsFor(opp.getUniqueId());
                        pref.setArena(a);
                        if (quick) pref.setRounds(plugin.getDuelSettings().defaultRounds());
                        setting(p, opp);
                    }));
        }
        GuiService.control(menu, "back", 44, new SGButton(button(Material.ARROW, GuiService.text("party-command-duel-sub-gui.back"), List.of(GuiService.text("party-command-duel-sub-gui.return-to-party-duel-setup"))))
                .withListener(e -> setting(p, opp)));
        GuiService.open(p, menu);
    }

    private void openKit(Player p, Player opp, boolean quick, int page) {
        if (page < 0 && !getPageCategories().isEmpty()) {
            openCategory(p, opp, quick);
            return;
        }
        int rows = Math.max(3, plugin.getConfig().getInt("guis.duel.kitselector.rows", 6));
        int pageSize = rows * 9;
        int maxSlot = DuelManager.stor.kits.stream().mapToInt(Kit::getSlot).max().orElse(0);
        int maxPage = maxSlot / pageSize;

        SGMenu m = plugin.getMenus().create("party.challenge-kits", rows, (page + 1), (maxPage + 1));
        DuelPreference pref = plugin.getExtendedPlayer(p).prefsFor(opp.getUniqueId());

        for (Kit k : DuelManager.stor.kits) {
            int g = k.getSlot();
            if (g / pageSize != page) continue;
            int local = g % pageSize;
            m.setButton(local, new SGButton(withLore(k.getDisplayItem().convertToItem(), "§7Choose this ruleset.", "§fClick to lock it in."))
                    .withListener(e -> {
                        pref.setKit(k);
                        if (quick) openArena(p, opp, true);
                        else setting(p, opp);
                    }));
        }

        if (page > 0) GuiService.control(m, "previous", pageSize - 10, new SGButton(button(Material.ARROW, GuiService.text("party-command-duel-sub-gui.previous"), List.of(GuiService.text("party-command-duel-sub-gui.view-the-previous-page"))))
                .withListener(e -> openKit(p, opp, quick, page - 1)));
        if (page < maxPage) GuiService.control(m, "next", pageSize - 9, new SGButton(button(Material.ARROW, GuiService.text("party-command-duel-sub-gui.next"), List.of(GuiService.text("party-command-duel-sub-gui.view-the-next-page"))))
                .withListener(e -> openKit(p, opp, quick, page + 1)));
        if (!getPageCategories().isEmpty()) GuiService.control(m, "categories", pageSize - 5, new SGButton(button(Material.ARROW, GuiService.text("party-command-duel-sub-gui.categories"), List.of(GuiService.text("party-command-duel-sub-gui.return-to-category-view"))))
                .withListener(e -> openCategory(p, opp, quick)));
        GuiService.control(m, "back", pageSize - 6, new SGButton(button(Material.ARROW, GuiService.text("party-command-duel-sub-gui.back"), List.of(GuiService.text("party-command-duel-sub-gui.return-to-party-duel-setup"))))
                .withListener(e -> setting(p, opp)));

        GuiService.open(p, m);
    }

    private void rounds(Player p, Player opp) {
        List<Integer> list = plugin.getDuelSettings().rounds();
        SGMenu menu = plugin.getMenus().create("party.challenge-rounds", 1);
        for (int r : list) {
            menu.addButton(new SGButton(button(Material.PAPER, GuiService.text("party-command-duel-sub-gui.first-to", r),
                    List.of(GuiService.text("party-command-duel-sub-gui.choose-how-many-rounds-decide-the-winner"))))
                    .withListener(evt -> {
                        plugin.getExtendedPlayer(p).prefsFor(opp.getUniqueId()).setRounds(r);
                        setting(p, opp);
                    }));
        }
        GuiService.control(menu, "back", 8, new SGButton(button(Material.ARROW, GuiService.text("party-command-duel-sub-gui.back"), List.of(GuiService.text("party-command-duel-sub-gui.return-to-party-duel-setup"))))
                .withListener(e -> setting(p, opp)));
        GuiService.open(p, menu);
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack withLore(ItemStack base, String... lines) {
        ItemMeta meta = base.getItemMeta();
        List<String> lore = meta != null && meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(" ");
        lore.addAll(Arrays.asList(lines));
        if (meta != null) {
            meta.setLore(lore);
            base.setItemMeta(meta);
        }
        return base;
    }
}
