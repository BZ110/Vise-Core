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
        mp.dirty = false;
        return mp;
    }


    String toJson() {
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

    private boolean dirty = true;          // new objects start dirty

    public MainPlayer(UUID uuid) {
        this.uuid = uuid;
    }

    /* ── public API : core stats ─────────────────────────────────── */
    public UUID uuid()                 { return uuid; }
    public int  level()                { return level; }
    public int  xp()                   { return xp; }
    public int  xpNeeded()             { return neededXP(level); }

    public void addXp(int amount) {
        if (amount <= 0) return;
        xp += amount;
        while (xp >= neededXP(level)) {
            xp -= neededXP(level);
            level++;
        }
        dirty = true;
    }

    public void setLevelAndXp(int newLevel, int newXp) {
        level = Math.max(1, newLevel);
        xp    = Math.max(0, newXp);
        dirty = true;
    }

    /* ── block list helpers ─────────────────────────────────────── */
    public boolean addBlock(UUID target) {
        if (blocked.add(target)) { dirty = true; return true; }
        return false;
    }
    public boolean removeBlock(UUID target) {
        if (blocked.remove(target)) { dirty = true; return true; }
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

    public void setKit(int index, CustomKit kit) {
        if (index < 0 || index >= 50) throw new IndexOutOfBoundsException("Kit index must be 0‑49");
        kits[index] = kit;
        dirty = true;
    }

    /* ── dirty tracking ─────────────────────────────────────────── */
    boolean isDirty()     { return dirty; }
    void    clearDirty()  { dirty = false; }

    /* ── private helpers ────────────────────────────────────────── */
    private static int neededXP(int lvl) {
        return 10 + (lvl - 1) * 10;
    }


    /* ── state ───────────────────────────────────────────── */

    public int  timePlayed()            { return timePlayed; }
    public void addTimePlayed(int sec)  {           // safe‑guard
        if (sec <= 0) return;
        timePlayed += sec;
        dirty = true;
    }

    public boolean elytra() { return elytra; }

    public void setElytra(boolean elytra) {
        this.elytra = elytra;
        dirty = true;
    }

    public String getDeathEffect() {
        return selectedDeathEffect;
    }

    public void setDeathEffect(String effect) {
        this.selectedDeathEffect = effect == null ? "NONE" : effect;
        dirty = true;
    }

    public boolean showKillEffects() { return showKillEffects; }
    public void setShowKillEffects(boolean show) {
        this.showKillEffects = show;
        dirty = true;
    }

    public boolean showRankedParticles() { return showRankedParticles; }
    public void setShowRankedParticles(boolean show) {
        this.showRankedParticles = show;
        dirty = true;
    }
    
    public boolean lobbyJumpBoost() { return lobbyJumpBoost; }
    public void setLobbyJumpBoost(boolean lobbyJumpBoost) {
        this.lobbyJumpBoost = lobbyJumpBoost;
        dirty = true;
    }

    public boolean lobbySpeed() { return lobbySpeed; }
    public void setLobbySpeed(boolean lobbySpeed) {
        this.lobbySpeed = lobbySpeed;
        dirty = true;
    }

    public boolean chatMentions() { return chatMentions; }
    public void setChatMentions(boolean chatMentions) {
        this.chatMentions = chatMentions;
        dirty = true;
    }

    public void setDuelInvites(boolean duelInvites) {
        this.duelInvites = duelInvites;
        dirty = true;
    }

    public boolean duelInvites() {
        return duelInvites;
    }

    public boolean allowSpectators() {
        return allowSpectators;
    }

    public void setAllowSpectators(boolean allowSpectators) {
        this.allowSpectators = allowSpectators;
        dirty = true;
    }

    public boolean isGlobalChat() {
        return globalChat;
    }

    public void setGlobalChat(boolean globalChat) {
        this.globalChat = globalChat;
        dirty = true;
    }

    public boolean isBroadcastChats() {
        return broadcastChats;
    }

    public void setBroadcastChats(boolean broadcastChats) {
        this.broadcastChats = broadcastChats;
        dirty = true;
    }

    public boolean partyDuelInvites() {
        return partyDuelInvites;
    }

    public void setPartyDuelInvites(boolean partyDuelInvites) {
        this.partyDuelInvites = partyDuelInvites;
        dirty = true;
    }





    public boolean friendRequests() {
        return friendRequests;
    }

    public void setFriendRequests(boolean friendRequests) {
        this.friendRequests = friendRequests;
        dirty = true;
    }

    public boolean isFriendsWith(UUID target) {
        return target != null && friends.contains(target);
    }

    public Set<UUID> getFriends() {
        return Collections.unmodifiableSet(friends);
    }

    public boolean addFriend(UUID target) {
        if (target == null || uuid.equals(target)) return false;
        boolean added = friends.add(target);
        if (added) dirty = true;
        return added;
    }

    public boolean removeFriend(UUID target) {
        boolean removed = target != null && friends.remove(target);
        if (removed) dirty = true;
        return removed;
    }


    public void setPartyInvites(boolean partyInvites) {
        this.partyInvites = partyInvites;
        dirty = true;
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

    public void setVisibleStats(boolean visible) {
        this.visibleStats = visible;
        dirty = true;
    }

    public boolean allowMessages() {
        return allowMessages;
    }

    public void setAllowMessages(boolean visible) {
        this.allowMessages = visible;
        dirty = true;
    }

    public Map<String, String> getArmorTrims() {
        if (armorTrims == null) armorTrims = new HashMap<>();
        return armorTrims;
    }

    public void setArmorTrim(String slot, String comboId) {
        getArmorTrims().put(slot, comboId);
        dirty = true;
    }
    
    public void clearArmorTrim(String slot) {
        getArmorTrims().remove(slot);
        dirty = true;
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
    public void putKitEdit(KitInventoryEdits edit) {
        kitInventoryEdits.removeIf(e -> e.getKitName().equalsIgnoreCase(edit.getKitName()));
        kitInventoryEdits.add(edit);
        dirty = true; // <-- keep your existing pattern
    }

    public boolean removeKitEdit(String kitName) {
        boolean changed = kitInventoryEdits.removeIf(e -> e.getKitName().equalsIgnoreCase(kitName));
        if (changed) dirty = true;
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

    public void addFFAKill(String kitName) {
        FFAStats s = getOrCreateFFAStats(kitName);
        if (s == null) return;
        s.incKill();
        dirty = true;
    }

    public void addFFADeath(String kitName) {
        FFAStats s = getOrCreateFFAStats(kitName);
        if (s == null) return;
        s.incDeath();
        dirty = true;
    }

    /**
     * Returns the player's custom layout for this kit if it exists.
     * Otherwise creates a DEFAULT snapshot that mirrors the canonical kit contents
     * (slot -> item) and stores it, then returns it (never null).
     *
     * @param kitName    the kit name
     * @param canonical  the canonical kit contents (slot -> SerializableItem), including armor/offhand slots if you use 36–40
     */
    public KitInventoryEdits getKitEditOrDefault(String kitName, Map<Integer, org.albi.core.serializable.SerializableItem> canonical) {
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
        dirty = true;
        return def;
    }


    public int getFFAKills(String kitName)     { FFAStats s = getFFAStats(kitName); return s == null ? 0 : s.getKills(); }
    public int getFFADeaths(String kitName)    { FFAStats s = getFFAStats(kitName); return s == null ? 0 : s.getDeaths(); }
    public int getFFAWinstreak(String kitName) { FFAStats s = getFFAStats(kitName); return s == null ? 0 : s.getWinStreak(); }

    /* ── Ranked stats helpers ─────────────────────────────────────── */
    public RankedStats getOverallRankedStats() {
        if (overallRankedStats == null) overallRankedStats = new RankedStats();
        return overallRankedStats;
    }

    public RankedStats getKitRankedStats(String kitName) {
        if (kitName == null) return null;
        return kitRankedStats.computeIfAbsent(kitName.toLowerCase(Locale.ROOT), k -> new RankedStats());
    }

    public void markRankedDirty() {
        this.dirty = true;
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
