package org.albi.core.modules.Duels.AutomatedCombat;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Manager {

    private static Manager instance;

    private final Core plugin;
    private final int maxSessions;
    private final Map<UUID, BotSession> sessions = new ConcurrentHashMap<>();

    private Manager(Core plugin) {
        this.plugin = plugin;
        maxSessions = Math.max(1, plugin.getConfig().getInt("duels.bots.max_sessions", 4));
        plugin.getServer().getPluginManager().registerEvents(new BotCombatListener(plugin, this), plugin);
    }

    public static void initialize(Core plugin) {
        if (!plugin.getConfig().getBoolean("duels.bots.enabled")) return;
        plugin.getLogger().warning("Experimental NPC practice is enabled. It uses version-specific Paper internals. "
                + "Test it on a staging server before upgrading Paper.");
        instance = new Manager(plugin);
    }

    public static Manager get() {
        return instance;
    }

    public static void shutdown(Core plugin) {
        if (instance == null || instance.plugin != plugin) return;
        for (BotSession session : List.copyOf(instance.sessions.values())) {
            session.stopOnDisable();
        }
        instance.sessions.clear();
        instance = null;
    }

    public void openMenu(Player player, AutomatedCombatCallbacks callbacks) {
        FileConfiguration config = plugin.getConfig();
        BotSettings settings = new BotSettings(config.getStringList("duels.bots.allowed_kits"),
                config.getInt("duels.bots.default_rounds", 1), config.getBoolean("duels.bots.debug_logging"));
        new BotMenu(plugin, this, callbacks).openMain(player, settings);
    }

    public void start(Player player, BotSettings settings, AutomatedCombatCallbacks callbacks) {
        BotSession existing = sessions.get(player.getUniqueId());
        if (existing == null && sessions.size() >= maxSessions) {
            Messages.send(player, "messages.manager.all-npc-practice-sessions-are-in-use-try");
            return;
        }
        if (existing != null) {
            existing.forceClose(false, "replaced");
        }

        BotSession session = new BotSession(plugin, this, player, settings.copy(), callbacks);
        sessions.put(player.getUniqueId(), session);
        session.start();
    }

    public BotSession getSession(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    public BotSession getSessionByNpcUuid(UUID uuid) {
        if (uuid == null) return null;
        for (BotSession session : sessions.values()) {
            if (session.ownsNpc(uuid)) return session;
        }
        return null;
    }

    public BotSession getSessionByTrackedPotion(UUID uuid) {
        if (uuid == null) return null;
        for (BotSession session : sessions.values()) {
            if (session.ownsPendingSplashPotion(uuid)) return session;
        }
        return null;
    }

    void remove(BotSession session) {
        if (session == null || session.getPlayer() == null) return;
        sessions.remove(session.getPlayer().getUniqueId(), session);
    }
}
