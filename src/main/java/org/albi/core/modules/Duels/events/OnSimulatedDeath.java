package org.albi.core.modules.Duels.events;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.Duels.DuelInstance;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

public final class OnSimulatedDeath implements Listener {

    private final Core plugin;
    public OnSimulatedDeath(Core plugin) { this.plugin = plugin; }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {

        if (!(e.getEntity() instanceof Player victim)) return;

        ItemStack main = victim.getInventory().getItemInMainHand();
        ItemStack off = victim.getInventory().getItemInOffHand();

        if ((main != null && main.getType() == Material.TOTEM_OF_UNDYING) ||
                (off != null && off.getType() == Material.TOTEM_OF_UNDYING)) {
            return;
        }

        ExtendedPlayer vEP = plugin.getExtendedPlayer(victim);
        if (vEP == null) return;
        DuelInstance di = vEP.getPlayerDuelInstance() == null ? null
                : vEP.getPlayerDuelInstance().getDuelInstance();
        if (di == null) return;                           // not in a duel

        double finalHealth = victim.getHealth() - e.getFinalDamage();
        if (finalHealth > 0) return;                      // not lethal

        e.setCancelled(true);                             // stop vanilla death

        /* --- pick killer (may be null / self) --- */
        Player killer = null;
        if (e instanceof EntityDamageByEntityEvent byEntity) {
            if (byEntity.getDamager() instanceof Player k) killer = k;
        }
        if (killer == null) killer = vEP.getLastHit() != null ? vEP.getLastHit() : victim;

        // Play the killer's cosmetic kill effect at the victim's location
        org.albi.core.managers.cosmetics.DeathEffectManager.playKillEffect(killer, victim.getLocation(), victim.getWorld().getPlayers(), plugin);

        /* --- drop inventory & clear --- */
        for (ItemStack it : victim.getInventory().getContents()) {
            if (it != null && it.getType() != Material.AIR) {
                victim.getWorld().dropItemNaturally(victim.getLocation(), it);
            }
        }
        for (ItemStack it : victim.getInventory().getArmorContents()) {
            if (it != null && it.getType() != Material.AIR) {
                victim.getWorld().dropItemNaturally(victim.getLocation(), it);
            }
        }
        victim.getInventory().clear();
        plugin.resetPlayer(victim, false);                // keep food/health = 0
        victim.setGameMode(GameMode.SPECTATOR);

        di.death(victim, killer);                         // notify duel

        // KILL THUNDER (Ranked Only)
        if (di.isRanked()) {
            victim.getWorld().playSound(victim.getLocation(), org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.2f);
        }
    }
}
