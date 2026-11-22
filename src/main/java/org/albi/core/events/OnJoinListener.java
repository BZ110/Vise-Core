package org.albi.core.events;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.data.MainPlayer;
import org.albi.core.serializable.SerializableLocation;
import org.albi.core.serializable.SerializableSpawnInventory;
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
import org.bukkit.util.Vector;

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

        plugin.resetPlayer(player);
        plugin.teleportToLobby(player);

        /* build (or rebuild) their ExtendedPlayer */
        plugin.getCache().removeExtendedPlayer(player);
        ExtendedPlayer ep = plugin.getExtendedPlayer(player);

        /* Fetch Tier data in background to warm cache */
        plugin.getTierManager().fetchAll(player.getUniqueId(), true);

        /* ⬇ NEW: delay post‑login logic until data is ready */
        ep.whenReady().thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§aYour profile is now ready.");

                    MainPlayer mp = ep.getMainPlayer();

                    // --- Elytra logic (unchanged) ---
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
    }


}
