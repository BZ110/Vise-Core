package org.albi.core.modules.Friends;

import org.albi.core.gui.Messages;

import org.albi.core.gui.GuiService;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.data.MainPlayer;
import org.albi.core.data.StatManagement;
import org.albi.core.modules.Module;
import org.albi.core.modules.Friends.commands.FriendsCommand;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class FriendsManager extends Module implements Listener {

    private final Map<UUID, Set<UUID>> incomingRequests = new ConcurrentHashMap<>();

    public FriendsManager(Core plugin) {
        super(plugin);
    }

    @Override
    public void init() {
        getPlugin().getServer().getPluginManager().registerEvents(this, getPlugin());
        if (getPlugin().getCommand("friends") != null) {
            getPlugin().getCommand("friends").setExecutor(new FriendsCommand(getPlugin(), this));
        }
    }

    public boolean isFriends(Player a, Player b) {
        if (a == null || b == null)
            return false;
        MainPlayer am = main(a);
        MainPlayer bm = main(b);
        return am != null && bm != null && am.isFriendsWith(b.getUniqueId()) && bm.isFriendsWith(a.getUniqueId());
    }

    public int getOnlineFriends(Player player) {
        MainPlayer mp = main(player);
        if (mp == null)
            return 0;
        int count = 0;
        for (UUID id : mp.getFriends()) {
            Player other = Bukkit.getPlayer(id);
            if (other != null && other.isOnline())
                count++;
        }
        return count;
    }

    public int getPendingCount(Player player) {
        return incomingRequests.getOrDefault(player.getUniqueId(), Collections.emptySet()).size();
    }

    public void sendRequest(Player sender, Player target) {
        if (sender == null || target == null)
            return;
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            Messages.send(sender, "messages.friends-manager.you-cannot-friend-yourself");
            return;
        }
        if (isFriends(sender, target)) {
            Messages.send(sender, "messages.friends-manager.you-are-already-friends-with", target.getName());
            return;
        }
        MainPlayer targetMain = main(target);
        if (targetMain == null || !targetMain.friendRequests()) {
            Messages.send(sender, "messages.friends-manager.that-player-is-not-accepting-friend-requests-right");
            return;
        }
        Set<UUID> incoming = incomingRequests.computeIfAbsent(target.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        if (!incoming.add(sender.getUniqueId())) {
            Messages.send(sender, "messages.friends-manager.you-already-sent-a-friend-request-to", target.getName());
            return;
        }
        Messages.send(sender, "messages.friends-manager.friend-request-sent-to", target.getName());
        Messages.send(target, "messages.friends-manager.sent-you-a-friend-request-friends-accept-or", sender.getName(), sender.getName(), sender.getName());
    }

    public void accept(Player target, OfflinePlayer sender) {
        if (target == null || sender == null || sender.getUniqueId() == null)
            return;
        UUID senderId = sender.getUniqueId();
        Set<UUID> incoming = incomingRequests.get(target.getUniqueId());
        if (incoming == null || !incoming.remove(senderId)) {
            Messages.send(target, "messages.friends-manager.you-do-not-have-a-pending-request-from");
            return;
        }
        addFriendship(target.getUniqueId(), senderId);
        Messages.send(target, "messages.friends-manager.you-are-now-friends-with", safeName(senderId));
        Player onlineSender = Bukkit.getPlayer(senderId);
        if (onlineSender != null) {
            Messages.send(onlineSender, "messages.friends-manager.accepted-your-friend-request", target.getName());
        }
    }

    public void deny(Player target, OfflinePlayer sender) {
        if (target == null || sender == null || sender.getUniqueId() == null)
            return;
        UUID senderId = sender.getUniqueId();
        Set<UUID> incoming = incomingRequests.get(target.getUniqueId());
        if (incoming == null || !incoming.remove(senderId)) {
            Messages.send(target, "messages.friends-manager.you-do-not-have-a-pending-request-from");
            return;
        }
        Messages.send(target, "messages.friends-manager.denied-friend-request-from", safeName(senderId));
        Player onlineSender = Bukkit.getPlayer(senderId);
        if (onlineSender != null) {
            Messages.send(onlineSender, "messages.friends-manager.denied-your-friend-request", target.getName());
        }
    }

    public void removeFriend(Player actor, OfflinePlayer target) {
        if (actor == null || target == null || target.getUniqueId() == null)
            return;
        UUID targetId = target.getUniqueId();
        MainPlayer actorMain = main(actor);
        if (actorMain == null || !actorMain.isFriendsWith(targetId)) {
            Messages.send(actor, "messages.friends-manager.you-are-not-friends-with-that-player");
            return;
        }
        actorMain.removeFriend(targetId);
        Player online = Bukkit.getPlayer(targetId);
        MainPlayer otherMain = StatManagement.getMain(targetId);
        if (otherMain != null)
            otherMain.removeFriend(actor.getUniqueId());
        Messages.send(actor, "messages.friends-manager.removed-from-your-friends-list", safeName(targetId));
        if (online != null)
            Messages.send(online, "messages.friends-manager.removed-you-from-their-friends-list", actor.getName());
    }

    private void addFriendship(UUID a, UUID b) {
        MainPlayer am = StatManagement.getMain(a);
        MainPlayer bm = StatManagement.getMain(b);
        if (am == null || bm == null)
            return;
        am.addFriend(b);
        bm.addFriend(a);
    }

    private MainPlayer main(Player player) {
        if (player == null)
            return null;
        ExtendedPlayer ep = getPlugin().getExtendedPlayer(player);
        return ep == null ? null : ep.getMainPlayer();
    }

    private String safeName(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name == null ? id.toString().substring(0, 8) : name;
    }

    public List<UUID> sortedFriends(Player player) {
        MainPlayer mp = main(player);
        if (mp == null)
            return List.of();
        return mp.getFriends().stream()
                .sorted(Comparator.comparing(this::safeName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    public List<UUID> incoming(Player player) {
        return incomingRequests.getOrDefault(player.getUniqueId(), Collections.emptySet())
                .stream()
                .sorted(Comparator.comparing(this::safeName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    public List<Player> addableOnline(Player player) {
        MainPlayer mp = main(player);
        if (mp == null)
            return List.of();
        return Bukkit.getOnlinePlayers().stream()
                .filter(other -> !other.getUniqueId().equals(player.getUniqueId()))
                .filter(other -> !mp.isFriendsWith(other.getUniqueId()))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    public void openMainMenu(Player player) {
        openMainMenu(player, 0);
    }

    public void openMainMenu(Player player, int page) {
        MainPlayer mp = main(player);
        if (mp == null)
            return;

        SGMenu menu = getPlugin().getMenus().create("friends.main", 6);
        GuiService.control(menu, "your-friends-profile", 4, new SGButton(head(Bukkit.getOfflinePlayer(player.getUniqueId()),
                "§6§lYour Friends Profile",
                List.of(
                        GuiService.text("friends-manager.friends-online", getOnlineFriends(player)),
                        GuiService.text("friends-manager.friends-total", mp.getFriends().size()),
                        GuiService.text("friends-manager.pending-requests", getPendingCount(player)),
                        " ",
                        GuiService.text("friends-manager.friends-show-a-small-star-in-supported-ui")))));

        GuiService.control(menu, "friend-requests", 10, new SGButton(named(mp.friendRequests() ? Material.LIME_DYE : Material.GRAY_DYE,
                GuiService.text("friends-manager.friend-requests", (mp.friendRequests() ? "§aON" : "§cOFF")),
                List.of(GuiService.text("friends-manager.let-other-players-send-you-friend-requests"), GuiService.text("friends-manager.click-to-toggle"))))
                .withListener(e -> {
                    mp.setFriendRequests(!mp.friendRequests());
                    openMainMenu(player, page);
                }));

        GuiService.control(menu, "pending-requests", 12, new SGButton(named(Material.WRITABLE_BOOK,
                GuiService.text("friends-manager.pending-requests-button", getPendingCount(player)),
                List.of(GuiService.text("friends-manager.accept-or-deny-incoming-requests"), GuiService.text("friends-manager.click-to-open"))))
                .withListener(e -> openIncomingMenu(player, 0)));

        GuiService.control(menu, "add-friends", 14, new SGButton(named(Material.COMPASS,
                GuiService.text("friends-manager.add-friends"),
                List.of(GuiService.text("friends-manager.browse-online-players-who-are-not-on-your"), GuiService.text("friends-manager.click-to-open"))))
                .withListener(e -> openAddMenu(player, 0)));

        GuiService.control(menu, "online-friends", 16, new SGButton(named(Material.EMERALD,
                GuiService.text("friends-manager.online-friends"),
                List.of("§7Currently online: §f" + getOnlineFriends(player),
                        "§7Total friends: §f" + mp.getFriends().size()))));

        List<UUID> friends = sortedFriends(player);
        int pageSize = 18;
        int maxPage = Math.max(0, (friends.size() - 1) / pageSize);
        int from = Math.max(0, Math.min(page, maxPage) * pageSize);
        int to = Math.min(friends.size(), from + pageSize);
        int slot = 27;
        for (int i = from; i < to; i++) {
            UUID id = friends.get(i);
            OfflinePlayer target = Bukkit.getOfflinePlayer(id);
            boolean online = target.isOnline();
            menu.setButton(slot++, new SGButton(head(target,
                    (online ? "§a" : "§7") + safeName(id),
                    List.of(
                            online ? GuiService.text("friends-manager.online") : GuiService.text("friends-manager.offline"),
                            GuiService.text("friends-manager.status", (online ? "§aOnline" : "§cOffline")),
                            " ",
                            GuiService.text("friends-manager.left-click-to-view-request-tools"),
                            GuiService.text("friends-manager.right-click-to-remove-friend"))))
                    .withListener(e -> {
                        if (e.getClick().isRightClick()) {
                            removeFriend(player, target);
                            openMainMenu(player, page);
                        } else {
                            Messages.send(player, "messages.friends-manager.friend-is", safeName(id), (online ? "§aonline" : "§7offline"));
                        }
                    }));
        }

        if (page > 0) {
            GuiService.control(menu, "previous", 45,
                    new SGButton(named(Material.ARROW, GuiService.text("friends-manager.previous"), List.of(GuiService.text("friends-manager.view-the-previous-friends-page"))))
                            .withListener(e -> openMainMenu(player, page - 1)));
        }
        if (page < maxPage) {
            GuiService.control(menu, "next", 53, new SGButton(named(Material.ARROW, GuiService.text("friends-manager.next"), List.of(GuiService.text("friends-manager.view-the-next-friends-page"))))
                    .withListener(e -> openMainMenu(player, page + 1)));
        }
        GuiService.control(menu, "close", 49, new SGButton(named(Material.BARRIER, GuiService.text("friends-manager.close"), List.of(GuiService.text("friends-manager.close-the-friends-menu"))))
                .withListener(e -> player.closeInventory()));

        GuiService.open(player, menu);
    }

    public void openIncomingMenu(Player player, int page) {
        List<UUID> incoming = incoming(player);
        SGMenu menu = getPlugin().getMenus().create("friends.requests", 4);
        int pageSize = 18;
        int maxPage = Math.max(0, (incoming.size() - 1) / pageSize);
        int from = Math.max(0, Math.min(page, maxPage) * pageSize);
        int to = Math.min(incoming.size(), from + pageSize);
        int slot = 9;
        for (int i = from; i < to; i++) {
            UUID id = incoming.get(i);
            OfflinePlayer target = Bukkit.getOfflinePlayer(id);
            menu.setButton(slot++, new SGButton(head(target,
                    "§f" + safeName(id),
                    List.of(
                            GuiService.text("friends-manager.left-click-to-accept"),
                            GuiService.text("friends-manager.right-click-to-deny"))))
                    .withListener(e -> {
                        if (e.getClick().isRightClick())
                            deny(player, target);
                        else
                            accept(player, target);
                        openIncomingMenu(player, page);
                    }));
        }
        GuiService.control(menu, "back", 27, new SGButton(named(Material.ARROW, GuiService.text("friends-manager.back"), List.of(GuiService.text("friends-manager.return-to-the-friends-hub"))))
                .withListener(e -> openMainMenu(player)));
        if (page > 0) {
            GuiService.control(menu, "previous", 30,
                    new SGButton(named(Material.ARROW, GuiService.text("friends-manager.previous"), List.of(GuiService.text("friends-manager.view-the-previous-requests-page"))))
                            .withListener(e -> openIncomingMenu(player, page - 1)));
        }
        if (page < maxPage) {
            GuiService.control(menu, "next", 32,
                    new SGButton(named(Material.ARROW, GuiService.text("friends-manager.next"), List.of(GuiService.text("friends-manager.view-the-next-requests-page"))))
                            .withListener(e -> openIncomingMenu(player, page + 1)));
        }
        GuiService.open(player, menu);
    }

    public void openAddMenu(Player player, int page) {
        List<Player> addable = addableOnline(player);
        SGMenu menu = getPlugin().getMenus().create("friends.add", 4);
        int pageSize = 18;
        int maxPage = Math.max(0, (addable.size() - 1) / pageSize);
        int from = Math.max(0, Math.min(page, maxPage) * pageSize);
        int to = Math.min(addable.size(), from + pageSize);
        int slot = 9;
        for (int i = from; i < to; i++) {
            Player target = addable.get(i);
            menu.setButton(slot++, new SGButton(head(target,
                    "§f" + target.getName(),
                    List.of(
                            GuiService.text("friends-manager.world", target.getWorld().getName()),
                            GuiService.text("friends-manager.click-to-send-a-friend-request"))))
                    .withListener(e -> {
                        sendRequest(player, target);
                        openAddMenu(player, page);
                    }));
        }
        GuiService.control(menu, "back", 27, new SGButton(named(Material.ARROW, GuiService.text("friends-manager.back"), List.of(GuiService.text("friends-manager.return-to-the-friends-hub"))))
                .withListener(e -> openMainMenu(player)));
        if (page > 0) {
            GuiService.control(menu, "previous", 30,
                    new SGButton(named(Material.ARROW, GuiService.text("friends-manager.previous"), List.of(GuiService.text("friends-manager.view-the-previous-addable-page"))))
                            .withListener(e -> openAddMenu(player, page - 1)));
        }
        if (page < maxPage) {
            GuiService.control(menu, "next", 32, new SGButton(named(Material.ARROW, GuiService.text("friends-manager.next"), List.of(GuiService.text("friends-manager.view-the-next-addable-page"))))
                    .withListener(e -> openAddMenu(player, page + 1)));
        }
        GuiService.open(player, menu);
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

    private ItemStack head(OfflinePlayer player, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta raw = item.getItemMeta();
        if (raw instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
            skull.setDisplayName(name);
            skull.setLore(lore);
            item.setItemMeta(skull);
        } else if (raw != null) {
            raw.setDisplayName(name);
            raw.setLore(lore);
            item.setItemMeta(raw);
        }
        return item;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        incomingRequests.remove(event.getPlayer().getUniqueId());
        for (Set<UUID> set : incomingRequests.values()) {
            set.remove(event.getPlayer().getUniqueId());
        }
    }
}
