package org.albi.core.modules.Party.helpers;

import org.albi.core.util.ConfigValues;

import org.albi.core.Core;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class Party {
    private final UUID leader;
    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> invites = new HashSet<>();
    private final int max;

    private boolean shuffle = false;
    private boolean autoBalance = true;
    private boolean open = false;
    private long lastClosedTimestamp = 0;

    public Party(Player creator) {
        this(creator, new org.bukkit.configuration.MemoryConfiguration());
    }

    public Party(Player creator, org.bukkit.configuration.ConfigurationSection config) {
        this.leader = creator.getUniqueId();
        this.members.add(leader);

        shuffle = config.getBoolean("party.default_shuffle", false);
        autoBalance = config.getBoolean("party.default_auto_balance", true);
        int detectedMax = ConfigValues.integer(config, "party.default_size", 4, 2, 100);
        for (PermissionAttachmentInfo perm : creator.getEffectivePermissions()) {
            String node = perm.getPermission();
            if (node.startsWith("core.party.size.")) {
                try {
                    int size = Integer.parseInt(node.substring("core.party.size.".length()));
                    if (size > detectedMax) detectedMax = size;
                } catch (NumberFormatException ignored) {}
            }
        }
        this.max = detectedMax;
    }

    public OfflinePlayer getLeaderPlayer() { return Bukkit.getOfflinePlayer(leader); }
    public void broadcast(Core plugin, String langKey, String[] repl) {
        for (UUID id : members) {
            Player online = Bukkit.getPlayer(id);
            if (online != null) plugin.getMessageAPI().sendMessageLang(online, langKey, repl == null ? new String[]{} : repl);
        }
    }

    public boolean isFull() { return members.size() >= max; }
    public boolean isLeader(Player p) { return leader.equals(p.getUniqueId()); }
    public Set<UUID> getInvites() { return invites; }
    public Set<UUID> getMembers() { return members; }
    public UUID getLeader() { return leader; }
    public int getMax() { return max; }

    public boolean isShuffle() { return shuffle; }
    public void setShuffle(boolean shuffle) { this.shuffle = shuffle; }
    public void toggleShuffle() { this.shuffle = !this.shuffle; }

    public boolean isAutoBalance() { return autoBalance; }
    public void setAutoBalance(boolean autoBalance) { this.autoBalance = autoBalance; }
    public void toggleAutoBalance() { this.autoBalance = !this.autoBalance; }

    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public long getLastClosedTimestamp() { return lastClosedTimestamp; }
    public void setLastClosedTimestamp(long lastClosedTimestamp) { this.lastClosedTimestamp = lastClosedTimestamp; }
}
