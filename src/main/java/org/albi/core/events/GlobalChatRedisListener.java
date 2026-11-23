package org.albi.core.events;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.albi.core.Core;
import org.albi.core.data.SqlChatBuffer;
import org.albi.core.data.SqlNetworkState;
import org.albi.core.data.StatManagement;
import org.albi.core.util.ConfigValues;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import redis.clients.jedis.JedisPubSub;

import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public final class GlobalChatRedisListener {
    private static final Gson JSON = new Gson();
    private static final Pattern COLOR = Pattern.compile("(?i)[§&][0-9A-FK-ORX]|&#[0-9A-Fa-f]{6}");
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");
    private record Envelope(UUID id, String server, String player, String message) {}

    private final Core plugin;
    private final StatManagement.RedisWrapper redis;
    private final SqlChatBuffer sql;
    private final Cache<UUID, Boolean> delivered = CacheBuilder.newBuilder().maximumSize(16384)
            .expireAfterWrite(45, TimeUnit.SECONDS).build();
    private final Semaphore pendingSends = new Semaphore(128);
    private final AtomicBoolean polling = new AtomicBoolean();
    private volatile JedisPubSub pubSub;
    private volatile boolean stopped;
    private volatile boolean prepared;
    private long cursor = -1;
    private long nextCleanup;
    private long nextWarning;

    public GlobalChatRedisListener(Core plugin) {
        this.plugin = plugin;
        redis = StatManagement.getRedis();
        String scope = plugin.getConfig().getString("network.matchmaking.namespace", "default").strip().toLowerCase(Locale.ROOT);
        if (!scope.matches("[a-z0-9_-]{1,64}")) scope = "default";
        sql = SqlNetworkState.enabled(plugin.getConfig(), "chat") ? new SqlChatBuffer(StatManagement.getSql(), scope) : null;
    }

    public void start() {
        if (sql != null) {
            int ticks = ConfigValues.integer(plugin.getConfig(), "network.mysql_failover.poll_ticks", 20, 10, 40);
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::poll, 1, ticks);
        }
        if (redis.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(plugin, this::subscribe);
    }

    public void send(String player, String text) {
        if (stopped || text.isBlank() || (!redis.isEnabled() && sql == null) || !pendingSends.tryAcquire()) return;
        String cleaned = sanitize(text);
        if (cleaned.isBlank() || cleaned.length() > 512) {
            pendingSends.release();
            return;
        }
        var message = new Envelope(UUID.randomUUID(), plugin.getServerName().toLowerCase(Locale.ROOT), player, cleaned);
        String payload = JSON.toJson(message);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (stopped) return;
                boolean sent = sql == null
                        ? redis.withJedis(j -> j.publish("chat:global", payload)) != null
                        : StatManagement.getSharedState().publishChat(message.id(), payload);
                if (sql != null && StatManagement.isSqlReady()) {
                    if (writeSql(payload) && !sent) StatManagement.getSharedState().askToPublishChat(message.id(), payload);
                }
            } finally {
                pendingSends.release();
            }
        });
    }

    private boolean writeSql(String payload) {
        for (int attempt = 0; attempt < 3 && !stopped; attempt++) {
            try {
                ensureSql();
                sql.write(payload);
                return true;
            } catch (SQLException failure) {
                if (!SqlNetworkState.busy(failure)) { warn(failure); return false; }
                if (!pause(25L * (attempt + 1))) return false;
            }
        }
        return false;
    }

    private void poll() {
        if (stopped || !StatManagement.isSqlReady() || !polling.compareAndSet(false, true)) return;
        try {
            ensureSql();
            if (cursor < 0) cursor = sql.head();
            var rows = sql.after(cursor);
            for (var row : rows) {
                receive(row.payload());
                cursor = row.sequence();
            }
            long now = System.nanoTime();
            if (nextCleanup == 0 || now >= nextCleanup) {
                nextCleanup = now + TimeUnit.SECONDS.toNanos(30);
                sql.purge();
            }
        } catch (SQLException failure) {
            warn(failure);
        } finally {
            polling.set(false);
        }
    }

    private synchronized void ensureSql() throws SQLException {
        if (!prepared) {
            sql.prepare();
            prepared = true;
        }
    }

    private synchronized void warn(SQLException failure) {
        if ("42S02".equals(failure.getSQLState())) prepared = false;
        if (nextWarning != 0 && System.nanoTime() < nextWarning) return;
        nextWarning = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        plugin.getLogger().warning("[Help Me Please] MySQL global chat is temporarily unavailable (" + failure.getSQLState() + ").");
    }

    private void subscribe() {
        pubSub = new JedisPubSub() {
            @Override public void onMessage(String channel, String payload) {
                if ("chat:global".equals(channel)) receive(payload);
            }
        };
        while (!stopped && plugin.isEnabled()) {
            redis.withJedis(jedis -> {
                if (!stopped) jedis.subscribe(pubSub, "chat:global");
                return null;
            });
            if (!pause(2000)) return;
        }
    }

    private void receive(String payload) {
        if (stopped || payload.length() > 4096) return;
        Envelope msg;
        try {
            msg = JSON.fromJson(payload, Envelope.class);
        } catch (JsonParseException failure) {
            return;
        }
        if (msg == null || msg.server() == null || !msg.server().matches("[a-zA-Z0-9_-]{1,80}")
                || msg.player() == null || !msg.player().matches("[a-zA-Z0-9_]{1,16}")
                || msg.message() == null || msg.message().length() > 512
                || msg.server().equalsIgnoreCase(plugin.getServerName())) return;
        String message = sanitize(msg.message());
        if (message.isBlank() || plugin.getChatFilter().isBlocked(message)) return;
        if (msg.id() != null && delivered.asMap().putIfAbsent(msg.id(), true) != null) return;
        if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> deliver(msg, message));
    }

    private void deliver(Envelope entry, String text) {
        if (stopped) return;
        String prefix = "§7[" + entry.server().toUpperCase(Locale.ROOT) + "§7] " + entry.player() + "§7: §f";
        for (Player player : Bukkit.getOnlinePlayers()) {
            var profile = plugin.getExtendedPlayer(player).getMainPlayer();
            if (profile == null || !profile.isGlobalChat()) continue;
            Pattern name = Pattern.compile("\\b" + Pattern.quote(player.getName()) + "\\b", Pattern.CASE_INSENSITIVE);
            boolean mention = profile.chatMentions() && name.matcher(text).find();
            player.sendMessage(prefix + (mention ? name.matcher(text).replaceAll("§e" + player.getName() + "§f") : text));
            if (mention) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        }
    }

    private static String sanitize(String text) {
        String plain = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text));
        return CONTROL.matcher(COLOR.matcher(plain).replaceAll("")).replaceAll("");
    }

    private static boolean pause(long milliseconds) {
        try { Thread.sleep(milliseconds); return true; }
        catch (InterruptedException stop) { Thread.currentThread().interrupt(); return false; }
    }

    public void stop() {
        stopped = true;
        var subscription = pubSub;
        if (subscription != null && subscription.isSubscribed()) {
            try { subscription.unsubscribe(); }
            catch (RuntimeException ignored) { }
        }
    }
}
