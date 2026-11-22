package org.albi.core.modules.Duels.GUIs;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import net.minecraft.util.profiling.jfr.event.PacketEvent;
import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.CustomKit.helpers.CustomKit;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Arena;
import org.albi.core.modules.Duels.helpers.DuelKitPageCategory;
import org.albi.core.modules.Duels.helpers.DuelPreference;
import org.albi.core.modules.Duels.helpers.Kit;
import org.albi.core.serializable.SerializableItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.function.Supplier;

public class DuelMainCommandGUIs {

    private final Core plugin;
    public DuelMainCommandGUIs(Core plugin) { this.plugin = plugin; }

    private Map<Integer, DuelKitPageCategory> getPageCategories() {
        Map<Integer, DuelKitPageCategory> cats =
                (Map<Integer, DuelKitPageCategory>) plugin.getData().get("duelKitPageCategories");
        return cats == null ? Collections.emptyMap() : cats;
    }

    private boolean hasPageCategories() { return !getPageCategories().isEmpty(); }

    public void openKitGUI(Player initiator, Player opponent) { openKitGUI(initiator, opponent, true); }

    private void openKitGUI(Player p, Player opponent, boolean quick) {
        if (hasPageCategories()) openKitCategoryGUI(p, opponent, quick);
        else openKitGUI(p, opponent, quick, 0);
    }

    private void openKitCategoryGUI(Player p, Player opponent, boolean quick) {
        SGMenu menu = plugin.getGUI().create("§d§lDuel Browser §8• §fCategories", 3);
        for (Map.Entry<Integer, DuelKitPageCategory> entry : getPageCategories().entrySet()) {
            DuelKitPageCategory cat = entry.getValue();
            if (cat == null || cat.getDisplayItem() == null) continue;
            ItemStack icon = cat.getDisplayItem().convertToItem();
            if (icon == null || icon.getType().isAir()) continue;
            ItemMeta im = icon.getItemMeta();
            List<String> lore = im != null && im.hasLore() ? new ArrayList<>(im.getLore()) : new ArrayList<>();
            lore.add(" ");
            lore.add("§dOpen page §f#" + cat.getPage());
            lore.add("§7Jump into this family of rulesets.");
            if (im != null) {
                im.setLore(lore);
                icon.setItemMeta(im);
            }
            int slot = Math.max(0, Math.min(26, cat.getGuiSlot()));
            final int page = cat.getPage();
            menu.setButton(slot, new SGButton(icon).withListener(e -> openKitGUI(p, opponent, quick, page)));
        }
        menu.setButton(22, new SGButton(button(Material.BARRIER, "§cClose", List.of("§7Close the duel browser.")))
                .withListener(e -> p.closeInventory()));
        p.openInventory(menu.getInventory());
    }

    public void requestDuel(Player initiator, Player opponent) {
        ExtendedPlayer ei = plugin.getExtendedPlayer(initiator);
        ExtendedPlayer eo = plugin.getExtendedPlayer(opponent);
        if (eo.isReady() && eo.getMainPlayer().getBlockedList().contains(initiator.getUniqueId())) {
            plugin.getMessageAPI().sendMessageLang(initiator, "duel_blocked");
            return;
        }
        DuelPreference pref = ei.prefsFor(opponent.getUniqueId());
        Kit kit = pref.getKit();
        Arena arena = pref.getArena();
        int rounds = Math.max(1, pref.getRounds());

        if (kit == null || arena == null) {
            initiator.sendMessage("§cPick both a kit and an arena before sending the duel.");
            openSettingGUI(initiator, opponent);
            return;
        }

        ItemStack display = kit.getDisplayItem() == null ? null : kit.getDisplayItem().convertToItem();
        String kitName = display != null && display.getItemMeta() != null && display.getItemMeta().getDisplayName() != null
                ? display.getItemMeta().getDisplayName()
                : kit.getName();

        plugin.getMessageAPI().sendMessageLang(opponent, "duel_request",
                new String[]{initiator.getName(), kitName, String.valueOf(rounds)});
        plugin.getMessageAPI().sendMessageLang(initiator, "duel_sent");
        plugin.getMessageAPI().sendCommandMessageLang(opponent, "duel_accept", "/acceptduel " + initiator.getName());
    }

    private void roundsGUI(Player p, Player opponent) {
        List<Integer> list = plugin.getConfig().getIntegerList("duels.rounds");
        SGMenu m = plugin.getGUI().create("§d§lRound Target", 1);
        for (int r : list) {
            m.addButton(new SGButton(button(Material.PAPER, "§dFirst to §f" + r,
                    List.of("§7Choose how many rounds decide the winner.")))
                    .withListener(e -> {
                        plugin.getExtendedPlayer(p).prefsFor(opponent.getUniqueId()).setRounds(r);
                        openSettingGUI(p, opponent);
                    }));
        }
        m.setButton(8, new SGButton(button(Material.ARROW, "§6Back", List.of("§7Return to duel setup.")))
                .withListener(e -> openSettingGUI(p, opponent)));
        p.openInventory(m.getInventory());
    }

    private void openSettingGUI(Player p, Player opponent) {
        DuelPreference pref = plugin.getExtendedPlayer(p).prefsFor(opponent.getUniqueId());
        Kit kit = pref.getKit();
        Arena arena = pref.getArena();
        int rounds = Math.max(1, pref.getRounds());

        if (kit == null) {
            kit = DuelManager.stor.kits.stream().filter(Objects::nonNull).findFirst().orElse(null);
            if (kit != null) pref.setKit(kit);
        }
        if (arena == null) {
            arena = DuelManager.stor.arenas.stream()
                    .filter(Objects::nonNull)
                    .filter(a -> !a.deactivated)
                    .findFirst()
                    .orElse(null);
            if (arena != null) pref.setArena(arena);
        }

        SGMenu m = plugin.getGUI().create("§d§lDuel Setup §8• §fOverview", 3);

        ItemStack kitItem = kit == null
                ? button(Material.GRAY_DYE, "§7No Ruleset Selected", List.of("§7Pick the kit both players will use.", "§fClick to browse rulesets."))
                : safeDisplay(kit.getDisplayItem(), Material.IRON_SWORD, "§dSelected Ruleset",
                List.of("§7This ruleset will be used for the duel.", "§fClick to change it."));
        appendLore(kitItem, "§8• §7Ruleset");
        m.setButton(10, new SGButton(kitItem).withListener(e -> openKitGUI(p, opponent, false)));

        ItemStack arenaItem = arena == null
                ? button(Material.MAP, "§7No Arena Selected", List.of("§7Pick where the duel should happen.", "§fClick to browse arenas."))
                : safeDisplay(arena.getDisplayItem(), Material.GRASS_BLOCK, "§bSelected Arena",
                List.of("§7This map will host the duel.", "§fClick to change it."));
        appendLore(arenaItem, "§8• §7Arena");
        m.setButton(12, new SGButton(arenaItem).withListener(e -> openArenaGUI(p, opponent, false)));

        m.setButton(14, new SGButton(button(Material.PAPER, "§dFirst to §f" + rounds,
                List.of("§7Pick the round target.", "§fClick to change it.")))
                .withListener(e -> roundsGUI(p, opponent)));

        boolean telAvailable = rounds <= 3;
        boolean tel = pref.isTelemetry() && telAvailable;
        if (pref.isTelemetry() && !telAvailable) pref.setTelemetry(false);

        ItemStack telItem = button(tel ? Material.REPEATING_COMMAND_BLOCK : Material.COMMAND_BLOCK,
                (tel ? "§a§l✔ " : "§c§l✘ ") + "§dMatch Telemetry",
                telAvailable
                        ? List.of("§7Record hits, ping, and movement.", "§7Generates a Match ID for analytics.", "§fClick to toggle " + (tel ? "OFF" : "ON") + ".")
                        : List.of("§cTelemetry is limited to first to 3s.", "§7Lower the round target to enable."));

        m.setButton(13, new SGButton(telItem).withListener(e -> {
            if (!telAvailable) {
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.9f);
                return;
            }
            pref.setTelemetry(!tel);
            openSettingGUI(p, opponent);
        }));

        boolean ready = kit != null && arena != null;
        List<String> challengeLore = new ArrayList<>(ready
                ? List.of("§7Everything looks good.", "§fClick to send the duel request.")
                : List.of("§7You still need a kit and arena.", "§fClick after both are selected."));
        if (ready && tel) {
            challengeLore.add(" ");
            challengeLore.add("§d§lTELEMETRY ENABLED");
        }

        m.setButton(16, new SGButton(button(ready ? Material.LIME_CONCRETE : Material.PINK_CONCRETE,
                ready ? "§d§lSend Challenge" : "§5§lFinish Setup",
                challengeLore))
                .withListener(e -> {
                    if (!ready) {
                        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.9f);
                        return;
                    }
                    requestDuel(p, opponent);
                    p.closeInventory();
                }));

        m.setButton(22, new SGButton(button(Material.BARRIER, "§cClose", List.of("§7Close the duel setup.")))
                .withListener(e -> p.closeInventory()));
        p.openInventory(m.getInventory());
    }

    private void openArenaGUI(Player p, Player opponent, boolean quick) {
        SGMenu m = plugin.getGUI().create("§b§lArena Browser §8• §fPick a Map", 5);
        int off = 0;
        for (Arena a : DuelManager.stor.arenas) {
            if (a == null) continue;
            if (a.deactivated) continue;
            ItemStack it = safeDisplay(a.getDisplayItem(), Material.GRASS_BLOCK, "§bArena", List.of("§7Click to select this arena."));
            appendLore(it, "§8• §7Map ready");
            m.setButton(off++, new SGButton(it).withListener(e -> {
                plugin.getExtendedPlayer(p).prefsFor(opponent.getUniqueId()).setArena(a);
                if (quick) plugin.getExtendedPlayer(p).prefsFor(opponent.getUniqueId()).setRounds(
                        plugin.getConfig().getInt("duels.default_rounds", 1));
                openSettingGUI(p, opponent);
            }));
        }
        m.setButton(44, new SGButton(button(Material.ARROW, "§6Back", List.of("§7Return to duel setup.")))
                .withListener(e -> openSettingGUI(p, opponent)));
        p.openInventory(m.getInventory());
    }

    private void openKitGUI(Player p, Player opponent, boolean quick, int page) {
        int rows = Math.max(3, plugin.getConfig().getInt("guis.duel.kitselector.rows", 6));
        int pageSize = rows * 9;
        int maxSlot = DuelManager.stor.kits.stream().filter(Objects::nonNull).mapToInt(Kit::getSlot).max().orElse(0);
        int maxPage = maxSlot / pageSize;
        SGMenu m = plugin.getGUI().create("§d§lRulesets §8• §fPage " + (page + 1) + "§8/§f" + (maxPage + 1), rows);

        DuelPreference pref = plugin.getExtendedPlayer(p).prefsFor(opponent.getUniqueId());

        for (Kit k : DuelManager.stor.kits) {
            if (k == null) continue;
            int global = k.getSlot();
            if (global / pageSize != page) continue;
            int local = global % pageSize;
            ItemStack it = safeDisplay(k.getDisplayItem(), Material.IRON_SWORD, "§dRuleset", List.of("§7Choose this ruleset.", "§fClick to lock it in."));
            appendLore(it, "§8• §7Ready for queue and duels");
            m.setButton(local, new SGButton(it).withListener(e -> {
                pref.setKit(k);
                if (quick) openArenaGUI(p, opponent, true);
                else openSettingGUI(p, opponent);
            }));
        }

        if (page > 0) {
            m.setButton(pageSize - 10, new SGButton(button(Material.ARROW, "§e← Previous", List.of("§7View the previous page.")))
                    .withListener(e -> openKitGUI(p, opponent, quick, page - 1)));
        }
        if (page < maxPage) {
            m.setButton(pageSize - 9, new SGButton(button(Material.ARROW, "§eNext →", List.of("§7View the next page.")))
                    .withListener(e -> openKitGUI(p, opponent, quick, page + 1)));
        }
        if (hasPageCategories()) {
            m.setButton(pageSize - 5, new SGButton(button(Material.ARROW, "§dCategories", List.of("§7Return to category view.")))
                    .withListener(e -> openKitCategoryGUI(p, opponent, quick)));
        }
        m.setButton(pageSize - 6, new SGButton(button(Material.ARROW, "§6Back", List.of("§7Return to duel setup.")))
                .withListener(e -> openSettingGUI(p, opponent)));

        addCustomKitButtons(p, opponent, m, quick, pageSize);
        p.openInventory(m.getInventory());
    }

    private void addCustomKitButtons(Player p, Player opponent, SGMenu m, boolean quick, int size) {
        ItemStack custom = button(Material.BLAZE_POWDER, "§eSelected Custom Kit",
                List.of("§7Use your currently selected custom kit.", "§fClick to apply it."));
        ItemStack perCustom = button(Material.BLAZE_POWDER, "§6Per Player Custom Kits",
                List.of("§7Each player uses their own selected custom kit.", "§fClick to apply it."));

        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        Supplier<CustomKit> sel = () -> {
            var map = ep.getSelectedKit();
            Integer idx = map.getOrDefault(p.getUniqueId(), 1);
            return ep.getMainPlayer().getKits()[idx];
        };

        m.setButton(size - 2, new SGButton(custom).withListener(e -> {
            CustomKit ck = sel.get();
            if (ck == null) { p.sendMessage("§cYou do not have a custom kit."); return; }
            Kit k = ck.toKit();
            k.setName("Custom kit");
            k.setDisplayItem(new SerializableItem(custom));
            ep.prefsFor(opponent.getUniqueId()).setKit(k);
            if (quick) openArenaGUI(p, opponent, true);
            else openSettingGUI(p, opponent);
        }));

        m.setButton(size - 1, new SGButton(perCustom).withListener(e -> {
            CustomKit ck = sel.get();
            if (ck == null) { p.sendMessage("§cYou do not have a custom kit."); return; }
            Kit k = ck.toKit();
            k.setName("Per custom kit");
            k.setPk(true);
            k.setDisplayItem(new SerializableItem(perCustom));
            ep.prefsFor(opponent.getUniqueId()).setKit(k);
            if (quick) openArenaGUI(p, opponent, true);
            else openSettingGUI(p, opponent);
        }));
    }

    private ItemStack safeDisplay(SerializableItem serializableItem, Material fallback, String fallbackName, List<String> fallbackLore) {
        ItemStack item = serializableItem == null ? null : serializableItem.convertToItem();
        if (item == null || item.getType().isAir()) {
            return button(fallback, fallbackName, fallbackLore);
        }
        return item;
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack it = new ItemStack(material);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(name);
            im.setLore(lore);
            it.setItemMeta(im);
        }
        return it;
    }

    private void appendLore(ItemStack item, String... extra) {
        if (item == null) return;
        ItemMeta im = item.getItemMeta();
        if (im == null) return;
        List<String> lore = im.hasLore() ? new ArrayList<>(im.getLore()) : new ArrayList<>();
        lore.add(" ");
        lore.addAll(Arrays.asList(extra));
        im.setLore(lore);
        item.setItemMeta(im);
    }
}
