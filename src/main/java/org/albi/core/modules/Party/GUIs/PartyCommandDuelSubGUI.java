package org.albi.core.modules.Party.GUIs;

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
        SGMenu menu = plugin.getGUI().create("§d§lParty Duel Categories", 3);
        for (Map.Entry<Integer, DuelKitPageCategory> entry : getPageCategories().entrySet()) {
            DuelKitPageCategory cat = entry.getValue();
            if (cat == null || cat.getDisplayItem() == null) continue;
            ItemStack icon = cat.getDisplayItem().convertToItem();
            if (icon == null || icon.getType().isAir()) continue;
            ItemMeta meta = icon.getItemMeta();
            List<String> lore = meta != null && meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(" ");
            lore.add("§dOpen page §f#" + cat.getPage());
            if (meta != null) {
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            int slot = Math.max(0, Math.min(26, cat.getGuiSlot()));
            menu.setButton(slot, new SGButton(icon).withListener(e -> openKit(p, opp, quick, cat.getPage())));
        }
        p.openInventory(menu.getInventory());
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

        SGMenu menu = plugin.getGUI().create("§d§lParty Duel Setup", 3);
        menu.setButton(10, new SGButton(withLore(kit.getDisplayItem().convertToItem(), "§7Current kit choice.", "§fClick to change."))
                .withListener(e -> {
                    if (getPageCategories().isEmpty()) openKit(p, opp, false, 0);
                    else openCategory(p, opp, false);
                }));
        menu.setButton(12, new SGButton(withLore(ar.getDisplayItem().convertToItem(), "§7Current arena choice.", "§fClick to change."))
                .withListener(e -> openArena(p, opp, false)));
        menu.setButton(14, new SGButton(button(Material.PAPER, "§dFirst to §f" + rd,
                List.of("§7Pick the round target.", "§fClick to change.")))
                .withListener(e -> rounds(p, opp)));
        menu.setButton(16, new SGButton(button(Material.LIME_CONCRETE, "§a§lSend Party Duel",
                List.of("§7Everything is ready.", "§fClick to send the duel request.")))
                .withListener(e -> { request(p, opp); p.closeInventory(); }));
        p.openInventory(menu.getInventory());
    }

    private void openArena(Player p, Player opp, boolean quick) {
        SGMenu menu = plugin.getGUI().create("§b§lParty Duel Arena", 5);
        int off = 0;
        for (Arena a : DuelManager.stor.arenas) {
            if(a.deactivated) continue;
            menu.setButton(off++, new SGButton(withLore(a.getDisplayItem().convertToItem(), "§7Click to select this arena."))
                    .withListener(e -> {
                        DuelPreference pref = plugin.getExtendedPlayer(p).prefsFor(opp.getUniqueId());
                        pref.setArena(a);
                        if (quick) pref.setRounds(plugin.getConfig().getInt("duels.default_rounds", 1));
                        setting(p, opp);
                    }));
        }
        menu.setButton(44, new SGButton(button(Material.ARROW, "§6Back", List.of("§7Return to party duel setup.")))
                .withListener(e -> setting(p, opp)));
        p.openInventory(menu.getInventory());
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

        SGMenu m = plugin.getGUI().create("§d§lParty Duel Kits §7(" + (page + 1) + "/" + (maxPage + 1) + ")", rows);
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

        if (page > 0) m.setButton(pageSize - 10, new SGButton(button(Material.ARROW, "§e← Previous", List.of("§7View the previous page.")))
                .withListener(e -> openKit(p, opp, quick, page - 1)));
        if (page < maxPage) m.setButton(pageSize - 9, new SGButton(button(Material.ARROW, "§eNext →", List.of("§7View the next page.")))
                .withListener(e -> openKit(p, opp, quick, page + 1)));
        if (!getPageCategories().isEmpty()) m.setButton(pageSize - 5, new SGButton(button(Material.ARROW, "§dCategories", List.of("§7Return to category view.")))
                .withListener(e -> openCategory(p, opp, quick)));
        m.setButton(pageSize - 6, new SGButton(button(Material.ARROW, "§6Back", List.of("§7Return to party duel setup.")))
                .withListener(e -> setting(p, opp)));

        p.openInventory(m.getInventory());
    }

    private void rounds(Player p, Player opp) {
        List<Integer> list = plugin.getConfig().getIntegerList("duels.rounds");
        SGMenu menu = plugin.getGUI().create("§5§lRound Target", 1);
        for (int r : list) {
            menu.addButton(new SGButton(button(Material.PAPER, "§dFirst to §f" + r,
                    List.of("§7Choose how many rounds decide the winner.")))
                    .withListener(evt -> {
                        plugin.getExtendedPlayer(p).prefsFor(opp.getUniqueId()).setRounds(r);
                        setting(p, opp);
                    }));
        }
        menu.setButton(8, new SGButton(button(Material.ARROW, "§6Back", List.of("§7Return to party duel setup.")))
                .withListener(e -> setting(p, opp)));
        p.openInventory(menu.getInventory());
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
