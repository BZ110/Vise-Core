package org.albi.core.modules.LavaRising.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.albi.core.Core;
import org.albi.core.util.ConfigValues;
import org.albi.core.util.WebhookClient;
import org.albi.core.modules.LavaRising.LavaRisingManager;
import org.albi.core.modules.LavaRising.helpers.LavaRisingGameInstance;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Watches the lava event lobby, counts players, starts the countdown,
 * fires a webhook, and launches the match when ready.
 */
public class LavaLobbyWatcher implements Listener {

    private final Core plugin;
    private final String lavaWorld;
    private final int needPlayers;
    private final int spectateY;
    private final int lobbyWait;
    private final int reminderInterval;
    private final int webhookWarn;

    private final Set<UUID> lobby = new HashSet<>();
    private BukkitRunnable countdown;

    public LavaLobbyWatcher(Core plugin) {
        this.plugin = plugin;

        lavaWorld = plugin.getConfig().getString("lava_rising.lava_rising_world", "lavaevent");
        needPlayers = plugin.getConfig().getInt("lava_rising.pregame.required_players", 5);
        spectateY = plugin.getConfig().getInt("lava_rising.pregame.spectate_height", 100);
        lobbyWait = plugin.getConfig().getInt("lava_rising.pregame.lobby_wait_seconds", 60);
        reminderInterval = plugin.getConfig().getInt("lava_rising.pregame.reminder_interval_seconds", 10);
        webhookWarn = plugin.getConfig().getInt("lava_rising.webhook_warn_seconds", 30);

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /* ---------------- world change handling --------------- */

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (e.getFrom() == null) return; // safety on first login for some forks
        Player p = e.getPlayer();

        // left the lava world
        if (e.getFrom().getName().equalsIgnoreCase(lavaWorld)) {
            handleLeave(p);
            return;
        }

        // did not enter the lava world
        if (!p.getWorld().getName().equalsIgnoreCase(lavaWorld)) return;

        // active game, late joiner becomes spectator
        if (LavaRisingManager.playing) {
            LavaRisingGameInstance inst = LavaRisingManager.getActiveInstance();
            if (inst == null) return;

            p.setGameMode(GameMode.SPECTATOR);
            Location spec = inst.getArena().getSpawn()
                    .convertToLocation()
                    .clone()
                    .add(0, spectateY, 0);
            p.teleportAsync(spec);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                p.setGameMode(GameMode.SPECTATOR);
            }, 2L);
            return;
        } else {
            if(Objects.requireNonNull(Bukkit.getWorld(lavaWorld)).getDifficulty().equals(Difficulty.HARD))
            Objects.requireNonNull(Bukkit.getWorld(lavaWorld)).setDifficulty(Difficulty.PEACEFUL);
        }

        // lobby phase
        lobby.add(p.getUniqueId());
        broadcast("&e" + p.getName() + " &7has joined. (&e" + lobby.size() + "/" + needPlayers + "&7)");
        tryStartCountdown();
    }

    /* ---------------- leave handlers ---------------------- */

    @EventHandler public void onQuit(PlayerQuitEvent e) { handleLeave(e.getPlayer()); }
    @EventHandler public void onKick(PlayerKickEvent e) { handleLeave(e.getPlayer()); }

    private void handleLeave(Player p) {
        if (!lobby.remove(p.getUniqueId())) return;
        if (countdown != null && lobby.size() < needPlayers) {
            countdown.cancel();
            countdown = null;
        }
    }

    /* ---------------- countdown logic --------------------- */

    private void tryStartCountdown() {
        if (lobby.size() < needPlayers || countdown != null) return;

        // clickable broadcast
        TextComponent base = new TextComponent("\n\n  LAVA RISING EVENT IS STARTING!\n      ");
        TextComponent join = new TextComponent("[Join]");
        join.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
        join.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lavaspawn"));
        base.addExtra(join);
        base.addExtra("\n\n");
        Bukkit.spigot().broadcast(base);

        countdown = new BukkitRunnable() {
            int remain = lobbyWait;
            @Override public void run() {
                if (lobby.size() < needPlayers || LavaRisingManager.playing) {
                    cancel();
                    countdown = null;
                    return;
                }

                if (remain == webhookWarn) fireWebhook();

                if (remain % reminderInterval == 0 || remain <= 5) {
                    broadcast("&eMatch begins in &c" + remain + " &eseconds.");
                }

                if (remain-- <= 0) {
                    cancel();
                    countdown = null;
                    startMatch();
                }
            }
        };
        countdown.runTaskTimer(plugin, 20L, 20L);
    }

    private void startMatch() {
        if (LavaRisingManager.playing) return;

        LavaRisingGameInstance match = new LavaRisingGameInstance(plugin);
        LavaRisingManager.lavaRisingListener.hook(match);
        match.start();
        LavaRisingManager.playing = true;
        lobby.clear(); // reset for next round
    }

    /* ---------------- helpers ----------------------------- */

    private void broadcast(String msg) {
        World w = Bukkit.getWorld(lavaWorld);
        if (w == null) return;
        for (Player p : w.getPlayers()) {
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
    }

    /**
     * Sends a Discord embed with an orange‑gold colour when the
     * lobby countdown reaches the configured warning threshold.
     */
    private void fireWebhook() {
        var endpoint = ConfigValues.httpEndpoint(ConfigValues.envOrYaml(plugin.getConfig(),
                "lava_rising.webhook_url", "VISE_LAVA_WEBHOOK_URL"));
        if (endpoint == null) return;

        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Lava Rising");
        embed.addProperty("description", "Starting in " + webhookWarn + " seconds on " + plugin.getServerName() + ".");
        embed.addProperty("color", 0xFFA500);
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonObject payload = new JsonObject();
        payload.add("embeds", embeds);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!WebhookClient.postJson(endpoint, payload)) {
                    plugin.getLogger().warning("[LavaRising] The webhook rejected the countdown notification.");
                }
            } catch (IOException e) {
                plugin.getLogger().warning("[LavaRising] Could not send the countdown notification.");
            }
        });
    }
}
