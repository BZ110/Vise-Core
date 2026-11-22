package org.albi.core.modules.Duels.events;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.Duels.DuelInstance;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Arena;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

public final class MovementOutside implements Listener {

    private final Core plugin;
    public MovementOutside(Core pl) {
        this.plugin = pl;

        /* poll every tick instead of PlayerMoveEvent (cheaper) */
        new BukkitRunnable() {
            @Override public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    ExtendedPlayer ep = plugin.getExtendedPlayer(p);
                    if (ep == null) continue;

                    /* ───────────────────────────────────────────────────
                     *  SECTION 1 – handle spectators first
                     * ─────────────────────────────────────────────────── */
                    if (ep.isSpectating()) {

                        // Only care if they’re actually in a duel world
                        if (!p.getWorld().getName().endsWith("-duel")) continue;

                        Arena    a    = ep.getSpectatingArena();
                        Location base = ep.getSpectatingBase();
                        Location baseX = ep.teleportBase;
                        if (a == null || base == null || baseX == null || !p.getWorld().equals(base.getWorld())) continue;

                        if (outside(p, base, a, isActivePolarArena(a))) {
                            // Rubber‑band them back to the centre (2 blocks above base)
                            p.teleportAsync(baseX.clone().add(0, 2, 0));
                        }
                        continue;   // done – don’t run the “player in duel” logic below
                    }

                    /* ───────────────────────────────────────────────────
                     *  SECTION 2 – regular duel participants
                     * ─────────────────────────────────────────────────── */
                    if (ep.getPlayerDuelInstance() == null) continue;



                    DuelInstance di = ep.getPlayerDuelInstance().getDuelInstance();
                    Arena        a  = (di != null) ? di.getArena() : null;
                    Location     b  = (di != null) ? di.getMotherLocation() : null;
                    if (di == null || a == null || b == null) continue;

                    if ("InGame".equals(ep.getPlayerDuelInstance().getStatus())
                            && outside(p, b, a, isActivePolarArena(a))) {
                        di.death(p, p);     // treat like falling into the void
                    }
                }


            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private boolean isActivePolarArena(Arena arena) {
        return arena != null
                && DuelManager.polarArenaManager != null
                && DuelManager.polarArenaManager.isPolarArena(arena);
    }

    private static boolean outside(Player p, Location base, Arena a, boolean polarArena) {
        double[] c = a.getCornerParameters();
        if (c == null || c.length < 4) return false;
        double minX;
        double minZ;
        double maxX;
        double maxZ;
        if (polarArena) {
            minX = Math.min(c[0], c[2]);
            minZ = Math.min(c[1], c[3]);
            maxX = Math.max(c[0], c[2]);
            maxZ = Math.max(c[1], c[3]);
        } else {
            minX = base.getX() + c[0];
            minZ = base.getZ() + c[1];
            maxX = base.getX() + c[2];
            maxZ = base.getZ() + c[3];
        }
        double x = p.getLocation().getX(), z = p.getLocation().getZ();
        return x < minX || x > maxX || z < minZ || z > maxZ;
    }
}
