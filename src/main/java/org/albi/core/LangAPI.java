package org.albi.core;

import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.albi.core.gui.InterfaceFiles;
import org.albi.core.gui.TextTemplates;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Formats localized messages and retains their configured click actions.
 * @author Yahibni (original implementation)
 */
public class LangAPI {
    private final JavaPlugin plugin;
    private final InterfaceFiles files;

    public LangAPI(JavaPlugin plugin) {
        this(plugin, null);
    }

    public LangAPI(JavaPlugin plugin, InterfaceFiles files) {
        this.plugin = plugin;
        this.files = files;
    }

    public String toColor(Player player, String text) {
        String coloured = ChatColor.translateAlternateColorCodes('&', text);
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
                ? PlaceholderAPI.setPlaceholders(player, coloured) : coloured;
    }

    public String toColor(String text) {
        return toColor(null, text);
    }

    public String format(Player viewer, String key, Object... values) {
        String raw = files == null ? plugin.getConfig().getString("lang." + key) : files.message(key);
        return raw == null ? "" : TextTemplates.format(toColor(viewer, raw), values);
    }

    public String toColorLang(String key) {
        return format(null, key);
    }

    public String toColorLang(Player player, String key) {
        return format(player, key);
    }

    public String toColorLang(Player player, String key, String[] replacements) {
        return format(player, key, (Object[]) replacements);
    }

    public String toColorLang(String key, String[] replacements) {
        return format(null, key, (Object[]) replacements);
    }

    public void sendMessage(Player player, String text) {
        sendMessage((CommandSender) player, text);
    }

    public void sendMessage(CommandSender sender, String text) {
        if (sender != null && text != null) sender.sendMessage(toColor(viewer(sender), text));
    }

    public void sendMessageLang(Player player, String key) {
        sendMessageLang((CommandSender) player, key);
    }

    public void sendMessageLang(CommandSender sender, String key) {
        sendMessageLang(sender, key, new String[0]);
    }

    public void sendMessageLang(Player player, String key, String[] replacements) {
        sendMessageLang((CommandSender) player, key, replacements);
    }

    public void sendMessageLang(CommandSender sender, String key, String[] replacements) {
        if (sender == null) return;
        String message = format(viewer(sender), key, (Object[]) replacements);
        if (!message.isEmpty()) sender.sendMessage(message);
    }

    public void sendCommandMessageLang(Player player, String key, String command) {
        sendClickable(player, format(player, key), ClickEvent.Action.RUN_COMMAND, command);
    }

    public void sendCommandMessageLang(Player player, String key, String[] replacements, String command) {
        sendClickable(player, format(player, key, (Object[]) replacements), ClickEvent.Action.RUN_COMMAND, command);
    }

    public void sendUrlMessage(Player player, String text, String url) {
        if (text != null) sendClickable(player, toColor(player, text), ClickEvent.Action.OPEN_URL, url);
    }

    private static void sendClickable(Player player, String message, ClickEvent.Action action, String value) {
        if (player == null || message.isEmpty() || value == null) return;
        TextComponent component = new TextComponent(message);
        component.setClickEvent(new ClickEvent(action, value));
        player.spigot().sendMessage(component);
    }

    private static Player viewer(CommandSender sender) {
        return sender instanceof Player player ? player : null;
    }
}
