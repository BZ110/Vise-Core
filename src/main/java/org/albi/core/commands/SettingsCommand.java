package org.albi.core.commands;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.data.MainPlayer;
import org.albi.core.data.StatManagement;
import org.albi.core.modules.Friends.FriendsManager;
import org.albi.core.modules.ModuleManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class SettingsCommand extends BaseCommand {

    public enum SettingsCategory {
        GENERAL, SOCIAL, MATCHMAKING
    }

    private final Core plugin;

    public SettingsCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;
        openSettingsMenu(player, SettingsCategory.GENERAL);
        return true;
    }

    private void openSettingsMenu(Player player, SettingsCategory category) {
        SGMenu menu = plugin.getGUI().create("§8✦ §b§lYour Settings", 3);
        MainPlayer mp = StatManagement.getMain(player);

        // Fill background
        fillBorders(menu);

        // --- Row 1: Categories ---
        ItemStack genIcon = icon(Material.BEACON, "§3§lGeneral Settings", List.of(
                "§7Basic profile and client",
                "§7behavior and rendering."
        ));
        if (category == SettingsCategory.GENERAL) addGlow(genIcon);
        menu.setButton(0, new SGButton(genIcon).withListener(e -> {
            e.setCancelled(true);
            if (category != SettingsCategory.GENERAL) openSettingsMenu(player, SettingsCategory.GENERAL);
        }));

        ItemStack socIcon = icon(Material.NAME_TAG, "§e§lSocial & Chat", List.of(
                "§7Manage your communications,",
                "§7visibility, and friends."
        ));
        if (category == SettingsCategory.SOCIAL) addGlow(socIcon);
        menu.setButton(1, new SGButton(socIcon).withListener(e -> {
            e.setCancelled(true);
            if (category != SettingsCategory.SOCIAL) openSettingsMenu(player, SettingsCategory.SOCIAL);
        }));

        ItemStack matchIcon = icon(Material.DIAMOND_SWORD, "§c§lMatchmaking", List.of(
                "§7Configure your incoming",
                "§7duel and party invites."
        ));
        if (category == SettingsCategory.MATCHMAKING) addGlow(matchIcon);
        menu.setButton(2, new SGButton(matchIcon).withListener(e -> {
            e.setCancelled(true);
            if (category != SettingsCategory.MATCHMAKING) openSettingsMenu(player, SettingsCategory.MATCHMAKING);
        }));


        // --- Row 3: Settings ---
        if (category == SettingsCategory.GENERAL) {
            registerSetting(menu, 18, mp::visibleStats, mp::setVisibleStats,
                    Material.PAPER, "§f§lProfile Visibility",
                    new String[]{"§7Control who can view your", "§7profile and statistics."},
                    state -> plugin.getMessageAPI().sendMessage(player, "&7Profile visibility is now " + (state ? "&aON" : "&cOFF")),
                    () -> openSettingsMenu(player, category));

            registerSetting(menu, 19, mp::allowSpectators, mp::setAllowSpectators,
                    Material.SPYGLASS, "§b§lAllow Spectators",
                    new String[]{"§7Let other players watch", "§7your ongoing duels."},
                    state -> plugin.getMessageAPI().sendMessage(player, "&7Spectators are now " + (state ? "&aON" : "&cOFF")),
                    () -> openSettingsMenu(player, category));

            registerSetting(menu, 20, mp::elytra, mp::setElytra,
                    Material.ELYTRA, "§a§lLobby Elytra",
                    new String[]{"§7Toggle your equipped lobby", "§7elytra wings (if unlocked)."},
                    state -> plugin.getMessageAPI().sendMessage(player, "&7Lobby elytra is now " + (state ? "&aON" : "&cOFF")),
                    () -> openSettingsMenu(player, category));

            registerSetting(menu, 21, mp::showKillEffects, mp::setShowKillEffects,
                    Material.TOTEM_OF_UNDYING, "§4§lView Kill Effects",
                    new String[]{"§7Control whether you see kill", "§7effects triggered by others."},
                    state -> plugin.getMessageAPI().sendMessage(player, "&7Kill effect visibility is now " + (state ? "&aON" : "&cOFF")),
                    () -> openSettingsMenu(player, category));

            registerSetting(menu, 22, mp::showRankedParticles, mp::setShowRankedParticles,
                    Material.REDSTONE, "§c§lRanked Particles",
                    new String[]{"§7Toggle tier-specific blood", "§7particles when hitting enemies.", "§7(Ranked only)"},
                    state -> plugin.getMessageAPI().sendMessage(player, "&7Ranked particles are now " + (state ? "&aON" : "&cOFF")),
                    () -> openSettingsMenu(player, category));

            if (player.hasPermission("core.lobby.speed")) {
                registerSetting(menu, 23, mp::lobbySpeed, mp::setLobbySpeed,
                        Material.SUGAR, "§b§lLobby Speed",
                        new String[]{"§7Toggle speed effect", "§7while in the lobby."},
                        state -> {
                            plugin.getMessageAPI().sendMessage(player, "&7Lobby Speed is now " + (state ? "&aON" : "&cOFF"));
                            String lobbyWorld = plugin.getConfig().getString("lobby_world");
                            if (lobbyWorld != null && player.getWorld().getName().equalsIgnoreCase(lobbyWorld)) {
                                if (state) player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false, false));
                                else player.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
                            }
                        },
                        () -> openSettingsMenu(player, category));
            } else {
                menu.setButton(23, new SGButton(icon(Material.SUGAR, "§b§lLobby Speed", List.of("§cRequires permission!"))).withListener(e -> e.setCancelled(true)));
            }

            if (player.hasPermission("core.lobby.jumpboost")) {
                registerSetting(menu, 24, mp::lobbyJumpBoost, mp::setLobbyJumpBoost,
                        Material.RABBIT_FOOT, "§a§lLobby Jump Boost",
                        new String[]{"§7Toggle jump boost effect", "§7while in the lobby."},
                        state -> {
                            plugin.getMessageAPI().sendMessage(player, "&7Lobby Jump Boost is now " + (state ? "&aON" : "&cOFF"));
                            String lobbyWorld = plugin.getConfig().getString("lobby_world");
                            if (lobbyWorld != null && player.getWorld().getName().equalsIgnoreCase(lobbyWorld)) {
                                if (state) player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 1, false, false, false));
                                else player.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST);
                            }
                        },
                        () -> openSettingsMenu(player, category));
            } else {
                menu.setButton(24, new SGButton(icon(Material.RABBIT_FOOT, "§a§lLobby Jump Boost", List.of("§cRequires permission!"))).withListener(e -> e.setCancelled(true)));
            }
        } else if (category == SettingsCategory.SOCIAL) {
            registerSetting(menu, 18, mp::isGlobalChat, mp::setGlobalChat,
                    Material.ENDER_EYE, "§5§lGlobal Chat",
                    new String[]{"§7Receive cross-server chat", "§7from players outside your game."},
                    state -> plugin.getMessageAPI().sendMessage(player, "&7Global Chat receiving is now " + (state ? "&aON" : "&cOFF")),
                    () -> openSettingsMenu(player, category));

            registerSetting(menu, 19, mp::isBroadcastChats, mp::setBroadcastChats,
                    Material.WRITTEN_BOOK, "§d§lBroadcast Chats",
                    new String[]{"§7Send your lobby messages", "§7globally across all servers."},
                    state -> plugin.getMessageAPI().sendMessage(player, "&7Global Chat sending is now " + (state ? "&aON" : "&cOFF")),
                    () -> openSettingsMenu(player, category));

            registerSetting(menu, 20, mp::allowMessages, mp::setAllowMessages,
                    Material.WRITABLE_BOOK, "§6§lAllow Messages",
                    new String[]{"§7Receive private /msg and", "§7/reply from strangers."},
                    state -> plugin.getMessageAPI().sendMessage(player, "&7Direct messages are now " + (state ? "&aON" : "&cOFF")),
                    () -> openSettingsMenu(player, category));

            registerSetting(menu, 21, mp::friendRequests, mp::setFriendRequests,
                    Material.PLAYER_HEAD, "§e§lFriend Requests",
                    new String[]{"§7Allow players to send you", "§7incoming friend requests."},
                    state -> plugin.getMessageAPI().sendMessage(player, "&7Friend requests are now " + (state ? "&aON" : "&cOFF")),
                    () -> openSettingsMenu(player, category));
                    
            registerSetting(menu, 22, mp::chatMentions, mp::setChatMentions,
                    Material.BELL, "§e§lChat Mentions",
                    new String[]{"§7Receive a sound ping when", "§7someone says your name."},
                    state -> plugin.getMessageAPI().sendMessage(player, "&7Chat Mentions are now " + (state ? "&aON" : "&cOFF")),
                    () -> openSettingsMenu(player, category));
        } else if (category == SettingsCategory.MATCHMAKING) {
            registerSetting(menu, 18, mp::duelInvites, mp::setDuelInvites,
                    Material.IRON_SWORD, "§c§lDuel Invites",
                    new String[]{"§7Allow incoming duel", "§7challenges in the lobby."},
                    state -> plugin.getMessageAPI().sendMessage(player, "&7Duel invites are now " + (state ? "&aON" : "&cOFF")),
                    () -> openSettingsMenu(player, category));

            registerSetting(menu, 19, mp::partyInvites, mp::setPartyInvites,
                    Material.GOAT_HORN, "§6§lParty Invites",
                    new String[]{"§7Allow other players to", "§7invite you to parties."},
                    state -> plugin.getMessageAPI().sendMessage(player, "&7Party invites are now " + (state ? "&aON" : "&cOFF")),
                    () -> openSettingsMenu(player, category));

            registerSetting(menu, 20, mp::partyDuelInvites, mp::setPartyDuelInvites,
                    Material.TARGET, "§d§lParty Duel Invites",
                    new String[]{"§7Allow full party vs party", "§7duel requests."},
                    state -> plugin.getMessageAPI().sendMessage(player, "&7Party duel invites are now " + (state ? "&aON" : "&cOFF")),
                    () -> openSettingsMenu(player, category));
        }

        // --- Row 3 Exit Barrier ---
        menu.setButton(26, new SGButton(icon(Material.BARRIER, "§c§lClose", List.of(
                "§7Close the settings menu."
        ))).withListener(e -> player.closeInventory()));

        player.openInventory(menu.getInventory());
    }

    private void fillBorders(SGMenu menu) {
        ItemStack topGlass = icon(Material.BLACK_STAINED_GLASS_PANE, "§0", List.of());
        ItemStack midGlass = icon(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§0", List.of());
        
        // Row 1 empty spots
        for (int i = 3; i < 9; i++) {
            menu.setButton(i, new SGButton(topGlass).withListener(e -> e.setCancelled(true)));
        }
        
        // Row 2 completely light blue
        for (int i = 9; i < 18; i++) {
            menu.setButton(i, new SGButton(midGlass).withListener(e -> e.setCancelled(true)));
        }
    }

    private void addGlow(ItemStack item) {
        item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 1);
        ItemMeta m = item.getItemMeta();
        if (m != null) {
            m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(m);
        }
    }

    private void registerSetting(
            SGMenu menu,
            int slot,
            BooleanSupplier getter,
            Consumer<Boolean> setter,
            Material material,
            String label,
            String[] lore,
            Consumer<Boolean> onToggleMessage,
            Runnable onUpdateGUI
    ) {
        boolean current = getter.getAsBoolean();
        List<String> finalLore = new ArrayList<>(Arrays.asList(lore));
        finalLore.add(" ");
        finalLore.add("§fCurrent State: " + state(current));
        finalLore.add(current ? "§cClick to disable." : "§aClick to enable.");

        ItemStack item = icon(material, (current ? "§a" : "§c") + label, finalLore);
        if (current) {
            addGlow(item);
        }

        menu.setButton(slot, new SGButton(item).withListener(e -> {
            boolean newState = !getter.getAsBoolean();
            setter.accept(newState);
            onToggleMessage.accept(newState);
            ((Player) e.getWhoClicked()).playSound(e.getWhoClicked().getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            onUpdateGUI.run();
        }));
    }

    private String state(boolean enabled) {
        return enabled ? "§a§lENABLED" : "§c§lDISABLED";
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }
}
