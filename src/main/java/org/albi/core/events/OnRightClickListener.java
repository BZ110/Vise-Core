package org.albi.core.events;

import org.albi.core.Core;
import org.albi.core.serializable.SerializableSpawnInventory;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OnRightClickListener implements Listener {

    private final Core plugin;
    private final Map<UUID, Long> duelClickCooldown = new ConcurrentHashMap<>();

    public OnRightClickListener(Core plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)
            return;

        Player player = event.getPlayer();
        World lobbyWorld = player.getServer().getWorld(plugin.getConfig().getString("lobby_world"));
        if (lobbyWorld == null || player.getWorld() != lobbyWorld)
            return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        Material handType = hand == null ? Material.AIR : hand.getType();
        if (handType == Material.FISHING_ROD)
            return;
        if (handType == Material.WIND_CHARGE)
            return;
        if (player.getInventory().getItemInOffHand().getType() == Material.FISHING_ROD)
            return;
        if (player.getInventory().getItemInOffHand().getType() == Material.WIND_CHARGE)
            return;

        if (!player.hasPermission(plugin.getConfig().getString("permissions.bypass"))
                && !plugin.getExtendedPlayer(player).isBypassRegion()) {
            event.setCancelled(true);
        }

        String displayName = null;
        if (hand != null && hand.hasItemMeta() && hand.getItemMeta().hasDisplayName()) {
            displayName = ChatColor.stripColor(hand.getItemMeta().getDisplayName());
        }

        if (displayName != null) {
            switch (displayName.toLowerCase()) {
                case "unranked queue" -> {
                    event.setCancelled(true);
                    player.performCommand("queue");
                    return;
                }
                case "create party" -> {
                    event.setCancelled(true);
                    if (plugin.getPartyManager() != null && plugin.getPartyManager().isInParty(player))
                        player.performCommand("party settings");
                    else
                        player.performCommand("party create");
                    return;
                }
                case "ranked queue" -> {
                    event.setCancelled(true);
                    player.performCommand("rankedqueue");
                    return;
                }
                case "leave queue" -> {
                    event.setCancelled(true);
                    player.performCommand("queue leave");
                    player.performCommand("rankedqueue leave");
                    return;
                }
                case "teleporter" -> {
                    event.setCancelled(true);
                    player.performCommand("goto");
                    return;
                }
                case "instance selector" -> {
                    event.setCancelled(true);
                    player.performCommand("travel");
                    return;
                }
                case "kit editor" -> {
                    event.setCancelled(true);
                    player.performCommand("kiteditor");
                    return;
                }
                case "settings" -> {
                    event.setCancelled(true);
                    player.performCommand("settings");
                    return;
                }
                case "party settings" -> {
                    event.setCancelled(true);
                    player.performCommand("party settings");
                    return;
                }
                case "statistics" -> {
                    event.setCancelled(true);
                    player.performCommand("stats");
                    return;
                }
                case "play" -> {
                    event.setCancelled(true);
                    player.performCommand("party play");
                    return;
                }
                case "members" -> {
                    event.setCancelled(true);
                    player.performCommand("party list");
                    return;
                }
                case "invite help" -> {
                    event.setCancelled(true);
                    player.performCommand("party help");
                    return;
                }
                case "disband party" -> {
                    event.setCancelled(true);
                    player.performCommand("party disband");
                    return;
                }
                case "leave party" -> {
                    event.setCancelled(true);
                    player.performCommand("party leave");
                    return;
                }
                case "leave tournament" -> {
                    event.setCancelled(true);
                    player.performCommand("tournament leave");
                    return;
                }
                case "spectate match" -> {
                    event.setCancelled(true);
                    player.performCommand("next");
                    return;
                }
            }
        }

        SerializableSpawnInventory spawnInv = (SerializableSpawnInventory) plugin.getData().get("spawnInventory");
        if (spawnInv == null)
            return;
        String cmd = spawnInv.getCommand(hand);
        if (cmd == null || cmd.isEmpty())
            return;
        event.setCancelled(true);
        player.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRightClickPlayer(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        if (!(event.getRightClicked() instanceof Player target))
            return;

        Player player = event.getPlayer();
        if (player.equals(target) || !target.isOnline() || !target.isValid())
            return;

        World lobbyWorld = player.getServer().getWorld(plugin.getConfig().getString("lobby_world"));
        if (lobbyWorld == null || player.getWorld() != lobbyWorld || target.getWorld() != lobbyWorld)
            return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        Material handType = hand == null ? Material.AIR : hand.getType();
        if (handType == Material.FISHING_ROD)
            return;
        if (handType == Material.WIND_CHARGE)
            return;
        if (player.getInventory().getItemInOffHand().getType() == Material.FISHING_ROD)
            return;
        if (player.getInventory().getItemInOffHand().getType() == Material.WIND_CHARGE)
            return;

        long now = System.currentTimeMillis();
        long last = duelClickCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 250L) {
            event.setCancelled(true);
            return;
        }
        duelClickCooldown.put(player.getUniqueId(), now);

        event.setCancelled(true);
        player.performCommand("duel " + target.getName());
    }
}
