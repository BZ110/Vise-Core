package org.albi.core.modules.CriticalDiscord;

import org.albi.core.gui.Messages;

import com.zaxxer.hikari.HikariDataSource;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.albi.core.Core;
import org.albi.core.data.StatManagement;
import org.albi.core.data.StatManagement.RedisWrapper;
import org.albi.core.modules.Module;
import org.albi.core.util.ConfigValues;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class CriticalDiscordManager extends Module {

    private static final String TABLE_LINKS = "player_discord_links";
    private final RedisWrapper redis;
    private final SQLInstance sql;

    public CriticalDiscordManager(Core plugin) {
        super(plugin);
        this.redis = StatManagement.getRedis();
        this.sql = new SQLInstance(StatManagement.getSql());
    }

    @Override
    public void init() {
        Core pl = getPlugin();
        pl.getLogger().info("[CriticalDiscord] Enabling module (async SQL init)");

        asyncSql(conn -> {
            try {
                conn.createStatement().executeUpdate(
                        "CREATE TABLE IF NOT EXISTS " + TABLE_LINKS + " (" +
                                "uuid CHAR(36) NOT NULL PRIMARY KEY, " +
                                "discord_id BIGINT NOT NULL" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
                );
            } catch (SQLException e) {
                pl.getLogger().severe("[CriticalDiscord] Failed to create link table: " + e.getMessage());
            }
        });

        new LocalCommandManager(this).registerCommands();
        pl.getLogger().info("[CriticalDiscord] Module ready");
    }

    public CompletableFuture<Optional<Long>> getLinkedDiscordIdAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = sql.open();
                 PreparedStatement ps = c.prepareStatement("SELECT discord_id FROM " + TABLE_LINKS + " WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(rs.getLong(1));
                }
            } catch (SQLException e) {
                getPlugin().getLogger().warning("[CriticalDiscord] DB lookup failed: " + e.getMessage());
            }
            return Optional.empty();
        });
    }

    public void linkAsync(UUID uuid, long discordId) {
        asyncSql(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "REPLACE INTO " + TABLE_LINKS + " (uuid, discord_id) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, discordId);
                ps.executeUpdate();
            } catch (SQLException e) {
                getPlugin().getLogger().warning("[CriticalDiscord] DB write failed: " + e.getMessage());
            }
        });
    }

    public void unlinkAsync(UUID uuid) {
        asyncSql(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + TABLE_LINKS + " WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                getPlugin().getLogger().warning("[CriticalDiscord] DB delete failed: " + e.getMessage());
            }
        });
    }

    public RedisWrapper getRedis() { return redis; }

    private void asyncSql(Consumer<Connection> task) {
        Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), () -> {
            try (Connection conn = sql.open()) {
                task.accept(conn);
            } catch (SQLException ex) {
                getPlugin().getLogger().warning("[CriticalDiscord] SQL error: " + ex.getMessage());
            }
        });
    }

    private static class LocalCommandManager {
        private final CriticalDiscordManager mod;
        LocalCommandManager(CriticalDiscordManager mod) { this.mod = mod; }
        void registerCommands() { register("discord", new DiscordCommand(mod)); }
        private void register(String name, CommandExecutor exec) {
            PluginCommand cmd = mod.getPlugin().getCommand(name);
            if (cmd == null) {
                mod.getPlugin().getLogger().warning("[CriticalDiscord] /" + name + " not defined in plugin.yml");
                return;
            }
            cmd.setExecutor(exec);
            if (exec instanceof TabCompleter tc) cmd.setTabCompleter(tc);
        }
    }

    private static class DiscordCommand implements CommandExecutor {
        private final CriticalDiscordManager mod;
        DiscordCommand(CriticalDiscordManager mod) { this.mod = mod; }

        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player player)) {
                Messages.send(sender, "messages.critical-discord-manager.this-command-is-only-for-players");
                return true;
            }

            if (args.length != 0) {
                String sub = args[0].toLowerCase();
                if (sub.equals("link")) {
                    handleLink(player);
                    return true;
                }
                if (sub.equals("unlink")) {
                    mod.unlinkAsync(player.getUniqueId());
                    Messages.send(player, "messages.critical-discord-manager.your-discord-account-is-no-longer-linked-changes");
                    return true;
                }
                if (sub.equals("check")) {
                    handleCheck(player);
                    return true;
                }
            }

            var invite = ConfigValues.httpEndpoint(mod.getPlugin().getConfig().getString("network.discord_url", ""));
            if (invite == null) {
                Messages.send(player, "messages.critical-discord-manager.the-discord-invite-is-unavailable-on-this-server");
                return true;
            }
            TextComponent linkMsg = new TextComponent("§bClick here to join the Discord.");
            linkMsg.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, invite.toString()));
            linkMsg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§7Opens in your browser")));
            player.spigot().sendMessage(linkMsg);
            return true;
        }

        private void handleLink(Player p) {
            Bukkit.getScheduler().runTaskAsynchronously(mod.getPlugin(), () -> {
                UUID uuid = p.getUniqueId();

                if (mod.getLinkedDiscordIdAsync(uuid).join().isPresent()) {
                    sendSync(p, "§cYou’re already linked to Discord.");
                    return;
                }

                String waitKey = "link-wait:" + uuid;
                Boolean pending = mod.getRedis().withJedis(j -> j.exists(waitKey));
                if (pending == null) {
                    sendSync(p, "§cAccount linking is temporarily unavailable.");
                    return;
                }
                if (pending) {
                    sendSync(p, "§eYou already have a code waiting – check your Discord DMs!");
                    return;
                }

                String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
                String codeKey = "link-code:" + code;

                Boolean stored = mod.getRedis().withJedis(j -> {
                    j.setex(codeKey, 300, uuid.toString());
                    j.setex(waitKey, 300, code);
                    return true;
                });
                if (!Boolean.TRUE.equals(stored)) {
                    sendSync(p, "§cAccount linking is temporarily unavailable.");
                    return;
                }

                sendSync(p,
                        "§7Copy this code, then follow the instructions on the Discord within five minutes",
                        "§e§l" + code);
            });
        }

        private void handleCheck(Player p) {
            Bukkit.getScheduler().runTaskAsynchronously(mod.getPlugin(), () -> {
                UUID uuid = p.getUniqueId();
                Optional<Long> opt = mod.getLinkedDiscordIdAsync(uuid).join();
                if (opt.isPresent()) {
                    sendSync(p, "§aYou're linked to Discord ID: §f" + opt.get());
                } else {
                    sendSync(p, "§eYou’re not linked to any Discord account.");
                }
            });
        }

        private void sendSync(Player p, String... lines) {
            Bukkit.getScheduler().runTask(mod.getPlugin(), () -> {
                for (String line : lines) p.sendMessage(line);
            });
        }
    }

    public static class SQLInstance {
        private final HikariDataSource pooled;
        public SQLInstance(HikariDataSource pooled) { this.pooled = pooled; }
        public Connection open() throws SQLException { return pooled.getConnection(); }
    }
}
