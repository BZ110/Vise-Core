package org.albi.core.modules.Duels.AutomatedCombat;

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

        SGMenu menu = plugin.getGUI().create("§dBot Duel", 3);
        fill(menu, Material.BLACK_STAINED_GLASS_PANE, " ");

        ItemStack info = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§dDifficulty Information");
        infoMeta.setLore(settings.toLore());
        info.setItemMeta(infoMeta);
        menu.setButton(11, new SGButton(info).withListener(e -> {
            e.setCancelled(true);
            openSettings((Player) e.getWhoClicked(), settings);
        }));

        ItemStack start = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta startMeta = start.getItemMeta();
        startMeta.setDisplayName("§a§lSTART");
        startMeta.setLore(Arrays.asList(
                "§7Start a PvP duel against the bot.",
                "§6Experimental NPC combat. You may encounter bugs.",
                "§7Rounds to win: §f" + settings.getRounds()
        ));
        start.setItemMeta(startMeta);
        menu.setButton(13, new SGButton(start).withListener(e -> {
            e.setCancelled(true);
            manager.start((Player) e.getWhoClicked(), settings, callbacks);
        }));

        ItemStack kitItem = settings.getKit() == null
                ? named(Material.BARRIER, "§cNo Kit Selected", List.of("§7Click to choose a whitelisted kit."))
                : decorate(settings.getKit().getDisplayItem().convertToItem(), "§eSelected Kit",
                List.of("§7Current: §f" + settings.getKit().getName(), "§7Click to change it."));
        menu.setButton(15, new SGButton(kitItem).withListener(e -> {
            e.setCancelled(true);
            openKitSelector((Player) e.getWhoClicked(), settings, 0);
        }));

        ItemStack comparator = new ItemStack(Material.COMPARATOR);
        ItemMeta cmp = comparator.getItemMeta();
        cmp.setDisplayName("§eAdvanced Settings");
        cmp.setLore(Arrays.asList(
                "§7Click to change the bot settings.",
                "§7Includes map, rounds, invulnerability,",
                "§7range, W taps, sprint hits, and more."
        ));
        comparator.setItemMeta(cmp);
        menu.setButton(22, new SGButton(comparator).withListener(e -> {
            e.setCancelled(true);
            openSettings((Player) e.getWhoClicked(), settings);
        }));

        player.openInventory(menu.getInventory());
    }

    private void openSettings(Player player, BotSettings settings) {
        SGMenu menu = plugin.getGUI().create("§eBot Settings", 4);
        fill(menu, Material.GRAY_STAINED_GLASS_PANE, " ");

        menu.setButton(10, new SGButton(named(Material.MAP,
                "§bMAP",
                List.of(
                        "§7Current: §f" + nameOf(settings.getArena()),
                        "§7Click to choose another arena."
                )
        )).withListener(e -> {
            e.setCancelled(true);
            openArenaSelector((Player) e.getWhoClicked(), settings, 0);
        }));

        menu.setButton(11, new SGButton(named(Material.BOOKSHELF,
                "§6Preset",
                List.of(
                        "§7Current: §f" + (settings.getPreset() == null ? "Custom" : settings.getPreset().getDisplayName()),
                        "§7Click to choose a settings preset."
                )
        )).withListener(e -> {
            e.setCancelled(true);
            openPresetSelector((Player) e.getWhoClicked(), settings);
        }));

        menu.setButton(12, new SGButton(named(Material.TOTEM_OF_UNDYING,
                "§aInvulnerability",
                List.of(
                        "§7State: " + (settings.isInvulnerability() ? "§aOn" : "§cOff"),
                        "§7When on, neither side loses HP.",
                        "§7Hits still animate and apply knockback."
                )
        )).withListener(e -> {
            e.setCancelled(true);
            settings.setInvulnerability(!settings.isInvulnerability());
            openSettings((Player) e.getWhoClicked(), settings);
        }));

        menu.setButton(13, adjustButton(
                Material.FEATHER,
                "§dFrequent Sprint Hits",
                settings.getFrequentSprintHitsPercent() + "%",
                "§7Left click: +5%, Right click: -5%",
                click -> {
                    if (click == ClickType.RIGHT) settings.setFrequentSprintHitsPercent(settings.getFrequentSprintHitsPercent() - 5);
                    else settings.setFrequentSprintHitsPercent(settings.getFrequentSprintHitsPercent() + 5);
                },
                player,
                settings
        ));

        menu.setButton(14, adjustButton(
                Material.SUGAR,
                "§dFrequent W Taps",
                settings.getFrequentWTapsPercent() + "%",
                "§7Left click: +5%, Right click: -5%",
                click -> {
                    if (click == ClickType.RIGHT) settings.setFrequentWTapsPercent(settings.getFrequentWTapsPercent() - 5);
                    else settings.setFrequentWTapsPercent(settings.getFrequentWTapsPercent() + 5);
                },
                player,
                settings
        ));

        menu.setButton(15, adjustButton(
                Material.ENDER_EYE,
                "§dFrequent Range",
                String.format("%.2f", settings.getFrequentRange()),
                "§7Left click: +0.05, Right click: -0.05",
                click -> {
                    if (click == ClickType.RIGHT) settings.setFrequentRange(settings.getFrequentRange() - 0.05D);
                    else settings.setFrequentRange(settings.getFrequentRange() + 0.05D);
                },
                player,
                settings
        ));

        menu.setButton(16, adjustButton(
                Material.LEATHER_BOOTS,
                "§dStrafe Chance",
                settings.getStrafePercent() + "%",
                "§7Left click: +5%, Right click: -5%",
                click -> {
                    if (click == ClickType.RIGHT) settings.setStrafePercent(settings.getStrafePercent() - 5);
                    else settings.setStrafePercent(settings.getStrafePercent() + 5);
                },
                player,
                settings
        ));

        menu.setButton(19, adjustButton(
                Material.SOUL_TORCH,
                "§dMove Speed",
                String.format("%.2f", settings.getMoveSpeed()),
                "§7Left click: +0.01, Right click: -0.01",
                click -> {
                    if (click == ClickType.RIGHT) settings.setMoveSpeed(settings.getMoveSpeed() - 0.01D);
                    else settings.setMoveSpeed(settings.getMoveSpeed() + 0.01D);
                },
                player,
                settings
        ));

        menu.setButton(20, adjustButton(
                Material.SHIELD,
                "§dShield Use",
                settings.getShieldUsePercent() + "%",
                "§7Left click: +5%, Right click: -5%",
                click -> {
                    if (click == ClickType.RIGHT) settings.setShieldUsePercent(settings.getShieldUsePercent() - 5);
                    else settings.setShieldUsePercent(settings.getShieldUsePercent() + 5);
                },
                player,
                settings
        ));

        menu.setButton(21, adjustButton(
                Material.IRON_AXE,
                "§dShield Break Cooldown",
                settings.getShieldBreakCooldownTicks() + "t",
                "§7Left click: +5t, Right click: -5t",
                click -> {
                    if (click == ClickType.RIGHT) settings.setShieldBreakCooldownTicks(settings.getShieldBreakCooldownTicks() - 5);
                    else settings.setShieldBreakCooldownTicks(settings.getShieldBreakCooldownTicks() + 5);
                },
                player,
                settings
        ));

        menu.setButton(22, new SGButton(named(Material.IRON_AXE,
                "§dShield Break Toggle",
                List.of(
                        "§7State: " + (settings.isShieldBreakEnabled() ? "§aOn" : "§cOff"),
                        "§7If the player blocks and the bot has an axe,",
                        "§7it will try to disable the shield."
                )
        )).withListener(e -> {
            e.setCancelled(true);
            settings.setShieldBreakEnabled(!settings.isShieldBreakEnabled());
            openSettings((Player) e.getWhoClicked(), settings);
        }));

        menu.setButton(23, adjustButton(
                Material.COMPASS,
                "§dMax Chase Distance",
                String.format("%.1f", settings.getMaxChaseDistance()),
                "§7Left click: +1.0, Right click: -1.0",
                click -> {
                    if (click == ClickType.RIGHT) settings.setMaxChaseDistance(settings.getMaxChaseDistance() - 1.0D);
                    else settings.setMaxChaseDistance(settings.getMaxChaseDistance() + 1.0D);
                },
                player,
                settings
        ));

        menu.setButton(24, adjustButton(
                Material.ENDER_PEARL,
                "§dDesync Snap Distance",
                String.format("%.1f", settings.getDesyncTeleportDistance()),
                "§7Left click: +0.5, Right click: -0.5",
                click -> {
                    if (click == ClickType.RIGHT) settings.setDesyncTeleportDistance(settings.getDesyncTeleportDistance() - 0.5D);
                    else settings.setDesyncTeleportDistance(settings.getDesyncTeleportDistance() + 0.5D);
                },
                player,
                settings
        ));

        menu.setButton(25, adjustButton(
                Material.REDSTONE_TORCH,
                "§dRounds To Win",
                Integer.toString(settings.getRounds()),
                "§7Left click: +1, Right click: -1",
                click -> {
                    if (click == ClickType.RIGHT) settings.setRounds(settings.getRounds() - 1);
                    else settings.setRounds(settings.getRounds() + 1);
                },
                player,
                settings
        ));

        menu.setButton(30, adjustButton(
                Material.CLOCK,
                "§dReaction / Delay",
                settings.getReactionTicks() + "t / " + settings.getAttackDelayTicks() + "t",
                "§7Left click: faster, Right click: slower",
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

        menu.setButton(31, adjustButton(
                Material.RABBIT_FOOT,
                "§dRetreat Speed",
                String.format("%.2f", settings.getRetreatSpeed()),
                "§7Left click: +0.01, Right click: -0.01",
                click -> {
                    if (click == ClickType.RIGHT) settings.setRetreatSpeed(settings.getRetreatSpeed() - 0.01D);
                    else settings.setRetreatSpeed(settings.getRetreatSpeed() + 0.01D);
                },
                player,
                settings
        ));

        menu.setButton(32, adjustButton(
                Material.TURTLE_HELMET,
                "§dClose Retreat Speed",
                String.format("%.2f", settings.getCloseRetreatSpeed()),
                "§7Left click: +0.01, Right click: -0.01",
                click -> {
                    if (click == ClickType.RIGHT) settings.setCloseRetreatSpeed(settings.getCloseRetreatSpeed() - 0.01D);
                    else settings.setCloseRetreatSpeed(settings.getCloseRetreatSpeed() + 0.01D);
                },
                player,
                settings
        ));

        menu.setButton(33, new SGButton(named(Material.ARROW, "§eBack", List.of("§7Return to the main menu.")))
                .withListener(e -> {
                    e.setCancelled(true);
                    openMain((Player) e.getWhoClicked(), settings);
                }));

        player.openInventory(menu.getInventory());
    }

    private void openPresetSelector(Player player, BotSettings settings) {
        SGMenu menu = plugin.getGUI().create("§6Bot Presets", 3);
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

        menu.setButton(22, new SGButton(named(Material.ARROW, "§eBack", List.of("§7Return to settings.")))
                .withListener(e -> {
                    e.setCancelled(true);
                    openSettings((Player) e.getWhoClicked(), settings);
                }));

        player.openInventory(menu.getInventory());
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

        SGMenu menu = plugin.getGUI().create("§bSelect Map §7(" + (page + 1) + "/" + (maxPage + 1) + ")", rows);
        fill(menu, Material.BLUE_STAINED_GLASS_PANE, " ");

        int idx = 0;
        for (Arena arena : DuelManager.stor.arenas) {
            if (arena.deactivated) continue;
            int local = idx++;
            int pg = local / pageSize;
            if (pg != page) continue;
            int slot = local % pageSize;
            ItemStack stack = decorate(arena.getDisplayItem().convertToItem(), "§b" + arena.getName(),
                    List.of("§7Click to select this arena."));
            menu.setButton(slot, new SGButton(stack).withListener(e -> {
                e.setCancelled(true);
                settings.setArena(arena);
                openSettings((Player) e.getWhoClicked(), settings);
            }));
        }

        navigationButtons(menu, page, maxPage,
                newPage -> openArenaSelector(player, settings, newPage),
                () -> openSettings(player, settings));

        player.openInventory(menu.getInventory());
    }

    private void openKitSelector(Player player, BotSettings settings, int page) {
        final int rows = 6;
        final int pageSize = rows * 9;
        List<Kit> allowedKits = settings.getAllowedPublicKits();
        int maxSlot = Math.max(0, allowedKits.size() - 1);
        int maxPage = Math.max(0, maxSlot / pageSize);

        SGMenu menu = plugin.getGUI().create("§bSelect Whitelisted Kit §7(" + (page + 1) + "/" + (maxPage + 1) + ")", rows);
        fill(menu, Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");

        if (allowedKits.isEmpty()) {
            menu.setButton(22, new SGButton(named(Material.BARRIER, "§cNo Whitelisted Kits",
                    List.of("§7Edit ALLOWED_KIT_NAMES in BotSettings.", "§7None of the current duel kits matched.")))
                    .withListener(e -> e.setCancelled(true)));
            navigationButtons(menu, page, maxPage,
                    newPage -> openKitSelector(player, settings, newPage),
                    () -> openMain(player, settings));
            player.openInventory(menu.getInventory());
            return;
        }

        int start = page * pageSize;
        int end = Math.min(start + pageSize, allowedKits.size());
        for (int i = start; i < end; i++) {
            Kit kit = allowedKits.get(i);
            int local = i - start;
            menu.setButton(local, new SGButton(decorate(kit.getDisplayItem().convertToItem(),
                    "§e" + kit.getName(), List.of("§7Click to select this kit.")))
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

        player.openInventory(menu.getInventory());
    }

    private void addCustomKitButtons(Player player, BotSettings settings, SGMenu menu) {
        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (ep == null || ep.getMainPlayer() == null) return;

        menu.setButton(52, new SGButton(named(Material.BLAZE_POWDER,
                "§eSelected Custom Kit",
                List.of("§7Use your selected custom kit.")))
                .withListener(e -> {
                    e.setCancelled(true);
                    CustomKit customKit = selectedCustomKit(ep, player);
                    if (customKit == null) {
                        player.sendMessage("§cYou do not have a selected custom kit.");
                        return;
                    }

                    Kit kit = customKit.toKit();
                    kit.setName("Custom kit");
                    kit.setDisplayItem(new SerializableItem(named(Material.BLAZE_POWDER, "§eCustom kit", List.of()).clone()));
                    settings.setKit(kit);
                    openMain((Player) e.getWhoClicked(), settings);
                }));

        menu.setButton(53, new SGButton(named(Material.BLAZE_ROD,
                "§eSelected Per Custom Kit",
                List.of("§7Use your selected per custom kit.")))
                .withListener(e -> {
                    e.setCancelled(true);
                    CustomKit customKit = selectedCustomKit(ep, player);
                    if (customKit == null) {
                        player.sendMessage("§cYou do not have a selected custom kit.");
                        return;
                    }

                    Kit kit = customKit.toKit();
                    kit.setName("Per custom kit");
                    kit.setPk(true);
                    kit.setDisplayItem(new SerializableItem(named(Material.BLAZE_ROD, "§ePer custom kit", List.of()).clone()));
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
            menu.setButton(menu.getInventory().getSize() - 10,
                    new SGButton(named(Material.ARROW, "§e← Prev", List.of()))
                            .withListener(e -> {
                                e.setCancelled(true);
                                onPage.accept(page - 1);
                            }));
        }
        if (page < maxPage) {
            menu.setButton(menu.getInventory().getSize() - 9,
                    new SGButton(named(Material.ARROW, "§eNext →", List.of()))
                            .withListener(e -> {
                                e.setCancelled(true);
                                onPage.accept(page + 1);
                            }));
        }
        menu.setButton(menu.getInventory().getSize() - 1,
                new SGButton(named(Material.BARRIER, "§cBack", List.of("§7Go back.")))
                        .withListener(e -> {
                            e.setCancelled(true);
                            onBack.run();
                        }));
    }

    private void fill(SGMenu menu, Material material, String name) {
        for (int i = 0; i < menu.getInventory().getSize(); i++) {
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
