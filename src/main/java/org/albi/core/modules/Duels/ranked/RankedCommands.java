package org.albi.core.modules.Duels.ranked;

import org.albi.core.gui.Messages;

import org.albi.core.gui.GuiService;

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
                Messages.send(player, "messages.ranked-commands.enter-the-player-s-exact-minecraft-name");
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
                Messages.send(player, "messages.ranked-commands.usage-matchinfo-id");
                return true;
            }
            showMatchInfo(player, args[0]);
            return true;
        }

        if (label.equalsIgnoreCase("rankedreport")) {
            if (args.length == 0) {
                Messages.send(player, "messages.ranked-commands.usage-rankedreport-match-id");
                return true;
            }
            generateMatchReport(player, args[0]);
            return true;
        }

        return false;
    }

    private void openLeaderboard(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            var rows = StatManagement.rankedLeaderboard(27);
            Bukkit.getScheduler().runTask(plugin, () -> openLeaderboard(player, rows));
        });
    }

    private void openLeaderboard(Player player, List<StatManagement.LBRow> rows) {
        if (!player.isOnline()) return;
        if (rows.isEmpty()) {
            Messages.send(player, "messages.leaderboard-command.no-data");
            return;
        }

        SGMenu menu = plugin.getMenus().create("ranked.leaderboard", 3);
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            var owner = Bukkit.getOfflinePlayer(row.uuid());
            String name = row.name() == null ? owner.getName() : row.name();
            int elo = (int) row.score();

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(owner);
            meta.setDisplayName(GuiService.text("ranked-commands.open-leaderboard", i + 1,
                    name == null ? row.uuid() : name));
            meta.setLore(List.of(
                    GuiService.text("ranked-commands.leaderboard-elo", elo),
                    GuiService.text("ranked-commands.leaderboard-tier", RankedTier.getTierDisplay(elo))));
            head.setItemMeta(meta);
            menu.addButton(new SGButton(head));
        }
        GuiService.open(player, menu);
    }

    private void showRecentGames(Player viewer, String name, UUID localId) {
        Messages.send(viewer, "messages.ranked-commands.ranked-fetching-recent-games-for", name);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID id = localId == null ? StatManagement.findRecentMatchPlayer(name) : localId;
            List<RankedMatchRecord> matches = id == null ? List.of() : StatManagement.loadRecentMatches(id, 10);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!viewer.isOnline()) return;
                if (matches.isEmpty()) {
                    Messages.send(viewer, "messages.ranked-commands.no-recent-ranked-matches-found-for-this-player");
                    return;
                }
                Messages.send(viewer, "messages.ranked-commands.show-recent-games");
                Messages.send(viewer, "messages.ranked-commands.recent-ranked-matches", name);
                for (RankedMatchRecord match : matches) {
                    String result = match.getWinner() == null ? "§eDraw"
                            : (match.getWinner().equals(id) ? "§aVictory" : "§cDefeat");
                    String opponent = match.getPlayer1().equals(id) ? match.getPlayer2Name() : match.getPlayer1Name();
                    Messages.send(viewer, "messages.ranked-commands.vs-id", result, opponent, match.getMatchId(), (match.isSuspicious() ? " §c§l[Suspicious!]" : ""));
                }
                Messages.send(viewer, "messages.ranked-commands.show-recent-games");
            });
        });
    }

    private void showMatchInfo(Player player, String matchId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            RankedMatchRecord record = StatManagement.loadMatchRecord(matchId);
            if (record == null) {
                Messages.send(player, "messages.ranked-commands.match-not-found");
                return;
            }

            Messages.send(player, "messages.ranked-commands.show-match-info");
            Messages.send(player, "messages.ranked-commands.match-info", record.getMatchId());
            Messages.send(player, "messages.ranked-commands.kit", record.getKitName());
            Messages.send(player, "messages.ranked-commands.players-vs", record.getPlayer1Name(), record.getPlayer2Name());

            double avgReach = record.getHits().stream().mapToDouble(RankedMatchRecord.HitRecord::distance).average()
                    .orElse(0);
            Messages.send(player, "messages.ranked-commands.average-reach-blocks", (avgReach > 3.5 ? "§c" : "§a"), String.format("%.2f", avgReach));

            if (record.isSuspicious()) {
                Messages.send(player, "messages.ranked-commands.suspicious-activity-detected");
                Messages.send(player, "messages.ranked-commands.reason", record.getSuspicionReason());
            }

            Messages.send(player, "messages.ranked-commands.show-match-info");
        });
    }

    private void generateMatchReport(Player player, String matchId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            RankedMatchRecord record = StatManagement.loadMatchRecord(matchId);
            if (record == null) {
                Messages.send(player, "messages.ranked-commands.match-not-found");
                return;
            }

            String html = RankedReportGenerator.generateHtml(record);
            File reportFolder = new File(plugin.getDataFolder(), "reports");
            if (!reportFolder.exists())
                reportFolder.mkdirs();

            File reportFile = new File(reportFolder, matchId + ".html");
            try (FileWriter writer = new FileWriter(reportFile)) {
                writer.write(html);
                Messages.send(player, "messages.ranked-commands.ranked-report-generated-successfully");
                Messages.send(player, "messages.ranked-commands.file", reportFile.getAbsolutePath());

                if (WebhookService.sendMatchReport(plugin, record, reportFile)) {
                    Messages.send(player, "messages.ranked-commands.ranked-discord-webhook-delivery-queued");
                }
            } catch (IOException e) {
                Messages.send(player, "messages.ranked-commands.failed-to-save-match-report");
                e.printStackTrace();
            }
        });
    }
}
