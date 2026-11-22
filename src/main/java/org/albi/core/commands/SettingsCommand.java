package org.albi.core.commands;

import org.albi.core.gui.Messages;

import org.albi.core.gui.GuiService;

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
        SGMenu menu = plugin.getMenus().create("settings.main", 3);
        MainPlayer mp = StatManagement.getMain(player);

        // --- Row 1: Categories ---
        ItemStack genIcon = icon(Material.BEACON, GuiService.text("settings-command.general-tab"), List.of(
                GuiService.text("settings-command.basic-profile-and-client"),
                GuiService.text("settings-command.behavior-and-rendering")
        ));
        if (category == SettingsCategory.GENERAL) addGlow(genIcon);
        GuiService.control(menu, "gen-icon", 0, new SGButton(genIcon).withListener(e -> {
            e.setCancelled(true);
            if (category != SettingsCategory.GENERAL) openSettingsMenu(player, SettingsCategory.GENERAL);
        }));

        ItemStack socIcon = icon(Material.NAME_TAG, GuiService.text("settings-command.social-tab"), List.of(
                GuiService.text("settings-command.manage-your-communications"),
                GuiService.text("settings-command.visibility-and-friends")
        ));
        if (category == SettingsCategory.SOCIAL) addGlow(socIcon);
        GuiService.control(menu, "soc-icon", 1, new SGButton(socIcon).withListener(e -> {
            e.setCancelled(true);
            if (category != SettingsCategory.SOCIAL) openSettingsMenu(player, SettingsCategory.SOCIAL);
        }));

        ItemStack matchIcon = icon(Material.DIAMOND_SWORD, GuiService.text("settings-command.matchmaking-tab"), List.of(
                GuiService.text("settings-command.configure-your-incoming"),
                GuiService.text("settings-command.duel-and-party-invites")
        ));
        if (category == SettingsCategory.MATCHMAKING) addGlow(matchIcon);
        GuiService.control(menu, "match-icon", 2, new SGButton(matchIcon).withListener(e -> {
            e.setCancelled(true);
            if (category != SettingsCategory.MATCHMAKING) openSettingsMenu(player, SettingsCategory.MATCHMAKING);
        }));


        // --- Row 3: Settings ---
        if (category == SettingsCategory.GENERAL) {
            registerSetting(menu, "profile-visibility", 18, mp::visibleStats, mp::setVisibleStats,
                    Material.PAPER, GuiService.text("settings-command.profile-visibility"),
                    List.of(GuiService.text("settings-command.profile-visibility-lore-1"), GuiService.text("settings-command.profile-visibility-lore-2")),
                    state -> Messages.send(player, "messages.settings-command.profile-visibility-is-now", Messages.text(player, state ? "messages.settings-command.on" : "messages.settings-command.off")),
                    () -> openSettingsMenu(player, category));

            registerSetting(menu, "allow-spectators", 19, mp::allowSpectators, mp::setAllowSpectators,
                    Material.SPYGLASS, GuiService.text("settings-command.allow-spectators"),
                    List.of(GuiService.text("settings-command.allow-spectators-lore-1"), GuiService.text("settings-command.allow-spectators-lore-2")),
                    state -> Messages.send(player, "messages.settings-command.spectators-are-now", Messages.text(player, state ? "messages.settings-command.on" : "messages.settings-command.off")),
                    () -> openSettingsMenu(player, category));

            registerSetting(menu, "lobby-elytra", 20, mp::elytra, mp::setElytra,
                    Material.ELYTRA, GuiService.text("settings-command.lobby-elytra"),
                    List.of(GuiService.text("settings-command.lobby-elytra-lore-1"), GuiService.text("settings-command.lobby-elytra-lore-2")),
                    state -> Messages.send(player, "messages.settings-command.lobby-elytra-is-now", Messages.text(player, state ? "messages.settings-command.on" : "messages.settings-command.off")),
                    () -> openSettingsMenu(player, category));

            registerSetting(menu, "view-kill-effects", 21, mp::showKillEffects, mp::setShowKillEffects,
                    Material.TOTEM_OF_UNDYING, GuiService.text("settings-command.view-kill-effects"),
                    List.of(GuiService.text("settings-command.view-kill-effects-lore-1"), GuiService.text("settings-command.view-kill-effects-lore-2")),
                    state -> Messages.send(player, "messages.settings-command.kill-effect-visibility-is-now", Messages.text(player, state ? "messages.settings-command.on" : "messages.settings-command.off")),
                    () -> openSettingsMenu(player, category));

            registerSetting(menu, "ranked-particles", 22, mp::showRankedParticles, mp::setShowRankedParticles,
                    Material.REDSTONE, GuiService.text("settings-command.ranked-particles"),
                    List.of(GuiService.text("settings-command.ranked-particles-lore-1"), GuiService.text("settings-command.ranked-particles-lore-2"), GuiService.text("settings-command.ranked-particles-lore-3")),
                    state -> Messages.send(player, "messages.settings-command.ranked-particles-are-now", Messages.text(player, state ? "messages.settings-command.on" : "messages.settings-command.off")),
                    () -> openSettingsMenu(player, category));

            if (player.hasPermission("core.lobby.speed")) {
                registerSetting(menu, "lobby-speed", 23, mp::lobbySpeed, mp::setLobbySpeed,
                    Material.SUGAR, GuiService.text("settings-command.lobby-speed"),
                    List.of(GuiService.text("settings-command.lobby-speed-lore-1"), GuiService.text("settings-command.lobby-speed-lore-2")),
                        state -> {
                            Messages.send(player, "messages.settings-command.lobby-speed-is-now", Messages.text(player, state ? "messages.settings-command.on" : "messages.settings-command.off"));
                            String lobbyWorld = plugin.getConfig().getString("lobby_world");
                            if (lobbyWorld != null && player.getWorld().getName().equalsIgnoreCase(lobbyWorld)) {
                                if (state) plugin.applyLobbyEffect(player, org.bukkit.potion.PotionEffectType.SPEED, plugin.getLobbySettings().equipment().speedLevel());
                                else player.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
                            }
                        },
                        () -> openSettingsMenu(player, category));
            } else {
                GuiService.control(menu, "lobby-speed", 23, new SGButton(icon(Material.SUGAR, GuiService.text("settings-command.lobby-speed"), List.of(GuiService.text("settings-command.requires-permission")))).withListener(e -> e.setCancelled(true)));
            }

            if (player.hasPermission("core.lobby.jumpboost")) {
                registerSetting(menu, "lobby-jump-boost", 24, mp::lobbyJumpBoost, mp::setLobbyJumpBoost,
                    Material.RABBIT_FOOT, GuiService.text("settings-command.lobby-jump-boost"),
                    List.of(GuiService.text("settings-command.lobby-jump-boost-lore-1"), GuiService.text("settings-command.lobby-jump-boost-lore-2")),
                        state -> {
                            Messages.send(player, "messages.settings-command.lobby-jump-boost-is-now", Messages.text(player, state ? "messages.settings-command.on" : "messages.settings-command.off"));
                            String lobbyWorld = plugin.getConfig().getString("lobby_world");
                            if (lobbyWorld != null && player.getWorld().getName().equalsIgnoreCase(lobbyWorld)) {
                                if (state) plugin.applyLobbyEffect(player, org.bukkit.potion.PotionEffectType.JUMP_BOOST, plugin.getLobbySettings().equipment().jumpBoostLevel());
                                else player.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST);
                            }
                        },
                        () -> openSettingsMenu(player, category));
            } else {
                GuiService.control(menu, "lobby-jump-boost", 24, new SGButton(icon(Material.RABBIT_FOOT, GuiService.text("settings-command.lobby-jump-boost"), List.of(GuiService.text("settings-command.requires-permission")))).withListener(e -> e.setCancelled(true)));
            }
        } else if (category == SettingsCategory.SOCIAL) {
            registerSetting(menu, "global-chat", 18, mp::isGlobalChat, mp::setGlobalChat,
                    Material.ENDER_EYE, GuiService.text("settings-command.global-chat"),
                    List.of(GuiService.text("settings-command.global-chat-lore-1"), GuiService.text("settings-command.global-chat-lore-2")),
                    state -> Messages.send(player, "messages.settings-command.global-chat-receiving-is-now", Messages.text(player, state ? "messages.settings-command.on" : "messages.settings-command.off")),
                    () -> openSettingsMenu(player, category));

            registerSetting(menu, "broadcast-chats", 19, mp::isBroadcastChats, mp::setBroadcastChats,
                    Material.WRITTEN_BOOK, GuiService.text("settings-command.broadcast-chats"),
                    List.of(GuiService.text("settings-command.broadcast-chats-lore-1"), GuiService.text("settings-command.broadcast-chats-lore-2")),
                    state -> Messages.send(player, "messages.settings-command.global-chat-sending-is-now", Messages.text(player, state ? "messages.settings-command.on" : "messages.settings-command.off")),
                    () -> openSettingsMenu(player, category));

            registerSetting(menu, "allow-messages", 20, mp::allowMessages, mp::setAllowMessages,
                    Material.WRITABLE_BOOK, GuiService.text("settings-command.allow-messages"),
                    List.of(GuiService.text("settings-command.allow-messages-lore-1"), GuiService.text("settings-command.allow-messages-lore-2")),
                    state -> Messages.send(player, "messages.settings-command.direct-messages-are-now", Messages.text(player, state ? "messages.settings-command.on" : "messages.settings-command.off")),
                    () -> openSettingsMenu(player, category));

            registerSetting(menu, "friend-requests", 21, mp::friendRequests, mp::setFriendRequests,
                    Material.PLAYER_HEAD, GuiService.text("settings-command.friend-requests"),
                    List.of(GuiService.text("settings-command.friend-requests-lore-1"), GuiService.text("settings-command.friend-requests-lore-2")),
                    state -> Messages.send(player, "messages.settings-command.friend-requests-are-now", Messages.text(player, state ? "messages.settings-command.on" : "messages.settings-command.off")),
                    () -> openSettingsMenu(player, category));
                    
            registerSetting(menu, "chat-mentions", 22, mp::chatMentions, mp::setChatMentions,
                    Material.BELL, GuiService.text("settings-command.chat-mentions"),
                    List.of(GuiService.text("settings-command.chat-mentions-lore-1"), GuiService.text("settings-command.chat-mentions-lore-2")),
                    state -> Messages.send(player, "messages.settings-command.chat-mentions-are-now", Messages.text(player, state ? "messages.settings-command.on" : "messages.settings-command.off")),
                    () -> openSettingsMenu(player, category));
        } else if (category == SettingsCategory.MATCHMAKING) {
            registerSetting(menu, "duel-invites", 18, mp::duelInvites, mp::setDuelInvites,
                    Material.IRON_SWORD, GuiService.text("settings-command.duel-invites"),
                    List.of(GuiService.text("settings-command.duel-invites-lore-1"), GuiService.text("settings-command.duel-invites-lore-2")),
                    state -> Messages.send(player, "messages.settings-command.duel-invites-are-now", Messages.text(player, state ? "messages.settings-command.on" : "messages.settings-command.off")),
                    () -> openSettingsMenu(player, category));

            registerSetting(menu, "party-invites", 19, mp::partyInvites, mp::setPartyInvites,
                    Material.GOAT_HORN, GuiService.text("settings-command.party-invites"),
                    List.of(GuiService.text("settings-command.party-invites-lore-1"), GuiService.text("settings-command.party-invites-lore-2")),
                    state -> Messages.send(player, "messages.settings-command.party-invites-are-now", Messages.text(player, state ? "messages.settings-command.on" : "messages.settings-command.off")),
                    () -> openSettingsMenu(player, category));

            registerSetting(menu, "party-duel-invites", 20, mp::partyDuelInvites, mp::setPartyDuelInvites,
                    Material.TARGET, GuiService.text("settings-command.party-duel-invites"),
                    List.of(GuiService.text("settings-command.party-duel-invites-lore-1"), GuiService.text("settings-command.party-duel-invites-lore-2")),
                    state -> Messages.send(player, "messages.settings-command.party-duel-invites-are-now", Messages.text(player, state ? "messages.settings-command.on" : "messages.settings-command.off")),
                    () -> openSettingsMenu(player, category));
        }

        // --- Row 3 Exit Barrier ---
        GuiService.control(menu, "close", 26, new SGButton(icon(Material.BARRIER, GuiService.text("settings-command.close"), List.of(
                GuiService.text("settings-command.close-the-settings-menu")
        ))).withListener(e -> player.closeInventory()));

        GuiService.open(player, menu);
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
            String id,
            int slot,
            BooleanSupplier getter,
            Consumer<Boolean> setter,
            Material material,
            String label,
            List<String> lore,
            Consumer<Boolean> onToggleMessage,
            Runnable onUpdateGUI
    ) {
        boolean current = getter.getAsBoolean();
        List<String> finalLore = new ArrayList<>(lore);
        finalLore.add(" ");
        finalLore.add(GuiService.text("settings-command.current-state", state(current)));
        finalLore.add(current ? GuiService.text("settings-command.click-to-disable") : GuiService.text("settings-command.click-to-enable"));

        ItemStack item = icon(material, (current ? "§a" : "§c") + label, finalLore);
        if (current) {
            addGlow(item);
        }

        GuiService.control(menu, id, slot, new SGButton(item).withListener(e -> {
            boolean newState = !getter.getAsBoolean();
            setter.accept(newState);
            onToggleMessage.accept(newState);
            ((Player) e.getWhoClicked()).playSound(e.getWhoClicked().getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            onUpdateGUI.run();
        }));
    }

    private String state(boolean enabled) {
        return GuiService.text(enabled ? "settings-command.enabled" : "settings-command.disabled");
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
