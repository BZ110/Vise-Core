package org.albi.core.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.albi.core.modules.CustomKit.helpers.CustomKit;
import org.albi.core.modules.Duels.ranked.RankedStats;
import org.albi.core.modules.FFA.helpers.KitInventoryEdits;

import java.util.*;

/**
 * Persistent profile shared across ALL minigames.
 *
 * • blocked   – mute / block list  (already used)
 * • level     – player’s account level
 * • xp        – experience toward next level
 * • kits[50]  – up to 50 CustomKit load‑outs shared by every module
 *
 *  XP curve: neededXP(level) = 10 + (level‑1) * 10  (tweak as desired)
 */
public final class MainPlayer {

    /* ── static helpers ─────────────────────────────────────────── */
    private static final Gson G = new GsonBuilder().serializeNulls().create();

    private int timePlayed = 0;           // total seconds online
    private boolean visibleStats = true;
    private boolean allowMessages = true;
    private boolean partyInvites = true;
    private boolean duelInvites = true;
    private boolean allowSpectators = true;
    private boolean partyDuelInvites = true;
    private boolean friendRequests = true;
    private boolean elytra = true;
    private boolean showKillEffects = true;
    private boolean lobbyJumpBoost = false;
    private boolean lobbySpeed = false;
    private boolean chatMentions = true;
    private boolean showRankedParticles = false;
    private Set<UUID> friends = new HashSet<>();
    private List<KitInventoryEdits> kitInventoryEdits = new ArrayList<>();
    // per-kit stats (kitName -> stats)
    private Map<String, FFAStats> ffaStats = new HashMap<>();
    private String selectedDeathEffect = "NONE";
    private Map<String, String> armorTrims = new HashMap<>();
    private RankedStats overallRankedStats = new RankedStats();
    private Map<String, RankedStats> kitRankedStats = new HashMap<>();

    /** Helper object that is (de)serialised.  */
    // Record now includes ffaStats and armorTrims
    private record Data(Set<UUID> blocked, int level, int xp,
                        int timePlayed,
                        boolean visibleStats,    boolean allowMessages,
                        boolean partyInvites,    boolean duelInvites,
                        boolean allowSpectators, boolean partyDuelInvites, boolean friendRequests, boolean elytra,
                        boolean lobbyJumpBoost, boolean lobbySpeed, boolean chatMentions,
                        Set<UUID> friends,
                        CustomKit[] kits,
                        List<KitInventoryEdits> kitInventoryEdits,
                        Map<String, FFAStats> ffaStats,
                        Map<String, String> armorTrims,
                        String selectedDeathEffect,
                        boolean showKillEffects,
                        RankedStats overallRankedStats,
                        Map<String, RankedStats> kitRankedStats,
                        boolean globalChat,
                        boolean broadcastChats,
                        boolean showRankedParticles) {}

    /* ── factory ────────────────────────────────────────────────── */
    static MainPlayer fromJson(UUID id, String json) {
        MainPlayer mp = new MainPlayer(id);
        if (json == null || json.isEmpty()) return mp;

        JsonElement tree = JsonParser.parseString(json);

        if (tree.isJsonArray()) {
            mp.blocked.addAll(Arrays.asList(G.fromJson(tree, UUID[].class)));
        } else {
            Data d = G.fromJson(tree, Data.class);
            if (d.blocked() != null)   mp.blocked.addAll(d.blocked());
            mp.level = d.level();
            mp.xp    = d.xp();
            mp.timePlayed = d.timePlayed();
            mp.visibleStats = d.visibleStats();
            mp.allowMessages = d.allowMessages();
            mp.partyInvites = d.partyInvites();
            mp.allowSpectators = d.allowSpectators();
            mp.duelInvites = d.duelInvites();
            mp.partyDuelInvites = d.partyDuelInvites();
            mp.friendRequests = tree.isJsonObject() && tree.getAsJsonObject().has("friendRequests") ? d.friendRequests() : true;
            mp.elytra = d.elytra();
            if (d.friends() != null) mp.friends.addAll(d.friends());
            mp.kitInventoryEdits = (d.kitInventoryEdits() != null)
                    ? new ArrayList<>(d.kitInventoryEdits())
                    : new ArrayList<>();

            mp.ffaStats = (d.ffaStats() != null) ? new HashMap<>(d.ffaStats()) : new HashMap<>();
            mp.armorTrims = (d.armorTrims() != null) ? new HashMap<>(d.armorTrims()) : new HashMap<>();
            if (d.kits() != null) {
                System.arraycopy(d.kits(), 0, mp.kits, 0, Math.min(50, d.kits().length));
            }
            if (d.selectedDeathEffect() != null) {
                mp.selectedDeathEffect = d.selectedDeathEffect();
            }
            mp.showKillEffects = d.showKillEffects();
            if (d.overallRankedStats() != null) mp.overallRankedStats = d.overallRankedStats();
            if (d.kitRankedStats() != null) mp.kitRankedStats = new HashMap<>(d.kitRankedStats());
            if (tree.isJsonObject() && tree.getAsJsonObject().has("globalChat")) {
                mp.globalChat = d.globalChat();
            } else {
                mp.globalChat = true; // default
            }
            if (tree.isJsonObject() && tree.getAsJsonObject().has("broadcastChats")) {
                mp.broadcastChats = d.broadcastChats();
            } else {
                mp.broadcastChats = true; // default
            }
            if (tree.isJsonObject() && tree.getAsJsonObject().has("lobbyJumpBoost")) {
                mp.lobbyJumpBoost = d.lobbyJumpBoost();
            } else {
                mp.lobbyJumpBoost = false;
            }
            if (tree.isJsonObject() && tree.getAsJsonObject().has("lobbySpeed")) {
                mp.lobbySpeed = d.lobbySpeed();
            } else {
                mp.lobbySpeed = false;
            }
            if (tree.isJsonObject() && tree.getAsJsonObject().has("chatMentions")) {
                mp.chatMentions = d.chatMentions();
            } else {
                mp.chatMentions = true;
            }
            if (tree.isJsonObject() && tree.getAsJsonObject().has("showRankedParticles")) {
                mp.showRankedParticles = d.showRankedParticles();
            } else {
                mp.showRankedParticles = false;
            }
        }
        mp.persistedRevision = mp.revision;
        return mp;
    }


    synchronized String toJson() {
        return G.toJson(new Data(blocked, level, xp, timePlayed, visibleStats, allowMessages,
                partyInvites, duelInvites, allowSpectators, partyDuelInvites, friendRequests, elytra,
                lobbyJumpBoost, lobbySpeed, chatMentions,
                friends, kits, kitInventoryEdits, ffaStats, armorTrims, selectedDeathEffect, showKillEffects,
                overallRankedStats, kitRankedStats, globalChat, broadcastChats, showRankedParticles));
    }

    /* ── state ──────────────────────────────────────────────────── */
    private final UUID uuid;
    private final Set<UUID> blocked = new HashSet<>();

    private int level = 1;
    private int xp    = 0;

    private CustomKit[] kits = new CustomKit[50];
    
    private boolean globalChat = true;
    private boolean broadcastChats = true;

    final Object writeLock = new Object();
    private long revision = 1; // New profiles have not been persisted.
    private long persistedRevision;

    public MainPlayer(UUID uuid) {
        this.uuid = uuid;
    }

    /* ── public API : core stats ─────────────────────────────────── */
    public UUID uuid()                 { return uuid; }
    public int  level()                { return level; }
    public int  xp()                   { return xp; }
    public int  xpNeeded()             { return neededXP(level); }

    public synchronized void addXp(int amount) {
        if (amount <= 0) return;
        xp += amount;
        while (xp >= neededXP(level)) {
            xp -= neededXP(level);
            level++;
        }
        revision++;
    }

    public synchronized void setLevelAndXp(int newLevel, int newXp) {
        level = Math.max(1, newLevel);
        xp    = Math.max(0, newXp);
        revision++;
    }

    /* ── block list helpers ─────────────────────────────────────── */
    public synchronized boolean addBlock(UUID target) {
        if (blocked.add(target)) { revision++; return true; }
        return false;
    }
    public synchronized boolean removeBlock(UUID target) {
        if (blocked.remove(target)) { revision++; return true; }
        return false;
    }
    public Set<UUID> getBlockedList() {
        return Collections.unmodifiableSet(blocked);
    }

    /* ── kit helpers ─────────────────────────────────────────────── */
    public CustomKit[] getKits() {
        return kits;
    }

    public CustomKit getKit(int index) {
        if (index < 0 || index >= 50) throw new IndexOutOfBoundsException("Kit index must be 0‑49");
        return kits[index];
    }

    public synchronized void setKit(int index, CustomKit kit) {
        if (index < 0 || index >= 50) throw new IndexOutOfBoundsException("Kit index must be 0‑49");
        kits[index] = kit;
        revision++;
    }

    /* ── dirty tracking ─────────────────────────────────────────── */
    synchronized boolean isDirty() { return revision != persistedRevision; }
    synchronized long revision() { return revision; }
    synchronized void markPersisted(long snapshot) { persistedRevision = snapshot; }

    /* ── private helpers ────────────────────────────────────────── */
    private static int neededXP(int lvl) {
        return 10 + (lvl - 1) * 10;
    }


    /* ── state ───────────────────────────────────────────── */

    public int  timePlayed()            { return timePlayed; }
    public synchronized void addTimePlayed(int sec)  {           // safe‑guard
        if (sec <= 0) return;
        timePlayed += sec;
        revision++;
    }

    public boolean elytra() { return elytra; }

    public synchronized void setElytra(boolean elytra) {
        this.elytra = elytra;
        revision++;
    }

    public String getDeathEffect() {
        return selectedDeathEffect;
    }

    public synchronized void setDeathEffect(String effect) {
        this.selectedDeathEffect = effect == null ? "NONE" : effect;
        revision++;
    }

    public boolean showKillEffects() { return showKillEffects; }
    public synchronized void setShowKillEffects(boolean show) {
        this.showKillEffects = show;
        revision++;
    }

    public boolean showRankedParticles() { return showRankedParticles; }
    public synchronized void setShowRankedParticles(boolean show) {
        this.showRankedParticles = show;
        revision++;
    }
    
    public boolean lobbyJumpBoost() { return lobbyJumpBoost; }
    public synchronized void setLobbyJumpBoost(boolean lobbyJumpBoost) {
        this.lobbyJumpBoost = lobbyJumpBoost;
        revision++;
    }

    public boolean lobbySpeed() { return lobbySpeed; }
    public synchronized void setLobbySpeed(boolean lobbySpeed) {
        this.lobbySpeed = lobbySpeed;
        revision++;
    }

    public boolean chatMentions() { return chatMentions; }
    public synchronized void setChatMentions(boolean chatMentions) {
        this.chatMentions = chatMentions;
        revision++;
    }

    public synchronized void setDuelInvites(boolean duelInvites) {
        this.duelInvites = duelInvites;
        revision++;
    }

    public boolean duelInvites() {
        return duelInvites;
    }

    public boolean allowSpectators() {
        return allowSpectators;
    }

    public synchronized void setAllowSpectators(boolean allowSpectators) {
        this.allowSpectators = allowSpectators;
        revision++;
    }

    public boolean isGlobalChat() {
        return globalChat;
    }

    public synchronized void setGlobalChat(boolean globalChat) {
        this.globalChat = globalChat;
        revision++;
    }

    public boolean isBroadcastChats() {
        return broadcastChats;
    }

    public synchronized void setBroadcastChats(boolean broadcastChats) {
        this.broadcastChats = broadcastChats;
        revision++;
    }

    public boolean partyDuelInvites() {
        return partyDuelInvites;
    }

    public synchronized void setPartyDuelInvites(boolean partyDuelInvites) {
        this.partyDuelInvites = partyDuelInvites;
        revision++;
    }





    public boolean friendRequests() {
        return friendRequests;
    }

    public synchronized void setFriendRequests(boolean friendRequests) {
        this.friendRequests = friendRequests;
        revision++;
    }

    public boolean isFriendsWith(UUID target) {
        return target != null && friends.contains(target);
    }

    public Set<UUID> getFriends() {
        return Collections.unmodifiableSet(friends);
    }

    public synchronized boolean addFriend(UUID target) {
        if (target == null || uuid.equals(target)) return false;
        boolean added = friends.add(target);
        if (added) revision++;
        return added;
    }

    public synchronized boolean removeFriend(UUID target) {
        boolean removed = target != null && friends.remove(target);
        if (removed) revision++;
        return removed;
    }


    public synchronized void setPartyInvites(boolean partyInvites) {
        this.partyInvites = partyInvites;
        revision++;
    }

    public boolean partyInvites() {
        return partyInvites;
    }
    public boolean visibleStats() {
        return visibleStats;
    }
    public List<KitInventoryEdits> kitInventoryEdits() {
        return kitInventoryEdits;
    }

    public synchronized void setVisibleStats(boolean visible) {
        this.visibleStats = visible;
        revision++;
    }

    public boolean allowMessages() {
        return allowMessages;
    }

    public synchronized void setAllowMessages(boolean visible) {
        this.allowMessages = visible;
        revision++;
    }

    public Map<String, String> getArmorTrims() {
        if (armorTrims == null) armorTrims = new HashMap<>();
        return armorTrims;
    }

    public synchronized void setArmorTrim(String slot, String comboId) {
        getArmorTrims().put(slot, comboId);
        revision++;
    }
    
    public synchronized void clearArmorTrim(String slot) {
        getArmorTrims().remove(slot);
        revision++;
    }



    // getters
    public List<KitInventoryEdits> getKitInventoryEdits() {
        return Collections.unmodifiableList(kitInventoryEdits);
    }

    // convenience lookups
    public KitInventoryEdits getKitEdit(String kitName) {
        if (kitName == null || kitName.isEmpty()) return null;
        if (kitInventoryEdits == null) kitInventoryEdits = new ArrayList<>(); // safety
        for (KitInventoryEdits e : kitInventoryEdits) {
            if (e != null && e.getKitName() != null && e.getKitName().equalsIgnoreCase(kitName)) {
                return e;
            }
        }
        return null;
    }


    // upsert/remove (mark profile dirty so your save loop catches it)
    public synchronized void putKitEdit(KitInventoryEdits edit) {
        kitInventoryEdits.removeIf(e -> e.getKitName().equalsIgnoreCase(edit.getKitName()));
        kitInventoryEdits.add(edit);
        revision++; // <-- keep your existing pattern
    }

    public synchronized boolean removeKitEdit(String kitName) {
        boolean changed = kitInventoryEdits.removeIf(e -> e.getKitName().equalsIgnoreCase(kitName));
        if (changed) revision++;
        return changed;
    }

    /* ── FFA stats helpers ─────────────────────────────────────────── */
    private FFAStats getOrCreateFFAStats(String kitName) {
        if (kitName == null || kitName.isEmpty()) return null;
        return ffaStats.computeIfAbsent(kitName.toLowerCase(Locale.ROOT), k -> new FFAStats());
    }

    public FFAStats getFFAStats(String kitName) {
        if (kitName == null) return null;
        return ffaStats.get(kitName.toLowerCase(Locale.ROOT));
    }

    public synchronized void addFFAKill(String kitName) {
        FFAStats s = getOrCreateFFAStats(kitName);
        if (s == null) return;
        s.incKill();
        revision++;
    }

    public synchronized void addFFADeath(String kitName) {
        FFAStats s = getOrCreateFFAStats(kitName);
        if (s == null) return;
        s.incDeath();
        revision++;
    }

    /**
     * Returns the player's custom layout for this kit if it exists.
     * Otherwise creates a DEFAULT snapshot that mirrors the canonical kit contents
     * (slot -> item) and stores it, then returns it (never null).
     *
     * @param kitName    the kit name
     * @param canonical  the canonical kit contents (slot -> SerializableItem), including armor/offhand slots if you use 36–40
     */
    public synchronized KitInventoryEdits getKitEditOrDefault(String kitName, Map<Integer, org.albi.core.serializable.SerializableItem> canonical) {
        if (kitInventoryEdits == null) kitInventoryEdits = new ArrayList<>(); // safety

        KitInventoryEdits existing = getKitEdit(kitName);
        if (existing != null) return existing;

        // Build default snapshot = canonical kit
        KitInventoryEdits def = new KitInventoryEdits(kitName);
        if (canonical != null && !canonical.isEmpty()) {
            // copy over canonical slots to the snapshot
            for (Map.Entry<Integer, org.albi.core.serializable.SerializableItem> e : canonical.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                def.getInventory().put(e.getKey(), e.getValue());
            }
        }
        // store & mark dirty so it persists
        kitInventoryEdits.add(def);
        revision++;
        return def;
    }


    public int getFFAKills(String kitName)     { FFAStats s = getFFAStats(kitName); return s == null ? 0 : s.getKills(); }
    public int getFFADeaths(String kitName)    { FFAStats s = getFFAStats(kitName); return s == null ? 0 : s.getDeaths(); }
    public int getFFAWinstreak(String kitName) { FFAStats s = getFFAStats(kitName); return s == null ? 0 : s.getWinStreak(); }

    /* ── Ranked stats helpers ─────────────────────────────────────── */
    public synchronized RankedStats getOverallRankedStats() {
        if (overallRankedStats == null) overallRankedStats = new RankedStats();
        return overallRankedStats;
    }

    public synchronized RankedStats getKitRankedStats(String kitName) {
        if (kitName == null) return null;
        return kitRankedStats.computeIfAbsent(kitName.toLowerCase(Locale.ROOT), k -> new RankedStats());
    }

    public synchronized void markRankedDirty() {
        this.revision++;
    }


    // NEW: org.albi.core.data.MainPlayer.FFAStats
    public static final class FFAStats implements java.io.Serializable {
        private int kills;
        private int deaths;
        private int winStreak;

        public int  getKills()     { return kills; }
        public int  getDeaths()    { return deaths; }
        public int  getWinStreak() { return winStreak; }

        public void incKill()      { kills++; winStreak++; }
        public void incDeath()     { deaths++; winStreak = 0; }
    }


}
