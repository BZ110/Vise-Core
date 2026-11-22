package org.albi.core.events;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.data.MainPlayer;
import org.albi.core.modules.Duels.DuelManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;

public class OnJoinListener implements Listener {

    private final Core plugin;

    public OnJoinListener(Core plugin) {
        this.plugin = plugin;
    }
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        event.setJoinMessage(null);

        plugin.getCache().removeExtendedPlayer(player);
        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        plugin.resetPlayer(player);
        var lobbyArrival = plugin.teleportToLobbyAsync(player);

        /* Fetch Tier data in background to warm cache */
        plugin.getTierManager().fetchAll(player.getUniqueId(), true);

        ep.whenReady().thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player) return;
                    player.sendMessage("§aYour profile is now ready.");

                    MainPlayer mp = ep.getMainPlayer();

                    if (player.hasPermission("core.elytra") && mp.elytra()) {
                        ItemStack elytra = new ItemStack(Material.ELYTRA);
                        ItemMeta elytraMeta = elytra.getItemMeta();
                        if (player.hasPermission("core.elytra.enchanted")) {
                            elytraMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
                            elytraMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                        }
                        elytraMeta.setUnbreakable(true);
                        elytraMeta.setLore(Collections.singletonList("§r§7" + player.getName() + "'s Elytra"));
                        elytra.setItemMeta(elytraMeta);
                        player.getInventory().setChestplate(elytra);
                    }
                })
        );
        lobbyArrival.thenCombine(ep.whenReady(), (arrived, ignored) -> arrived).thenAccept(arrived -> {
            if (!arrived || !plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                var matchmaking = DuelManager.getMatchmaking();
                if (matchmaking != null) matchmaking.playerJoined(player);
                var spectators = DuelManager.getSpectators();
                if (spectators != null) spectators.playerJoined(player);
            });
        });
    }


}
