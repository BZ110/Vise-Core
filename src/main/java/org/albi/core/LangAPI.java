package org.albi.core;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * LangAPI – Utility class to handle lang messages and color codes.
 * Supports placeholder parsing, lang keys from config, and clickable messages.
 *
 * @author Yahibni
 */
public class LangAPI {

    private final JavaPlugin plugin;

    public LangAPI(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * When parsing placeholders we need a Player-context.
     * If <code>p</code> is null, PlaceholderAPI will still work (or
     * simply return the raw string).
     */
    private Player ctx(Player p) {
        return p;   // simple but keeps the hook in one place
    }

    /**
     * Translate legacy '&' colour codes and run PlaceholderAPI if present.
     *
     * @param p    Placeholder context (may be null).
     * @param text Raw text with '&' colours.
     * @return Fully formatted string.
     */
    public String toColor(Player p, String text) {
        String coloured = ChatColor.translateAlternateColorCodes('&', text);
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
                ? PlaceholderAPI.setPlaceholders(ctx(p), coloured)
                : coloured;
    }

    public String toColor(String text) {
        String coloured = ChatColor.translateAlternateColorCodes('&', text);
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
                ? PlaceholderAPI.setPlaceholders(null, coloured)
                : coloured;
    }

    /* --------------------------------------------------------------------
       Helpers that pull raw strings from config (lang.* section)
       -------------------------------------------------------------------- */

    public String toColorLang(String key) {
        String raw = plugin.getConfig().getString("lang." + key);
        return raw == null ? "" : toColor(null, raw);
    }

    public String toColorLang(Player p, String key) {
        String raw = plugin.getConfig().getString("lang." + key);
        return raw == null ? "" : toColor(p, raw);
    }

    public String toColorLang(Player p, String key, String[] repl) {
        String raw = plugin.getConfig().getString("lang." + key);
        if (raw == null) return "";
        for (int i = 0; i < repl.length; i++)
            raw = raw.replace("{" + (i + 1) + "}", repl[i]);
        return toColor(p, raw);
    }

    public String toColorLang(String key, String[] repl) {
        String raw = plugin.getConfig().getString("lang." + key);
        if (raw == null) return "";
        for (int i = 0; i < repl.length; i++)
            raw = raw.replace("{" + (i + 1) + "}", repl[i]);
        return toColor(null, raw);
    }

    /* --------------------------------------------------------------------
       Basic senders
       -------------------------------------------------------------------- */

    public void sendMessage(Player player, String text) {
        if (player == null || text == null) return;
        player.sendMessage(toColor(player, text));
    }

    public void sendMessageLang(Player player, String key) {
        String raw = plugin.getConfig().getString("lang." + key);
        if (raw != null) sendMessage(player, raw);
    }

    /* ------ Console-safe overloads (no unsafe cast) ------ */

    public void sendMessage(CommandSender sender, String text) {
        if (text == null) return;
        if (sender instanceof Player p) sendMessage(p, text);
        else sender.sendMessage(toColor(null, text));
    }

    public void sendMessageLang(CommandSender sender, String key) {
        String raw = plugin.getConfig().getString("lang." + key);
        if (raw == null) return;
        if (sender instanceof Player p) sendMessage(p, raw);
        else sender.sendMessage(toColor(null, raw));
    }

    public void sendMessageLang(Player player, String key, String[] repl) {
        String raw = plugin.getConfig().getString("lang." + key);
        if (raw == null) return;
        for (int i = 0; i < repl.length; i++)
            raw = raw.replace("{" + (i + 1) + "}", repl[i]);
        sendMessage(player, raw);
    }

    public void sendMessageLang(CommandSender sender, String key, String[] repl) {
        String raw = plugin.getConfig().getString("lang." + key);
        if (raw == null) return;
        for (int i = 0; i < repl.length; i++)
            raw = raw.replace("{" + (i + 1) + "}", repl[i]);
        if (sender instanceof Player p) sendMessage(p, raw);
        else sender.sendMessage(toColor(null, raw));
    }

    /* --------------------------------------------------------------------
       Clickable messages (Player-only, no console variant needed)
       -------------------------------------------------------------------- */

    public void sendCommandMessageLang(Player player, String key, String command) {
        String raw = plugin.getConfig().getString("lang." + key);
        if (raw == null) return;
        TextComponent tc = new TextComponent(toColor(player, raw));
        tc.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        player.spigot().sendMessage(tc);
    }

    public void sendCommandMessageLang(Player player, String key,
                                       String[] repl, String command) {
        String raw = plugin.getConfig().getString("lang." + key);
        if (raw == null) return;
        for (int i = 0; i < repl.length; i++)
            raw = raw.replace("{" + (i + 1) + "}", repl[i]);
        TextComponent tc = new TextComponent(toColor(player, raw));
        tc.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        player.spigot().sendMessage(tc);
    }

    public void sendUrlMessage(Player player, String text, String url) {
        if (player == null || text == null || url == null) return;
        TextComponent tc = new TextComponent(toColor(player, text));
        tc.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        player.spigot().sendMessage(tc);
    }
}
