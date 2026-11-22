package org.albi.core.commands;

import org.albi.core.gui.Messages;

import org.albi.core.gui.GuiService;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.data.StatManagement;
import org.albi.core.util.ConfigValues;
import org.albi.core.util.ProxyTransfer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class TravelCommand extends BaseCommand {
    private static final int INSTANCE_SLOTS = 8;
    private final long offlineThresholdMillis;

    private final Core plugin;
    private final List<Region> regions;
    // Instance snapshots are written by the async refresh and read by inventory handlers.
    private volatile List<Instance> instances = List.of();

    public TravelCommand(Core plugin) {
        this.plugin = plugin;
        regions = loadRegions();
        offlineThresholdMillis = ConfigValues.seconds(plugin.getConfig(), "network.travel.offline_after_seconds", 10, 1, 3600);
        int refreshTicks = ConfigValues.integer(plugin.getConfig(), "network.travel.refresh_ticks", 100, 20, 12_000);
        var shared = StatManagement.getSharedState();
        if (shared.isEnabled()) {
            var refreshing = new AtomicBoolean();
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                if (!refreshing.compareAndSet(false, true)) return;
                try {
                    instances = shared.healthSnapshots().stream().map(this::readInstance).filter(Objects::nonNull)
                            .distinct().sorted(Comparator.comparing(Instance::name)).toList();
                } finally {
                    refreshing.set(false);
                }
            }, 0L, refreshTicks);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageAPI().sendMessageLang(sender, "players_only");
            return true;
        }

        var profile = plugin.getExtendedPlayer(player);
        if (profile.checkCooldown(command.getName())) {
            plugin.getMessageAPI().sendMessageLang(player, "cooldown");
            return true;
        }
        profile.addCooldown(command.getName(), plugin.getConfig().getInt("cooldowns.travel", 3));
        openInstanceMenu(player);
        return true;
    }

    private void openInstanceMenu(Player player) {
        String currentRegion = plugin.getConfig().getString("region", "EU");
        List<Instance> local = instances.stream()
                .filter(instance -> instance.isInRegion(currentRegion))
                .limit(INSTANCE_SLOTS * 6L).toList();
        int rows = Math.max(1, (local.size() + INSTANCE_SLOTS - 1) / INSTANCE_SLOTS);
        SGMenu menu = plugin.getMenus().create("travel.instances", rows);
        for (int i = 0; i < local.size(); i++) {
            Instance instance = local.get(i);
            boolean current = instance.name().equalsIgnoreCase(plugin.getServerName());
            Material material = current ? Material.EMERALD_BLOCK : Material.DIAMOND_BLOCK;
            var lore = !instance.online() ? List.of(GuiService.text("travel-command.offline"))
                    : List.of("§7Online: §f" + instance.players(), current ? "§eYou are here." : "§eClick to travel.");
            var button = new SGButton(icon(material, "§b" + instance.name(), lore, null));
            if (instance.online() && !current) {
                button.withListener(event -> transferAfterSave(player, ready -> ProxyTransfer.connect(plugin, ready, instance.name())));
            }
            GuiService.entry(menu, instance.name(), (i / INSTANCE_SLOTS) * 9 + (i % INSTANCE_SLOTS), button);
        }

        if (!regions.isEmpty()) {
            GuiService.control(menu, "region-selector", 8, new SGButton(icon(Material.COMPASS, GuiService.text("travel-command.region-selector"),
                    List.of(GuiService.text("travel-command.travel-between-regions")), null)).withListener(event -> openRegionSelector(player)));
        } else if (local.isEmpty()) {
            GuiService.control(menu, "no-instances-available", 4, new SGButton(icon(Material.BARRIER, GuiService.text("travel-command.no-instances-available"), List.of(), null)));
        }
        GuiService.open(player, menu);
    }

    private void openRegionSelector(Player player) {
        int rows = (regions.size() + 8) / 9;
        SGMenu menu = plugin.getMenus().create("travel.regions", rows);
        String currentRegion = plugin.getConfig().getString("region", "EU");
        List<Instance> snapshot = instances;

        for (int i = 0; i < regions.size(); i++) {
            Region region = regions.get(i);
            boolean current = region.id().equalsIgnoreCase(currentRegion);
            int total = snapshot.stream().filter(instance -> instance.online() && instance.isInRegion(region.id()))
                    .mapToInt(Instance::players).sum();

            List<String> lore = new ArrayList<>();
            if (!region.location().isBlank()) lore.add(GuiService.text("travel-command.hosted-in", region.location()));
            lore.add(GuiService.text("travel-command.players", total));
            lore.add(current ? GuiService.text("travel-command.you-are-here") : GuiService.text("travel-command.click-to-transfer"));

            Material material = region.texture() == null ? Material.COMPASS : Material.PLAYER_HEAD;
            SGButton button = new SGButton(icon(material, "§b" + region.name(), lore, region.texture()));
            if (!current) {
                button.withListener(event -> transferAfterSave(player, ready -> {
                    Messages.send(ready, "messages.travel-command.transferring-to", region.name());
                    ready.transfer(region.host(), region.port());
                }));
            }
            GuiService.entry(menu, region.id(), i, button);
        }
        GuiService.open(player, menu);
    }

    private List<Region> loadRegions() {
        var configured = plugin.getConfig().getConfigurationSection("network.travel.regions");
        if (configured == null) return List.of();

        List<Region> destinations = new ArrayList<>();
        for (String id : configured.getKeys(false)) {
            var settings = configured.getConfigurationSection(id);
            if (settings == null) continue;
            String host = settings.getString("host", "").strip();
            int port = settings.getInt("port", 25565);
            if (host.isEmpty() || port < 1 || port > 65535) {
                plugin.getLogger().warning("Ignoring travel destination " + id + " because its host or port is invalid.");
                continue;
            }
            URI texture = ConfigValues.httpEndpoint(settings.getString("texture_url", ""));
            if (texture != null && !"textures.minecraft.net".equalsIgnoreCase(texture.getHost())) texture = null;
            destinations.add(new Region(id, settings.getString("name", id), settings.getString("location", ""),
                    host, port, texture));
            if (destinations.size() == 54) break;
        }
        return List.copyOf(destinations);
    }

    private ItemStack icon(Material material, String name, List<String> lore, URI texture) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        if (texture != null && meta instanceof SkullMeta skull) {
            try {
                var profile = Bukkit.createProfile(java.util.UUID.randomUUID());
                var textures = profile.getTextures();
                textures.setSkin(texture.toURL());
                profile.setTextures(textures);
                skull.setPlayerProfile(profile);
            } catch (IllegalArgumentException | IOException e) {
                plugin.getLogger().warning("A travel destination specifies an invalid Minecraft skin texture.");
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    private void transferAfterSave(Player player, Consumer<Player> transfer) {
        var profile = plugin.getExtendedPlayer(player);
        if (!profile.isReady() || !StatManagement.isSqlReady()) {
            Messages.send(player, "messages.travel-command.profile-save-unavailable");
            return;
        }
        player.closeInventory();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean saved = StatManagement.flushForTransfer(profile.getStat(), profile.getMainPlayer());
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (saved) transfer.accept(player);
                else Messages.send(player, "messages.travel-command.profile-save-unavailable");
            });
        });
    }

    private Instance readInstance(Map<String, String> data) {
        String name = data.get("server");
        if (name == null || name.isBlank()) return null;
        try {
            long timestamp = Long.parseLong(data.getOrDefault("timestamp_ms", "0"));
            int players = Integer.parseInt(data.getOrDefault("players_online", "0"));
            return new Instance(name, players, System.currentTimeMillis() - timestamp < offlineThresholdMillis);
        } catch (NumberFormatException e) {
            return null; // A stale or malformed heartbeat should not hide other instances.
        }
    }

    private record Region(String id, String name, String location, String host, int port, URI texture) {}

    private record Instance(String name, int players, boolean online) {
        private boolean isInRegion(String region) {
            return name.toUpperCase(Locale.ROOT).startsWith(region.toUpperCase(Locale.ROOT) + "-");
        }
    }
}
