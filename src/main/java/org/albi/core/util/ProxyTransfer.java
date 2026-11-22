package org.albi.core.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class ProxyTransfer {
    private ProxyTransfer() {}

    public static boolean connect(Plugin plugin, Player player, String server) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF("Connect");
            output.writeUTF(server);
            player.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
            return true;
        } catch (IOException | IllegalArgumentException e) {
            player.sendMessage("§cCould not request the server transfer.");
            return false;
        }
    }
}
