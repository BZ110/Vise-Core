package org.albi.core.modules.FFA.managers;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.data.MainPlayer;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Kit;
import org.albi.core.modules.FFA.FFAManager;
import org.albi.core.modules.FFA.helpers.FFAKitSetup;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class EventManager implements Listener {

    private final Core plugin;
    private final List<String> ffaWorlds;

    public EventManager(Core plugin, FFAManager mgr) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.ffaWorlds = plugin.getConfig().getStringList("ffa_worlds");
    }

    private FFAKitSetup getSetupForWorld(World w) {
        for (FFAKitSetup s : FFAManager.stor.allSetups()) {
            if (w.getName().equalsIgnoreCase(s.getWorldName())) return s;
        }
        return null;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        FFAKitSetup s = getSetupForWorld(e.getBlockPlaced().getWorld());
        if (s == null) return;
        if (!s.isAllowBuild()) e.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        FFAKitSetup s = getSetupForWorld(e.getBlock().getWorld());
        if (s == null) return;
        if (!s.isAllowBuild()) e.setCancelled(true);
    }

    /** Optional: update simple gamerules when world loads or players join. */
    public static void applyWorldRules(World w, FFAKitSetup s) {
        if (w == null || s == null) return;
        try {
            w.setGameRule(GameRule.NATURAL_REGENERATION, s.isNaturalRegen());
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onFFAKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        String worldName = victim.getWorld().getName();

        if (!ffaWorlds.contains(worldName)) return;

        // Play kill effect
        org.albi.core.managers.cosmetics.DeathEffectManager.playKillEffect(killer, victim.getLocation(), victim.getWorld().getPlayers(), plugin);

        // Find kit configured for this FFA world
        FFAKitSetup setup = getSetupForWorld(victim.getWorld());
        String kitName = (setup != null) ? setup.getKitName() : null;

        // Stats
        ExtendedPlayer vP = plugin.getExtendedPlayer(victim);
        MainPlayer mp = vP.getMainPlayer();
        if (kitName != null) mp.addFFADeath(kitName);

        // Rekit killer once, safely
        if (killer != null) {
            ExtendedPlayer kP = plugin.getExtendedPlayer(killer);
            String lastKitName = (kP != null) ? kP.getLastFFAKitName() : null;

            if (lastKitName != null) {
                Kit k = DuelManager.stor.kits.stream()
                        .filter(kk -> kk.getName().equalsIgnoreCase(lastKitName))
                        .findFirst().orElse(null);

                if (k != null) {
                    giveKitAndApplySavedEdit(killer, k);
                }
            }
        }

        // Broadcast in-world
        String ffaMsg;
        if (killer != null)
            ffaMsg = ChatColor.GREEN + victim.getName() + ChatColor.GRAY + " has been killed by " + ChatColor.RED + killer.getName();
        else
            ffaMsg = ChatColor.GREEN + victim.getName() + ChatColor.GRAY + " has died";

        for (Player p : victim.getWorld().getPlayers()) {
            p.sendMessage(ffaMsg);
        }

        // Also send to lobby
        String lobbyMsg = ChatColor.GRAY + "[FFA] " + ffaMsg;
        World lobbyWorld = Bukkit.getWorld(plugin.getConfig().getString("lobby_world", "lobbytest"));
        if (lobbyWorld != null) {
            for (Player p : lobbyWorld.getPlayers()) {
                p.sendMessage(lobbyMsg);
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();

        if (ffaWorlds.contains(worldName)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.teleportToLobby(player);
                ItemStack feather = new ItemStack(Material.FEATHER);
                ItemMeta meta = feather.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Teleport Back");
                    feather.setItemMeta(meta);
                }
                player.getInventory().setItem(1, feather); // visual slot 2
            });
        }
    }

    @EventHandler
    public void onFeatherClick(PlayerInteractEvent event) {
        Player p = event.getPlayer();

        String lobbyWorldName = plugin.getConfig().getString("lobby_world", "lobbytest");
        if (!p.getWorld().getName().equalsIgnoreCase(lobbyWorldName)) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.FEATHER) return;
        if (!item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (!ChatColor.stripColor(meta.getDisplayName()).equalsIgnoreCase("Teleport Back")) return;

        event.setCancelled(true);

        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        String kitName = ep.getLastFFAKitName();
        Location loc = ep.getLastFFADearthLocation(); // last FFA location

        if (loc == null || kitName == null) {
            Messages.send(p, "messages.event-manager.no-ffa-session-to-return-to", ChatColor.RED);
            return;
        }

        p.teleportAsync(loc);
        Kit k = DuelManager.stor.kits.stream()
                .filter(kk -> kk.getName().equalsIgnoreCase(kitName))
                .findFirst().orElse(null);

        if (k != null) {
            giveKitAndApplySavedEdit(p, k);
            if (!k.isBuild()) p.setGameMode(GameMode.ADVENTURE);
        }
    }

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!ffaWorlds.contains(player.getWorld().getName())) return;

        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        String kitName = ep.getLastFFAKitName();
        if (kitName == null) return;

        Kit k = DuelManager.stor.kits.stream()
                .filter(kk -> kk.getName().equalsIgnoreCase(kitName))
                .findFirst().orElse(null);
        if (k != null && !k.isLoseHunger()) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onHealthRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!ffaWorlds.contains(player.getWorld().getName())) return;

        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        String kitName = ep.getLastFFAKitName();
        if (kitName == null) return;

        Kit k = DuelManager.stor.kits.stream()
                .filter(kk -> kk.getName().equalsIgnoreCase(kitName))
                .findFirst().orElse(null);
        if (k != null && !k.isNaturalRegen()
                && event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) {
            event.setCancelled(true);
        }
    }

    /** Reset player, give kit, then apply saved KitInventoryEdits only if non-empty. */
    private void giveKitAndApplySavedEdit(Player p, Kit k) {
        plugin.resetPlayer(p, false);
        k.giveKit(p);

        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep == null || !ep.isReady()) return;

        MainPlayer mp = ep.getMainPlayer();
        var edit = mp.getKitEdit(k.getName()); // null if never saved
        if (edit != null && !edit.isEmpty()) {
            edit.applyToIncludingOffhand(p);
            p.updateInventory();
        }
    }


}
