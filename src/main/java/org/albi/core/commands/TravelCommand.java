package org.albi.core.commands;

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
import redis.clients.jedis.params.ScanParams;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TravelCommand extends BaseCommand {
    private static final int INSTANCE_SLOTS = 8;
    private static final long REFRESH_TICKS = 100L;
    private static final long OFFLINE_THRESHOLD_MS = TimeUnit.SECONDS.toMillis(10);

    private final Core plugin;
    private final List<Region> regions;
    // Redis snapshots are written by the async refresh and read by inventory handlers.
    private volatile List<Instance> instances = List.of();

    public TravelCommand(Core plugin) {
        this.plugin = plugin;
        regions = loadRegions();
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshInstances, 0L, REFRESH_TICKS);
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
        SGMenu menu = plugin.getGUI().create("&bTravel Menu", rows);
        for (int i = 0; i < local.size(); i++) {
            Instance instance = local.get(i);
            boolean current = instance.name().equalsIgnoreCase(plugin.getServerName());
            Material material = current ? Material.EMERALD_BLOCK : Material.DIAMOND_BLOCK;
            var lore = !instance.online() ? List.of("§cOffline")
                    : List.of("§7Online: §f" + instance.players(), current ? "§eYou are here." : "§eClick to travel.");
            var button = new SGButton(icon(material, "§b" + instance.name(), lore, null));
            if (instance.online() && !current) {
                button.withListener(event -> {
                    player.closeInventory();
                    sendPlayerToServer(player, instance.name());
                });
            }
            menu.setButton((i / INSTANCE_SLOTS) * 9 + (i % INSTANCE_SLOTS), button);
        }

        if (!regions.isEmpty()) {
            menu.setButton(8, new SGButton(icon(Material.COMPASS, "§dRegion Selector",
                    List.of("§7Travel between regions."), null)).withListener(event -> openRegionSelector(player)));
        } else if (local.isEmpty()) {
            menu.setButton(4, new SGButton(icon(Material.BARRIER, "§cNo instances available", List.of(), null)));
        }
        player.openInventory(menu.getInventory());
    }

    private void openRegionSelector(Player player) {
        int rows = (regions.size() + 8) / 9;
        SGMenu menu = plugin.getGUI().create("&dSelect Region", rows);
        String currentRegion = plugin.getConfig().getString("region", "EU");
        List<Instance> snapshot = instances;

        for (int i = 0; i < regions.size(); i++) {
            Region region = regions.get(i);
            boolean current = region.id().equalsIgnoreCase(currentRegion);
            int total = snapshot.stream().filter(instance -> instance.online() && instance.isInRegion(region.id()))
                    .mapToInt(Instance::players).sum();

            List<String> lore = new ArrayList<>();
            if (!region.location().isBlank()) lore.add("§7Hosted in §f" + region.location());
            lore.add("§7Players: §f" + total);
            lore.add(current ? "§eYou are here." : "§eClick to transfer.");

            Material material = region.texture() == null ? Material.COMPASS : Material.PLAYER_HEAD;
            SGButton button = new SGButton(icon(material, "§b" + region.name(), lore, region.texture()));
            if (!current) {
                button.withListener(event -> {
                    player.closeInventory();
                    player.sendMessage("§7Transferring to §b" + region.name() + "§7...");
                    player.transfer(region.host(), region.port());
                });
            }
            menu.setButton(i, button);
        }
        player.openInventory(menu.getInventory());
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

    private void sendPlayerToServer(Player player, String serverName) {
        ProxyTransfer.connect(plugin, player, serverName);
    }

    private void refreshInstances() {
        var active = StatManagement.getRedis().withJedis(redis -> {
            List<Instance> snapshot = new ArrayList<>();
            ScanParams scan = new ScanParams().match("server:*:health:latest");
            String cursor = ScanParams.SCAN_POINTER_START;
            do {
                var page = redis.scan(cursor, scan);
                for (String key : page.getResult()) {
                    Instance instance = readInstance(redis.hgetAll(key));
                    if (instance != null) snapshot.add(instance);
                }
                cursor = page.getCursor();
            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));

            return snapshot.stream().distinct().sorted(Comparator.comparing(Instance::name)).toList();
        });
        instances = active == null ? List.of() : active;
    }

    private Instance readInstance(Map<String, String> data) {
        String name = data.get("server");
        if (name == null || name.isBlank()) return null;
        try {
            long timestamp = Long.parseLong(data.getOrDefault("timestamp_ms", "0"));
            int players = Integer.parseInt(data.getOrDefault("players_online", "0"));
            return new Instance(name, players, System.currentTimeMillis() - timestamp < OFFLINE_THRESHOLD_MS);
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
