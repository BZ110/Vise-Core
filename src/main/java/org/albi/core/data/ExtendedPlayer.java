package org.albi.core.data;

import org.albi.core.modules.Duels.helpers.Arena;
import org.albi.core.modules.Duels.helpers.DuelPreference;
import org.albi.core.modules.Duels.helpers.Kit;
import org.albi.core.modules.Duels.helpers.PlayerDuelInstance;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.albi.core.data.StatManagement.PlayerStat;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ExtendedPlayer {

    private int displayTab = 0;

    /* ────────────────────────── basic data ────────────────────────── */
    private final Player player;
    private PlayerStat stat;
    private MainPlayer mp;

    private Player lastHit;
    private @NotNull Location deathlocation;

    /* ────────────────────────── ffa stuff ────────────────────────── */
    private Location lastFFADearthLocation;
    private String lastFFAKitName;
    private String lastFFAWorlName;
    private boolean respawnToLobby;

    private boolean lavaRisingEvent;

    public boolean isLavaRisingEvent() {
        return lavaRisingEvent;
    }

    public void setLavaRisingEvent(boolean lavaRisingEvent) {
        this.lavaRisingEvent = lavaRisingEvent;
    }

    public Player getLastHit() {
        return lastHit;
    }

    public void setLastHit(Player lastHit) {
        this.lastHit = lastHit;
    }

    private final java.util.Map<UUID, DuelPreference> duelPrefs = new java.util.HashMap<>();

    /** fetches or creates the preference bundle for this opponent */
    public DuelPreference prefsFor(UUID opponent) {
        return duelPrefs.computeIfAbsent(opponent, id -> new DuelPreference());
    }

    /** delete settings once the duel goes through or is cancelled */
    public void clearPrefs(UUID opponent) {
        duelPrefs.remove(opponent);
    }

    /* ───────── spectator support ───────── */
    private boolean spectating = false;
    private Arena spectatingArena = null; // arena they’re watching
    private Location spectatingBase = null; // mother‑location of that arena
    public Location teleportBase = null; // too lazy to make getter and setter

    /* … ctor and other code stay untouched … */

    /* ───── getters / setters for spectator flag ───── */
    public boolean isSpectating() {
        return spectating;
    }

    public void setSpectating(boolean b) {
        spectating = b;
    }

    public Arena getSpectatingArena() {
        return spectatingArena;
    }

    public void setSpectatingArena(Arena a) {
        spectatingArena = a;
    }

    public Location getSpectatingBase() {
        return spectatingBase;
    }

    public void setSpectatingBase(Location loc) {
        spectatingBase = loc;
    }

    private PlayerDuelInstance duelInstance;

    /* ────────────────────────── async‑load tracking ───────────────── */
    private final CompletableFuture<Void> loadFuture = new CompletableFuture<>();
    private volatile boolean statLoaded = false;
    private volatile boolean mpLoaded = false;

    /* ────────────────────────── misc state ────────────────────────── */
    private boolean bypassRegion = false;
    private boolean fly = false;
    private boolean vanish = false;

    private final HashMap<String, Long> cooldowns = new HashMap<>();
    private Player replyTarget;
    private HashMap<UUID, Integer> selectedKit = new HashMap<>();

    // 10 SLOTS AVAILABLE FOR EVERY PLAYER TO SAVE REFERENCES
    // KEPT IN MEMORY, IS NOT GARBAGE COLLECTED UNTIL QUIT
    public Player[] playersSaved = new Player[10];
    public Kit[] chosenKits = new Kit[10];
    public Arena[] chosenArenas = new Arena[10];
    public int[] chosenRounds = new int[10];

    /* ════════════════════════ ctor ════════════════════════════════ */
    public ExtendedPlayer(Player p) {
        this.player = p;

        /* async load – STAT */
        StatManagement.getAsync(p.getUniqueId(), p.getName())
                .thenAccept(s -> {
                    this.stat = s;
                    statLoaded = true;
                    checkReady();
                });

        /* async load – MAIN PROFILE */
        StatManagement.getMainAsync(p)
                .thenAccept(m -> {
                    this.mp = m;
                    mpLoaded = true;
                    checkReady();
                });
    }

    /* called after either async branch finishes */
    private void checkReady() {
        if (statLoaded && mpLoaded && !loadFuture.isDone()) {
            loadFuture.complete(null); // signal that everything is ready
        }
    }

    /* public helpers for the rest of the plugin */
    public CompletableFuture<Void> whenReady() {
        return loadFuture;
    }

    public boolean isReady() {
        return loadFuture.isDone();
    }

    /* ═══════════════════════ free on quit ════════════════════════ */
    public void freePlayer() {
        StatManagement.saveAndRemove(player.getUniqueId());
        cooldowns.clear();
        replyTarget = null;
        duelPrefs.clear(); // NEW
    }

    /* ────────────────────────────────────────── */
    /* NEW: per‑player “hide lobby players” flag */
    /* ────────────────────────────────────────── */
    private boolean hideLobbyPlayers = false; // default = OFF (players visible)

    public boolean isHideLobbyPlayers() {
        return hideLobbyPlayers;
    }

    public void setHideLobbyPlayers(boolean b) {
        hideLobbyPlayers = b;
    }

    /* ══════════════════════ getters / setters ═════════════════════ */
    public Player getPlayer() {
        return player;
    }

    public PlayerStat getStat() {
        return stat;
    }

    public MainPlayer getMainPlayer() {
        return mp;
    }

    public PlayerDuelInstance getPlayerDuelInstance() {
        return duelInstance;
    }

    public void setPlayerDuelInstance(PlayerDuelInstance duelInstance) {
        this.duelInstance = duelInstance;
    }

    /* cooldowns */
    public void addCooldown(String cmd, int seconds) {
        if (checkCooldown(cmd))
            return;
        cooldowns.put(cmd, System.currentTimeMillis() + seconds * 1000L);
    }

    public boolean checkCooldown(String cmd) {
        Long t = cooldowns.get(cmd);
        if (t == null)
            return false;
        if (System.currentTimeMillis() < t)
            return true;
        cooldowns.remove(cmd);
        return false;
    }

    /* misc toggles */
    public boolean isBypassRegion() {
        return bypassRegion;
    }

    public void setBypassRegion(boolean b) {
        bypassRegion = b;
    }

    public boolean isFly() {
        return fly;
    }

    public void setFly(boolean f) {
        fly = f;
    }

    public void setVanish(boolean v) {
        vanish = v;
    }

    public boolean isVanish() {
        return vanish;
    }

    /* /reply target */
    public Player getReplyTarget() {
        return replyTarget;
    }

    public void setReplyTarget(Player p) {
        replyTarget = p;
    }

    public HashMap<UUID, Integer> getSelectedKit() {
        return selectedKit;
    }

    public void setSelectedKit(HashMap<UUID, Integer> selectedKit) {
        this.selectedKit = selectedKit;
    }

    public void clearSpectatorStatus() {
        spectating = false;
        spectatingArena = null;
        spectatingBase = null;
    }

    public boolean hasPref(UUID opponent) {
        return duelPrefs.containsKey(opponent);
    }

    public void setDeathLocation(@NotNull Location location) {
        this.deathlocation = location;
    }

    public @NotNull Location getDeathlocation() {
        return deathlocation;
    }

    public void setLastFFADearthLocation(Location loc) {
        this.lastFFADearthLocation = loc;
    }

    public Location getLastFFADearthLocation() {
        return lastFFADearthLocation;
    }

    public void setLastFFAKitName(String kitName) {
        this.lastFFAKitName = kitName;
    }

    public String getLastFFAKitName() {
        return lastFFAKitName;
    }

    public void setLastFFAWorlName(String worldName) {
        this.lastFFAWorlName = worldName;
    }

    public String getLastFFAWorlName() {
        return lastFFAWorlName;
    }

    public int getDisplayTab() {
        return displayTab;
    }

    public void setDisplayTab(int displayTab) {
        this.displayTab = displayTab;
    }

    public void setRespawnToLobby(boolean respawnToLobby) {
        this.respawnToLobby = respawnToLobby;
    }

    public boolean getRespawnToLobby() {
        return respawnToLobby;
    }
}
