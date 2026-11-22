package org.albi.core.modules.Duels.queue;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.Duels.DuelInstance;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Arena;
import org.albi.core.modules.Duels.helpers.DuelKitPageCategory;
import org.albi.core.modules.Duels.helpers.Kit;
import org.albi.core.modules.Duels.ranked.RankedStats;
import org.albi.core.util.KitSafety;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;

public class RankedQueueModule implements Listener, CommandExecutor {

    private final Core plugin;
    private final Map<String, Deque<UUID>> queues = new ConcurrentHashMap<>();
    private final Map<UUID, Long> queuedSince = new ConcurrentHashMap<>();
    private final Map<UUID, String> queuedKey = new ConcurrentHashMap<>();

    public RankedQueueModule(Core plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getCommand("rankedqueue").setExecutor(this);

        new BukkitRunnable() {
            @Override
            public void run() {
                matchQueues();
            }
        }.runTaskTimer(plugin, 20L, 20L); // Check every second

        new BukkitRunnable() {
            @Override
            public void run() {
                sendQueuedActionBars();
            }
        }.runTaskTimer(plugin, 20L, 20L);

        new BukkitRunnable() {
            @Override
            public void run() {
                sendQueuedMessages();
            }
        }.runTaskTimer(plugin, 40L, 40L); // 2 seconds
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player))
            return false;

        if (args.length > 0 && args[0].equalsIgnoreCase("leave")) {
            leaveQueue(player);
            return true;
        }

        new RankedQueueGUI(player).open();
        return true;
    }

    private class RankedQueueGUI {
        private static final int PAGE_SIZE = 27;
        private final Player viewer;

        RankedQueueGUI(Player viewer) {
            this.viewer = viewer;
        }

        void open() {
            SGMenu menu = plugin.getGUI().create("§6§lRanked Queue", 3);

            // Informational Sign
            ItemStack sign = new ItemStack(Material.OAK_SIGN);
            ItemMeta signMeta = sign.getItemMeta();
            if (signMeta != null) {
                signMeta.setDisplayName("§6§lRanking Information");
                signMeta.setLore(Arrays.asList(
                        "§7Our system uses a dynamic ELO algorithm.",
                        "§7Stakes scale with opponent difficulty:",
                        "§7 - Beat a PRO: §aHigh Reward",
                        "§7 - Lose to a NOOB: §cHeavy Penalty",
                        "§7Matches are §fBest of 1§7."));
                sign.setItemMeta(signMeta);
            }
            menu.setButton(4, new SGButton(sign));

            for (Kit k : DuelManager.stor.kits) {
                if (k.isPk())
                    continue; // Ranked doesn't support player custom kits usually
                int global = k.getSlot();
                if (global >= PAGE_SIZE)
                    continue;
                int local = global;

                ItemStack icon = k.getDisplayItem().convertToItem();
                ItemMeta im = icon.getItemMeta();
                if (im != null) {
                    List<String> lore = im.hasLore() ? new ArrayList<>(im.getLore()) : new ArrayList<>();
                    lore.add(" ");
                    RankedStats rs = plugin.getExtendedPlayer(viewer).getMainPlayer().getKitRankedStats(k.getName());
                    lore.add("§6Your Elo: §f" + rs.getElo());
                    lore.add("§6Status: " + rs.getTierDisplay());
                    lore.add("§eQueued: §f" + getQueued(k));
                    lore.add(" ");
                    lore.add("§dClick to join ranked queue!");
                    im.setLore(lore);
                    icon.setItemMeta(im);
                }

                menu.setButton(local, new SGButton(icon).withListener(e -> {
                    joinQueue(viewer, k);
                    viewer.closeInventory();
                }));
            }

            viewer.openInventory(menu.getInventory());
        }
    }

    private void joinQueue(Player player, Kit kit) {
        // Safety checks
        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (ep.getPlayerDuelInstance() != null) {
            player.sendMessage("§cYou cannot queue while in a duel!");
            return;
        }
        if (org.albi.core.modules.Tournaments.TournamentManager.getInstance() != null
                && org.albi.core.modules.Tournaments.TournamentManager.getInstance().isInTournament(player)) {
            player.sendMessage("§cYou cannot queue for ranked duels while you are in a tournament.");
            return;
        }
        if (plugin.getPartyManager() != null && plugin.getPartyManager().isInParty(player)) {
            player.sendMessage("§cYou cannot join solo ranked while in a party!");
            return;
        }
        if (ep.isSpectating()) {
            player.sendMessage("§cYou cannot queue while spectating!");
            return;
        }

        leaveQueue(player);
        queues.computeIfAbsent(kit.getName(), k -> new ConcurrentLinkedDeque<>()).add(player.getUniqueId());
        queuedSince.put(player.getUniqueId(), System.currentTimeMillis());
        queuedKey.put(player.getUniqueId(), kit.getName());

        player.sendMessage("§aYou have joined the §6Ranked §aqueue for §f" + KitSafety.queueDisplayName(kit) + "§a.");
        plugin.giveAppropriateLobbyItems(player);
    }

    public void leaveQueue(Player player) {
        UUID id = player.getUniqueId();
        queues.values().forEach(q -> q.remove(id));
        queuedSince.remove(id);
        queuedKey.remove(id);
        plugin.giveAppropriateLobbyItems(player);
    }

    public boolean isInQueue(Player player) {
        return queuedSince.containsKey(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        leaveQueue(e.getPlayer());
    }

    private void sendQueuedActionBars() {
        for (Map.Entry<UUID, Long> entry : new HashMap<>(queuedSince).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                clearPlayerQueueState(entry.getKey());
                continue;
            }
            long elapsed = (System.currentTimeMillis() - entry.getValue()) / 1000L;
            String kitName = queuedKey.get(player.getUniqueId());
            int eloRange = getEloRange((int) elapsed);

            player.sendActionBar("§e" + elapsed + "s §7| §6Range: ±" + eloRange);
        }
    }

    private void sendQueuedMessages() {
        for (Map.Entry<UUID, Long> entry : new HashMap<>(queuedSince).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline())
                continue;

            long elapsed = (System.currentTimeMillis() - entry.getValue()) / 1000L;
            String kitName = queuedKey.get(player.getUniqueId());
            int eloRange = getEloRange((int) elapsed);

            ExtendedPlayer ep = plugin.getExtendedPlayer(player);
            org.albi.core.modules.Duels.ranked.RankedStats rs = ep.getMainPlayer().getKitRankedStats(kitName);

            player.sendMessage(" \n§6§lSEARCHING...\n§7Mode: §f" + kitName + "\n§7Elo: §f" + rs.getElo() + " §8(§7"
                    + rs.getTierDisplay() + "§8)\n§7Range: §e±" + eloRange + "\n ");
        }
    }

    private int getEloRange(int seconds) {
        return 50 + (seconds / 3) * 25;
    }

    private void matchQueues() {
        for (Map.Entry<String, Deque<UUID>> entry : queues.entrySet()) {
            String kitName = entry.getKey();
            Deque<UUID> waiting = entry.getValue();
            if (waiting.size() < 2)
                continue;

            List<UUID> players = new ArrayList<>(waiting);
            for (int i = 0; i < players.size(); i++) {
                Player p1 = Bukkit.getPlayer(players.get(i));
                if (p1 == null)
                    continue;

                long wait1 = (System.currentTimeMillis()
                        - queuedSince.getOrDefault(p1.getUniqueId(), System.currentTimeMillis())) / 1000L;
                int elo1 = plugin.getExtendedPlayer(p1).getMainPlayer().getKitRankedStats(kitName).getElo();
                int range1 = getEloRange((int) wait1);

                for (int j = i + 1; j < players.size(); j++) {
                    Player p2 = Bukkit.getPlayer(players.get(j));
                    if (p2 == null)
                        continue;

                    long wait2 = (System.currentTimeMillis()
                            - queuedSince.getOrDefault(p2.getUniqueId(), System.currentTimeMillis())) / 1000L;
                    int elo2 = plugin.getExtendedPlayer(p2).getMainPlayer().getKitRankedStats(kitName).getElo();
                    int range2 = getEloRange((int) wait2);

                    int diff = Math.abs(elo1 - elo2);
                    if (diff <= range1 || diff <= range2) {
                        // Match found!
                        waiting.remove(p1.getUniqueId());
                        waiting.remove(p2.getUniqueId());
                        startRankedMatch(p1, p2, kitName);
                        break;
                    }
                }
            }
        }
    }

    private void startRankedMatch(Player p1, Player p2, String kitName) {
        clearPlayerQueueState(p1.getUniqueId());
        clearPlayerQueueState(p2.getUniqueId());

        for (Player p : List.of(p1, p2)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 4, false, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 4, false, false, false));
            p.sendTitle("§6§lRANKED MATCH", "§7Opponent found! Teleporting...", 10, 60, 10);
            p.playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p1.isOnline() || !p2.isOnline())
                    return;

                if (org.albi.core.modules.Tournaments.TournamentManager.getInstance() != null
                        && org.albi.core.modules.Tournaments.TournamentManager.getInstance().isInTournament(p1)) {
                    p1.sendMessage("§cYou cannot queue for ranked duels while you are in a tournament.");
                    return;
                }
                if (org.albi.core.modules.Tournaments.TournamentManager.getInstance() != null
                        && org.albi.core.modules.Tournaments.TournamentManager.getInstance().isInTournament(p2)) {
                    p2.sendMessage("§cYou cannot queue for ranked duels while you are in a tournament.");
                    return;
                }

                Kit kit = DuelManager.stor.getKitByName(kitName);
                Arena arena = selectRandomRankedArena(kitName);

                DuelInstance duel = new DuelInstance(plugin);
                duel.setRanked(true);
                duel.prepare(new Player[] { p1 }, new Player[] { p2 }, arena, kit, kit, 1);
                duel.initialize(10, false);
            }
        }.runTaskLater(plugin, 60L);
    }

    private Arena selectRandomRankedArena(String kitName) {
        List<String> allowedArenas = DuelManager.stor.getRankedArenas().get(kitName);
        if (allowedArenas == null || allowedArenas.isEmpty()) {
            return DuelManager.stor.arenas.get(ThreadLocalRandom.current().nextInt(DuelManager.stor.arenas.size()));
        }
        String arenaName = allowedArenas.get(ThreadLocalRandom.current().nextInt(allowedArenas.size()));
        return DuelManager.stor.getArenaByName(arenaName);
    }

    private void clearPlayerQueueState(UUID id) {
        queuedSince.remove(id);
        queuedKey.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (p != null)
            plugin.giveAppropriateLobbyItems(p);
    }

    public int getQueued(Kit k) {
        Deque<UUID> q = queues.get(k.getName());
        return q == null ? 0 : q.size();
    }

    public int getTotalQueued() {
        return queues.values().stream().mapToInt(Deque::size).sum();
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
}
