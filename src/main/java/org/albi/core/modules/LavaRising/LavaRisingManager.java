package org.albi.core.modules.LavaRising;

import org.albi.core.Core;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.SchematicManager;
import org.albi.core.modules.LavaRising.helpers.LavaRisingGameInstance;
import org.albi.core.modules.LavaRising.helpers.LavaRisingStorage;
import org.albi.core.modules.LavaRising.managers.LavaLobbyWatcher;
import org.albi.core.modules.LavaRising.managers.LavaRisingCommandManager;
import org.albi.core.modules.LavaRising.managers.LavaRisingListener;
import org.albi.core.modules.Module;
import org.albi.core.serializable.SerializableLocation;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.io.File;

public class LavaRisingManager extends Module {

    public static SchematicManager schematicManager;

    public static boolean playing = false;
    public static Core plugin;
    public static LavaRisingListener lavaRisingListener;
    // add near other statics
    public static LavaLobbyWatcher lobbyWatcher;


    public LavaRisingManager(Core plugin) {
        super(plugin);
    }


    @Override
    public void init() {
        getPlugin().getLogger().info("[LavaRising] Initializing");

        getPlugin().getLogger().info("[LavaRising] Using DuelManager's Static Schematic Manager");
        schematicManager = DuelManager.schemManager;
        plugin = getPlugin();

        getPlugin().getLogger().info("[LavaRising] Loading Command Manager");
        new LavaRisingCommandManager(getPlugin());

        getPlugin().getLogger().info("[LavaRising] Registering Controlled Listener");
        lavaRisingListener = new LavaRisingListener(plugin);

        getPlugin().getLogger().info("[LavaRising] Registering LavaLobbyWatcher");
        lobbyWatcher = new LavaLobbyWatcher(plugin);

        getPlugin().getLogger().info("[LavaRising] Checking LavaRising Storage");
        if (getPlugin().getData().get("lavastor") == null) {
            getPlugin().getLogger().info("[LavaRising] Creating new LavaRising Storage");
            getPlugin().getData().set("lavastor", new LavaRisingStorage());
        } else getPlugin().getLogger().info("[LavaRising] Object Found in 'lavastor' plugin location!");



    }

    public static File generateLavaRisingSchematic(String schematic) {
        return new File(plugin.getDataFolder(), "schems" + File.separator + "lavarising" + File.separator + schematic);
    }

    public static void teleportToLobby(Player player) {
        player.getInventory().clear();


        if (playing)
             player.setGameMode(GameMode.SPECTATOR);
        else player.setGameMode(GameMode.ADVENTURE);

        player.teleportAsync(((SerializableLocation) plugin.getData().get("lavaspawn")).convertToLocation());
        plugin.resetPlayer(player);
    }

    public static LavaRisingGameInstance getActiveInstance() {
        return lavaRisingListener.getActiveInstance();
    }
}