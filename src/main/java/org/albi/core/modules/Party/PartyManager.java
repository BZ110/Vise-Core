package org.albi.core.modules.Party;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.modules.Module;
import org.albi.core.modules.Friends.FriendsManager;
import org.albi.core.modules.ModuleManager;
import org.albi.core.modules.Party.managers.PartyCommandManager;
import org.albi.core.modules.Party.helpers.Party;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public final class PartyManager extends Module {

    private final Map<UUID, Party> partyOfPlayer = new HashMap<>();
    private static final List<String> READY_SUBTITLES = List.of(
            "§7The squad forms under your banner.",
            "§7A storm is coming. You started it.",
            "§7The lobby just got more dangerous.",
            "§7Steel up. Your crew is online.",
            "§7No weak links. No slow starts.",
            "§7The hunt begins now.",
            "§7Built different. Fight together.",
            "§7The war room is open.",
            "§7You brought the cavalry.",
            "§7More than a party. A problem.",
            "§7This squad was made for damage.",
            "§7Pressure rises the second you queue.",
            "§7You lit the fuse. Now move.",
            "§7Strength in numbers. Fear in everyone else.",
            "§7Locked in. Dialed up.",
            "§7A clean roster. A dirty outcome.",
            "§7This is where momentum starts.",
            "§7You did not come here to play safe.",
            "§7The room got louder for a reason.",
            "§7A proper unit has assembled."
    );
    private final Random random = new Random();

    public PartyManager(Core plugin) { super(plugin); }

    @Override public void init() {
        getPlugin().getLogger().info("[Party] Initializing");
        new PartyCommandManager(getPlugin(), this);
        getPlugin().setPartyManager(this);
    }

    public Party getParty(Player p) { return partyOfPlayer.get(p.getUniqueId()); }
    public boolean isInParty(Player p) { return getParty(p) != null; }
    public boolean isLeader(Player p) { return isInParty(p) && getParty(p).isLeader(p); }
    public Collection<UUID> getMembers(Player p) { return isInParty(p) ? getParty(p).getMembers() : List.of(); }

    public Party createParty(Player creator) {
        if (isInParty(creator)) {
            getPlugin().getMessageAPI().sendMessageLang(creator, "party_already_in");
            return getParty(creator);
        }

        var extendedPlayer = getPlugin().getExtendedPlayer(creator);
        if (extendedPlayer != null && extendedPlayer.getPlayerDuelInstance() != null) {
            getPlugin().getMessageAPI().sendMessage(creator, "&cYou cannot create a party while you are in a duel.");
            return null;
        }

        Party party = new Party(creator);
        partyOfPlayer.put(creator.getUniqueId(), party);

        getPlugin().getMessageAPI().sendMessageLang(creator, "party_created");
        creator.sendTitle("§6§lParty Created", READY_SUBTITLES.get(random.nextInt(READY_SUBTITLES.size())), 5, 35, 10);
        giveLobbyPartyItems(creator, true);
        return party;
    }

    public void invite(Player leader, Player target) {
        Party party = getParty(leader);
        if (party == null || !party.isLeader(leader)) {
            getPlugin().getMessageAPI().sendMessageLang(leader, "party_not_leader");
            return;
        }
        if (isInParty(target)) {
            getPlugin().getMessageAPI().sendMessageLang(leader, "party_invite_fail_inparty");
            return;
        }
        if (party.isFull()) {
            getPlugin().getMessageAPI().sendMessageLang(leader, "party_full");
            return;
        }
        FriendsManager friends = ModuleManager.getModule(FriendsManager.class);
        boolean friendBypass = friends != null && friends.isFriends(leader, target);
        if (getPlugin().getExtendedPlayer(target).getMainPlayer().getBlockedList().contains(leader.getUniqueId()) || 
            (!friendBypass && !getPlugin().getExtendedPlayer(target).getMainPlayer().partyInvites())) {
            getPlugin().getMessageAPI().sendMessageLang(leader, "party_blocked");
            return;
        }
        if (!party.getInvites().add(target.getUniqueId())) {
            getPlugin().getMessageAPI().sendMessageLang(leader, "party_invite_already");
            return;
        }

        getPlugin().getMessageAPI().sendMessageLang(leader, "party_invite_sent", new String[]{target.getName()});
        getPlugin().getMessageAPI().sendCommandMessageLang(target, "party_invite_receive", new String[]{leader.getName()}, "/party accept");
    }

    public void accept(Player target) {
        Party party = partyOfPlayer.values().stream()
                .filter(p -> p.getInvites().contains(target.getUniqueId()))
                .findFirst().orElse(null);

        if (party == null) {
            getPlugin().getMessageAPI().sendMessageLang(target, "party_not_invited");
            return;
        }
        if (party.isFull()) {
            getPlugin().getMessageAPI().sendMessageLang(target, "party_full");
            return;
        }

        party.getInvites().remove(target.getUniqueId());
        party.getMembers().add(target.getUniqueId());
        partyOfPlayer.put(target.getUniqueId(), party);

        party.broadcast(getPlugin(), "party_join", new String[]{target.getName()});
        OfflinePlayer leader = Bukkit.getOfflinePlayer(party.getLeader());
        getPlugin().getMessageAPI().sendMessageLang(target, "party_joined", new String[]{leader.getName() == null ? "Unknown" : leader.getName()});
        giveLobbyPartyItems(target, false);

        Player onlineLeader = Bukkit.getPlayer(party.getLeader());
        if (onlineLeader != null) giveLobbyPartyItems(onlineLeader, true);
    }

    public void list(Player player) {
        Party party = getParty(player);
        if (party == null) {
            getPlugin().getMessageAPI().sendMessageLang(player, "party_none");
            return;
        }
        getPlugin().getMessageAPI().sendMessage(player, "&7Party Members &8(&b" + party.getMembers().size() + "&7/&b" + party.getMax() + "&8):");
        for (UUID id : party.getMembers()) {
            String name = Bukkit.getOfflinePlayer(id).getName();
            String prefix = id.equals(party.getLeader()) ? "&6(Leader) " : "  ";
            getPlugin().getMessageAPI().sendMessage(player, prefix + "&f" + name);
        }
    }

    public void leave(Player player) {
        Party party = getParty(player);
        if (party == null) {
            getPlugin().getMessageAPI().sendMessageLang(player, "party_none");
            return;
        }
        party.getMembers().remove(player.getUniqueId());
        partyOfPlayer.remove(player.getUniqueId());

        if (player.getUniqueId().equals(party.getLeader())) {
            disband(party);
            return;
        }

        party.broadcast(getPlugin(), "party_leave", new String[]{player.getName()});
        getPlugin().getMessageAPI().sendMessageLang(player, "party_left");
        restoreLobbyItems(player);

        Player leader = Bukkit.getPlayer(party.getLeader());
        if (leader != null) giveLobbyPartyItems(leader, true);
    }

    public void disband(Party p) {
        for (UUID id : p.getMembers()) {
            partyOfPlayer.remove(id);
            Player online = Bukkit.getPlayer(id);
            if (online != null) restoreLobbyItems(online);
        }
        p.broadcast(getPlugin(), "party_disband", null);
    }

    public void toggleOpen(Player player) {
        if (!player.hasPermission("core.openparty")) {
            getPlugin().getMessageAPI().sendMessageLang(player, "no_permission");
            return;
        }

        Party party = getParty(player);
        if (party == null || !party.isLeader(player)) {
            getPlugin().getMessageAPI().sendMessageLang(player, "party_not_leader");
            return;
        }

        if (!party.isOpen()) {
            // Opening the party
            long now = System.currentTimeMillis();
            long cooldownMillis = 60 * 1000L;
            if (now - party.getLastClosedTimestamp() < cooldownMillis && !player.hasPermission("core.openparty_cooldownbypass")) {
                long remainingSeconds = (cooldownMillis - (now - party.getLastClosedTimestamp())) / 1000;
                getPlugin().getMessageAPI().sendMessageLang(player, "party_open_cooldown", new String[]{String.valueOf(remainingSeconds)});
                return;
            }

            party.setOpen(true);
            getPlugin().getMessageAPI().sendMessageLang(player, "party_opened");

            // Broadcast to lobby
            String lobbyWorldName = getPlugin().getConfig().getString("lobby_world", "world");
            World lobbyWorld = Bukkit.getWorld(lobbyWorldName);
            if (lobbyWorld != null) {
                for (Player p : lobbyWorld.getPlayers()) {
                    getPlugin().getMessageAPI().sendCommandMessageLang(p, "party_opened_broadcast", new String[]{player.getName()}, "/party join " + player.getName());
                }
            }
        } else {
            // Closing the party
            party.setOpen(false);
            party.setLastClosedTimestamp(System.currentTimeMillis());
            getPlugin().getMessageAPI().sendMessageLang(player, "party_privated");
        }
    }

    public void join(Player player, String leaderName) {
        if (isInParty(player)) {
            getPlugin().getMessageAPI().sendMessageLang(player, "party_already_in");
            return;
        }

        Player leader = Bukkit.getPlayer(leaderName);
        if (leader == null || getPlugin().getExtendedPlayer(leader).getMainPlayer().getBlockedList().contains(player.getUniqueId())) {
            getPlugin().getMessageAPI().sendMessageLang(player, "player_not_found");
            return;
        }

        Party party = getParty(leader);
        if (party == null || !party.isLeader(leader)) {
            getPlugin().getMessageAPI().sendMessageLang(player, "party_none");
            return;
        }

        if (!party.isOpen()) {
            getPlugin().getMessageAPI().sendMessageLang(player, "party_join_not_open");
            return;
        }

        if (party.isFull()) {
            getPlugin().getMessageAPI().sendMessageLang(player, "party_full");
            return;
        }

        party.getMembers().add(player.getUniqueId());
        partyOfPlayer.put(player.getUniqueId(), party);

        party.broadcast(getPlugin(), "party_join", new String[]{player.getName()});
        getPlugin().getMessageAPI().sendMessageLang(player, "party_joined", new String[]{leader.getName()});
        giveLobbyPartyItems(player, false);

        if (leader.isOnline()) giveLobbyPartyItems(leader, true);
    }

    public void openPartySettings(Player player) {
        Party party = getParty(player);
        if (party == null) {
            getPlugin().getMessageAPI().sendMessageLang(player, "party_none");
            return;
        }

        SGMenu menu = getPlugin().getGUI().create("§6§lParty Settings", 3);
        menu.setButton(10, new SGButton(named(Material.COMPARATOR,
                "§b§lShuffle Teams: " + (party.isShuffle() ? "§aON" : "§cOFF"),
                List.of("§7Shuffle the draft order for auto-fill actions.", "§fClick to toggle.")))
                .withListener(e -> {
                    if (!party.isLeader((Player) e.getWhoClicked())) {
                        e.getWhoClicked().sendMessage("§cOnly the party leader can change party settings.");
                        return;
                    }
                    party.toggleShuffle();
                    openPartySettings((Player) e.getWhoClicked());
                }));

        menu.setButton(12, new SGButton(named(Material.TARGET,
                "§d§lAuto Balance: " + (party.isAutoBalance() ? "§aON" : "§cOFF"),
                List.of("§7Use even fill as the default draft helper.", "§fClick to toggle.")))
                .withListener(e -> {
                    if (!party.isLeader((Player) e.getWhoClicked())) {
                        e.getWhoClicked().sendMessage("§cOnly the party leader can change party settings.");
                        return;
                    }
                    party.toggleAutoBalance();
                    openPartySettings((Player) e.getWhoClicked());
                }));

        menu.setButton(14, new SGButton(named(Material.EMERALD,
                "§a§lParty Play",
                List.of("§7Open the team builder and setup menu.", "§fClick to continue.")))
                .withListener(e -> ((Player) e.getWhoClicked()).performCommand("party play")));

        menu.setButton(16, new SGButton(named(Material.PLAYER_HEAD,
                "§b§lRoster",
                List.of("§7View your party members in chat.", "§fClick to list members.")))
                .withListener(e -> ((Player) e.getWhoClicked()).performCommand("party list")));

        menu.setButton(22, new SGButton(named(Material.ARROW,
                "§e§lBack",
                List.of("§7Return to your party hotbar.")))
                .withListener(e -> {
                    Player clicker = (Player) e.getWhoClicked();
                    giveLobbyPartyItems(clicker, isLeader(clicker));
                    clicker.closeInventory();
                }));

        player.openInventory(menu.getInventory());
    }

    public void giveLobbyPartyItems(Player player, boolean leader) {
        if (player == null) return;
        String lobbyWorld = getPlugin().getConfig().getString("lobby_world");
        if (lobbyWorld == null || !player.getWorld().getName().equalsIgnoreCase(lobbyWorld)) return;

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);

        ItemStack play = named(Material.EMERALD, "§a§lPlay", List.of("§7Open party play.", "§fClick to build teams or launch Party FFA."));
        ItemStack list = named(Material.PLAYER_HEAD, "§b§lMembers", List.of("§7View your current party roster."));
        if (list.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
            list.setItemMeta(skull);
        }
        ItemStack help = named(Material.NAME_TAG, "§d§lInvite Help", List.of("§7Use §f/party invite <player>§7.", "§7Quick reminder for inviting teammates."));
        ItemStack settings = named(Material.COMPARATOR, "§6§lParty Settings", List.of("§7Shuffle, balancing, and party controls.", "§fRight click to manage your party."));
        ItemStack end = named(Material.BARRIER, leader ? "§c§lDisband Party" : "§c§lLeave Party", List.of(leader ? "§7End the whole party instantly." : "§7Leave your current party."));

        player.getInventory().setItem(0, play);
        player.getInventory().setItem(1, list);
        player.getInventory().setItem(2, help);
        player.getInventory().setItem(7, settings);
        player.getInventory().setItem(8, end);
        player.updateInventory();
    }

    public void restoreLobbyItems(Player player) {
        if (player == null) return;
        String lobbyWorld = getPlugin().getConfig().getString("lobby_world");
        if (lobbyWorld == null || !player.getWorld().getName().equalsIgnoreCase(lobbyWorld)) return;
        getPlugin().giveAppropriateLobbyItems(player);
        player.updateInventory();
    }

    private ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
