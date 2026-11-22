package org.albi.core.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Set;

import static org.albi.core.util.ConfigValues.*;

/** Lobby behavior captured at startup so hot event handlers share the same rules. */
public record LobbySettings(Join join, Protection protection, Equipment equipment, Interaction interaction) {
    public record Join(boolean teleport, boolean resetPlayer, boolean hideJoinMessage,
                       boolean hideQuitMessage, boolean warmTierProfiles) {}

    public record Protection(boolean blockPlace, boolean blockBreak, boolean itemDrop, boolean itemPickup,
                             boolean interactions, boolean blockPlayerHeadsOutsideLobby, boolean heightLimit, boolean pvp) {}

    public record Equipment(boolean hotbar, boolean elytra, boolean elytraUnbreakable, int elytraUnbreakingLevel,
                            int speedLevel, int jumpBoostLevel, int windCharges, double shortcutCooldown) {}

    public record Interaction(boolean rightClickDuel, long rightClickDuelMillis, boolean sneakTabToggle,
                              boolean shulkerEditor, boolean physicalTriggers, Set<Material> allowedItems,
                              boolean protectWindChargeBlocks, boolean boostWindCharge,
                              double windRadius, double windHorizontal, double windVertical) {}

    public static LobbySettings from(ConfigurationSection config) {
        return new LobbySettings(
                new Join(config.getBoolean("lobby.join.teleport", true), config.getBoolean("lobby.join.reset_player", true),
                        config.getBoolean("lobby.join.hide_join_message", true), config.getBoolean("lobby.join.hide_quit_message", true),
                        config.getBoolean("lobby.join.warm_tier_profiles", true)),
                new Protection(config.getBoolean("lobby.protection.block_placement", true),
                        config.getBoolean("lobby.protection.block_breaking", true), config.getBoolean("lobby.protection.item_drops", true),
                        config.getBoolean("lobby.protection.item_pickup", true), config.getBoolean("lobby.protection.interactions", true),
                        config.getBoolean("lobby.protection.block_player_heads_outside_lobby", true),
                        config.getBoolean("lobby.protection.height_limit", true), config.getBoolean("lobby.protection.pvp", true)),
                new Equipment(config.getBoolean("lobby.hotbar.enabled", true), config.getBoolean("lobby.elytra.enabled", true),
                        config.getBoolean("lobby.elytra.unbreakable", true), integer(config, "lobby.elytra.unbreaking_level", 1, 0, 255),
                        integer(config, "lobby.movement.speed_level", 2, 0, 10), integer(config, "lobby.movement.jump_boost_level", 2, 0, 10),
                        integer(config, "lobby.elytra.wind_charges", 64, 0, 64), decimal(config, "lobby.hotbar.cooldown_seconds", 1, 0, 60)),
                new Interaction(config.getBoolean("lobby.interaction.right_click_duel", true),
                        integer(config, "lobby.interaction.right_click_duel_cooldown_ms", 250, 0, 30_000),
                        config.getBoolean("lobby.interaction.sneak_tab_toggle", true),
                        config.getBoolean("lobby.interaction.shulker_editor", true),
                        config.getBoolean("lobby.interaction.physical_triggers", false),
                        materials(config, "lobby.interaction.allowed_items", Set.of(Material.FISHING_ROD, Material.WIND_CHARGE)),
                        config.getBoolean("lobby.wind_charges.protect_blocks", true),
                        config.getBoolean("lobby.wind_charges.boost", true), decimal(config, "lobby.wind_charges.radius", 2, 0, 16),
                        decimal(config, "lobby.wind_charges.horizontal_velocity", 1, 0, 4),
                        decimal(config, "lobby.wind_charges.vertical_velocity", 1.25, 0, 4)));
    }
}
