package org.albi.core.commands;

import org.albi.core.gui.Messages;

import org.albi.core.gui.GuiService;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.data.MainPlayer;
import org.albi.core.data.StatManagement;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Kit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class StatsCommand extends BaseCommand {

    private final Core plugin;
    public StatsCommand(Core plugin) { this.plugin = plugin; }

    /* ─────────────────────────────────────────────────────────────── */
    /* /stats                                                         */
    /* ─────────────────────────────────────────────────────────────── */
    @Override
    public boolean onCommand(CommandSender sender, Command cmd,
                             String label, String[] args) {

        if (!(sender instanceof Player viewer)) return true;

        // choose target
        OfflinePlayer target = viewer;
        if (args.length > 0) {
            OfflinePlayer o = Bukkit.getOfflinePlayer(args[0]);
            if (o == null || !o.hasPlayedBefore()) {
                plugin.getMessageAPI().sendMessage(viewer, Messages.text(viewer, "messages.stats-command.player-not-found"));
                return true;
            }
            target = o;
        }

        OfflinePlayer finalTarget = target;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            MainPlayer mp = StatManagement.getMain(finalTarget.getUniqueId());
            if (mp == null || (!mp.visibleStats()
                    && !viewer.getUniqueId().equals(finalTarget.getUniqueId()))) {
                plugin.getMessageAPI().sendMessage(viewer, Messages.text(viewer, "messages.stats-command.that-player-s-stats-are-private"));
                return;
            }

            Map<String, StatManagement.Stat> raw =
                    StatManagement.getAsync(finalTarget.getUniqueId(),
                                    finalTarget.getName())
                            .join()
                            .all();

            if (raw == null || raw.isEmpty()) {
                plugin.getMessageAPI().sendMessage(viewer, Messages.text(viewer, "messages.stats-command.no-stats-found"));
                return;
            }

            // clean null keys or values
            Map<String, StatManagement.Stat> stats = raw.entrySet().stream()
                    .filter(e -> e.getKey() != null && e.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            if (stats.isEmpty()) {
                plugin.getMessageAPI().sendMessage(viewer, Messages.text(viewer, "messages.stats-command.no-valid-stats-to-display"));
                return;
            }

            /* ── sort by kit name, case-insensitive ── */
            List<String> keys = new ArrayList<>(stats.keySet());
            keys.sort((a, b) -> {
                int n = baseKitName(a).compareToIgnoreCase(baseKitName(b));
                return (n != 0) ? n : a.compareToIgnoreCase(b);
            });

            /* ── build paged GUI (5×9 items + control row) ── */
            List<SGMenu> pages = new ArrayList<>();
            SGMenu current = plugin.getMenus().create("stats.main", 6);
            pages.add(current);

            int slot = 0, page = 0;
            for (String game : keys) {

                if (slot >= 45) { // fill, then new page
                    GuiService.control(current, "next", 53, pageButton(page + 1));
                    page++;
                    current = plugin.getMenus().create("stats.main", 6);
                    pages.add(current);
                    slot = 0;
                }

                StatManagement.Stat st = stats.get(game);
                if (st == null) continue;

                /* choose icon */
                ItemStack icon;
                String base = baseKitName(game);
                Kit kit = DuelManager.stor == null ? null : DuelManager.stor.kits.stream()
                        .filter(k -> k.getName().equalsIgnoreCase(base))
                        .findFirst()
                        .orElse(null);

                if (kit == null) {                         // custom game or unknown kit
                    icon = customIcon(base, st);
                } else {                                   // regular kit (or mismatch)
                    icon = kitIcon(game, kit, st);
                }

                icon.setAmount(teamSize(game));
                current.setButton(slot++, new SGButton(icon));
            }

            /* add total-time clock on first page */
            int total = (mp != null) ? mp.timePlayed() : 0;
            int duel  = stats.values().stream().mapToInt(StatManagement.Stat::timePlayed).sum();
            current = pages.get(0); // first page

            ItemStack clock = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta cm = clock.getItemMeta();
            cm.setDisplayName(GuiService.text("stats-command.more-information"));
            cm.setLore(List.of(
                    "§7On server: §f" + fmt(total),
                    "§7In games: §f" + fmt(duel),
                    " ",
                    "§7Level: §f" + mp.level(),
                    "§7XP: §f" + mp.xp() + "§8/" + mp.xpNeeded()
            ));
            clock.setItemMeta(cm);
            GuiService.control(current, "clock", 49, new SGButton(clock));

            Bukkit.getScheduler().runTask(plugin,
                    () -> GuiService.open(viewer, pages.get(0)));
        });

        return true;
    }

    /* ─────────────────────────────────────────────────────────────── */
    /* helpers                                                        */
    /* ─────────────────────────────────────────────────────────────── */

    /** kit base name without “ 1v1 ”, “ 2v2 ”, or “ Mismatch ” suffix */
    private String baseKitName(String game) {
        return game.replaceAll(" (\\d+v\\d+|Mismatch)$", "");
    }

    /** “1v1”, “2v2”, or “Mismatch” part for display */
    private String modeSuffix(String game) {
        int idx = game.lastIndexOf(' ');
        return (idx < 0) ? "" : game.substring(idx + 1);
    }

    /** number before the ‘v’, default 1, used for ItemStack amount */
    private int teamSize(String game) {
        String suffix = modeSuffix(game);
        if (suffix.matches("\\d+v\\d+")) {
            try { return Integer.parseInt(suffix.split("v")[0]); }
            catch (NumberFormatException ignored) {}
        }
        return 1;
    }

    /** green concrete for page jump */
    private SGButton pageButton(int page) {
        ItemStack it = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(GuiService.text("stats-command.page", page));
        it.setItemMeta(im);
        return new SGButton(it);
    }

    /** lore builder reused by both icon flavours */
    private List<String> lore(StatManagement.Stat s) {
        return List.of(
                "§7 - Games played: §f" + s.roundsPlayed(),
                "§7 - Games won: §f"   + s.wins(),
                "§7 - KDR: §a"        + s.kills() + "§7 : §c" + s.deaths(),
                "§7 - Time: §f"       + fmt(s.timePlayed()),
                " ",
                "§7Current Streak: §f" + s.winStreak()
        );
    }

    private ItemStack customIcon(String title, StatManagement.Stat s) {
        ItemStack it = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName("§6" + title);
        im.setLore(lore(s));
        it.setItemMeta(im);
        return it;
    }

    private ItemStack kitIcon(String game, Kit kit, StatManagement.Stat s) {
        ItemStack it = kit.getDisplayItem().convertToItem();
        ItemMeta im = it.getItemMeta();

        /* keep kit's colour, then reset + gray suffix */
        String display = im.getDisplayName();
        String suffix  = modeSuffix(game); // “1v1”, “2v2”, or “Mismatch”
        im.setDisplayName(GuiService.text("stats-command.kit-icon", display + ChatColor.RESET + ChatColor.DARK_GRAY, suffix));
        im.setLore(lore(s));
        it.setItemMeta(im);
        return it;
    }

    private String fmt(int sec) {
        int d = sec / 86400;  sec %= 86400;
        int h = sec / 3600;   sec %= 3600;
        int m = sec / 60;     sec %= 60;

        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (sec > 0 || sb.length() == 0) sb.append(sec).append("s");
        return sb.toString().trim();
    }
}
