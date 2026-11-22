package org.albi.core.commands;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.data.MainPlayer;
import org.albi.core.data.StatManagement;
import org.albi.core.managers.tiers.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProfileGUI {

    private final Core plugin;

    public ProfileGUI(Core plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, OfflinePlayer target) {
        MainPlayer targetMp = StatManagement.getMain(target.getUniqueId());
        if (targetMp == null) {
            plugin.getMessageAPI().sendMessage(viewer, "&cError loading player data.");
            return;
        }

        plugin.getTierManager().fetchAll(target.getUniqueId(), false).thenAccept(tiers -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                String title = "§8✦ §b" + target.getName() + "'s Profile";
                SGMenu menu = plugin.getGUI().create(title, 2);

                // Slot 4: Player Head
                menu.setButton(4, new SGButton(getPlayerHead(target, targetMp)));

                // Slot 8: General Stats
                menu.setButton(8, new SGButton(getGeneralStats(targetMp)));

                // Slot 9: PVPTIERS
                menu.setButton(9, new SGButton(
                        getTierItem(tiers.get(PvPTiersProfile.class), "PVPTIERS", Material.NETHERITE_SWORD)));

                // Slot 10: MCTIERS
                menu.setButton(10, new SGButton(
                        getTierItem(tiers.get(MCTiersProfile.class), "MCTIERS", Material.DIAMOND_CHESTPLATE)));

                // Slot 11: SUBTIERS
                menu.setButton(11,
                        new SGButton(getTierItem(tiers.get(SubtiersProfile.class), "SUBTIERS", Material.GOLDEN_APPLE)));

                // Slot 12: View Stats Button
                menu.setButton(12, new SGButton(icon(Material.BOOK, "§e§lView Stats", List.of(
                        "§7Click to view detailed kit statistics.",
                        "§b/stats " + target.getName())))
                        .withListener(e -> viewer.performCommand("stats " + target.getName())));

                // Slot 13: Social Summary
                menu.setButton(13, new SGButton(getSocialSummary(targetMp)));

                // Slot 14: ACCOUNT INFO
                menu.setButton(14, new SGButton(getAccountInfo(targetMp)));

                // Slot 17: Exit
                menu.setButton(17, new SGButton(icon(Material.BARRIER, "§c§lExit", List.of()))
                        .withListener(e -> viewer.closeInventory()));

                viewer.openInventory(menu.getInventory());
            });
        });
    }

    private ItemStack getPlayerHead(OfflinePlayer target, MainPlayer mp) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.setDisplayName("§b§l" + target.getName());
            List<String> lore = new ArrayList<>();
            lore.add("§fLevel: §b" + mp.level());
            lore.add(getXPBar(mp.xp(), mp.xpNeeded()));
            lore.add("§7(" + mp.xp() + " / " + mp.xpNeeded() + " XP)");
            lore.add(" ");
            lore.add("§fStatus: " + (target.isOnline() ? "§aOnline" : "§cOffline"));
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack getGeneralStats(MainPlayer mp) {
        return icon(Material.PAPER, "§f§lGeneral Stats", List.of(
                "§fTotal Playtime: §e" + formatTime(mp.timePlayed()),
                "§fAccount Level: §b" + mp.level(),
                " ",
                "§7Basic overview of the account."));
    }

    private ItemStack getTierItem(SuperProfile profile, String name, Material material) {
        List<String> lore = new ArrayList<>();
        String displayName;

        if (profile != null && profile.status == Status.READY) {
            displayName = "§l§f" + name + " " + profile.getOverallRankName();
            lore.add("§fRegion: §7" + profile.region);
            lore.add("§fPoints: §e" + profile.points);
            lore.add("§fOverall Rank: §7#" + profile.overallPosition);
            lore.add(" ");
            lore.add("§6§lRankings:");

            boolean found = false;
            for (GameMode gm : profile.gameModes) {
                if (gm.status == Status.READY) {
                    String line = "§f" + gm.getDisplayName() + ": §b" + gm.getTierLabel();
                    if (gm.hasPeak()) {
                        line += " §8(Peak: §7" + gm.getPeakTierLabel() + "§8)";
                    }
                    lore.add(line);
                    found = true;
                }
            }

            if (!found) {
                lore.add("§7No active rankings found.");
            }
        } else if (profile != null && profile.status == Status.NOT_EXISTING) {
            displayName = "§l§f" + name + " §7(§8Not Ranked§7)";
            lore.add("§7This player is not ranked on " + name + ".");
        } else if (profile != null && profile.status == Status.API_ISSUE) {
            displayName = "§l§f" + name + " §7(§cAPI Error§7)";
            lore.add("§cCould not reach the " + name + " API.");
        } else {
            displayName = "§l§f" + name + " §7(§eLoading...§7)";
        }

        return icon(material, displayName, lore);
    }

    private ItemStack getSocialSummary(MainPlayer mp) {
        return icon(Material.GOLD_INGOT, "§6§lSocial Summary", List.of(
                "§fFriends: §b" + mp.getFriends().size(),
                "§fBlocked: §c" + mp.getBlockedList().size()));
    }

    private ItemStack getAccountInfo(MainPlayer mp) {
        // Since we don't have join date easily in MainPlayer, we use level and playtime
        return icon(Material.COMPASS, "§b§lAccount Info", List.of(
                "§fPlaytime: §e" + formatTime(mp.timePlayed()),
                "§fExperience: §d" + mp.xp() + " XP",
                "§fRank: §b" + mp.level() // Or maybe use player rank if available
        ));
    }

    private String getXPBar(int current, int max) {
        int bars = 10;
        float percent = (float) current / max;
        int progress = (int) (bars * percent);
        StringBuilder bar = new StringBuilder("§8[");
        for (int i = 0; i < bars; i++) {
            bar.append(i < progress ? "§b■" : "§7■");
        }
        bar.append("§8]");
        return bar.toString();
    }

    private String formatTime(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        if (h > 0)
            return h + "h " + m + "m";
        return m + "m";
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
