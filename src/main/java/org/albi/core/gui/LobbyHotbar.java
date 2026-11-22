package org.albi.core.gui;

import org.albi.core.Core;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.ModuleManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Hotbar actions are identified by item metadata, so icon and language changes preserve clicks. */
public final class LobbyHotbar {
    public enum Layout {
        LOBBY("hotbar"), QUEUED("hotbar-queued"), PARTY("hotbar-party"), TOURNAMENT("hotbar-tournament");

        private final String id;

        Layout(String id) { this.id = id; }
    }

    private enum Action {
        TRAVEL("travel", 0, Material.NETHER_STAR, "travel"),
        KIT_EDITOR("kit-editor", 1, Material.BOOK, "kiteditor"),
        CASUAL("casual-queue", 3, Material.IRON_SWORD, "queue"),
        PARTY_CREATE("create-party", 4, Material.GOAT_HORN, "party create"),
        RANKED("ranked-queue", 5, Material.DIAMOND_SWORD, "rankedqueue"),
        STATS("statistics", 7, Material.PAPER, "stats"),
        SETTINGS("settings", 8, Material.COMPARATOR, "settings"),
        LEAVE_QUEUE("leave-queue", 8, Material.BARRIER, "queue leave", "rankedqueue leave"),
        PLAY("play", 0, Material.EMERALD, "party play"),
        MEMBERS("members", 1, Material.PLAYER_HEAD, "party list"),
        HELP("invite-help", 2, Material.NAME_TAG, "party help"),
        PARTY_SETTINGS("party-settings", 7, Material.COMPARATOR, "party settings"),
        DISBAND("disband-party", 8, Material.BARRIER, "party disband"),
        LEAVE("leave-party", 8, Material.BARRIER, "party leave"),
        SPECTATE("spectate-match", 0, Material.ENDER_EYE, "next"),
        TOURNAMENT_LEAVE("leave-tournament", 8, Material.BARRIER, "tournament leave");

        private final String id;
        private final int slot;
        private final Material material;
        private final List<String> commands;

        Action(String id, int slot, Material material, String... commands) {
            this.id = id;
            this.slot = slot;
            this.material = material;
            this.commands = List.of(commands);
        }
    }

    private final Core plugin;
    private final GuiService gui;
    private final NamespacedKey actionKey;

    public LobbyHotbar(Core plugin, GuiService gui) {
        this.plugin = plugin;
        this.gui = gui;
        this.actionKey = new NamespacedKey(plugin, "hotbar-action");
    }

    public void render(Player player, Layout layout, boolean leader) {
        if (!plugin.getLobbySettings().equipment().hotbar()) return;
        var config = gui.menu("lobby." + layout.id);
        if (!gui.visible(config, player)) return;

        var items = new ArrayList<MenuLayout.Element<ItemStack>>();
        for (Action action : actions(layout, leader)) {
            var options = GuiService.section(config, "buttons." + action.id);
            if (!gui.visible(options, player)) continue;
            int slot = gui.chosenSlot(options, action.slot, 9, layout.id + "." + action.id);
            ItemStack icon = style(player, config, options, icon(action, player), layout, action.id, slot);
            items.add(new MenuLayout.Element<>(icon, slot, true, slot != action.slot));
        }

        var custom = GuiService.section(config, "custom-buttons");
        if (custom != null) {
            for (String id : custom.getKeys(false)) {
                var options = custom.getConfigurationSection(id);
                if (options == null || !gui.visible(options, player)) continue;
                int slot = gui.chosenSlot(options, 8, 9, layout.id + ".custom-buttons." + id);
                ItemStack icon = style(player, config, options, new ItemStack(Material.PAPER), layout, "custom/" + id, slot);
                items.add(new MenuLayout.Element<>(icon, slot, true, true));
            }
        }

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        MenuLayout.plan(9, List.of(), Set.of(), items).getFirst().forEach(player.getInventory()::setItem);
        player.updateInventory();
    }

    /** Returns whether this click was on a managed hotbar item, including obsolete copies. */
    public boolean click(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String tag = item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (tag == null) return false;
        if (!plugin.getLobbySettings().equipment().hotbar()) return true;
        String[] parts = tag.split(":", 2);
        if (parts.length != 2) return true;

        for (Layout layout : Layout.values()) {
            if (!layout.id.equals(parts[0])) continue;
            var config = gui.menu("lobby." + layout.id);
            if (!gui.visible(config, player)) return true;

            String button = parts[1];
            boolean custom = button.startsWith("custom/");
            var options = GuiService.section(config, custom ? "custom-buttons." + button.substring(7) : "buttons." + button);
            if (!gui.visible(options, player) || (custom && options == null)) return true;
            if (options != null && options.isList("commands")) {
                runCommands(player, options.getStringList("commands"));
                return true;
            }
            if (!custom) {
                for (Action action : Action.values()) {
                    if (action.id.equals(button)) runCommands(player, action.commands);
                }
            }
            return true;
        }
        return true;
    }

    private List<Action> actions(Layout layout, boolean leader) {
        return switch (layout) {
            case TOURNAMENT -> List.of(Action.SPECTATE, Action.TOURNAMENT_LEAVE);
            case QUEUED -> List.of(Action.LEAVE_QUEUE);
            case PARTY -> List.of(Action.PLAY, Action.MEMBERS, Action.HELP, Action.PARTY_SETTINGS,
                    leader ? Action.DISBAND : Action.LEAVE);
            case LOBBY -> {
                var actions = new ArrayList<>(List.of(Action.TRAVEL, Action.STATS, Action.SETTINGS));
                if (ModuleManager.getModule(DuelManager.class) != null) {
                    actions.addAll(List.of(Action.KIT_EDITOR, Action.CASUAL, Action.RANKED));
                }
                if (plugin.getPartyManager() != null) actions.add(Action.PARTY_CREATE);
                yield actions;
            }
        };
    }

    private ItemStack style(Player player, ConfigurationSection config, ConfigurationSection options,
                            ItemStack original, Layout layout, String action, int slot) {
        var tokens = Map.of("viewer", player.getName(), "player", player.getName(), "slot", slot);
        ItemStack result = gui.item(options, original, player, gui.menuFormat(config), tokens);
        var meta = result.getItemMeta();
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, layout.id + ":" + action);
        double fallback = layout == Layout.PARTY ? 0 : plugin.getLobbySettings().equipment().shortcutCooldown();
        double seconds = config == null ? fallback : config.getDouble("cooldown-seconds", fallback);
        if (seconds > 0 && seconds <= 60) {
            var cooldown = meta.getUseCooldown();
            cooldown.setCooldownSeconds((float) seconds);
            meta.setUseCooldown(cooldown);
        }
        result.setItemMeta(meta);
        return result;
    }

    private static ItemStack icon(Action action, Player player) {
        var stack = new ItemStack(action.material);
        var meta = stack.getItemMeta();
        String key = "lobby-hotbar." + action.id;
        switch (action) {
            case PLAY -> setText(meta, "party-manager.play", List.of("party-manager.open-party-play",
                    "party-manager.click-to-build-teams-or-launch-party-ffa"));
            case MEMBERS -> {
                setText(meta, "party-manager.members", List.of("party-manager.view-your-current-party-roster"));
                if (meta instanceof SkullMeta skull) skull.setOwningPlayer(player);
            }
            case HELP -> setText(meta, "party-manager.invite-help", List.of("party-manager.use-party-invite-player",
                    "party-manager.quick-reminder-for-inviting-teammates"));
            case PARTY_SETTINGS -> setText(meta, "party-manager.party-settings",
                    List.of("party-manager.shuffle-balancing-and-party-controls", "party-manager.right-click-to-manage-your-party"));
            case DISBAND, LEAVE -> setText(meta, key, List.of(key + "-first"));
            default -> setText(meta, key, List.of(key + "-first", key + "-second"));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static void setText(ItemMeta meta, String name, List<String> lore) {
        meta.setDisplayName(GuiService.text(name));
        meta.setLore(lore.stream().map(GuiService::text).toList());
    }

    private static void runCommands(Player player, List<String> commands) {
        for (String template : commands) {
            String command = TextTemplates.format(template, Map.of("player", player.getName())).strip();
            if (command.startsWith("/")) command = command.substring(1);
            if (!command.isBlank()) player.performCommand(command);
        }
    }
}
