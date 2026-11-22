package org.albi.core.modules.Friends;

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
            sender.sendMessage("§cYou cannot friend yourself.");
            return;
        }
        if (isFriends(sender, target)) {
            sender.sendMessage("§eYou are already friends with §f" + target.getName() + "§e.");
            return;
        }
        MainPlayer targetMain = main(target);
        if (targetMain == null || !targetMain.friendRequests()) {
            sender.sendMessage("§cThat player is not accepting friend requests right now.");
            return;
        }
        Set<UUID> incoming = incomingRequests.computeIfAbsent(target.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        if (!incoming.add(sender.getUniqueId())) {
            sender.sendMessage("§eYou already sent a friend request to §f" + target.getName() + "§e.");
            return;
        }
        sender.sendMessage("§aFriend request sent to §f" + target.getName() + "§a.");
        target.sendMessage("§6✦ §f" + sender.getName() + " §7sent you a friend request. §a/friends accept "
                + sender.getName() + " §7or §c/friends deny " + sender.getName());
    }

    public void accept(Player target, OfflinePlayer sender) {
        if (target == null || sender == null || sender.getUniqueId() == null)
            return;
        UUID senderId = sender.getUniqueId();
        Set<UUID> incoming = incomingRequests.get(target.getUniqueId());
        if (incoming == null || !incoming.remove(senderId)) {
            target.sendMessage("§cYou do not have a pending request from that player.");
            return;
        }
        addFriendship(target.getUniqueId(), senderId);
        target.sendMessage("§aYou are now friends with §f" + safeName(senderId) + "§a.");
        Player onlineSender = Bukkit.getPlayer(senderId);
        if (onlineSender != null) {
            onlineSender.sendMessage("§a" + target.getName() + " accepted your friend request.");
        }
    }

    public void deny(Player target, OfflinePlayer sender) {
        if (target == null || sender == null || sender.getUniqueId() == null)
            return;
        UUID senderId = sender.getUniqueId();
        Set<UUID> incoming = incomingRequests.get(target.getUniqueId());
        if (incoming == null || !incoming.remove(senderId)) {
            target.sendMessage("§cYou do not have a pending request from that player.");
            return;
        }
        target.sendMessage("§eDenied friend request from §f" + safeName(senderId) + "§e.");
        Player onlineSender = Bukkit.getPlayer(senderId);
        if (onlineSender != null) {
            onlineSender.sendMessage("§c" + target.getName() + " denied your friend request.");
        }
    }

    public void removeFriend(Player actor, OfflinePlayer target) {
        if (actor == null || target == null || target.getUniqueId() == null)
            return;
        UUID targetId = target.getUniqueId();
        MainPlayer actorMain = main(actor);
        if (actorMain == null || !actorMain.isFriendsWith(targetId)) {
            actor.sendMessage("§cYou are not friends with that player.");
            return;
        }
        actorMain.removeFriend(targetId);
        Player online = Bukkit.getPlayer(targetId);
        MainPlayer otherMain = StatManagement.getMain(targetId);
        if (otherMain != null)
            otherMain.removeFriend(actor.getUniqueId());
        actor.sendMessage("§eRemoved §f" + safeName(targetId) + " §efrom your friends list.");
        if (online != null)
            online.sendMessage("§e" + actor.getName() + " removed you from their friends list.");
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

        SGMenu menu = getPlugin().getGUI().create("§6§lFriends Hub", 6);
        menu.setButton(4, new SGButton(head(Bukkit.getOfflinePlayer(player.getUniqueId()),
                "§6§lYour Friends Profile",
                List.of(
                        "§7Friends Online: §a" + getOnlineFriends(player),
                        "§7Friends Total: §f" + mp.getFriends().size(),
                        "§7Pending Requests: §e" + getPendingCount(player),
                        " ",
                        "§6⭐ §7Friends show a small star in supported UI."))));

        menu.setButton(10, new SGButton(named(mp.friendRequests() ? Material.LIME_DYE : Material.GRAY_DYE,
                "§b§lFriend Requests: " + (mp.friendRequests() ? "§aON" : "§cOFF"),
                List.of("§7Let other players send you friend requests.", "§fClick to toggle.")))
                .withListener(e -> {
                    mp.setFriendRequests(!mp.friendRequests());
                    openMainMenu(player, page);
                }));

        menu.setButton(12, new SGButton(named(Material.WRITABLE_BOOK,
                "§e§lPending Requests §8(" + getPendingCount(player) + ")",
                List.of("§7Accept or deny incoming requests.", "§fClick to open.")))
                .withListener(e -> openIncomingMenu(player, 0)));

        menu.setButton(14, new SGButton(named(Material.COMPASS,
                "§d§lAdd Friends",
                List.of("§7Browse online players who are not on your list.", "§fClick to open.")))
                .withListener(e -> openAddMenu(player, 0)));

        menu.setButton(16, new SGButton(named(Material.EMERALD,
                "§a§lOnline Friends",
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
                            online ? "§aOnline" : "§7Offline",
                            "§7Status: §f" + (online ? "§aOnline" : "§cOffline"),
                            " ",
                            "§fLeft click §7to view request tools.",
                            "§cRight click §7to remove friend.")))
                    .withListener(e -> {
                        if (e.getClick().isRightClick()) {
                            removeFriend(player, target);
                            openMainMenu(player, page);
                        } else {
                            player.sendMessage("§6Friend §8» §f" + safeName(id) + " §7is "
                                    + (online ? "§aonline" : "§7offline") + "§7.");
                        }
                    }));
        }

        if (page > 0) {
            menu.setButton(45,
                    new SGButton(named(Material.ARROW, "§e← Previous", List.of("§7View the previous friends page.")))
                            .withListener(e -> openMainMenu(player, page - 1)));
        }
        if (page < maxPage) {
            menu.setButton(53, new SGButton(named(Material.ARROW, "§eNext →", List.of("§7View the next friends page.")))
                    .withListener(e -> openMainMenu(player, page + 1)));
        }
        menu.setButton(49, new SGButton(named(Material.BARRIER, "§cClose", List.of("§7Close the friends menu.")))
                .withListener(e -> player.closeInventory()));

        player.openInventory(menu.getInventory());
    }

    public void openIncomingMenu(Player player, int page) {
        List<UUID> incoming = incoming(player);
        SGMenu menu = getPlugin().getGUI().create("§e§lFriend Requests", 4);
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
                            "§aLeft click §7to accept.",
                            "§cRight click §7to deny.")))
                    .withListener(e -> {
                        if (e.getClick().isRightClick())
                            deny(player, target);
                        else
                            accept(player, target);
                        openIncomingMenu(player, page);
                    }));
        }
        menu.setButton(27, new SGButton(named(Material.ARROW, "§eBack", List.of("§7Return to the friends hub.")))
                .withListener(e -> openMainMenu(player)));
        if (page > 0) {
            menu.setButton(30,
                    new SGButton(named(Material.ARROW, "§e← Previous", List.of("§7View the previous requests page.")))
                            .withListener(e -> openIncomingMenu(player, page - 1)));
        }
        if (page < maxPage) {
            menu.setButton(32,
                    new SGButton(named(Material.ARROW, "§eNext →", List.of("§7View the next requests page.")))
                            .withListener(e -> openIncomingMenu(player, page + 1)));
        }
        player.openInventory(menu.getInventory());
    }

    public void openAddMenu(Player player, int page) {
        List<Player> addable = addableOnline(player);
        SGMenu menu = getPlugin().getGUI().create("§d§lAdd Friends", 4);
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
                            "§7World: §f" + target.getWorld().getName(),
                            "§fClick to send a friend request.")))
                    .withListener(e -> {
                        sendRequest(player, target);
                        openAddMenu(player, page);
                    }));
        }
        menu.setButton(27, new SGButton(named(Material.ARROW, "§eBack", List.of("§7Return to the friends hub.")))
                .withListener(e -> openMainMenu(player)));
        if (page > 0) {
            menu.setButton(30,
                    new SGButton(named(Material.ARROW, "§e← Previous", List.of("§7View the previous addable page.")))
                            .withListener(e -> openAddMenu(player, page - 1)));
        }
        if (page < maxPage) {
            menu.setButton(32, new SGButton(named(Material.ARROW, "§eNext →", List.of("§7View the next addable page.")))
                    .withListener(e -> openAddMenu(player, page + 1)));
        }
        player.openInventory(menu.getInventory());
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
