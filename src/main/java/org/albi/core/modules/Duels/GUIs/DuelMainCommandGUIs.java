package org.albi.core.modules.Duels.GUIs;

import org.albi.core.gui.Messages;

import org.albi.core.gui.GuiService;

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
        SGMenu menu = plugin.getMenus().create("duels.categories", 3);
        for (Map.Entry<Integer, DuelKitPageCategory> entry : getPageCategories().entrySet()) {
            DuelKitPageCategory cat = entry.getValue();
            if (cat == null || cat.getDisplayItem() == null) continue;
            ItemStack icon = cat.getDisplayItem().convertToItem();
            if (icon == null || icon.getType().isAir()) continue;
            ItemMeta im = icon.getItemMeta();
            List<String> lore = im != null && im.hasLore() ? new ArrayList<>(im.getLore()) : new ArrayList<>();
            lore.add(" ");
            lore.add(GuiService.text("duel-main-command-guis.open-page", cat.getPage()));
            lore.add(GuiService.text("duel-main-command-guis.jump-into-this-family-of-rulesets"));
            if (im != null) {
                im.setLore(lore);
                icon.setItemMeta(im);
            }
            int slot = Math.max(0, Math.min(26, cat.getGuiSlot()));
            final int page = cat.getPage();
            menu.setButton(slot, new SGButton(icon).withListener(e -> openKitGUI(p, opponent, quick, page)));
        }
        GuiService.control(menu, "close", 22, new SGButton(button(Material.BARRIER, GuiService.text("duel-main-command-guis.close"), List.of(GuiService.text("duel-main-command-guis.close-the-duel-browser"))))
                .withListener(e -> p.closeInventory()));
        GuiService.open(p, menu);
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
            Messages.send(initiator, "messages.duel-main-command-guis.pick-both-a-kit-and-an-arena-before");
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
        SGMenu m = plugin.getMenus().create("duels.rounds", 1);
        for (int r : list) {
            m.addButton(new SGButton(button(Material.PAPER, GuiService.text("duel-main-command-guis.first-to", r),
                    List.of(GuiService.text("duel-main-command-guis.choose-how-many-rounds-decide-the-winner"))))
                    .withListener(e -> {
                        plugin.getExtendedPlayer(p).prefsFor(opponent.getUniqueId()).setRounds(r);
                        openSettingGUI(p, opponent);
                    }));
        }
        GuiService.control(m, "back", 8, new SGButton(button(Material.ARROW, GuiService.text("duel-main-command-guis.back"), List.of(GuiService.text("duel-main-command-guis.return-to-duel-setup"))))
                .withListener(e -> openSettingGUI(p, opponent)));
        GuiService.open(p, m);
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

        SGMenu m = plugin.getMenus().create("duels.setup", 3);

        ItemStack kitItem = kit == null
                ? button(Material.GRAY_DYE, GuiService.text("duel-main-command-guis.no-ruleset-selected"), List.of(GuiService.text("duel-main-command-guis.pick-the-kit-both-players-will-use"), GuiService.text("duel-main-command-guis.click-to-browse-rulesets")))
                : safeDisplay(kit.getDisplayItem(), Material.IRON_SWORD, GuiService.text("duel-main-command-guis.selected-ruleset"),
                List.of(GuiService.text("duel-main-command-guis.this-ruleset-will-be-used-for-the-duel"), GuiService.text("duel-main-command-guis.click-to-change-it")));
        appendLore(kitItem, GuiService.text("duel-main-command-guis.ruleset-tag"));
        GuiService.control(m, "kit-item", 10, new SGButton(kitItem).withListener(e -> openKitGUI(p, opponent, false)));

        ItemStack arenaItem = arena == null
                ? button(Material.MAP, GuiService.text("duel-main-command-guis.no-arena-selected"), List.of(GuiService.text("duel-main-command-guis.pick-where-the-duel-should-happen"), GuiService.text("duel-main-command-guis.click-to-browse-arenas")))
                : safeDisplay(arena.getDisplayItem(), Material.GRASS_BLOCK, GuiService.text("duel-main-command-guis.selected-arena"),
                List.of(GuiService.text("duel-main-command-guis.this-map-will-host-the-duel"), GuiService.text("duel-main-command-guis.click-to-change-it")));
        appendLore(arenaItem, GuiService.text("duel-main-command-guis.arena-tag"));
        GuiService.control(m, "arena-item", 12, new SGButton(arenaItem).withListener(e -> openArenaGUI(p, opponent, false)));

        GuiService.control(m, "first-to", 14, new SGButton(button(Material.PAPER, GuiService.text("duel-main-command-guis.first-to", rounds),
                List.of(GuiService.text("duel-main-command-guis.pick-the-round-target"), GuiService.text("duel-main-command-guis.click-to-change-it"))))
                .withListener(e -> roundsGUI(p, opponent)));

        boolean telAvailable = rounds <= 3;
        boolean tel = pref.isTelemetry() && telAvailable;
        if (pref.isTelemetry() && !telAvailable) pref.setTelemetry(false);

        ItemStack telItem = button(tel ? Material.REPEATING_COMMAND_BLOCK : Material.COMMAND_BLOCK,
                GuiService.text("duel-main-command-guis.match-telemetry", (tel ? "§a§l✔ " : "§c§l✘ ")),
                telAvailable
                        ? List.of(GuiService.text("duel-main-command-guis.record-hits-ping-and-movement"), GuiService.text("duel-main-command-guis.generates-a-match-id-for-analytics"), GuiService.text("duel-main-command-guis.click-to-toggle", (tel ? "OFF" : "ON")))
                        : List.of(GuiService.text("duel-main-command-guis.telemetry-is-limited-to-first-to-3s"), GuiService.text("duel-main-command-guis.lower-the-round-target-to-enable")));

        GuiService.control(m, "tel-item", 13, new SGButton(telItem).withListener(e -> {
            if (!telAvailable) {
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.9f);
                return;
            }
            pref.setTelemetry(!tel);
            openSettingGUI(p, opponent);
        }));

        boolean ready = kit != null && arena != null;
        List<String> challengeLore = new ArrayList<>(ready
                ? List.of(GuiService.text("duel-main-command-guis.everything-looks-good"), GuiService.text("duel-main-command-guis.click-to-send-the-duel-request"))
                : List.of(GuiService.text("duel-main-command-guis.you-still-need-a-kit-and-arena"), GuiService.text("duel-main-command-guis.click-after-both-are-selected")));
        if (ready && tel) {
            challengeLore.add(" ");
            challengeLore.add(GuiService.text("duel-main-command-guis.telemetry-enabled"));
        }

        GuiService.control(m, "send-challenge", 16, new SGButton(button(ready ? Material.LIME_CONCRETE : Material.PINK_CONCRETE,
                ready ? GuiService.text("duel-main-command-guis.send-challenge") : GuiService.text("duel-main-command-guis.finish-setup"),
                challengeLore))
                .withListener(e -> {
                    if (!ready) {
                        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.9f);
                        return;
                    }
                    requestDuel(p, opponent);
                    p.closeInventory();
                }));

        GuiService.control(m, "close", 22, new SGButton(button(Material.BARRIER, GuiService.text("duel-main-command-guis.close"), List.of(GuiService.text("duel-main-command-guis.close-the-duel-setup"))))
                .withListener(e -> p.closeInventory()));
        GuiService.open(p, m);
    }

    private void openArenaGUI(Player p, Player opponent, boolean quick) {
        SGMenu m = plugin.getMenus().create("duels.arenas", 5);
        int off = 0;
        for (Arena a : DuelManager.stor.arenas) {
            if (a == null) continue;
            if (a.deactivated) continue;
            ItemStack it = safeDisplay(a.getDisplayItem(), Material.GRASS_BLOCK, GuiService.text("duel-main-command-guis.arena"), List.of(GuiService.text("duel-main-command-guis.click-to-select-this-arena")));
            appendLore(it, GuiService.text("duel-main-command-guis.map-ready"));
            GuiService.entry(m, a.getName(), off++, new SGButton(it).withListener(e -> {
                plugin.getExtendedPlayer(p).prefsFor(opponent.getUniqueId()).setArena(a);
                if (quick) plugin.getExtendedPlayer(p).prefsFor(opponent.getUniqueId()).setRounds(
                        plugin.getConfig().getInt("duels.default_rounds", 1));
                openSettingGUI(p, opponent);
            }));
        }
        GuiService.control(m, "back", 44, new SGButton(button(Material.ARROW, GuiService.text("duel-main-command-guis.back"), List.of(GuiService.text("duel-main-command-guis.return-to-duel-setup"))))
                .withListener(e -> openSettingGUI(p, opponent)));
        GuiService.open(p, m);
    }

    private void openKitGUI(Player p, Player opponent, boolean quick, int page) {
        int rows = Math.max(3, plugin.getConfig().getInt("guis.duel.kitselector.rows", 6));
        int pageSize = rows * 9;
        int maxSlot = DuelManager.stor.kits.stream().filter(Objects::nonNull).mapToInt(Kit::getSlot).max().orElse(0);
        int maxPage = maxSlot / pageSize;
        SGMenu m = plugin.getMenus().create("duels.kits", rows, (page + 1), (maxPage + 1));

        DuelPreference pref = plugin.getExtendedPlayer(p).prefsFor(opponent.getUniqueId());

        for (Kit k : DuelManager.stor.kits) {
            if (k == null) continue;
            int global = k.getSlot();
            if (global / pageSize != page) continue;
            int local = global % pageSize;
            ItemStack it = safeDisplay(k.getDisplayItem(), Material.IRON_SWORD, GuiService.text("duel-main-command-guis.ruleset"), List.of(GuiService.text("duel-main-command-guis.choose-this-ruleset"), GuiService.text("duel-main-command-guis.click-to-lock-it-in")));
            appendLore(it, GuiService.text("duel-main-command-guis.ready-for-queue-and-duels"));
            GuiService.entry(m, k.getName(), local, new SGButton(it).withListener(e -> {
                pref.setKit(k);
                if (quick) openArenaGUI(p, opponent, true);
                else openSettingGUI(p, opponent);
            }));
        }

        if (page > 0) {
            GuiService.control(m, "previous", pageSize - 10, new SGButton(button(Material.ARROW, GuiService.text("duel-main-command-guis.previous"), List.of(GuiService.text("duel-main-command-guis.view-the-previous-page"))))
                    .withListener(e -> openKitGUI(p, opponent, quick, page - 1)));
        }
        if (page < maxPage) {
            GuiService.control(m, "next", pageSize - 9, new SGButton(button(Material.ARROW, GuiService.text("duel-main-command-guis.next"), List.of(GuiService.text("duel-main-command-guis.view-the-next-page"))))
                    .withListener(e -> openKitGUI(p, opponent, quick, page + 1)));
        }
        if (hasPageCategories()) {
            GuiService.control(m, "categories", pageSize - 5, new SGButton(button(Material.ARROW, GuiService.text("duel-main-command-guis.categories"), List.of(GuiService.text("duel-main-command-guis.return-to-category-view"))))
                    .withListener(e -> openKitCategoryGUI(p, opponent, quick)));
        }
        GuiService.control(m, "back", pageSize - 6, new SGButton(button(Material.ARROW, GuiService.text("duel-main-command-guis.back"), List.of(GuiService.text("duel-main-command-guis.return-to-duel-setup"))))
                .withListener(e -> openSettingGUI(p, opponent)));

        addCustomKitButtons(p, opponent, m, quick, pageSize);
        GuiService.open(p, m);
    }

    private void addCustomKitButtons(Player p, Player opponent, SGMenu m, boolean quick, int size) {
        ItemStack custom = button(Material.BLAZE_POWDER, GuiService.text("duel-main-command-guis.selected-custom-kit"),
                List.of(GuiService.text("duel-main-command-guis.use-your-currently-selected-custom-kit"), GuiService.text("duel-main-command-guis.click-to-apply-it")));
        ItemStack perCustom = button(Material.BLAZE_POWDER, GuiService.text("duel-main-command-guis.per-player-custom-kits"),
                List.of(GuiService.text("duel-main-command-guis.each-player-uses-their-own-selected-custom-kit"), GuiService.text("duel-main-command-guis.click-to-apply-it")));

        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        Supplier<CustomKit> sel = () -> {
            var map = ep.getSelectedKit();
            Integer idx = map.getOrDefault(p.getUniqueId(), 1);
            return ep.getMainPlayer().getKits()[idx];
        };

        GuiService.control(m, "custom-kit", size - 2, new SGButton(custom).withListener(e -> {
            CustomKit ck = sel.get();
            if (ck == null) { Messages.send(p, "messages.duel-main-command-guis.you-do-not-have-a-custom-kit"); return; }
            Kit k = ck.toKit();
            k.setName("Custom kit");
            k.setDisplayItem(new SerializableItem(custom));
            ep.prefsFor(opponent.getUniqueId()).setKit(k);
            if (quick) openArenaGUI(p, opponent, true);
            else openSettingGUI(p, opponent);
        }));

        GuiService.control(m, "per-player-custom-kit", size - 1, new SGButton(perCustom).withListener(e -> {
            CustomKit ck = sel.get();
            if (ck == null) { Messages.send(p, "messages.duel-main-command-guis.you-do-not-have-a-custom-kit"); return; }
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
