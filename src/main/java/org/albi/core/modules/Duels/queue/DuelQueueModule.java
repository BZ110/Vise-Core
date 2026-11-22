package org.albi.core.modules.Duels.queue;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.data.MainPlayer;
import org.albi.core.modules.CustomKit.helpers.CustomKit;
import org.albi.core.modules.Duels.DuelInstance;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.commands.BaseCommand;
import org.albi.core.modules.Duels.helpers.Arena;
import org.albi.core.modules.Duels.helpers.DuelKitPageCategory;
import org.albi.core.modules.Duels.helpers.Kit;
import org.albi.core.serializable.SerializableItem;
import org.albi.core.util.KitSafety;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
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

public class DuelQueueModule implements Listener {

    private static final String PK_QUEUE = "__PER_CUSTOM__";
    private static final String QUICK_QUEUE = "__QUICK__";

    private final Core plugin;
    private final Map<String, Deque<UUID>> queues = new ConcurrentHashMap<>();
    private final Map<UUID, Kit> playerChoice = new ConcurrentHashMap<>();
    private final Map<UUID, Long> queuedSince = new ConcurrentHashMap<>();
    private final Map<UUID, String> queuedKey = new ConcurrentHashMap<>();

    public DuelQueueModule(Core plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        new BukkitRunnable() {
            @Override public void run() { matchQueues(); }
        }.runTaskTimer(plugin, 2L, 2L);

        new BukkitRunnable() {
            @Override public void run() { sendQueuedActionBars(); }
        }.runTaskTimer(plugin, 20L, 20L);

        Objects.requireNonNull(plugin.getCommand("queue")).setExecutor(new QueueCommand());
        if (plugin.getCommand("quickqueue") != null) {
            plugin.getCommand("quickqueue").setExecutor(new QuickQueueCommand());
        }
    }

    private Map<Integer, DuelKitPageCategory> getPageCategories() {
        Map<Integer, DuelKitPageCategory> cats =
                (Map<Integer, DuelKitPageCategory>) plugin.getData().get("duelKitPageCategories");
        return cats == null ? Collections.emptyMap() : cats;
    }

    private class QueueCommand extends BaseCommand {
        @Override
        public boolean onCommand(CommandSender snd, Command cmd, String lbl, String[] args) {
            if (!(snd instanceof Player p)) return false;

            if (args.length == 0) {
                if (getPageCategories().isEmpty()) new QueueGUI(p, 0).open();
                else new QueueCategoryGUI(p).open();
                return true;
            }
            if (args[0].equalsIgnoreCase("leave")) {
                leaveQueue(p);
                p.sendMessage("§eYou have left the queue.");
                p.sendActionBar(plugin.getMessageAPI().toColor("&cYou left the queue."));
                return true;
            }
            p.sendMessage("§c/queue [leave]");
            return true;
        }
    }

    private class QuickQueueCommand extends BaseCommand {
        @Override
        public boolean onCommand(CommandSender snd, Command cmd, String lbl, String[] args) {
            if (!(snd instanceof Player p)) return false;
            if (args.length > 0 && args[0].equalsIgnoreCase("leave")) {
                leaveQueue(p);
                p.sendMessage("§eYou left quick queue.");
                return true;
            }
            queueQuick(p);
            return true;
        }
    }

    private class QueueCategoryGUI {
        private final Player viewer;
        QueueCategoryGUI(Player viewer) { this.viewer = viewer; }

        void open() {
            SGMenu menu = plugin.getGUI().create("§d§lQueue Categories", 3);
            for (Map.Entry<Integer, DuelKitPageCategory> entry : getPageCategories().entrySet()) {
                DuelKitPageCategory cat = entry.getValue();
                if (cat == null || cat.getDisplayItem() == null) continue;
                ItemStack icon = cat.getDisplayItem().convertToItem();
                if (icon == null || icon.getType().isAir()) continue;
                ItemMeta meta = icon.getItemMeta();
                List<String> lore = meta != null && meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(" ");
                lore.add("§dOpen page §f#" + cat.getPage());
                lore.add("§7Jump into this ruleset family.");
                if (meta != null) {
                    meta.setLore(lore);
                    icon.setItemMeta(meta);
                }
                menu.setButton(Math.max(0, Math.min(26, cat.getGuiSlot())),
                        new SGButton(icon).withListener(e -> new QueueGUI(viewer, cat.getPage()).open()));
            }
            menu.setButton(22, new SGButton(button(Material.BARRIER, "§cClose", List.of("§7Close the queue browser.")))
                    .withListener(e -> viewer.closeInventory()));
            viewer.openInventory(menu.getInventory());
        }
    }

    private class QueueGUI {
        private static final int PAGE_SIZE = 54;
        private final Player viewer;
        private final int page;
        private final int maxPage;

        QueueGUI(Player viewer, int page) {
            this.viewer = viewer;
            this.page = page;
            int maxSlot = DuelManager.stor.kits.stream().mapToInt(Kit::getSlot).max().orElse(0);
            this.maxPage = maxSlot / PAGE_SIZE;
        }

        void open() {
            SGMenu menu = plugin.getGUI().create("§8§lQueue Browser §7(" + (page + 1) + "/" + (maxPage + 1) + ")", 6);

            for (Kit k : DuelManager.stor.kits) {
                int global = k.getSlot();
                if (global / PAGE_SIZE != page) continue;
                int local = global % PAGE_SIZE;

                ItemStack icon = k.getDisplayItem().convertToItem();
                ItemMeta im = icon.getItemMeta();
                List<String> lore = im != null && im.hasLore() ? new ArrayList<>(im.getLore()) : new ArrayList<>();
                lore.add(" ");
                lore.add("§eQueued: §f" + getQueued(k));
                lore.add("§dClick to queue this kit.");
                if (im != null) {
                    im.setLore(lore);
                    icon.setItemMeta(im);
                }

                menu.setButton(local, new SGButton(icon).withListener(e -> {
                    queue(viewer, k);
                    viewer.closeInventory();
                }));
            }

            ItemStack pkItem = button(Material.BLAZE_POWDER, "§ePer Custom Kit",
                    List.of("§7Use every player's own selected custom kit.", "§8Queued: " + getQueuedPerCustom()));
            menu.setButton(52, new SGButton(pkItem).withListener(e -> {
                Kit dummy = new Kit();
                dummy.setPk(true);
                dummy.setName("per-custom");
                queue(viewer, dummy);
                viewer.closeInventory();
            }));

            ItemStack quickItem = button(Material.LIGHTNING_ROD, "§bQuick Queue",
                    List.of("§7Match into any live queue faster.", "§8Queued: " + getQueuedQuick()));
            menu.setButton(53, new SGButton(quickItem).withListener(e -> {
                queueQuick(viewer);
                viewer.closeInventory();
            }));

            if (!getPageCategories().isEmpty()) {
                menu.setButton(49, new SGButton(button(Material.ARROW, "§dCategories", List.of("§7Return to category view.")))
                        .withListener(e -> new QueueCategoryGUI(viewer).open()));
            }
            if (page > 0) {
                menu.setButton(44, new SGButton(button(Material.ARROW, "§e← Previous", List.of("§7View previous kit page.")))
                        .withListener(e -> new QueueGUI(viewer, page - 1).open()));
            }
            if (page < maxPage) {
                menu.setButton(45, new SGButton(button(Material.ARROW, "§eNext →", List.of("§7View next kit page.")))
                        .withListener(e -> new QueueGUI(viewer, page + 1).open()));
            }

            viewer.openInventory(menu.getInventory());
        }
    }

    private void queueQuick(Player player) {
        leaveQueue(player);
        queues.computeIfAbsent(QUICK_QUEUE, k -> new ConcurrentLinkedDeque<>()).add(player.getUniqueId());
        queuedSince.put(player.getUniqueId(), System.currentTimeMillis());
        queuedKey.put(player.getUniqueId(), QUICK_QUEUE);
        player.sendMessage("§bQuick queue enabled. §7You will match into any available queue.");
        plugin.giveAppropriateLobbyItems(player);
        matchQueues();
    }

    private void queue(Player player, Kit kitClicked) {
        leaveQueue(player);
        String qId = kitClicked.isPk() ? PK_QUEUE : kitClicked.getName();
        queues.computeIfAbsent(qId, k -> new ConcurrentLinkedDeque<>()).add(player.getUniqueId());
        playerChoice.put(player.getUniqueId(), kitClicked);
        queuedSince.put(player.getUniqueId(), System.currentTimeMillis());
        queuedKey.put(player.getUniqueId(), qId);

        if (org.albi.core.modules.Tournaments.TournamentManager.getInstance() != null && org.albi.core.modules.Tournaments.TournamentManager.getInstance().isInTournament(player)) {
            player.sendMessage("§cYou cannot queue for duels while you are in a tournament.");
            return;
        }

        player.sendMessage("§aQueued for §f" + KitSafety.queueDisplayName(kitClicked) + "§a. §7Use §f/queue leave §7to back out.");
        plugin.giveAppropriateLobbyItems(player);
        matchQueues();
    }

    public boolean isInQueue(Player player) {
        return queuedSince.containsKey(player.getUniqueId());
    }

    private void leaveQueue(Player p) {
        UUID id = p.getUniqueId();
        queues.values().forEach(q -> q.remove(id));
        playerChoice.remove(id);
        queuedSince.remove(id);
        queuedKey.remove(id);
        plugin.giveAppropriateLobbyItems(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { leaveQueue(e.getPlayer()); }

    private void sendQueuedActionBars() {
        for (Map.Entry<UUID, Long> entry : new HashMap<>(queuedSince).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                clearPlayerQueueState(entry.getKey());
                continue;
            }
            ExtendedPlayer ep = plugin.getExtendedPlayer(player);
            if (ep != null && ep.getPlayerDuelInstance() != null) {
                leaveQueue(player);
                continue;
            }
            long elapsed = Math.max(0L, (System.currentTimeMillis() - entry.getValue()) / 1000L);
            String queueId = queuedKey.get(player.getUniqueId());
            String display = QUICK_QUEUE.equals(queueId)
                    ? plugin.getMessageAPI().toColor("&bQUICK MODE")
                    : plugin.getMessageAPI().toColor("&f" + KitSafety.queueDisplayName(playerChoice.get(player.getUniqueId())));
            player.sendActionBar(plugin.getMessageAPI().toColor("&r&7" + timeEmoji((int) elapsed) + " Queued for &r" + display + "&r&7... (&r&f" + fmt((int) elapsed) + " &7- /queue leave to leave)"));
        }
    }

    private String fmt(int s) {
        int d = s / 86400; s %= 86400;
        int h = s / 3600; s %= 3600;
        int m = s / 60;   s %= 60;

        StringBuilder b = new StringBuilder();
        if (d > 0) b.append(d).append("d ");
        if (h > 0) b.append(h).append("h ");
        if (m > 0) b.append(m).append("m ");
        if (s > 0 || b.length() == 0) b.append(s).append("s");

        return b.toString().trim();
    }

    private String timeEmoji(int seconds) {
        String[] clocks = {"🕛", "🕐", "🕑", "🕒", "🕓", "🕔", "🕕", "🕖", "🕗", "🕘", "🕙", "🕚"};
        return clocks[Math.floorMod(seconds, clocks.length)];
    }

    private void matchQueues() {
        tryQuickMatches();
        tryStandardMatches();
    }

    private void tryQuickMatches() {
        Deque<UUID> quick = queues.computeIfAbsent(QUICK_QUEUE, k -> new ConcurrentLinkedDeque<>());
        while (true) {
            Player quickPlayer = popNextValid(quick);
            if (quickPlayer == null) return;

            Player secondQuick = popNextValid(quick);
            if (secondQuick != null) {
                Kit random = randomNonPkKit();
                if (random == null) {
                    quick.addFirst(quickPlayer.getUniqueId());
                    quick.addFirst(secondQuick.getUniqueId());
                    return;
                }
                clearPlayerQueueState(quickPlayer.getUniqueId());
                clearPlayerQueueState(secondQuick.getUniqueId());
                startPendingQueuedDuel(quickPlayer, secondQuick, random, random, false);
                continue;
            }

            MatchTarget target = popAnyNonQuickTarget();
            if (target == null) {
                quick.addFirst(quickPlayer.getUniqueId());
                return;
            }

            clearPlayerQueueState(quickPlayer.getUniqueId());
            clearPlayerQueueState(target.player().getUniqueId());

            if (PK_QUEUE.equals(target.queueId())) {
                Kit kitA = resolvePersonalKit(quickPlayer);
                Kit kitB = resolvePersonalKit(target.player());
                if (kitA == null || kitB == null) {
                    quickPlayer.sendMessage("§cA custom kit is missing, so the duel could not start.");
                    target.player().sendMessage("§cA custom kit is missing, so the duel could not start.");
                    continue;
                }
                startPendingQueuedDuel(quickPlayer, target.player(), kitA, kitB, true);
            } else {
                Kit chosen = target.choice();
                if (chosen == null) chosen = findKitByName(target.queueId());
                if (chosen == null) continue;
                startPendingQueuedDuel(quickPlayer, target.player(), chosen, chosen, false);
            }
        }
    }

    private void tryStandardMatches() {
        for (Map.Entry<String, Deque<UUID>> entry : queues.entrySet()) {
            if (QUICK_QUEUE.equals(entry.getKey())) continue;
            Deque<UUID> waiting = entry.getValue();
            while (true) {
                Player p1 = popNextValid(waiting);
                Player p2 = popNextValid(waiting);
                if (p1 == null || p2 == null) {
                    if (p1 != null) waiting.addFirst(p1.getUniqueId());
                    if (p2 != null) waiting.addFirst(p2.getUniqueId());
                    break;
                }

                Kit choice1 = playerChoice.get(p1.getUniqueId());
                boolean isPk = PK_QUEUE.equals(entry.getKey());
                clearPlayerQueueState(p1.getUniqueId());
                clearPlayerQueueState(p2.getUniqueId());

                if (isPk) {
                    Kit kitA = resolvePersonalKit(p1);
                    Kit kitB = resolvePersonalKit(p2);
                    if (kitA == null || kitB == null) {
                        p1.sendMessage("§cA custom kit is missing, so the duel could not start.");
                        p2.sendMessage("§cA custom kit is missing, so the duel could not start.");
                        continue;
                    }
                    startPendingQueuedDuel(p1, p2, kitA, kitB, true);
                } else if (choice1 != null) {
                    startPendingQueuedDuel(p1, p2, choice1, choice1, false);
                }
            }
        }
    }

    private MatchTarget popAnyNonQuickTarget() {
        for (Map.Entry<String, Deque<UUID>> entry : queues.entrySet()) {
            if (QUICK_QUEUE.equals(entry.getKey())) continue;
            Player player = popNextValid(entry.getValue());
            if (player == null) continue;
            return new MatchTarget(entry.getKey(), player, playerChoice.get(player.getUniqueId()));
        }
        return null;
    }

    private Player popNextValid(Deque<UUID> deque) {
        while (deque != null && !deque.isEmpty()) {
            UUID id = deque.poll();
            if (id == null) continue;
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                clearPlayerQueueState(id);
                continue;
            }
            ExtendedPlayer ep = plugin.getExtendedPlayer(player);
            if (ep != null && ep.getPlayerDuelInstance() != null) {
                clearPlayerQueueState(id);
                continue;
            }
            return player;
        }
        return null;
    }

    private void clearPlayerQueueState(UUID id) {
        playerChoice.remove(id);
        queuedSince.remove(id);
        queuedKey.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (p != null) plugin.giveAppropriateLobbyItems(p);
    }

    private void startPendingQueuedDuel(Player p1, Player p2, Kit kitA, Kit kitB, boolean isPk) {
        if (p1 == null || p2 == null || kitA == null || kitB == null) return;

        for (Player player : List.of(p1, p2)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 4, false, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 4, false, false, false));
            player.sendTitle("§a§lMatch Found", "§7Teleporting...", 0, 50, 10);
            player.sendMessage("§aMatch found against §f" + (player == p1 ? p2.getName() : p1.getName()) + "§a.");
            player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p1.isOnline() || !p2.isOnline()) return;
                ExtendedPlayer ep1 = plugin.getExtendedPlayer(p1);
                ExtendedPlayer ep2 = plugin.getExtendedPlayer(p2);
                if ((ep1 != null && ep1.getPlayerDuelInstance() != null) || (ep2 != null && ep2.getPlayerDuelInstance() != null)) return;

                Arena arena = randomArena();
                DuelInstance duel = new DuelInstance(plugin);
                duel.prepare(new Player[]{p1}, new Player[]{p2}, arena, kitA, kitB, 1);
                duel.initialize(10, isPk);
            }
        }.runTaskLater(plugin, 60L);
    }

    private int getQueued(Kit k) {
        Deque<UUID> q = queues.get(k.getName());
        return q == null ? 0 : q.size();
    }

    private int getQueuedPerCustom() {
        Deque<UUID> q = queues.get(PK_QUEUE);
        return q == null ? 0 : q.size();
    }

    private int getQueuedQuick() {
        Deque<UUID> q = queues.get(QUICK_QUEUE);
        return q == null ? 0 : q.size();
    }

    private Arena randomArena() {
        List<Arena> activeArenas = DuelManager.stor.arenas.stream()
                .filter(a -> a != null && !a.deactivated)
                .collect(java.util.stream.Collectors.toList());

        if (activeArenas.isEmpty()) return null;
        return activeArenas.get(ThreadLocalRandom.current().nextInt(activeArenas.size()));
    }

    private Kit resolvePersonalKit(Player p) {
        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep == null) return null;
        MainPlayer mp = ep.getMainPlayer();
        if (mp == null) return null;

        int slot = ep.getSelectedKit().getOrDefault(p.getUniqueId(), 1);
        CustomKit ck = mp.getKits()[slot];
        if (ck == null) return null;

        Kit k = ck.toKit();
        k.setPk(false);
        k.setName("Personal kit");
        k.setDisplayItem(new SerializableItem(new ItemStack(Material.BLAZE_POWDER)));
        return k;
    }

    private Kit randomNonPkKit() {
        List<Kit> kits = DuelManager.stor.kits;
        if (kits == null || kits.isEmpty()) return null;
        return kits.get(ThreadLocalRandom.current().nextInt(kits.size()));
    }

    private Kit findKitByName(String name) {
        if (name == null) return null;
        return DuelManager.stor.kits.stream()
                .filter(k -> name.equalsIgnoreCase(k.getName()))
                .findFirst()
                .orElse(null);
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

    private record MatchTarget(String queueId, Player player, Kit choice) {}
}
