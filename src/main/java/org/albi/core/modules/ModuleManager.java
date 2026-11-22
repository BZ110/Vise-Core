package org.albi.core.modules;

import org.albi.core.Core;
import org.albi.core.modules.AntiGhostArrow.AntiGhostArrowManager;
import org.albi.core.modules.CriticalDiscord.CriticalDiscordManager;
import org.albi.core.modules.CustomKit.CustomKitManager;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.FFA.FFAManager;
import org.albi.core.modules.Friends.FriendsManager;
import org.albi.core.modules.KnockbackSynchronization.KnockbackSynchronizationManager;
import org.albi.core.modules.LavaRising.LavaRisingManager;
import org.albi.core.modules.Party.PartyManager;
import org.albi.core.modules.Tournaments.TournamentManager;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ModuleManager {
    private static final Map<Class<? extends Module>, Module> registry = new HashMap<>();

    private ModuleManager() {}

    public static void initializeModules(Core plugin) {
        registry.clear();
        boolean hasWorldEdit = plugin.getServer().getPluginManager().isPluginEnabled("FastAsyncWorldEdit")
                || plugin.getServer().getPluginManager().isPluginEnabled("WorldEdit");

        // Duels owns the arena and kit storage shared by Party, FFA and Tournaments.
        boolean hasDuels = initModule(plugin, "Duels", () -> new DuelManager(plugin), hasWorldEdit, "FAWE or WorldEdit");
        initModule(plugin, "CriticalDiscord", () -> new CriticalDiscordManager(plugin));
        boolean hasParty = initModule(plugin, "Party", () -> new PartyManager(plugin), hasDuels, "Duels");
        initModule(plugin, "CustomKit", () -> new CustomKitManager(plugin));
        initModule(plugin, "LavaRising", () -> new LavaRisingManager(plugin), hasDuels, "Duels");
        initModule(plugin, "FFA", () -> new FFAManager(plugin), hasDuels, "Duels");
        initModule(plugin, "Friends", () -> new FriendsManager(plugin));
        initModule(plugin, "KnockbackSynchronization", () -> new KnockbackSynchronizationManager(plugin));
        initModule(plugin, "AntiGhostArrow", () -> new AntiGhostArrowManager(plugin));
        initModule(plugin, "Tournaments", () -> new TournamentManager(plugin), hasParty, "Party and Duels");
    }

    private static void initModule(Core plugin, String name, Supplier<Module> moduleFactory) {
        initModule(plugin, name, moduleFactory, true, "");
    }

    private static boolean initModule(Core plugin, String name, Supplier<Module> moduleFactory,
                                      boolean dependencyAvailable, String dependencyName) {
        if (!plugin.getConfig().getBoolean("modules." + name)) return false;
        if (!dependencyAvailable) {
            plugin.getLogger().warning("Skipping " + name + " because it requires " + dependencyName + ".");
            return false;
        }
        Module module = moduleFactory.get();
        module.init();
        registry.put(module.getClass(), module);
        plugin.getLogger().info("Enabled module " + name + ".");
        return true;
    }

    /** Returns the running module or null when its configuration or dependencies disable it. */
    public static <T extends Module> T getModule(Class<T> type) {
        return type.cast(registry.get(type));
    }
}
