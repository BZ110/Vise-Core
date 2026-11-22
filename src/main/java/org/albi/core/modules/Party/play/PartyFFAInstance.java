package org.albi.core.modules.Party.play;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.Duels.DuelInstance;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.*;
import org.albi.core.modules.Duels.polar.PolarArenaReservation;
import org.albi.core.util.TitleFx;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lightweight party free-for-all built on top of the duel world / arena system.
 * No SQL or leaderboard hooks on purpose.
 */
public final class PartyFFAInstance extends DuelInstance {

    private final Core plugin;

    private final List<UUID> participants = new ArrayList<>();
    private final LinkedHashSet<UUID> alive = new LinkedHashSet<>();
    private final Map<UUID, Location> lockedCountdownSpawns = new HashMap<>();
    private final Map<UUID, Location> scatterSpawns = new HashMap<>();
    private final Map<UUID, Long> lastCombatTimes = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();

    private long liveStartedAtMillis = 0L;

    private Arena arena;
    private Kit kit;
    private Location motherLocation;
    private PolarArenaReservation polarReservation;
    private boolean finished = false;
    private boolean cancelled = false;

    public PartyFFAInstance(Core plugin) {
        super(plugin);
        this.plugin = plugin;
    }

    public PartyFFAInstance prepare(List<Player> fighters, Arena arena, Kit kit) {
        this.participants.clear();
        this.alive.clear();
        this.lockedCountdownSpawns.clear();
        this.scatterSpawns.clear();
        this.kills.clear();
        this.liveStartedAtMillis = 0L;
        if (fighters != null) {
            for (Player fighter : fighters) {
                if (fighter != null) {
                    this.participants.add(fighter.getUniqueId());
                    this.alive.add(fighter.getUniqueId());
                }
            }
        }
        this.arena = arena;
        this.kit = kit;
        return this;
    }

    public void start() {
        List<Player> fighters = onlineParticipants();
        if (fighters.size() < 2) {
            fighters.forEach(p -> p.sendMessage("§cYou need at least 2 online players to start a Party FFA."));
            return;
        }

        for (Player p : fighters) {
            ExtendedPlayer ep = plugin.getExtendedPlayer(p);
            if (ep != null && ep.getPlayerDuelInstance() != null && ep.getPlayerDuelInstance().getStatus() != null) {
                fighters.forEach(pp -> plugin.getMessageAPI().sendMessageLang(pp, "duel_in"));
                return;
            }
        }

        if (usingPolarArena()) {
            polarReservation = DuelManager.polarArenaManager.reserveArena(arena);
            if (polarReservation == null) {
                fighters.forEach(pp -> plugin.getMessageAPI().sendMessageLang(pp, "duel_no_arena"));
                return;
            }
            motherLocation = polarReservation.getMotherLocation();
        } else {
            motherLocation = findArenaSpot(arena);
            if (motherLocation == null) {
                fighters.forEach(pp -> plugin.getMessageAPI().sendMessageLang(pp, "duel_no_arena"));
                return;
            }
            DuelManager.schemManager.enqueueSchematic(motherLocation, getSchematicFile(arena), true);
        }

        clearEntitiesInArena(motherLocation, arena);
        List<Location> spawns = computeScatterSpawns(fighters.size());

        Location waitingSpawn = computeSafeMidSpawn();
        for (int i = 0; i < fighters.size(); i++) {
            Player p = fighters.get(i);
            ExtendedPlayer ep = plugin.getExtendedPlayer(p);
            if (ep == null) continue;

            plugin.resetPlayer(p);
            plugin.applyPossibleKit(p, kit);

            PlayerDuelInstance pdi = new PlayerDuelInstance();
            pdi.setPlayer(p);
            pdi.setStatus("Countdown");
            pdi.setDuelInstance(this);
            pdi.setKit(kit);
            pdi.setTeam(i + 1);
            pdi.setTeammates(new Player[]{p});
            pdi.setOpponents(fighters.stream().filter(other -> !other.getUniqueId().equals(p.getUniqueId())).toArray(Player[]::new));
            ep.setPlayerDuelInstance(pdi);

            Location spawn = waitingSpawn == null ? motherLocation.clone() : waitingSpawn.clone();
            lockedCountdownSpawns.put(p.getUniqueId(), spawn);
            if (i < spawns.size()) scatterSpawns.put(p.getUniqueId(), spawns.get(i));
            p.teleportAsync(spawn);
        }

        new BukkitRunnable() {
            int ticksLeft = 10 * 20;
            boolean skipped = false;
            boolean firstToggleSoundPlayed = false;
            java.util.Set<UUID> readyToggles = new java.util.HashSet<>();
            java.util.Map<UUID, Boolean> lastSneak = new java.util.HashMap<>();
            @Override
            public void run() {
                if (finished || cancelled) {
                    cancel();
                    return;
                }

                List<Player> current = onlineParticipants();
                if (current.size() < 2) {
                    finish(current.isEmpty() ? null : current.get(0));
                    cancel();
                    return;
                }

                for (Player p : current) {
                    ExtendedPlayer ep = plugin.getExtendedPlayer(p);
                    if (ep == null || ep.getPlayerDuelInstance() == null) {
                        cancelled = true;
                        emergencyReturn();
                        cancel();
                        return;
                    }
                    Location lock = lockedCountdownSpawns.get(p.getUniqueId());
                    if (lock != null && p.getWorld() == lock.getWorld() && p.getLocation().distanceSquared(lock) > 0.05) {
                        p.teleportAsync(lock);
                    }
                }

                for(Player p : current) {
                    boolean sneaks = p.isSneaking();
                    boolean was = lastSneak.getOrDefault(p.getUniqueId(), false);
                    if(sneaks && !was) {
                        boolean wasReady = readyToggles.contains(p.getUniqueId());
                        if(wasReady) readyToggles.remove(p.getUniqueId());
                        else {
                            readyToggles.add(p.getUniqueId());
                            if (!firstToggleSoundPlayed) {
                                firstToggleSoundPlayed = true;
                                for (Player all : current) {
                                    all.playSound(all, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.5f, 1.2f);
                                }
                            }
                        }
                    }
                    lastSneak.put(p.getUniqueId(), sneaks);
                    
                    p.sendActionBar(org.bukkit.ChatColor.translateAlternateColorCodes('&', 
                        "&7SHIFT TO SKIP COUNTDOWN (&f" + readyToggles.size() + "&7/" + current.size() + ")"));
                }

                if (!skipped && readyToggles.size() >= current.size() && !current.isEmpty()) {
                    skipped = true;
                    ticksLeft = 0;
                    for (Player p : current) {
                        p.sendTitle("§a§lSkipped", "§7Everyone readied up, starting now.", 0, 20, 10);
                    }
                }

                if (ticksLeft % 20 == 0) {
                    int sec = ticksLeft / 20;
                    for (Player p : current) {
                        p.playSound(p, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                        p.sendTitle("§d§lOrganize", "§7Scatter unlocks in §f" + sec + "§7…", 0, 20, 0);
                    }
                }

                if (ticksLeft-- <= 0) {
                    cancel();
                    beginScatter();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void beginScatter() {
        for (Player p : onlineParticipants()) {
            ExtendedPlayer ep = plugin.getExtendedPlayer(p);
            if (ep == null || ep.getPlayerDuelInstance() == null) continue;
            ep.getPlayerDuelInstance().setStatus("Scatter");

            // Scatter spawns disabled
            // Location scatter = scatterSpawns.get(p.getUniqueId());
            // if (scatter != null) p.teleportAsync(scatter);

            p.sendTitle("§6§lScatter!", "§7PvP unlocks in §f10§7…", 0, 30, 10);
            p.playSound(p, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.15f);
        }

        new BukkitRunnable() {
            int ticksLeft = 10 * 20;
            boolean skipped = false;
            boolean firstToggleSoundPlayed = false;
            java.util.Set<UUID> readyToggles = new java.util.HashSet<>();
            java.util.Map<UUID, Boolean> lastSneak = new java.util.HashMap<>();
            @Override
            public void run() {
                if (finished || cancelled) {
                    cancel();
                    return;
                }

                List<Player> current = onlineParticipants();
                if (current.size() < 2) {
                    finish(current.isEmpty() ? null : current.get(0));
                    cancel();
                    return;
                }

                for(Player p : current) {
                    boolean sneaks = p.isSneaking();
                    boolean was = lastSneak.getOrDefault(p.getUniqueId(), false);
                    if(sneaks && !was) {
                        boolean wasReady = readyToggles.contains(p.getUniqueId());
                        if(wasReady) readyToggles.remove(p.getUniqueId());
                        else {
                            readyToggles.add(p.getUniqueId());
                            if (!firstToggleSoundPlayed) {
                                firstToggleSoundPlayed = true;
                                for (Player all : current) {
                                    all.playSound(all, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.5f, 1.2f);
                                }
                            }
                        }
                    }
                    lastSneak.put(p.getUniqueId(), sneaks);
                    
                    p.sendActionBar(org.bukkit.ChatColor.translateAlternateColorCodes('&', 
                        "&7SHIFT TO SKIP COUNTDOWN (&f" + readyToggles.size() + "&7/" + current.size() + ")"));
                }

                // Only allow skip in the last 3 seconds (ticksLeft <= 60)
                if (!skipped && readyToggles.size() >= current.size() && !current.isEmpty() && ticksLeft <= 60) {
                    skipped = true;
                    ticksLeft = 0;
                    for (Player p : current) {
                        p.sendTitle("§a§lSkipped", "§7Everyone readied up, starting now.", 0, 20, 10);
                    }
                }

                if (ticksLeft % 20 == 0) {
                    int sec = ticksLeft / 20;
                    for (Player p : current) {
                        p.playSound(p, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                        p.sendTitle("§6§lScatter!", "§7PvP unlocks in §f" + sec + "§7…", 0, 20, 0);
                    }
                }

                if (ticksLeft-- <= 0) {
                    cancel();
                    startLive();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void startLive() {
        liveStartedAtMillis = System.currentTimeMillis();
        for (Player p : onlineParticipants()) {
            ExtendedPlayer ep = plugin.getExtendedPlayer(p);
            if (ep == null || ep.getPlayerDuelInstance() == null) continue;
            ep.getPlayerDuelInstance().setStatus("InGame");
            applyEffects(p, kit);
            TitleFx.playFight(plugin, p, "§7Last player standing wins.");
            p.playSound(p, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.35f);
        }
    }

    @Override
    public void leave(Player p) {
        if (p == null || finished) return;
        alive.remove(p.getUniqueId());
        lockedCountdownSpawns.remove(p.getUniqueId());
        scatterSpawns.remove(p.getUniqueId());

        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep != null) ep.setPlayerDuelInstance(null);

        plugin.resetPlayer(p);
        plugin.teleportToLobby(p);
        broadcast("§e" + p.getName() + " §7has left the Party FFA.");
        checkForWinner();
    }

    @Override
    public void death(Player p, Player killer) {
        if (p == null || finished) return;
        if (!alive.remove(p.getUniqueId())) return;

        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep != null && ep.getPlayerDuelInstance() != null) {
            ep.getPlayerDuelInstance().setStatus("Dead");
        }

        p.setGameMode(GameMode.SPECTATOR);
        p.playSound(p, Sound.ENTITY_VILLAGER_DEATH, 1f, 1f);
        p.sendTitle("§c§lEliminated", killer != null && killer != p ? "§7By §f" + killer.getName() : "§7You are out.", 0, 30, 10);

        if (killer != null && killer != p) {
            lastCombatTimes.put(killer.getUniqueId(), System.currentTimeMillis());
            kills.merge(killer.getUniqueId(), 1, Integer::sum);
            killer.playSound(killer, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
            killer.sendMessage("§a✦ §fYou eliminated §d" + p.getName() + "§f.");
            broadcast("§d" + p.getName() + " §7was eliminated by §f" + killer.getName() + "§7.");
        } else {
            broadcast("§d" + p.getName() + " §7has been eliminated.");
        }

        checkForWinner();
    }

    private void checkForWinner() {
        if (finished) return;
        List<Player> survivors = onlineAliveParticipants();
        if (survivors.size() <= 1) {
            finish(survivors.isEmpty() ? null : survivors.get(0));
        }
    }

    private void finish(Player winner) {
        if (finished) return;
        finished = true;
        cancelled = true;
        clearEntitiesInArena(motherLocation, arena);

        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            boolean won = winner != null && winner.getUniqueId().equals(id);
            plugin.resetPlayer(p, false);
            if (won) {
                p.sendTitle("§a§lVictory!", "§7You were the last fighter standing.", 0, 40, 10);
                p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            } else {
                String subtitle = winner == null ? "§7The match ended." : "§7Winner: §f" + winner.getName();
                p.sendTitle("§c§lMatch Over", subtitle, 0, 40, 10);
            }
        }

        if (winner != null) {
            broadcast("§6§lParty FFA §8» §f" + winner.getName() + " §7is the last player standing.");
        } else {
            broadcast("§6§lParty FFA §8» §7The match ended with no remaining winner.");
        }

        new BukkitRunnable() {
            int ticks = 60;
            @Override public void run() {
                if (--ticks > 0) return;
                for (UUID id : participants) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null) continue;
                    ExtendedPlayer ep = plugin.getExtendedPlayer(p);
                    if (ep != null) ep.setPlayerDuelInstance(null);
                    plugin.resetPlayer(p);
                    plugin.teleportToLobby(p);
                }
                freeArena();
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void emergencyReturn() {
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            ExtendedPlayer ep = plugin.getExtendedPlayer(p);
            if (ep != null) ep.setPlayerDuelInstance(null);
            plugin.resetPlayer(p);
            plugin.teleportToLobby(p);
            plugin.getMessageAPI().sendMessageLang(p, "duel_cancelled");
        }
        freeArena();
    }

    private boolean usingPolarArena() {
        return DuelManager.polarArenaManager != null && DuelManager.polarArenaManager.isPolarArena(arena);
    }

    private void broadcast(String message) {
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(message);
        }
    }

    private List<Player> onlineParticipants() {
        List<Player> out = new ArrayList<>();
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) out.add(p);
        }
        return out;
    }

    private List<Player> onlineAliveParticipants() {
        return alive.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .collect(Collectors.toList());
    }

    private List<Location> computeScatterSpawns(int count) {
        List<Location> out = new ArrayList<>();
        World world = motherLocation == null ? null : motherLocation.getWorld();
        if (world == null) return out;

        double[] c = arena.getCornerParameters();
        double minX = usingPolarArena() ? Math.min(c[0], c[2]) : motherLocation.getX() + c[0];
        double minZ = usingPolarArena() ? Math.min(c[1], c[3]) : motherLocation.getZ() + c[1];
        double maxX = usingPolarArena() ? Math.max(c[0], c[2]) : motherLocation.getX() + c[2];
        double maxZ = usingPolarArena() ? Math.max(c[1], c[3]) : motherLocation.getZ() + c[3];
        double centerX = (minX + maxX) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;
        double radius = Math.max(3.5, Math.min(Math.abs(maxX - minX), Math.abs(maxZ - minZ)) / 3.0);
        double y = usingPolarArena() ? Math.max(arena.getSpawn1()[1], arena.getSpawn2()[1]) : motherLocation.getY() + Math.max(arena.getSpawn1()[1], arena.getSpawn2()[1]);
        Location center = new Location(world, centerX, y, centerZ);

        for (int i = 0; i < count; i++) {
            double angle = ((Math.PI * 2) / Math.max(1, count)) * i;
            double x = centerX + Math.cos(angle) * radius;
            double z = centerZ + Math.sin(angle) * radius;
            Location loc = new Location(world, x, y, z);
            loc = adjustToSafeGround(loc);
            Vector direction = center.toVector().subtract(loc.toVector());
            loc.setDirection(direction.lengthSquared() == 0 ? new Vector(0, 0, 1) : direction);
            out.add(loc);
        }
        return out;
    }

    private Location computeSafeMidSpawn() {
        World world = motherLocation == null ? null : motherLocation.getWorld();
        if (world == null) return null;
        double midX = (arena.getSpawn1()[0] + arena.getSpawn2()[0]) / 2.0D;
        double midY = (arena.getSpawn1()[1] + arena.getSpawn2()[1]) / 2.0D;
        double midZ = (arena.getSpawn1()[2] + arena.getSpawn2()[2]) / 2.0D;
        float yaw = (float) ((arena.getSpawn1()[3] + arena.getSpawn2()[3]) / 2.0D);
        float pitch = (float) ((arena.getSpawn1()[4] + arena.getSpawn2()[4]) / 2.0D);
        Location loc = usingPolarArena()
                ? new Location(world, midX, midY, midZ, yaw, pitch)
                : new Location(world,
                motherLocation.getX() + midX,
                motherLocation.getY() + midY,
                motherLocation.getZ() + midZ,
                yaw, pitch);
        return adjustToSafeGround(loc);
    }

    private Location adjustToSafeGround(Location preferred) {
        if (preferred == null || preferred.getWorld() == null) return preferred;
        World world = preferred.getWorld();
        int x = preferred.getBlockX();
        int z = preferred.getBlockZ();
        int startY = Math.max(world.getMinHeight() + 2, preferred.getBlockY() + 8);
        int endY = Math.max(world.getMinHeight() + 1, preferred.getBlockY() - 40);
        for (int y = startY; y >= endY; y--) {
            Location feet = new Location(world, preferred.getX(), y, preferred.getZ(), preferred.getYaw(), preferred.getPitch());
            if (isSafeStandingSpot(feet)) return feet.add(0, 0, 0);
        }
        int highest = world.getHighestBlockYAt(x, z);
        Location fallback = new Location(world, preferred.getX(), highest + 1.0D, preferred.getZ(), preferred.getYaw(), preferred.getPitch());
        return fallback;
    }

    private boolean isSafeStandingSpot(Location feet) {
        if (feet == null || feet.getWorld() == null) return false;
        var world = feet.getWorld();
        var below = world.getBlockAt(feet.clone().add(0, -1, 0));
        var at = world.getBlockAt(feet);
        var above = world.getBlockAt(feet.clone().add(0, 1, 0));
        return below.getType().isSolid() && at.isPassable() && above.isPassable();
    }


    public int getKills(UUID playerId) {
        if (playerId == null) return 0;
        return kills.getOrDefault(playerId, 0);
    }

    public int getRemainingPlayers() {
        return (int) alive.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .count();
    }

    public int getElapsedSeconds() {
        if (liveStartedAtMillis <= 0L) return 0;
        return (int) ((System.currentTimeMillis() - liveStartedAtMillis) / 1000L);
    }

    private Location findArenaSpot(Arena arena) {
        World world = plugin.getServer().getWorld(arena.getName() + "-duel");
        if (world == null) {
            plugin.getLogger().severe("[PartyFFA] World \"" + arena.getName() + "-duel\" not loaded!");
            return null;
        }

        if (!GlobalDuelCache.availableArenas.containsKey(arena)) {
            GlobalDuelCache.availableArenas.put(arena, new ArrayList<>());
            GlobalDuelCache.usedArenas.put(arena, new ArrayList<>());
        }

        ArrayList<Location> available = GlobalDuelCache.availableArenas.get(arena);
        ArrayList<Location> used = GlobalDuelCache.usedArenas.get(arena);
        if (available.isEmpty()) {
            int maxArenas = arena.getMaxArenas();
            int zOffset = arena.getOffsetZAxis();
            int xOffset = arena.getOffsetXAxis();
            int maxPerLine = arena.getMaxPerLine();
            Location base = new Location(world, 0, 100, 0);
            for (int i = 0; i < maxArenas; i++) {
                int row = i / maxPerLine;
                int col = i % maxPerLine;
                available.add(base.clone().add(row * xOffset, 0, col * zOffset));
            }
        }

        for (Location potential : available) {
            if (!used.contains(potential)) {
                used.add(potential);
                return potential;
            }
        }
        return null;
    }

    private void freeArena() {
        if (arena == null || motherLocation == null) return;
        if (usingPolarArena()) {
            if (polarReservation != null) {
                DuelManager.polarArenaManager.releaseArena(polarReservation, true);
                polarReservation = null;
            }
            return;
        }
        List<Location> used = GlobalDuelCache.usedArenas.get(arena);
        List<Location> available = GlobalDuelCache.availableArenas.get(arena);
        if (used != null) used.remove(motherLocation);
        if (available != null) available.add(motherLocation);
    }

    private File getSchematicFile(Arena arena) {
        return new File(plugin.getDataFolder(), "schems" + File.separator + "duels" + File.separator + arena.getSchematic());
    }

    private void clearEntitiesInArena(Location base, Arena arena) {
        if (base == null || arena == null) return;
        double[] params = arena.getCornerParameters();
        if (params == null || params.length < 4) return;
        double minX = usingPolarArena() ? Math.min(params[0], params[2]) : base.getX() + params[0];
        double minZ = usingPolarArena() ? Math.min(params[1], params[3]) : base.getZ() + params[1];
        double maxX = usingPolarArena() ? Math.max(params[0], params[2]) : base.getX() + params[2];
        double maxZ = usingPolarArena() ? Math.max(params[1], params[3]) : base.getZ() + params[3];
        World world = base.getWorld();
        if (world == null) return;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Player) continue;
            Location loc = entity.getLocation();
            double x = loc.getX();
            double z = loc.getZ();
            if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) entity.remove();
        }
    }

    private void applyEffects(Player p, Kit kit) {
        if (kit == null) return;
        Object raw = kit.getMetaData().get("effects");
        if (!(raw instanceof Iterable<?> list)) return;
        for (Object obj : list) {
            if (!(obj instanceof org.albi.core.data.EffectMeta meta)) continue;
            try {
                var type = meta.getEffect();
                if (type == null) continue;
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        type,
                        meta.getDuration() * 20,
                        meta.getAmplifier(),
                        meta.isAmbient(),
                        true,
                        true
                ));
            } catch (Exception ignored) {}
        }
    }

    @Override public Arena getArena() { return arena; }
    @Override public Location getMotherLocation() { return motherLocation; }
    @Override public Kit getKitTeamOne() { return kit; }
    @Override public Kit getKitTeamTwo() { return kit; }
    @Override public boolean isFinished() { return finished; }
}
