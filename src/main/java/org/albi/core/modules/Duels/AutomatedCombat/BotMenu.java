package org.albi.core.modules.Duels.AutomatedCombat;

import org.albi.core.gui.Messages;

import org.albi.core.gui.GuiService;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.CustomKit.helpers.CustomKit;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Arena;
import org.albi.core.modules.Duels.helpers.Kit;
import org.albi.core.serializable.SerializableItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public final class BotMenu {

    private final Core plugin;
    private final Manager manager;
    private final AutomatedCombatCallbacks callbacks;

    public BotMenu(Core plugin, Manager manager, AutomatedCombatCallbacks callbacks) {
        this.plugin = plugin;
        this.manager = manager;
        this.callbacks = callbacks;
    }

    public void openMain(Player player, BotSettings settings) {
        if (callbacks != null) callbacks.onMenuOpened(player, settings);

        SGMenu menu = plugin.getMenus().create("bots.main", 3);
        fill(menu, Material.BLACK_STAINED_GLASS_PANE, " ");

        ItemStack info = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(GuiService.text("bot-menu.difficulty-information"));
        infoMeta.setLore(settings.toLore());
        info.setItemMeta(infoMeta);
        GuiService.control(menu, "info", 11, new SGButton(info).withListener(e -> {
            e.setCancelled(true);
            openSettings((Player) e.getWhoClicked(), settings);
        }));

        ItemStack start = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta startMeta = start.getItemMeta();
        startMeta.setDisplayName(GuiService.text("bot-menu.start"));
        startMeta.setLore(Arrays.asList(
                GuiService.text("bot-menu.start-a-pv-p-duel-against-the-bot"),
                GuiService.text("bot-menu.experimental-npc-combat-you-may-encounter-bugs"),
                GuiService.text("bot-menu.rounds-to-win", settings.getRounds())
        ));
        start.setItemMeta(startMeta);
        GuiService.control(menu, "start", 13, new SGButton(start).withListener(e -> {
            e.setCancelled(true);
            manager.start((Player) e.getWhoClicked(), settings, callbacks);
        }));

        ItemStack kitItem = settings.getKit() == null
                ? named(Material.BARRIER, GuiService.text("bot-menu.no-kit-selected"), List.of(GuiService.text("bot-menu.click-to-choose-a-whitelisted-kit")))
                : decorate(settings.getKit().getDisplayItem().convertToItem(), GuiService.text("bot-menu.selected-kit"),
                List.of(GuiService.text("bot-menu.current", settings.getKit().getName()), GuiService.text("bot-menu.click-to-change-it")));
        GuiService.control(menu, "kit-item", 15, new SGButton(kitItem).withListener(e -> {
            e.setCancelled(true);
            openKitSelector((Player) e.getWhoClicked(), settings, 0);
        }));

        ItemStack comparator = new ItemStack(Material.COMPARATOR);
        ItemMeta cmp = comparator.getItemMeta();
        cmp.setDisplayName(GuiService.text("bot-menu.advanced-settings"));
        cmp.setLore(Arrays.asList(
                GuiService.text("bot-menu.click-to-change-the-bot-settings"),
                GuiService.text("bot-menu.includes-map-rounds-invulnerability"),
                GuiService.text("bot-menu.range-w-taps-sprint-hits-and-more")
        ));
        comparator.setItemMeta(cmp);
        GuiService.control(menu, "comparator", 22, new SGButton(comparator).withListener(e -> {
            e.setCancelled(true);
            openSettings((Player) e.getWhoClicked(), settings);
        }));

        GuiService.open(player, menu);
    }

    private void openSettings(Player player, BotSettings settings) {
        SGMenu menu = plugin.getMenus().create("bots.settings", 4);
        fill(menu, Material.GRAY_STAINED_GLASS_PANE, " ");

        GuiService.control(menu, "map", 10, new SGButton(named(Material.MAP,
                GuiService.text("bot-menu.map"),
                List.of(
                        GuiService.text("bot-menu.current", nameOf(settings.getArena())),
                        GuiService.text("bot-menu.click-to-choose-another-arena")
                )
        )).withListener(e -> {
            e.setCancelled(true);
            openArenaSelector((Player) e.getWhoClicked(), settings, 0);
        }));

        GuiService.control(menu, "preset", 11, new SGButton(named(Material.BOOKSHELF,
                GuiService.text("bot-menu.preset"),
                List.of(
                        GuiService.text("bot-menu.current", (settings.getPreset() == null ? "Custom" : settings.getPreset().getDisplayName())),
                        GuiService.text("bot-menu.click-to-choose-a-settings-preset")
                )
        )).withListener(e -> {
            e.setCancelled(true);
            openPresetSelector((Player) e.getWhoClicked(), settings);
        }));

        GuiService.control(menu, "invulnerability", 12, new SGButton(named(Material.TOTEM_OF_UNDYING,
                GuiService.text("bot-menu.invulnerability"),
                List.of(
                        GuiService.text("bot-menu.state", (settings.isInvulnerability() ? "§aOn" : "§cOff")),
                        GuiService.text("bot-menu.when-on-neither-side-loses-hp"),
                        GuiService.text("bot-menu.hits-still-animate-and-apply-knockback")
                )
        )).withListener(e -> {
            e.setCancelled(true);
            settings.setInvulnerability(!settings.isInvulnerability());
            openSettings((Player) e.getWhoClicked(), settings);
        }));

        GuiService.control(menu, "frequent-sprint-hits", 13, adjustButton(
                Material.FEATHER,
                GuiService.text("bot-menu.frequent-sprint-hits"),
                settings.getFrequentSprintHitsPercent() + "%",
                GuiService.text("bot-menu.left-click-5-right-click-5"),
                click -> {
                    if (click == ClickType.RIGHT) settings.setFrequentSprintHitsPercent(settings.getFrequentSprintHitsPercent() - 5);
                    else settings.setFrequentSprintHitsPercent(settings.getFrequentSprintHitsPercent() + 5);
                },
                player,
                settings
        ));

        GuiService.control(menu, "frequent-w-taps", 14, adjustButton(
                Material.SUGAR,
                GuiService.text("bot-menu.frequent-w-taps"),
                settings.getFrequentWTapsPercent() + "%",
                GuiService.text("bot-menu.left-click-5-right-click-5"),
                click -> {
                    if (click == ClickType.RIGHT) settings.setFrequentWTapsPercent(settings.getFrequentWTapsPercent() - 5);
                    else settings.setFrequentWTapsPercent(settings.getFrequentWTapsPercent() + 5);
                },
                player,
                settings
        ));

        GuiService.control(menu, "frequent-range", 15, adjustButton(
                Material.ENDER_EYE,
                GuiService.text("bot-menu.frequent-range"),
                String.format("%.2f", settings.getFrequentRange()),
                GuiService.text("bot-menu.left-click-0-05-right-click-0-05"),
                click -> {
                    if (click == ClickType.RIGHT) settings.setFrequentRange(settings.getFrequentRange() - 0.05D);
                    else settings.setFrequentRange(settings.getFrequentRange() + 0.05D);
                },
                player,
                settings
        ));

        GuiService.control(menu, "strafe-chance", 16, adjustButton(
                Material.LEATHER_BOOTS,
                GuiService.text("bot-menu.strafe-chance"),
                settings.getStrafePercent() + "%",
                GuiService.text("bot-menu.left-click-5-right-click-5"),
                click -> {
                    if (click == ClickType.RIGHT) settings.setStrafePercent(settings.getStrafePercent() - 5);
                    else settings.setStrafePercent(settings.getStrafePercent() + 5);
                },
                player,
                settings
        ));

        GuiService.control(menu, "move-speed", 19, adjustButton(
                Material.SOUL_TORCH,
                GuiService.text("bot-menu.move-speed"),
                String.format("%.2f", settings.getMoveSpeed()),
                GuiService.text("bot-menu.left-click-0-01-right-click-0-01"),
                click -> {
                    if (click == ClickType.RIGHT) settings.setMoveSpeed(settings.getMoveSpeed() - 0.01D);
                    else settings.setMoveSpeed(settings.getMoveSpeed() + 0.01D);
                },
                player,
                settings
        ));

        GuiService.control(menu, "shield-use", 20, adjustButton(
                Material.SHIELD,
                GuiService.text("bot-menu.shield-use"),
                settings.getShieldUsePercent() + "%",
                GuiService.text("bot-menu.left-click-5-right-click-5"),
                click -> {
                    if (click == ClickType.RIGHT) settings.setShieldUsePercent(settings.getShieldUsePercent() - 5);
                    else settings.setShieldUsePercent(settings.getShieldUsePercent() + 5);
                },
                player,
                settings
        ));

        GuiService.control(menu, "shield-break-cooldown", 21, adjustButton(
                Material.IRON_AXE,
                GuiService.text("bot-menu.shield-break-cooldown"),
                settings.getShieldBreakCooldownTicks() + "t",
                GuiService.text("bot-menu.left-click-5t-right-click-5t"),
                click -> {
                    if (click == ClickType.RIGHT) settings.setShieldBreakCooldownTicks(settings.getShieldBreakCooldownTicks() - 5);
                    else settings.setShieldBreakCooldownTicks(settings.getShieldBreakCooldownTicks() + 5);
                },
                player,
                settings
        ));

        GuiService.control(menu, "shield-break-toggle", 22, new SGButton(named(Material.IRON_AXE,
                GuiService.text("bot-menu.shield-break-toggle"),
                List.of(
                        GuiService.text("bot-menu.state", (settings.isShieldBreakEnabled() ? "§aOn" : "§cOff")),
                        GuiService.text("bot-menu.if-the-player-blocks-and-the-bot-has"),
                        GuiService.text("bot-menu.it-will-try-to-disable-the-shield")
                )
        )).withListener(e -> {
            e.setCancelled(true);
            settings.setShieldBreakEnabled(!settings.isShieldBreakEnabled());
            openSettings((Player) e.getWhoClicked(), settings);
        }));

        GuiService.control(menu, "max-chase-distance", 23, adjustButton(
                Material.COMPASS,
                GuiService.text("bot-menu.max-chase-distance"),
                String.format("%.1f", settings.getMaxChaseDistance()),
                GuiService.text("bot-menu.left-click-1-0-right-click-1-0"),
                click -> {
                    if (click == ClickType.RIGHT) settings.setMaxChaseDistance(settings.getMaxChaseDistance() - 1.0D);
                    else settings.setMaxChaseDistance(settings.getMaxChaseDistance() + 1.0D);
                },
                player,
                settings
        ));

        GuiService.control(menu, "desync-snap-distance", 24, adjustButton(
                Material.ENDER_PEARL,
                GuiService.text("bot-menu.desync-snap-distance"),
                String.format("%.1f", settings.getDesyncTeleportDistance()),
                GuiService.text("bot-menu.left-click-0-5-right-click-0-5"),
                click -> {
                    if (click == ClickType.RIGHT) settings.setDesyncTeleportDistance(settings.getDesyncTeleportDistance() - 0.5D);
                    else settings.setDesyncTeleportDistance(settings.getDesyncTeleportDistance() + 0.5D);
                },
                player,
                settings
        ));

        GuiService.control(menu, "rounds-to-win", 25, adjustButton(
                Material.REDSTONE_TORCH,
                GuiService.text("bot-menu.round-target-label"),
                Integer.toString(settings.getRounds()),
                GuiService.text("bot-menu.left-click-1-right-click-1"),
                click -> {
                    if (click == ClickType.RIGHT) settings.setRounds(settings.getRounds() - 1);
                    else settings.setRounds(settings.getRounds() + 1);
                },
                player,
                settings
        ));

        GuiService.control(menu, "reaction-delay", 30, adjustButton(
                Material.CLOCK,
                GuiService.text("bot-menu.reaction-delay"),
                settings.getReactionTicks() + "t / " + settings.getAttackDelayTicks() + "t",
                GuiService.text("bot-menu.left-click-faster-right-click-slower"),
                click -> {
                    if (click == ClickType.RIGHT) {
                        settings.setReactionTicks(settings.getReactionTicks() + 1);
                        settings.setAttackDelayTicks(settings.getAttackDelayTicks() + 1);
                    } else {
                        settings.setReactionTicks(settings.getReactionTicks() - 1);
                        settings.setAttackDelayTicks(settings.getAttackDelayTicks() - 1);
                    }
                },
                player,
                settings
        ));

        GuiService.control(menu, "retreat-speed", 31, adjustButton(
                Material.RABBIT_FOOT,
                GuiService.text("bot-menu.retreat-speed"),
                String.format("%.2f", settings.getRetreatSpeed()),
                GuiService.text("bot-menu.left-click-0-01-right-click-0-01"),
                click -> {
                    if (click == ClickType.RIGHT) settings.setRetreatSpeed(settings.getRetreatSpeed() - 0.01D);
                    else settings.setRetreatSpeed(settings.getRetreatSpeed() + 0.01D);
                },
                player,
                settings
        ));

        GuiService.control(menu, "close-retreat-speed", 32, adjustButton(
                Material.TURTLE_HELMET,
                GuiService.text("bot-menu.close-retreat-speed"),
                String.format("%.2f", settings.getCloseRetreatSpeed()),
                GuiService.text("bot-menu.left-click-0-01-right-click-0-01"),
                click -> {
                    if (click == ClickType.RIGHT) settings.setCloseRetreatSpeed(settings.getCloseRetreatSpeed() - 0.01D);
                    else settings.setCloseRetreatSpeed(settings.getCloseRetreatSpeed() + 0.01D);
                },
                player,
                settings
        ));

        GuiService.control(menu, "back", 33, new SGButton(named(Material.ARROW, GuiService.text("bot-menu.return-label"), List.of(GuiService.text("bot-menu.return-to-the-main-menu"))))
                .withListener(e -> {
                    e.setCancelled(true);
                    openMain((Player) e.getWhoClicked(), settings);
                }));

        GuiService.open(player, menu);
    }

    private void openPresetSelector(Player player, BotSettings settings) {
        SGMenu menu = plugin.getMenus().create("bots.presets", 3);
        fill(menu, Material.BROWN_STAINED_GLASS_PANE, " ");

        Kit kit = settings.getKit();
        String kitName = kit != null ? kit.getName() : "";
        String resolvedCategory = resolveCategory(kitName);

        int slot = 10;
        for (BotPreset preset : BotPreset.values()) {
            // Filter: only show presets that match the kit category
            if (resolvedCategory != null && !preset.getKitCategory().equalsIgnoreCase(resolvedCategory)) {
                continue;
            }

            menu.setButton(slot++, new SGButton(named(Material.WRITABLE_BOOK,
                    "§6" + preset.getDisplayName(),
                    preset.getLore()))
                    .withListener(e -> {
                        e.setCancelled(true);
                        settings.applyPreset(preset);
                        openSettings((Player) e.getWhoClicked(), settings);
                    }));
        }

        GuiService.control(menu, "back", 22, new SGButton(named(Material.ARROW, GuiService.text("bot-menu.return-label"), List.of(GuiService.text("bot-menu.return-to-settings"))))
                .withListener(e -> {
                    e.setCancelled(true);
                    openSettings((Player) e.getWhoClicked(), settings);
                }));

        GuiService.open(player, menu);
    }

    private String resolveCategory(String kitName) {
        if (kitName == null) return null;
        String lower = kitName.toLowerCase();
        if (lower.contains("beast")) return "beast";
        if (lower.contains("smp")) return "smp";
        if (lower.contains("pot")) return "pot";
        if (lower.contains("nethop") || lower.contains("netherite")) return "nethop";
        return null;
    }

    private SGButton adjustButton(Material material, String name, String value, String help,
                                  java.util.function.Consumer<ClickType> action,
                                  Player player, BotSettings settings) {
        return new SGButton(named(material, name, List.of("§7Value: §f" + value, help)))
                .withListener(e -> {
                    e.setCancelled(true);
                    action.accept(e.getClick());
                    openSettings(player, settings);
                });
    }

    private void openArenaSelector(Player player, BotSettings settings, int page) {
        final int rows = 3;
        final int pageSize = rows * 9;
        int maxSlot = Math.max(0, DuelManager.stor.arenas.size() - 1);
        int maxPage = maxSlot / pageSize;

        SGMenu menu = plugin.getMenus().create("bots.arenas", rows, (page + 1), (maxPage + 1));
        fill(menu, Material.BLUE_STAINED_GLASS_PANE, " ");

        int idx = 0;
        for (Arena arena : DuelManager.stor.arenas) {
            if (arena.deactivated) continue;
            int local = idx++;
            int pg = local / pageSize;
            if (pg != page) continue;
            int slot = local % pageSize;
            ItemStack stack = decorate(arena.getDisplayItem().convertToItem(), "§b" + arena.getName(),
                    List.of(GuiService.text("bot-menu.click-to-select-this-arena")));
            GuiService.entry(menu, arena.getName(), slot, new SGButton(stack).withListener(e -> {
                e.setCancelled(true);
                settings.setArena(arena);
                openSettings((Player) e.getWhoClicked(), settings);
            }));
        }

        navigationButtons(menu, page, maxPage,
                newPage -> openArenaSelector(player, settings, newPage),
                () -> openSettings(player, settings));

        GuiService.open(player, menu);
    }

    private void openKitSelector(Player player, BotSettings settings, int page) {
        final int rows = 6;
        final int pageSize = rows * 9;
        List<Kit> allowedKits = settings.getAllowedPublicKits();
        int maxSlot = Math.max(0, allowedKits.size() - 1);
        int maxPage = Math.max(0, maxSlot / pageSize);

        SGMenu menu = plugin.getMenus().create("bots.kits", rows, (page + 1), (maxPage + 1));
        fill(menu, Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");

        if (allowedKits.isEmpty()) {
            GuiService.control(menu, "no-whitelisted-kits", 22, new SGButton(named(Material.BARRIER, GuiService.text("bot-menu.no-whitelisted-kits"),
                    List.of(GuiService.text("bot-menu.edit-allowed-kit-names-in-bot-settings"), GuiService.text("bot-menu.none-of-the-current-duel-kits-matched"))))
                    .withListener(e -> e.setCancelled(true)));
            navigationButtons(menu, page, maxPage,
                    newPage -> openKitSelector(player, settings, newPage),
                    () -> openMain(player, settings));
            GuiService.open(player, menu);
            return;
        }

        int start = page * pageSize;
        int end = Math.min(start + pageSize, allowedKits.size());
        for (int i = start; i < end; i++) {
            Kit kit = allowedKits.get(i);
            int local = i - start;
            GuiService.entry(menu, kit.getName(), local, new SGButton(decorate(kit.getDisplayItem().convertToItem(),
                    "§e" + kit.getName(), List.of(GuiService.text("bot-menu.click-to-select-this-kit"))))
                    .withListener(e -> {
                        e.setCancelled(true);
                        settings.setKit(kit);
                        openMain((Player) e.getWhoClicked(), settings);
                    }));
        }

        addCustomKitButtons(player, settings, menu);
        navigationButtons(menu, page, maxPage,
                newPage -> openKitSelector(player, settings, newPage),
                () -> openMain(player, settings));

        GuiService.open(player, menu);
    }

    private void addCustomKitButtons(Player player, BotSettings settings, SGMenu menu) {
        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (ep == null || ep.getMainPlayer() == null) return;

        GuiService.control(menu, "pick-personal-kit", 52, new SGButton(named(Material.BLAZE_POWDER,
                GuiService.text("bot-menu.selected-custom-kit"),
                List.of(GuiService.text("bot-menu.use-your-selected-custom-kit"))))
                .withListener(e -> {
                    e.setCancelled(true);
                    CustomKit customKit = selectedCustomKit(ep, player);
                    if (customKit == null) {
                        Messages.send(player, "messages.bot-menu.you-do-not-have-a-selected-custom-kit");
                        return;
                    }

                    Kit kit = customKit.toKit();
                    kit.setName("Custom kit");
                    kit.setDisplayItem(new SerializableItem(named(Material.BLAZE_POWDER, GuiService.text("bot-menu.custom-kit"), List.of()).clone()));
                    settings.setKit(kit);
                    openMain((Player) e.getWhoClicked(), settings);
                }));

        GuiService.control(menu, "pick-per-player-kit", 53, new SGButton(named(Material.BLAZE_ROD,
                GuiService.text("bot-menu.selected-per-custom-kit"),
                List.of(GuiService.text("bot-menu.use-your-selected-per-custom-kit"))))
                .withListener(e -> {
                    e.setCancelled(true);
                    CustomKit customKit = selectedCustomKit(ep, player);
                    if (customKit == null) {
                        Messages.send(player, "messages.bot-menu.you-do-not-have-a-selected-custom-kit");
                        return;
                    }

                    Kit kit = customKit.toKit();
                    kit.setName("Per custom kit");
                    kit.setPk(true);
                    kit.setDisplayItem(new SerializableItem(named(Material.BLAZE_ROD, GuiService.text("bot-menu.per-custom-kit"), List.of()).clone()));
                    settings.setKit(kit);
                    openMain((Player) e.getWhoClicked(), settings);
                }));
    }

    private CustomKit selectedCustomKit(ExtendedPlayer ep, Player player) {
        Integer idx = ep.getSelectedKit().getOrDefault(player.getUniqueId(), 1);
        CustomKit[] kits = ep.getMainPlayer().getKits();
        if (idx < 0 || idx >= kits.length) return null;
        return kits[idx];
    }

    private void navigationButtons(SGMenu menu, int page, int maxPage,
                                   java.util.function.IntConsumer onPage,
                                   Runnable onBack) {
        if (page > 0) {
            GuiService.control(menu, "previous", menu.getPageSize() - 10,
                    new SGButton(named(Material.ARROW, GuiService.text("bot-menu.previous"), List.of()))
                            .withListener(e -> {
                                e.setCancelled(true);
                                onPage.accept(page - 1);
                            }));
        }
        if (page < maxPage) {
            GuiService.control(menu, "next", menu.getPageSize() - 9,
                    new SGButton(named(Material.ARROW, GuiService.text("bot-menu.next"), List.of()))
                            .withListener(e -> {
                                e.setCancelled(true);
                                onPage.accept(page + 1);
                            }));
        }
        GuiService.control(menu, "back", menu.getPageSize() - 1,
                new SGButton(named(Material.BARRIER, GuiService.text("bot-menu.back"), List.of(GuiService.text("bot-menu.go-back"))))
                        .withListener(e -> {
                            e.setCancelled(true);
                            onBack.run();
                        }));
    }

    private void fill(SGMenu menu, Material material, String name) {
        for (int i = 0; i < menu.getPageSize(); i++) {
            if (menu.getButton(i) != null) continue;
            menu.setButton(i, new SGButton(named(material, name, List.of())).withListener(e -> e.setCancelled(true)));
        }
    }

    private ItemStack named(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null && !lore.isEmpty()) meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack decorate(ItemStack stack, String name, List<String> lore) {
        ItemStack out = stack.clone();
        ItemMeta meta = out.getItemMeta();
        if (name != null) meta.setDisplayName(name);
        if (lore != null && !lore.isEmpty()) meta.setLore(lore);
        out.setItemMeta(meta);
        return out;
    }

    private String nameOf(Arena arena) {
        return arena == null ? "None" : arena.getName();
    }
}
