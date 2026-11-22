package org.albi.core.util;

import net.digitalingot.feather.serverapi.api.FeatherAPI;
import net.digitalingot.feather.serverapi.api.meta.MetaService;
import net.digitalingot.feather.serverapi.api.meta.exception.ServerListBackgroundException;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;

/** Loaded only when the optional FeatherServerAPI dependency is enabled. */
public final class FeatherBackground {
    private FeatherBackground() {}

    public static void load(JavaPlugin plugin) {
        var file = plugin.getDataFolder().toPath().resolve("server-background.png");
        if (!Files.isRegularFile(file)) return;

        try {
            MetaService meta = FeatherAPI.getMetaService();
            meta.setServerListBackground(meta.getServerListBackgroundFactory().byPath(file));
        } catch (ServerListBackgroundException | IOException e) {
            plugin.getLogger().warning("Feather could not load server-background.png: " + e.getMessage());
        }
    }
}
