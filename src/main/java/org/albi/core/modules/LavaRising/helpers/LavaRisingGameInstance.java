package org.albi.core.modules.LavaRising.helpers;

import com.google.gson.JsonObject;
import org.albi.core.Core;
import org.albi.core.util.ConfigValues;
import org.albi.core.util.WebhookClient;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.data.MainPlayer;
import org.albi.core.modules.LavaRising.LavaRisingManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;


import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * Lava Rising match that keeps the original LavaArena immutable.
 */
public class LavaRisingGameInstance {

    /*──────────────────────── config ───────────────────────*/
    private final Core   plugin;
    private final World  world;
    private final LavaArena arena;
    private final int timeFinish, timePvp, timeLavaStart, timeLavaRise;

    /*──────────────────────── runtime ──────────────────────*/
    private boolean active = false;
    private int seconds = 0, lavaY = -63;
    private int gameTaskId = -1, boundaryTaskId = -1;

    /* Offset from arena’s stored co‑ords → paste position */
    private final int shiftX, shiftZ;   // +X, +Z applied to all stored values
    private final int minX, maxX, minZ, maxZ; // shifted bounds cache


    private void fireStartWebhook() {
        var endpoint = ConfigValues.httpEndpoint(ConfigValues.envOrYaml(plugin.getConfig(),
                "lava_rising.webhook_url", "VISE_LAVA_WEBHOOK_URL"));
        if (endpoint == null) return;

        JsonObject payload = new JsonObject();
        payload.addProperty("content", "Lava Rising started in " + arena.getName() + ".");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!WebhookClient.postJson(endpoint, payload)) {
                    plugin.getLogger().warning("[LavaRising] The webhook rejected the start notification.");
                }
            } catch (IOException e) {
                plugin.getLogger().warning("[LavaRising] Could not send the start notification.");
            }
        });
    }

    public LavaRisingGameInstance(Core plugin) {
        this.plugin = plugin;

        // Pull config
        String worldName = plugin.getConfig().getString("lava_rising.lava_rising_world", "lavaevent");
        timeFinish       = plugin.getConfig().getInt("lava_rising.time_finish",      900);
        timePvp          = plugin.getConfig().getInt("lava_rising.time_pvp",         600);
        timeLavaStart    = plugin.getConfig().getInt("lava_rising.time_lava_start",  300);
        timeLavaRise     = plugin.getConfig().getInt("lava_rising.time_lava_rise",    10);

        world = Bukkit.getWorld(worldName);
        if (world == null) throw new IllegalStateException("World '" + worldName + "' not found.");

        LavaRisingStorage store = (LavaRisingStorage) plugin.getData().get("lavastor");
        if (store == null || store.lavaArenas.isEmpty())
            throw new IllegalStateException("No LavaArenas saved in lavastor.");

        arena = store.lavaArenas.get(new Random().nextInt(store.lavaArenas.size()));

        /* We will paste at (0,0,0).  Compute how far we must
           shift the stored arena bounds to line up with that. */
        shiftX = - (int) arena.getLeastX();   // so new leastX = 0
        shiftZ = - (int) arena.getLeastZ();

        minX = (int) arena.getLeastX() + shiftX; // 0
        maxX = (int) arena.getMostX()  + shiftX;
        minZ = (int) arena.getLeastZ() + shiftZ; // 0
        maxZ = (int) arena.getMostZ()  + shiftZ;
    }

    /*────────────────────── public API ─────────────────────*/

    public void start() {
        if (active) return;
        active = true;

        LavaRisingManager.lavaRisingListener.hook(this);

        broadcast(ChatColor.GOLD + "» Lava Rising starting!");
        broadcast(ChatColor.YELLOW + "Arena: " + ChatColor.AQUA + arena.getName());


        /* 1) Paste schematic at origin (0,0,0), with air */
        Location origin = new Location(world, 0, 0, 0);
        arena.pasteSchematic(origin, true);

        /* 2) Delay 10 s for schematic to paste, then teleport players */
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.setDifficulty(Difficulty.HARD);
            List<Player> list = world.getPlayers();
            for (Player p : list) {
                Location spawnShifted = arena.getSpawn()
                        .convertToLocation().clone()
                        .add(shiftX, 0, shiftZ);
                p.teleportAsync(spawnShifted);
                ItemStack[] kit = {
                        new ItemStack(Material.ENCHANTING_TABLE),
                        new ItemStack(Material.BOOK, 24),
                        new ItemStack(Material.IRON_BLOCK, 3),
                        new ItemStack(Material.GOLD_BLOCK, 2),
                        new ItemStack(Material.APPLE, 9),
                        new ItemStack(Material.OAK_LOG, 24),
                        new ItemStack(Material.COOKED_BEEF, 4),
                        new ItemStack(Material.ARROW, 8),
                        new ItemStack(Material.STRING, 3),
                        new ItemStack(Material.LAPIS_BLOCK, 3),
                        new ItemStack(Material.EXPERIENCE_BOTTLE, 10)
                };
                p.getInventory().addItem(kit);
                p.setGameMode(GameMode.SURVIVAL);
                sendBorder(p);
                p.sendTitle(ChatColor.RED + "Lava Rising!",
                        ChatColor.GOLD + "Survive as long as you can",
                        10, 60, 20);
            }
            world.getEntities().stream()
                    .filter(ent -> !(ent instanceof Player))            // keep players
                    .forEach(ent -> {
                        ent.remove();
                        try {
                            ent.setTicksLived(0); // safety: stop lingering client‑side
                        } catch (Exception e) {
                            // Do nothing, if it doesn't work, it doesn't.
                        }
                    });
            lavaY = world.getMinHeight();
            beginLoops();
        }, 50L); // 10 s
    }

    public void stop() {
        LavaRisingManager.lavaRisingListener.hook(null);
        LavaRisingManager.playing = false;
        if (!active) return;
        active = false;

        if (gameTaskId   != -1) Bukkit.getScheduler().cancelTask(gameTaskId);
        if (boundaryTaskId != -1) Bukkit.getScheduler().cancelTask(boundaryTaskId);
        world.setPVP(false);
        world.getPlayers().forEach(p -> {
            p.setWorldBorder(null);
            plugin.resetPlayer(p);
            plugin.teleportToLobby(p);
            plugin.resetPlayer(p);
        });
        broadcast(ChatColor.GREEN + "» Lava Rising ended.");
    }

    /*──────────── getters (external inspection) ───────────*/
    public boolean   isActive()          { return active;      }
    public int       getSecondsElapsed() { return seconds;     }
    public int       getCurrentLavaY()   { return lavaY;       }
    public LavaArena getArena()          { return arena;       }
    public World     getWorld()          { return world;       }
    public int       getTimePvp()        { return timePvp;     }

    /*───────────────────── internals ──────────────────────*/

    private void beginLoops() {

        broadcast(ChatColor.YELLOW + "Lava rises in "
                + ChatColor.RED + timeLavaStart + ChatColor.YELLOW + " s!");

        /* Main 1‑second loop */
        gameTaskId = new BukkitRunnable() {
            int dmg = 4;

            @Override public void run() {
                seconds++;

                /* PvP enable */
                if (seconds == timePvp) {
                    world.setPVP(true);
                    broadcast(ChatColor.DARK_RED + "PvP is now ENABLED!");
                }

                if (seconds == (timePvp - 60)) {
                    broadcast(ChatColor.DARK_RED + "PvP will enable in 1 minute!");
                }

                if (seconds == (timePvp - 30)) {
                    broadcast(ChatColor.DARK_RED + "PvP will enable in 30 seconds!");
                }

                if (seconds == (timePvp - 300)) {
                    broadcast(ChatColor.DARK_RED + "PvP will enable in 5 minutes!");
                }

                if (seconds == (timePvp - 480)) {
                    broadcast(ChatColor.DARK_RED + "PvP will enable in 8 minutes!");
                }


                if (seconds == (timePvp - 10)) {
                    broadcast(ChatColor.DARK_RED + "PvP will enable in 10 seconds!");
                }

                /* Lava rising */
                if ((seconds >= timeLavaStart && (seconds - timeLavaStart) % timeLavaRise == 0 || (seconds % 2 == 0 && lavaY > 60) || seconds > timeFinish) && lavaY < 320) {

                    lavaY++;
                    fillLavaPerimeter(lavaY);
                    if (lavaY % 5 == 0) broadcast(ChatColor.GOLD + "Lava now at Y="
                            + ChatColor.RED + lavaY + ChatColor.GOLD + "!");
                }

                /* Immersed players take damage */
                for (Player p : world.getPlayers()) {
                    if (p.getLocation().getY() <= lavaY) {
                        double diff = lavaY - p.getLocation().getY();
                        double scaledDmg = Math.min(dmg + (diff), 8); // scales with depth
                        p.damage(scaledDmg);
                    }
                }

                if (seconds == timeFinish - 30)
                    broadcast(ChatColor.RED + "⚠ 30 seconds until Final Phase: 1s lava rises!");
                /* Finish */
                if (seconds == timeFinish) {
                    broadcast(ChatColor.RED + "⚠ Time is up! Lava will now rise every second.");
                    broadcast(ChatColor.GOLD + "Only the strongest will survive. Good luck. You'll need it.");
                    dmg += 10; // Make 8 the default by adding 10, Math.min will take care of it.
                }
            }
        }.runTaskTimer(plugin, 20L, 20L).getTaskId();

        /*  Boundary check: once/second, ½♥ if outside shifted bounds */
        boundaryTaskId = new BukkitRunnable() {
            @Override public void run() {
                if (!active) { cancel(); return; }
                for (Player p : world.getPlayers()) {
                    Location l = p.getLocation();
                    if (l.getBlockX() < minX || l.getBlockX() > maxX ||
                            l.getBlockZ() < minZ || l.getBlockZ() > maxZ) {
                        p.damage(5.0); // ½♥
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L).getTaskId();
    }

    /*──────── world border per player ────────*/
    private void sendBorder(Player p) {
        WorldBorder b = Bukkit.createWorldBorder();
        double cx = (minX + maxX) / 2.0;
        double cz = (minZ + maxZ) / 2.0;
        double cy = arena.getSpawn().convertToLocation().getY();

        b.setCenter(cx, cz);
        b.setSize(Math.max(maxX - minX, maxZ - minZ));
        b.setWarningDistance(1);
        b.setDamageBuffer(0.25);
        b.setDamageAmount(0.5);

        p.setWorldBorder(b);
    }

    /*──────── lava fill: full slice - used to be perimeter ─────*/
    private void fillLavaPerimeter(int y) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                setLava(x, y, z);
            }
        }
    }
    private void setLava(int x, int y, int z) {
        Block b = world.getBlockAt(x, y, z);
        if(b.getType().isAir()) b.setType(Material.LAVA, false);
    }

    public void broadcast(String msg) {
        world.getPlayers().forEach(p -> p.sendMessage(msg));
    }

    public int getMinX() { return minX; }
    public int getMaxX() { return maxX; }
    public int getMinZ() { return minZ; }
    public int getMaxZ() { return maxZ; }

    /** Call this every time someone is eliminated or leaves.
     *  It checks remaining non‑spectator players in the match world.
     */
    public void checkForWin() {
        if (!active) return;

        // players still “alive”
        java.util.List<Player> alive = world.getPlayers().stream()
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                .toList();

        if (alive.size() == 1) {
            Player winner = alive.get(0);

            // announce to world (or use Bukkit.broadcastMessage for global)
            broadcast(ChatColor.GOLD + "» " + ChatColor.AQUA + winner.getName()
                    + ChatColor.GOLD + " wins Lava Rising!");

            // optional: title / sound
            winner.sendTitle(ChatColor.GREEN + "VICTORY", "", 10, 100, 20);
            world.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1);

            ExtendedPlayer eW = plugin.getExtendedPlayer(winner);
            MainPlayer mW = eW.getMainPlayer();
            mW.addXp(500);

            winner.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + ">> +500 XP <<");

            stop();            // ends match, cleans borders, etc.
        } else if (alive.isEmpty()) {
            broadcast(ChatColor.RED + "Everyone died – no winner this round!");
            stop();
        }
    }

    // Game state
    public boolean isActiveGame()       { return active; }
    public int getSeconds()             { return seconds; }
    public int getLavaY()               { return lavaY; }
    public int getGameTaskId()          { return gameTaskId; }
    public int getBoundaryTaskId()      { return boundaryTaskId; }

    // Arena info
    public int getShiftX()              { return shiftX; }
    public int getShiftZ()              { return shiftZ; }
    public int getMinXBound()           { return minX; }
    public int getMaxXBound()           { return maxX; }
    public int getMinZBound()           { return minZ; }
    public int getMaxZBound()           { return maxZ; }

    // Timers from config
    public int getTimeFinish()          { return timeFinish; }
    public int getTimeLavaStart()       { return timeLavaStart; }
    public int getTimeLavaRise()        { return timeLavaRise; }

    // Raw objects
    public Core getPlugin()             { return plugin; }
    public LavaArena getLavaArena()     { return arena; }



}
