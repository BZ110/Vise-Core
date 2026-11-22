package org.albi.core.modules.Duels.queue;

import org.albi.core.gui.Messages;

import org.albi.core.gui.GuiService;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.modules.Duels.queue.global.GlobalMatchmaking;
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
            SGMenu menu = plugin.getMenus().create("queues.ranked", 3);

            // Informational Sign
            ItemStack sign = new ItemStack(Material.OAK_SIGN);
            ItemMeta signMeta = sign.getItemMeta();
            if (signMeta != null) {
                signMeta.setDisplayName(GuiService.text("ranked-queue-module.ranking-information"));
                signMeta.setLore(Arrays.asList(
                        GuiService.text("ranked-queue-module.our-system-uses-a-dynamic-elo-algorithm"),
                        GuiService.text("ranked-queue-module.stakes-scale-with-opponent-difficulty"),
                        GuiService.text("ranked-queue-module.beat-a-pro-high-reward"),
                        GuiService.text("ranked-queue-module.lose-to-a-noob-heavy-penalty"),
                        GuiService.text("ranked-queue-module.matches-are-best-of-1")));
                sign.setItemMeta(signMeta);
            }
            GuiService.control(menu, "sign", 4, new SGButton(sign));

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
                    lore.add(GuiService.text("ranked-queue-module.your-elo", rs.getElo()));
                    lore.add(GuiService.text("ranked-queue-module.status", rs.getTierDisplay()));
                    lore.add(GuiService.text("ranked-queue-module.queued", getQueued(k)));
                    lore.add(" ");
                    lore.add(GuiService.text("ranked-queue-module.click-to-join-ranked-queue"));
                    im.setLore(lore);
                    icon.setItemMeta(im);
                }

                GuiService.entry(menu, k.getName(), local, new SGButton(icon).withListener(e -> {
                    joinQueue(viewer, k);
                    viewer.closeInventory();
                }));
            }

            GuiService.open(viewer, menu);
        }
    }

    private void joinQueue(Player player, Kit kit) {
        // Safety checks
        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (ep == null || !ep.isReady()) {
            Messages.send(player, "messages.ranked-queue-module.wait-for-your-profile-to-load-before-joining");
            return;
        }
        if (ep.getPlayerDuelInstance() != null) {
            Messages.send(player, "messages.ranked-queue-module.you-cannot-queue-while-in-a-duel");
            return;
        }
        if (org.albi.core.modules.Tournaments.TournamentManager.getInstance() != null
                && org.albi.core.modules.Tournaments.TournamentManager.getInstance().isInTournament(player)) {
            Messages.send(player, "messages.ranked-queue-module.you-cannot-queue-for-ranked-duels-while-you");
            return;
        }
        if (plugin.getPartyManager() != null && plugin.getPartyManager().isInParty(player)) {
            Messages.send(player, "messages.ranked-queue-module.you-cannot-join-solo-ranked-while-in-a");
            return;
        }
        if (ep.isSpectating()) {
            Messages.send(player, "messages.ranked-queue-module.you-cannot-queue-while-spectating");
            return;
        }

        var matchmaking = DuelManager.getMatchmaking();
        if (matchmaking != null) {
            if (!matchmaking.join(player, kit.getName(), true)) return;
        } else {
            if (!GlobalMatchmaking.eligible(player, true)) return;
            leaveQueue(player);
        }
        queues.computeIfAbsent(kit.getName(), k -> new ConcurrentLinkedDeque<>()).add(player.getUniqueId());
        queuedSince.put(player.getUniqueId(), System.currentTimeMillis());
        queuedKey.put(player.getUniqueId(), kit.getName());

        Messages.send(player, "messages.ranked-queue-module.you-have-joined-the-ranked-queue-for", KitSafety.queueDisplayName(kit));
        plugin.giveAppropriateLobbyItems(player);
    }

    public void leaveQueue(Player player) {
        var matchmaking = DuelManager.getMatchmaking();
        if (matchmaking != null) matchmaking.leave(player, true);
        else removeQueueState(player.getUniqueId());
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
                removeQueueState(entry.getKey());
                continue;
            }
            long elapsed = (System.currentTimeMillis() - entry.getValue()) / 1000L;
            String kitName = queuedKey.get(player.getUniqueId());
            int eloRange = getEloRange((int) elapsed);

            player.sendActionBar(Messages.text(player, "messages.ranked-queue-module.s-range", elapsed, eloRange));
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

            Messages.send(player, "messages.ranked-queue-module.searching-mode-elo-range", kitName, rs.getElo(), rs.getTierDisplay(), eloRange);
        }
    }

    private int getEloRange(int seconds) {
        return GlobalMatchmaking.eloRange(seconds);
    }

    private void matchQueues() {
        if (DuelManager.getMatchmaking() != null) return;
        for (Map.Entry<String, Deque<UUID>> entry : queues.entrySet()) {
            String kitName = entry.getKey();
            Deque<UUID> waiting = entry.getValue();
            if (waiting.size() < 2)
                continue;

            List<UUID> players = new ArrayList<>(waiting);
            for (int i = 0; i < players.size(); i++) {
                Player p1 = Bukkit.getPlayer(players.get(i));
                if (!kitName.equals(queuedKey.get(players.get(i))) || !GlobalMatchmaking.eligible(p1, true))
                    continue;

                long wait1 = (System.currentTimeMillis()
                        - queuedSince.getOrDefault(p1.getUniqueId(), System.currentTimeMillis())) / 1000L;
                int elo1 = plugin.getExtendedPlayer(p1).getMainPlayer().getKitRankedStats(kitName).getElo();
                int range1 = getEloRange((int) wait1);

                for (int j = i + 1; j < players.size(); j++) {
                    Player p2 = Bukkit.getPlayer(players.get(j));
                    if (!kitName.equals(queuedKey.get(players.get(j))) || !GlobalMatchmaking.eligible(p2, true))
                        continue;

                    long wait2 = (System.currentTimeMillis()
                            - queuedSince.getOrDefault(p2.getUniqueId(), System.currentTimeMillis())) / 1000L;
                    int elo2 = plugin.getExtendedPlayer(p2).getMainPlayer().getKitRankedStats(kitName).getElo();
                    int range2 = getEloRange((int) wait2);

                    int diff = Math.abs(elo1 - elo2);
                    if (diff <= range1 || diff <= range2) {
                        // Match found!
                        startRankedMatch(p1, p2, kitName);
                        break;
                    }
                }
            }
        }
    }

    private void startRankedMatch(Player p1, Player p2, String kitName) {
        removeQueueState(p1.getUniqueId());
        removeQueueState(p2.getUniqueId());

        for (Player p : List.of(p1, p2)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 4, false, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 4, false, false, false));
            p.sendTitle(Messages.text(p, "messages.ranked-queue-module.ranked-match"), Messages.text(p, "messages.ranked-queue-module.opponent-found-teleporting"), 10, 60, 10);
            p.playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!GlobalMatchmaking.eligible(p1, true) || !GlobalMatchmaking.eligible(p2, true))
                    return;

                Kit kit = DuelManager.stor.getKitByName(kitName);
                if (kit == null) return;
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
        List<Arena> arenas = DuelManager.stor.arenas.stream()
                .filter(arena -> arena != null && !arena.deactivated)
                .filter(arena -> allowedArenas == null || allowedArenas.isEmpty()
                        || allowedArenas.stream().anyMatch(name -> name.equalsIgnoreCase(arena.getName())))
                .toList();
        return arenas.isEmpty() ? null : arenas.get(ThreadLocalRandom.current().nextInt(arenas.size()));
    }

    public void removeQueueState(UUID id) {
        String key = queuedKey.remove(id);
        if (key == null) return;
        if (queues.containsKey(key)) queues.get(key).remove(id);
        queuedSince.remove(id);
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
