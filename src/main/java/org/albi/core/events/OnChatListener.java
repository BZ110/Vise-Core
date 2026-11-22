package org.albi.core.events;

import com.google.gson.JsonObject;
import me.clip.placeholderapi.PlaceholderAPI;
import org.albi.core.Core;
import org.albi.core.data.StatManagement;
import org.albi.core.modules.CustomKit.GUIs.CustomKitGUIMain;
import org.albi.core.modules.Party.play.PartyPlayGUIs;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.data.MainPlayer;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OnChatListener implements Listener {

    private static final Pattern COLOR_CODES = Pattern.compile("(?i)[§&][0-9A-FK-ORX]|&#[0-9A-Fa-f]{6}");
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");

    // Guards a re-dispatched chat so we don't loop the GUI check
    private static final Set<UUID> REDISPATCH_GUARD = ConcurrentHashMap.newKeySet();

    private final Core plugin;
    private String chatFormatRaw;

    public OnChatListener(Core plugin) {
        this.plugin = plugin;
        this.chatFormatRaw = plugin.getConfig().getString("chat", "{player}: {chat}");
    }

    private static String applyHexColours(String msg) {
        Matcher m = HEX.matcher(msg);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex = m.group(1);
            StringBuilder replacement = new StringBuilder("\u00a7x");
            for (char c : hex.toCharArray()) {
                replacement.append('\u00a7').append(c);
            }
            m.appendReplacement(sb, replacement.toString());
        }
        m.appendTail(sb);
        return sb.toString();
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // If this is a re-dispatched chat, skip GUI checks and run normal flow
        if (!REDISPATCH_GUARD.remove(player.getUniqueId())) {
            // First pass: run GUI handlers on main thread. If none handle it, re-dispatch.
            final String msgCopy = message;

            Bukkit.getScheduler().runTask(plugin, () -> {
                boolean guiHandled = CustomKitGUIMain.handleChatRename(player, msgCopy, plugin.getGuiKit())
                        || CustomKitGUIMain.handleChatStack(player, msgCopy, plugin.getGuiKit())
                        || CustomKitGUIMain.handleChatEnchant(player, msgCopy, plugin.getGuiKit())
                        || CustomKitGUIMain.handleChatRenameBox(player, msgCopy, plugin.getGuiKit())
                        || CustomKitGUIMain.handleChatStackBox(player, msgCopy, plugin.getGuiKit())
                        || CustomKitGUIMain.handleChatEnchantBox(player, msgCopy, plugin.getGuiKit())
                        || CustomKitGUIMain.handleChatRenameEc(player, msgCopy, plugin.getGuiKit())
                        || CustomKitGUIMain.handleChatStackEc(player, msgCopy, plugin.getGuiKit())
                        || CustomKitGUIMain.handleChatEnchantEc(player, msgCopy, plugin.getGuiKit())
                        || CustomKitGUIMain.handleChatDamage(player, msgCopy, plugin.getGuiKit())
                        || CustomKitGUIMain.handleChatMending(player, msgCopy, plugin.getGuiKit())
                        || CustomKitGUIMain.handleChatEffectCreate(player, msgCopy, plugin.getGuiKit())
                        || PartyPlayGUIs.CustomSizeListener.handle(player, msgCopy, plugin, plugin.getPartyManager());

                if (!guiHandled) {
                    // Not a GUI message: re-dispatch chat so normal flow runs
                    REDISPATCH_GUARD.add(player.getUniqueId());
                    player.chat(msgCopy);
                }
            });

            // Cancel the original async event; GUI or re-dispatch will handle it
            event.setCancelled(true);
            return;
        }

        // ────────────────────────────────────────────────────────────
        // Normal chat flow (second pass after re-dispatch), or when
        // REDISPATCH_GUARD was already set for this message.
        // ────────────────────────────────────────────────────────────

        if (plugin.getChatFilter().handleBlockedMessage(event)) {
            return;
        }

        MainPlayer mp = plugin.getExtendedPlayer(player).getMainPlayer();

        // Color permissions
        boolean canColour = player.hasPermission("core.chat.color");
        if (canColour) {
            message = ChatColor.translateAlternateColorCodes('&', message);
        } else {
            message = COLOR_CODES.matcher(ChatColor.translateAlternateColorCodes('&', message)).replaceAll("");
        }

        // Highlight chat mentions format
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(player))
                continue;
            ExtendedPlayer ep = plugin.getExtendedPlayer(p);
            if (ep != null && ep.getMainPlayer().chatMentions()) {
                if (message.toLowerCase().contains(p.getName().toLowerCase())) {
                    message = message.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(p.getName()) + "\\b",
                            ChatColor.YELLOW + p.getName() + ChatColor.RESET);
                }
            }
        }

        // Apply placeholders to format
        String format = chatFormatRaw;
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            format = PlaceholderAPI.setPlaceholders(player, format);
        }

        // Insert data into format string
        format = format.replace("{player}", player.getName())
                .replace("{chat}", message);

        // Apply colors
        format = applyHexColours(format);
        format = ChatColor.translateAlternateColorCodes('&', format);

        // Escape `%` to prevent format crashes
        format = format.replace("%", "%%");

        // Apply the formatted line
        event.setFormat(format);

        // ────────────────────────────────────────────────────────────
        // Chat Isolation (Contextual Routing)
        // ────────────────────────────────────────────────────────────
        ExtendedPlayer senderEp = plugin.getExtendedPlayer(player);
        org.albi.core.modules.Duels.helpers.PlayerDuelInstance senderDi = senderEp.getPlayerDuelInstance();

        event.getRecipients().removeIf(recipient -> {
            ExtendedPlayer recipientEp = plugin.getExtendedPlayer(recipient);

            // If sender is in a duel, only people in the same duel (or spectators of it)
            // receive the local chat
            if (senderDi != null && senderDi.getDuelInstance() != null) {
                org.albi.core.modules.Duels.helpers.PlayerDuelInstance recDi = recipientEp.getPlayerDuelInstance();
                boolean sameDuel = recDi != null && recDi.getDuelInstance() != null
                        && recDi.getDuelInstance().equals(senderDi.getDuelInstance());
                boolean isSpectating = recipientEp.isSpectating() && recipientEp.getSpectatingArena() != null
                        && senderDi.getDuelInstance().spectators.contains(recipient);

                // If they are not natively in the same duel, check if they receive global chat
                if (!sameDuel && !isSpectating) {
                    return !recipientEp.getMainPlayer().isGlobalChat();
                }
                return false; // Keep them (they are in the duel)
            }

            // If sender is not in a duel but recipient IS in a duel, check if recipient
            // allows global chat
            if (recipientEp.getPlayerDuelInstance() != null) {
                return !recipientEp.getMainPlayer().isGlobalChat();
            }

            // Otherwise, keep people in the same world (Lobby/FFA logic) unless they allow
            // global chat
            if (!recipient.getWorld().equals(player.getWorld())) {
                return !recipientEp.getMainPlayer().isGlobalChat();
            }

            return false;
        });

        // ────────────────────────────────────────────────────────────
        // Chat Mentions
        // ────────────────────────────────────────────────────────────
        for (Player recipient : event.getRecipients()) {
            if (recipient.equals(player))
                continue;
            ExtendedPlayer recEp = plugin.getExtendedPlayer(recipient);

            if (recEp != null && recEp.getMainPlayer().chatMentions()) {
                if (message.toLowerCase().contains(recipient.getName().toLowerCase())) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        recipient.playSound(recipient.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f,
                                2.0f);
                    });
                }
            }
        }

        // ────────────────────────────────────────────────────────────
        // Redis logging / Cross-Server Broadcasting
        // ────────────────────────────────────────────────────────────
        if (mp != null && mp.isBroadcastChats() && plugin.getConfig().getBoolean("network.global_chat", true)) {
            String finalMessage = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', event.getMessage()));
            finalMessage = finalMessage.replaceAll("(?i)[§&][0-9A-FK-ORX]|&#[0-9A-Fa-f]{6}", "");

            final String redisMessage = finalMessage;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                StatManagement.getRedis().withJedis(jedis -> {
                    JsonObject payload = new JsonObject();
                    payload.addProperty("server", plugin.getServerName().toLowerCase(java.util.Locale.ROOT));
                    payload.addProperty("player", player.getName());
                    payload.addProperty("message", redisMessage);
                    jedis.publish("chat:global", payload.toString());
                    return null;
                });
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        CustomKitGUIMain.clear(event.getPlayer().getUniqueId());
    }
}
