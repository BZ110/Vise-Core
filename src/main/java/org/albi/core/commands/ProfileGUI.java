package org.albi.core.commands;

import org.albi.core.gui.Messages;

import org.albi.core.gui.GuiService;

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
import java.util.Locale;

public class ProfileGUI {

    private final Core plugin;

    public ProfileGUI(Core plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, OfflinePlayer target) {
        MainPlayer targetMp = StatManagement.getMain(target.getUniqueId());
        if (targetMp == null) {
            plugin.getMessageAPI().sendMessage(viewer, Messages.text(viewer, "messages.profile-gui.error-loading-player-data"));
            return;
        }

        plugin.getTierManager().fetchAll(target.getUniqueId(), false).thenAccept(tiers -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                SGMenu menu = plugin.getMenus().create("profile.main", 2, target.getName());

                // Slot 4: Player Head
                GuiService.control(menu, "slot-4", 4, new SGButton(getPlayerHead(target, targetMp)));

                // Slot 8: General Stats
                GuiService.control(menu, "slot-8", 8, new SGButton(getGeneralStats(targetMp)));

                addTierButton(menu, "pvptiers", 9, tiers.get(PvPTiersProfile.class), Material.NETHERITE_SWORD);
                addTierButton(menu, "mctiers", 10, tiers.get(MCTiersProfile.class), Material.DIAMOND_CHESTPLATE);
                addTierButton(menu, "subtiers", 11, tiers.get(SubtiersProfile.class), Material.GOLDEN_APPLE);

                // Slot 12: View Stats Button
                GuiService.control(menu, "view-stats", 12, new SGButton(icon(Material.BOOK, GuiService.text("profile-gui.view-stats"), List.of(
                        GuiService.text("profile-gui.click-to-view-detailed-kit-statistics"),
                        GuiService.text("profile-gui.stats", target.getName()))))
                        .withListener(e -> viewer.performCommand("stats " + target.getName())));

                // Slot 13: Social Summary
                GuiService.control(menu, "slot-13", 13, new SGButton(getSocialSummary(targetMp)));

                // Slot 14: ACCOUNT INFO
                GuiService.control(menu, "slot-14", 14, new SGButton(getAccountInfo(targetMp)));

                // Slot 17: Exit
                GuiService.control(menu, "exit", 17, new SGButton(icon(Material.BARRIER, GuiService.text("profile-gui.exit"), List.of()))
                        .withListener(e -> viewer.closeInventory()));

                GuiService.open(viewer, menu);
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
            lore.add(GuiService.text("profile-gui.level", mp.level()));
            lore.add(getXPBar(mp.xp(), mp.xpNeeded()));
            lore.add(GuiService.text("profile-gui.xp", mp.xp(), mp.xpNeeded()));
            lore.add(" ");
            lore.add(GuiService.text("profile-gui.status", (target.isOnline() ? "§aOnline" : "§cOffline")));
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack getGeneralStats(MainPlayer mp) {
        return icon(Material.PAPER, GuiService.text("profile-gui.general-stats"), List.of(
                GuiService.text("profile-gui.total-playtime", formatTime(mp.timePlayed())),
                GuiService.text("profile-gui.account-level", mp.level()),
                " ",
                GuiService.text("profile-gui.basic-overview-of-the-account")));
    }

    private void addTierButton(SGMenu menu, String id, int slot, SuperProfile profile, Material material) {
        if (profile == null) return;
        var icon = getTierItem(profile, id.toUpperCase(Locale.ROOT), material);
        GuiService.control(menu, id, slot, new SGButton(icon));
    }

    private ItemStack getTierItem(SuperProfile profile, String name, Material material) {
        List<String> lore = new ArrayList<>();
        String displayName;

        if (profile.status == Status.READY) {
            displayName = "§l§f" + name + " " + profile.getOverallRankName();
            lore.add(GuiService.text("profile-gui.region", profile.region));
            lore.add(GuiService.text("profile-gui.points", profile.points));
            lore.add(GuiService.text("profile-gui.overall-rank", profile.overallPosition));
            lore.add(" ");
            lore.add(GuiService.text("profile-gui.rankings"));

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
                lore.add(GuiService.text("profile-gui.no-active-rankings-found"));
            }
        } else if (profile.status == Status.NOT_EXISTING) {
            displayName = "§l§f" + name + " §7(§8Not Ranked§7)";
            lore.add(GuiService.text("profile-gui.this-player-is-not-ranked-on", name));
        } else if (profile.status == Status.API_ISSUE) {
            displayName = "§l§f" + name + " §7(§cAPI Error§7)";
            lore.add(GuiService.text("profile-gui.could-not-reach-the-api", name));
        } else {
            displayName = "§l§f" + name + " §7(§eLoading...§7)";
        }

        return icon(material, displayName, lore);
    }

    private ItemStack getSocialSummary(MainPlayer mp) {
        return icon(Material.GOLD_INGOT, GuiService.text("profile-gui.social-summary"), List.of(
                "§fFriends: §b" + mp.getFriends().size(),
                "§fBlocked: §c" + mp.getBlockedList().size()));
    }

    private ItemStack getAccountInfo(MainPlayer mp) {
        // Since we don't have join date easily in MainPlayer, we use level and playtime
        return icon(Material.COMPASS, GuiService.text("profile-gui.account-info"), List.of(
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
