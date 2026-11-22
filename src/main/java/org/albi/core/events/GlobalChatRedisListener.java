package org.albi.core.events;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.data.StatManagement;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import redis.clients.jedis.JedisPubSub;

public class GlobalChatRedisListener {

    private final Core plugin;
    private JedisPubSub pubSub;

    public GlobalChatRedisListener(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            pubSub = new JedisPubSub() {
                @Override
                public void onMessage(String channel, String message) {
                    if (!channel.equals("chat:global"))
                        return;

                    try {
                        JsonObject json = JsonParser.parseString(message).getAsJsonObject();
                        String server = json.get("server").getAsString();
                        String playerName = json.get("player").getAsString();
                        String chatMessage = json.get("message").getAsString();
                        
                        // Check if the message contains slurs before processing/sending to players
                        if (plugin.getChatFilter().isBlocked(chatMessage)) {
                            return;
                        }

                        String ourServer = plugin.getServerName().toLowerCase();

                        // Avoid reflecting chat back to our own server, because OnChatListener already
                        // handles it locally
                        if (server.equalsIgnoreCase(ourServer)) {
                            return;
                        }

                        // Format Server String: "na-1" -> "NA-1"
                        String formattedServer = server.toUpperCase();

                        // Strict strip of any sneaking color codes from other servers
                        chatMessage = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', chatMessage));
                        chatMessage = chatMessage.replaceAll("(?i)[§&][0-9A-FK-ORX]|&#[0-9A-Fa-f]{6}", "");

                        // Highlight chat mentions format for players ON THIS SERVER
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            ExtendedPlayer ep = plugin.getExtendedPlayer(p);
                            if (ep != null && ep.getMainPlayer() != null && ep.getMainPlayer().chatMentions()) {
                                if (chatMessage.toLowerCase().contains(p.getName().toLowerCase())) {
                                    chatMessage = chatMessage.replaceAll(
                                            "(?i)\\b" + java.util.regex.Pattern.quote(p.getName()) + "\\b",
                                            ChatColor.YELLOW + p.getName() + ChatColor.GRAY);
                                }
                            }
                        }

                        String finalMessage = ChatColor.translateAlternateColorCodes('&',
                                "&7[" + formattedServer + "&7] " + playerName + "&7: &f" + chatMessage);

                        // Broadcast to players who have Global Chat receiving enabled
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            ExtendedPlayer ep = plugin.getExtendedPlayer(p);
                            if (ep != null && ep.getMainPlayer() != null && ep.getMainPlayer().isGlobalChat()) {
                                p.sendMessage(finalMessage);

                                // Play ping if mentioned
                                if (ep.getMainPlayer().chatMentions()
                                        && chatMessage.toLowerCase().contains(p.getName().toLowerCase())) {
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f,
                                                2.0f);
                                    });
                                }
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger()
                                .warning("[GlobalChat] Failed to parse Redis chat message: " + e.getMessage());
                    }
                }
            };

            while (plugin.isEnabled()) {
                StatManagement.getRedis().withJedis(jedis -> {
                    if (jedis == null)
                        return null;
                    try {
                        plugin.getLogger().info("[GlobalChat] Subscribing to chat:global channel...");
                        jedis.subscribe(pubSub, "chat:global");
                    } catch (Exception e) {
                        plugin.getLogger().warning("[GlobalChat] Redis connection lost, reconnecting in 5s...");
                    }
                    return null;
                });

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                }
            }
        });
    }

    public void stop() {
        if (pubSub != null && pubSub.isSubscribed()) {
            try {
                pubSub.unsubscribe();
            } catch (Exception ignored) {
            }
        }
    }
}
