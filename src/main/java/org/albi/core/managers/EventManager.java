package org.albi.core.managers;

import org.albi.core.Core;
import org.albi.core.events.*;
import org.bukkit.event.Listener;

public class EventManager {
    public EventManager(Core plugin) {
        registerEvent("blockplace", new OnBlockPlaceListener(plugin), plugin);
        registerEvent("blockbreak", new OnBlockBreakListener(plugin), plugin);
        registerEvent("join", new OnJoinListener(plugin), plugin);
        registerEvent("chat", new OnChatListener(plugin), plugin);
        registerEvent("rightclick", new OnRightClickListener(plugin), plugin);
        registerEvent("pickup", new OnItemPickupListener(plugin), plugin);
        registerEvent("drop", new OnItemDropListener(plugin), plugin);
        registerEvent("height", new OnHeightTeleportListener(plugin), plugin);
        registerEvent("leave", new OnLeaveListener(plugin), plugin);
        registerEvent("interact", new LobbyInteractListener(plugin), plugin);
        registerEvent("entitymax", new OnEntityMax(plugin), plugin);
        registerEvent("death", new OnDeathCWorld(plugin), plugin);
        registerEvent("serverload", new OnServerLoaded(plugin), plugin);
        registerEvent("foodcooldown", new FoodCooldownListener(plugin), plugin);
        registerEvent("guisounds", new GuiClickSoundListener(), plugin);
    }

    private void registerEvent(String name, Listener event, Core plugin) {
        try {
            plugin.getLogger().info("[Core] Registering Event: " + name);
            plugin.getServer().getPluginManager().registerEvents(event, plugin);
        } catch (Exception e) {
            plugin.getLogger().severe("[Core] Could not register event: " + name);
        }
    }
}
