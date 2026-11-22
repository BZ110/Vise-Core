package org.albi.core.gui;

import org.albi.core.LangAPI;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Messages {
    private static volatile LangAPI language;

    private Messages() {}

    public static void use(LangAPI api) {
        language = api;
    }

    public static String text(Object recipient, String key, Object... values) {
        return language.format(recipient instanceof Player player ? player : null, key, values);
    }

    public static void send(CommandSender recipient, String key, Object... values) {
        String message = text(recipient, key, values);
        if (!message.isEmpty()) recipient.sendMessage(message);
    }
}
