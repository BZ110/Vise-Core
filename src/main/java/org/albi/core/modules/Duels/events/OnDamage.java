package org.albi.core.modules.Duels.events;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import org.albi.core.modules.Duels.ranked.MatchTracker;
import org.bukkit.Location;
import java.util.List;

public final class OnDamage implements Listener {

    private final Core plugin;

    public OnDamage(Core plugin) {
        this.plugin = plugin;
    }

    /* ───────────────────────────── */
    /* Global Invincibility */
    /* ───────────────────────────── */
    @EventHandler(ignoreCancelled = true)
    public void onAnyDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim))
            return;

        ExtendedPlayer vEP = plugin.getExtendedPlayer(victim);
        if (vEP == null)
            return;

        var vDI = vEP.getPlayerDuelInstance();
        if (vDI == null || vDI.getStatus() == null)
            return;

        if (vDI.getStatus().equalsIgnoreCase("Countdown")
                || vDI.getStatus().equalsIgnoreCase("Scatter")) {
            e.setCancelled(true);
        }
    }

    /* ───────────────────────────── */
    /* Player vs Player Damage */
    /* ───────────────────────────── */
    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {

        if (!(e.getEntity() instanceof Player victim)
                || !(e.getDamager() instanceof Player damager))
            return;

        ExtendedPlayer vEP = plugin.getExtendedPlayer(victim);
        ExtendedPlayer aEP = plugin.getExtendedPlayer(damager);

        if (vEP == null || aEP == null)
            return;

        vEP.setLastHit(damager);

        /* ───────────────────────────── */
        /* Lobby protection */
        /* ───────────────────────────── */
        if (plugin.getLobbySettings().protection().pvp() && plugin.isLobby(victim)) {
            e.setCancelled(true);
            return;
        }

        var vDI = vEP.getPlayerDuelInstance();
        var aDI = aEP.getPlayerDuelInstance();

        if (vDI == null || aDI == null)
            return;
        if (vDI.getStatus() == null)
            return;

        /* ───────────────────────────── */
        /* Countdown protection */
        /* ───────────────────────────── */
        if (vDI.getStatus().equalsIgnoreCase("Countdown")
                || vDI.getStatus().equalsIgnoreCase("Scatter")) {
            e.setCancelled(true);
            return;
        }

        /* ───────────────────────────── */
        /* Friendly fire */
        /* ───────────────────────────── */
        for (Player mate : vDI.getTeammates()) {
            if (mate.getUniqueId().equals(damager.getUniqueId())) {
                e.setCancelled(true);
                plugin.getMessageAPI()
                        .sendMessageLang(damager, "duel_hit_team");
                return;
            }
        }

        /* ───────────────────────────── */
        /* Damage Boost System */
        /* Metadata + Config Override */
        /* ───────────────────────────── */

        int percentBoost = 0;

        /* 1️⃣ Metadata lookup */
        Integer metaBoost = aDI.getKit()
                .getMetaData()
                .getInt("dmg_inc_%");

        if (metaBoost != null) {
            percentBoost = metaBoost;
        }

        /* 2️⃣ Config override (legacy support) */
        List<String> configBoost = plugin.getConfig()
                .getStringList("33_percent_boost");

        String kitName = aDI.getKit() != null
                ? aDI.getKit().getName()
                : "";

        if (configBoost.stream()
                .anyMatch(n -> n.equalsIgnoreCase(kitName))) {

            percentBoost = plugin.getCombatSettings().legacyBoostPercent();
        }
        percentBoost = plugin.getCombatSettings().damageBoost(kitName, percentBoost);

        /* 3️⃣ Apply multiplier */
        if (percentBoost > 0) {

            double multiplier = 1 + (percentBoost / 100.0);

            e.setDamage(
                    e.getDamage() * multiplier);
        }

        /* 4️⃣ Match Tracker (Ranked only) */
        MatchTracker.get(plugin).onHit(damager, victim);

        // RANKED BLOOD PARTICLES
        if (vDI != null && vDI.getDuelInstance() != null && vDI.getDuelInstance().isRanked()) {
            spawnRankedBlood(victim, vEP, vDI.getDuelInstance());
        }
    }

    private void spawnRankedBlood(Player victim, ExtendedPlayer vEP, org.albi.core.modules.Duels.DuelInstance di) {
        var rs = vEP.getMainPlayer()
                .getKitRankedStats(di.getKitTeamOne().getName());
        var tier = org.albi.core.modules.Duels.ranked.RankedTier.getTier(rs.getElo());
        org.bukkit.Color color = org.bukkit.Color.GRAY;

        switch (tier) {
            case BRONZE:
                color = org.bukkit.Color.GRAY;
                break;
            case SILVER:
                color = org.bukkit.Color.WHITE;
                break;
            case GOLD:
                color = org.bukkit.Color.YELLOW;
                break;
            case PLATINUM:
                color = org.bukkit.Color.AQUA;
                break;
            case DIAMOND:
                color = org.bukkit.Color.BLUE;
                break;
            case MASTER:
                color = org.bukkit.Color.PURPLE;
                break;
            case GRANDMASTER:
                color = org.bukkit.Color.RED;
                break;
            case CHAMPION:
                color = org.bukkit.Color.BLACK;
                break;
        }

        org.bukkit.Particle.DustOptions dustOptions = new org.bukkit.Particle.DustOptions(color, 1.5f);
        Location loc = victim.getLocation().clone().add(0, 1, 0);

        java.util.List<Player> everyone = new java.util.ArrayList<>();
        everyone.addAll(java.util.Arrays.asList(di.getTeamOnePlayers()));
        everyone.addAll(java.util.Arrays.asList(di.getTeamTwoPlayers()));
        everyone.addAll(di.spectators);

        for (Player p : everyone) {
            ExtendedPlayer ep = plugin.getExtendedPlayer(p);
            if (p.isOnline() && ep != null && ep.getMainPlayer().showRankedParticles()) {
                p.spawnParticle(org.bukkit.Particle.DUST, loc, 15, 0.4, 0.4, 0.4, 0.1, dustOptions);
            }
        }
    }

    /* ───────────────────────────── */
    /* Shield Break Sound */
    /* ───────────────────────────── */
    @EventHandler
    public void onShieldHit(EntityDamageByEntityEvent event) {

        if (!(event.getEntity() instanceof Player defender)
                || !(event.getDamager() instanceof Player attacker))
            return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();

        if (weapon.getType().toString().toLowerCase().contains("axe")
                && defender.isBlocking()
                && (int) event.getFinalDamage() == 0) {

            attacker.playSound(
                    defender.getLocation(),
                    Sound.ITEM_SHIELD_BREAK,
                    1f,
                    1f);
        }
    }
}
