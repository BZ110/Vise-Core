package org.albi.core.modules.LavaRising.managers;

import org.albi.core.Core;
import org.albi.core.modules.LavaRising.helpers.LavaRisingGameInstance;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

public class LavaRisingListener implements Listener {

    private LavaRisingGameInstance match;   // injected when a game starts
    /* ───────── spectator watchdog ───────── */
    private org.bukkit.scheduler.BukkitTask specTask;
    private final int spectateY = 100; // same height you use in LavaLobbyWatcher


    public LavaRisingListener(Core plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /* ----------------------------------------------------------------
       Hook the active match instance (call once per game start)
       ---------------------------------------------------------------- */
    /* ----------------------------------------------------------------
   Hook / un‑hook the active match instance
   ---------------------------------------------------------------- */
    public void hook(LavaRisingGameInstance inst) {
        this.match = inst;

        // cancel previous task, if any
        if (specTask != null) {
            specTask.cancel();
            specTask = null;
        }

        // start watchdog when a new match begins
        if (inst != null) {
            specTask = new org.bukkit.scheduler.BukkitRunnable() {
                @Override public void run() {
                    if (match == null || !match.isActive()) { cancel(); return; }

                    for (Player p : match.getWorld().getPlayers()) {
                        // only check spectators
                        if (p.getGameMode() != GameMode.SPECTATOR) continue;

                        // outside shifted bounds?
                        Location l = p.getLocation();
                        if (l.getBlockX() < match.getMinX() || l.getBlockX() > match.getMaxX()
                                || l.getBlockZ() < match.getMinZ() || l.getBlockZ() > match.getMaxZ()) {

                            Location safe = match.getArena().getSpawn()
                                    .convertToLocation()
                                    .clone()
                                    .add(match.getShiftX(), spectateY, match.getShiftZ());

                            p.teleportAsync(safe);
                        }
                    }
                }
            }.runTaskTimer(match.getPlugin(), 1L, 1L); // every tick
        }
    }


    /* ----------------------------------------------------------------
       1. Lethal‑damage interception → spectator with drops
       ---------------------------------------------------------------- */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!inMatchWorld(p))                     return;

        /* Pre‑PvP protection (melee & projectile) */
        if (e instanceof EntityDamageByEntityEvent dmg) {
            boolean isPlayerHit =
                    dmg.getDamager() instanceof Player ||
                            (dmg.getDamager() instanceof Projectile proj &&
                                    proj.getShooter() instanceof Player);

            if (isPlayerHit && match.getSecondsElapsed() < match.getTimePvp()) {
                e.setCancelled(true);
                return;
            }
        }

        /* Convert lethal damage into spectator mode */
        double finalDmg = e.getFinalDamage();
        if (finalDmg >= p.getHealth()) {
            e.setCancelled(true);

            // drop all inventory & armour
            dropInventory(p);

            // spectator mode
            p.getInventory().clear();
            p.setGameMode(GameMode.SPECTATOR);
            // inside onLethalDamage, right after p.setGameMode(SPECTATOR);
            match.checkForWin();
            p.sendMessage("§cYou are eliminated! Spectator mode enabled.");
            match.broadcast("§c" + p.getName() + " has been ELIMINATED!");
        }
    }

    /* ----------------------------------------------------------------
       2. Block edits outside arena bounds are cancelled
       ---------------------------------------------------------------- */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!inMatchWorld(e.getPlayer())) return;
        if (e.getBlock().getType().name().endsWith("BED")) e.setCancelled(true);
        if (!insideShiftedBounds(e.getBlock().getLocation())) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!inMatchWorld(e.getPlayer())) return;
        if (e.getBlock().getType() == Material.ENCHANTING_TABLE
                || e.getBlock().getType() == Material.BOOKSHELF) {
            e.getPlayer().sendMessage("That can't be broken.");
            e.setCancelled(true);
        }
        if (!insideShiftedBounds(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExp(PlayerExpChangeEvent e) {
        if (e.getPlayer().getWorld().getName().equals("lavaevent")) {
            e.setAmount(e.getAmount() * 30);
        }
    }

    @EventHandler
    public void onLeafBreak(BlockBreakEvent e) {
        if (!e.getBlock().getWorld().getName().equals("lavaevent")) return;
        Material type = e.getBlock().getType();
        if (type.name().endsWith("LEAVES") && Math.random() < 0.10) {
            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(),
                    new ItemStack(Material.APPLE));
        }
    }




    /* ----------------------------------------------------------------
       3. Quit / kick / world change → leave match safely
       ---------------------------------------------------------------- */
    @EventHandler public void onQuit(PlayerQuitEvent  e){ cleanup(e.getPlayer()); }
    @EventHandler public void onKick(PlayerKickEvent  e){ cleanup(e.getPlayer()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent e){
        cleanup(e.getPlayer());
    }

    /* ───────────────────── helper methods ───────────────────── */

    private boolean inMatchWorld(Player p) {
        return match != null && match.isActive() && p.getWorld().equals(match.getWorld());
    }

    private boolean insideShiftedBounds(Location loc) {
        return loc.getBlockX() >= match.getMinX() && loc.getBlockX() <= match.getMaxX() &&
                loc.getBlockZ() >= match.getMinZ() && loc.getBlockZ() <= match.getMaxZ();
    }

    private void cleanup(Player p) {
        if (match != null)
            match.checkForWin();

        if (!inMatchWorld(p)) return;
        p.setWorldBorder(null);
        p.setGameMode(GameMode.SURVIVAL);
        p.getInventory().clear();
    }

    private void dropInventory(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR)
                p.getWorld().dropItemNaturally(p.getLocation(), item);
        }
        for (ItemStack item : p.getInventory().getArmorContents()) {
            if (item != null && item.getType() != Material.AIR)
                p.getWorld().dropItemNaturally(p.getLocation(), item);
        }
        ItemStack off = p.getInventory().getItemInOffHand();
        if (off != null && off.getType() != Material.AIR)
            p.getWorld().dropItemNaturally(p.getLocation(), off);
    }

    public LavaRisingGameInstance getActiveInstance() {
        return match;
    }
}
