package org.albi.core.modules.Duels.ranked;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.data.StatManagement;
import org.albi.core.modules.Duels.ranked.RankedMatchRecord;
import org.albi.core.modules.Duels.ranked.RankedTier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class RankedCommands implements CommandExecutor {

    private final Core plugin;

    public RankedCommands(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player))
            return true;

        if (label.equalsIgnoreCase("rankedlb")) {
            openLeaderboard(player);
            return true;
        }

        if (label.equalsIgnoreCase("seegames")) {
            String name = args.length == 0 ? player.getName() : args[0];
            if (!name.matches("[a-zA-Z0-9_]{1,16}")) {
                player.sendMessage("§cEnter the player's exact Minecraft name.");
                return true;
            }
            var profile = plugin.getExtendedPlayer(player);
            if (profile.checkCooldown("seegames")) {
                plugin.getMessageAPI().sendMessageLang(player, "cooldown");
                return true;
            }
            profile.addCooldown("seegames", plugin.getConfig().getInt("cooldowns.seegames", 2));
            Player target = Bukkit.getPlayerExact(name);
            showRecentGames(player, target == null ? name : target.getName(), target == null ? null : target.getUniqueId());
            return true;
        }

        if (label.equalsIgnoreCase("matchinfo")) {
            if (args.length == 0) {
                player.sendMessage("§cUsage: /matchinfo <id>");
                return true;
            }
            showMatchInfo(player, args[0]);
            return true;
        }

        if (label.equalsIgnoreCase("rankedreport")) {
            if (args.length == 0) {
                player.sendMessage("§cUsage: /rankedreport <matchId>");
                return true;
            }
            generateMatchReport(player, args[0]);
            return true;
        }

        return false;
    }

    private void openLeaderboard(Player player) {
        SGMenu menu = plugin.getGUI().create("\u00A78Ranked Leaderboard (Top 27)", 3);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            StatManagement.getRedis().withJedis(j -> {
                List<redis.clients.jedis.resps.Tuple> tuples = j.zrevrangeWithScores("lb:ranked:elo", 0, 26);
                List<SGButton> buttons = new ArrayList<>();
                int rank = 1;

                for (redis.clients.jedis.resps.Tuple tuple : tuples) {
                    UUID uuid = UUID.fromString(tuple.getElement());
                    int elo = (int) tuple.getScore();
                    String name = StatManagement.getName(uuid);
                    if (name == null)
                        name = Bukkit.getOfflinePlayer(uuid).getName();
                    if (name == null)
                        name = "Unknown";

                    ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta meta = (SkullMeta) head.getItemMeta();
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
                    meta.setDisplayName("\u00A7d#" + rank + " \u00A7f" + name);
                    meta.setLore(List.of(
                            "\u00A77Elo: \u00A76" + elo,
                            "\u00A77Tier: " + RankedTier.getTierDisplay(elo)));
                    head.setItemMeta(meta);

                    buttons.add(new SGButton(head));
                    rank++;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (SGButton btn : buttons)
                        menu.addButton(btn);
                    player.openInventory(menu.getInventory());
                });
                return null;
            });
        });
    }

    private void showRecentGames(Player viewer, String name, UUID localId) {
        viewer.sendMessage("§8[§dRanked§8] §7Fetching recent games for §f" + name + "§7...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID id = localId == null ? StatManagement.findRecentMatchPlayer(name) : localId;
            List<RankedMatchRecord> matches = id == null ? List.of() : StatManagement.loadRecentMatches(id, 10);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!viewer.isOnline()) return;
                if (matches.isEmpty()) {
                    viewer.sendMessage("§cNo recent ranked matches found for this player.");
                    return;
                }
                viewer.sendMessage("§8§m--------------------------------------");
                viewer.sendMessage("§d§lRecent Ranked Matches - " + name);
                for (RankedMatchRecord match : matches) {
                    String result = match.getWinner() == null ? "§eDraw"
                            : (match.getWinner().equals(id) ? "§aVictory" : "§cDefeat");
                    String opponent = match.getPlayer1().equals(id) ? match.getPlayer2Name() : match.getPlayer1Name();
                    viewer.sendMessage("§8» " + result + " §7vs §f" + opponent + " §8| §7ID: §f" + match.getMatchId()
                            + (match.isSuspicious() ? " §c§l[Suspicious!]" : ""));
                }
                viewer.sendMessage("§8§m--------------------------------------");
            });
        });
    }

    private void showMatchInfo(Player player, String matchId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            RankedMatchRecord record = StatManagement.loadMatchRecord(matchId);
            if (record == null) {
                player.sendMessage("§cMatch not found.");
                return;
            }

            player.sendMessage("§8§m--------------------------------------");
            player.sendMessage("§d§lMatch info: " + record.getMatchId());
            player.sendMessage("§7Kit: §f" + record.getKitName());
            player.sendMessage("§7Players: §f" + record.getPlayer1Name() + " §7vs §f" + record.getPlayer2Name());

            double avgReach = record.getHits().stream().mapToDouble(RankedMatchRecord.HitRecord::distance).average()
                    .orElse(0);
            player.sendMessage(
                    "§7Average Reach: " + (avgReach > 3.5 ? "§c" : "§a") + String.format("%.2f", avgReach) + " blocks");

            if (record.isSuspicious()) {
                player.sendMessage("§c§lSuspicious Activity Detected!");
                player.sendMessage("§cReason: " + record.getSuspicionReason());
            }

            player.sendMessage("§8§m--------------------------------------");
        });
    }

    private void generateMatchReport(Player player, String matchId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            RankedMatchRecord record = StatManagement.loadMatchRecord(matchId);
            if (record == null) {
                player.sendMessage("§cMatch not found.");
                return;
            }

            String html = RankedReportGenerator.generateHtml(record);
            File reportFolder = new File(plugin.getDataFolder(), "reports");
            if (!reportFolder.exists())
                reportFolder.mkdirs();

            File reportFile = new File(reportFolder, matchId + ".html");
            try (FileWriter writer = new FileWriter(reportFile)) {
                writer.write(html);
                player.sendMessage("§8[§dRanked§8] §aReport generated successfully!");
                player.sendMessage("§7File: §f" + reportFile.getAbsolutePath());

                if (WebhookService.sendMatchReport(plugin, record, reportFile)) {
                    player.sendMessage("§8[§dRanked§8] §eDiscord webhook delivery queued.");
                }
            } catch (IOException e) {
                player.sendMessage("§cFailed to save match report.");
                e.printStackTrace();
            }
        });
    }
}
