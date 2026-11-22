package org.albi.core.modules.Party;

import org.albi.core.util.ConfigValues;

import org.albi.core.gui.Messages;

import org.albi.core.gui.GuiService;

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

import java.util.*;

public final class PartyManager extends Module {

    private final Map<UUID, Party> partyOfPlayer = new HashMap<>();
    private static final List<String> READY_SUBTITLES = List.of(
            GuiService.text("party-manager.the-squad-forms-under-your-banner"),
            GuiService.text("party-manager.a-storm-is-coming-you-started-it"),
            GuiService.text("party-manager.the-lobby-just-got-more-dangerous"),
            GuiService.text("party-manager.steel-up-your-crew-is-online"),
            GuiService.text("party-manager.no-weak-links-no-slow-starts"),
            GuiService.text("party-manager.the-hunt-begins-now"),
            GuiService.text("party-manager.built-different-fight-together"),
            GuiService.text("party-manager.the-war-room-is-open"),
            GuiService.text("party-manager.you-brought-the-cavalry"),
            GuiService.text("party-manager.more-than-a-party-a-problem"),
            GuiService.text("party-manager.this-squad-was-made-for-damage"),
            GuiService.text("party-manager.pressure-rises-the-second-you-queue"),
            GuiService.text("party-manager.you-lit-the-fuse-now-move"),
            GuiService.text("party-manager.strength-in-numbers-fear-in-everyone-else"),
            GuiService.text("party-manager.locked-in-dialed-up"),
            GuiService.text("party-manager.a-clean-roster-a-dirty-outcome"),
            GuiService.text("party-manager.this-is-where-momentum-starts"),
            GuiService.text("party-manager.you-did-not-come-here-to-play-safe"),
            GuiService.text("party-manager.the-room-got-louder-for-a-reason"),
            GuiService.text("party-manager.a-proper-unit-has-assembled")
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
            getPlugin().getMessageAPI().sendMessage(creator, Messages.text(creator, "messages.party-manager.you-cannot-create-a-party-while-you-are"));
            return null;
        }

        Party party = new Party(creator, getPlugin().getConfig());
        partyOfPlayer.put(creator.getUniqueId(), party);

        getPlugin().getMessageAPI().sendMessageLang(creator, "party_created");
        creator.sendTitle(Messages.text(creator, "messages.party-manager.party-created"), READY_SUBTITLES.get(random.nextInt(READY_SUBTITLES.size())), 5, 35, 10);
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
        getPlugin().getMessageAPI().sendMessage(player, Messages.text(player, "messages.party-manager.party-members", party.getMembers().size(), party.getMax()));
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
            long cooldownMillis = ConfigValues.seconds(getPlugin().getConfig(), "party.open_cooldown_seconds", 60, 0, 86_400);
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

        SGMenu menu = getPlugin().getMenus().create("party.settings", 3);
        GuiService.control(menu, "shuffle-teams", 10, new SGButton(named(Material.COMPARATOR,
                GuiService.text("party-manager.shuffle-teams", (party.isShuffle() ? "§aON" : "§cOFF")),
                List.of(GuiService.text("party-manager.shuffle-the-draft-order-for-auto-fill-actions"), GuiService.text("party-manager.click-to-toggle"))))
                .withListener(e -> {
                    if (!party.isLeader((Player) e.getWhoClicked())) {
                        Messages.send(e.getWhoClicked(), "messages.party-manager.only-the-party-leader-can-change-party-settings");
                        return;
                    }
                    party.toggleShuffle();
                    openPartySettings((Player) e.getWhoClicked());
                }));

        GuiService.control(menu, "auto-balance", 12, new SGButton(named(Material.TARGET,
                GuiService.text("party-manager.auto-balance", (party.isAutoBalance() ? "§aON" : "§cOFF")),
                List.of(GuiService.text("party-manager.use-even-fill-as-the-default-draft-helper"), GuiService.text("party-manager.click-to-toggle"))))
                .withListener(e -> {
                    if (!party.isLeader((Player) e.getWhoClicked())) {
                        Messages.send(e.getWhoClicked(), "messages.party-manager.only-the-party-leader-can-change-party-settings");
                        return;
                    }
                    party.toggleAutoBalance();
                    openPartySettings((Player) e.getWhoClicked());
                }));

        GuiService.control(menu, "party-play", 14, new SGButton(named(Material.EMERALD,
                GuiService.text("party-manager.party-play"),
                List.of(GuiService.text("party-manager.open-the-team-builder-and-setup-menu"), GuiService.text("party-manager.click-to-continue"))))
                .withListener(e -> ((Player) e.getWhoClicked()).performCommand("party play")));

        GuiService.control(menu, "roster", 16, new SGButton(named(Material.PLAYER_HEAD,
                GuiService.text("party-manager.roster"),
                List.of(GuiService.text("party-manager.view-your-party-members-in-chat"), GuiService.text("party-manager.click-to-list-members"))))
                .withListener(e -> ((Player) e.getWhoClicked()).performCommand("party list")));

        GuiService.control(menu, "back", 22, new SGButton(named(Material.ARROW,
                GuiService.text("party-manager.back"),
                List.of(GuiService.text("party-manager.return-to-your-party-hotbar"))))
                .withListener(e -> {
                    Player clicker = (Player) e.getWhoClicked();
                    giveLobbyPartyItems(clicker, isLeader(clicker));
                    clicker.closeInventory();
                }));

        GuiService.open(player, menu);
    }

    public void giveLobbyPartyItems(Player player, boolean leader) {
        if (player == null) return;
        String lobbyWorld = getPlugin().getConfig().getString("lobby_world");
        if (lobbyWorld == null || !player.getWorld().getName().equalsIgnoreCase(lobbyWorld)) return;

        getPlugin().getLobbyHotbar().render(player, org.albi.core.gui.LobbyHotbar.Layout.PARTY, leader);
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
