package org.albi.core.modules.FFA.commands;

import org.albi.core.Core;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Kit;
import org.albi.core.modules.FFA.FFAManager;
import org.albi.core.modules.FFA.helpers.FFAKitSetup;
import org.albi.core.modules.FFA.helpers.FFASpot;
import org.albi.core.serializable.SerializableItem;
import org.albi.core.serializable.SerializableLocation;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class FFAAdminCommand extends org.albi.core.commands.BaseCommand {

    private final Core plugin;
    private final FFAManager mgr;

    public FFAAdminCommand(Core plugin, FFAManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    private Kit findKit(String name) {
        return DuelManager.stor.kits.stream()
                .filter(k -> k.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return true;

        if (a.length >= 2 && a[0].equalsIgnoreCase("kit") && a[1].equalsIgnoreCase("create") && a.length >= 3) {
            String kitName = a[2];
            Kit k = findKit(kitName);
            if (k == null) { p.sendMessage("§cUnknown kit: " + kitName); return true; }
            FFAKitSetup setup = FFAManager.stor.getOrCreate(k.getName());
            try {
                // Best-effort copy of flags (method names may vary across builds)
                boolean allowBuild = false, nat = true, health = true, hunger = true;
                try { allowBuild = (boolean) k.getClass().getMethod("isBuild").invoke(k); } catch (Throwable ignored){}
                try { nat        = (boolean) k.getClass().getMethod("isNaturalRegen").invoke(k); } catch (Throwable ignored){}
                try { health     = (boolean) k.getClass().getMethod("isHealthIndics").invoke(k); } catch (Throwable ignored){}
                try { hunger     = (boolean) k.getClass().getMethod("isLoseHunger").invoke(k); } catch (Throwable ignored){}

                setup.setAllowBuild(allowBuild);
                setup.setNaturalRegen(nat);
                setup.setHealthIndicators(health);
                setup.setLoseHunger(hunger);
            } catch (Exception ignored) {}
            FFAManager.persist(plugin);
            p.sendMessage("§aCreated FFA kit profile for §b" + k.getName() + "§a.");
            return true;
        }

        if (a.length >= 3 && a[0].equalsIgnoreCase("setupworld")) {
            String kitName = a[1];
            String worldName = a[2];
            World w = Bukkit.getWorld(worldName);
            if (w == null) { p.sendMessage("§cWorld not found: " + worldName); return true; }
            FFAKitSetup setup = FFAManager.stor.getOrCreate(kitName);
            setup.setWorldName(worldName);
            FFAManager.persist(plugin);
            org.albi.core.modules.FFA.managers.EventManager.applyWorldRules(w, setup);
            p.sendMessage("§aLinked kit §b" + kitName + " §ato world §e" + worldName + "§a.");
            return true;
        }

        if (a.length >= 2 && a[0].equalsIgnoreCase("world") && a[1].equalsIgnoreCase("teleport") && a.length >= 3) {
            int slot;
            try { slot = Integer.parseInt(a[2]); } catch (NumberFormatException ex) {
                p.sendMessage("§cUsage: /ffaadmin world teleport <slot 0-8>");
                return true;
            }
            World w = p.getWorld();
            FFAKitSetup setup = null;
            for (FFAKitSetup s2 : FFAManager.stor.allSetups())
                if (w.getName().equalsIgnoreCase(s2.getWorldName())) { setup = s2; break; }
            if (setup == null) { p.sendMessage("§cThis world is not linked to any FFA kit."); return true; }

            if (p.getInventory().getItemInMainHand() == null || p.getInventory().getItemInMainHand().getType().isAir()) {
                p.sendMessage("§cHold an item to use as the icon.");
                return true;
            }
            SerializableItem icon = new SerializableItem(p.getInventory().getItemInMainHand());
            SerializableLocation loc = new SerializableLocation(p.getLocation());
            FFASpot spot = new FFASpot(slot, icon, loc);
            setup.setSpot(slot, spot);
            FFAManager.persist(plugin);
            p.sendMessage("§aSaved FFA teleport spot at slot §e" + slot + "§a for kit §b" + setup.getKitName() + "§a.");
            return true;
        }

        p.sendMessage("§cUsage:");
        p.sendMessage("§7/ffaadmin kit create <kitname>");
        p.sendMessage("§7/ffaadmin setupworld <kit> <world>");
        p.sendMessage("§7/ffaadmin world teleport <slot>");
        return true;
    }
}