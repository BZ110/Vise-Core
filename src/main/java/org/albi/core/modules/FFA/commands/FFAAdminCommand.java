package org.albi.core.modules.FFA.commands;

import org.albi.core.gui.Messages;

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
            if (k == null) { Messages.send(p, "messages.ffaadmin-command.unknown-kit", kitName); return true; }
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
            Messages.send(p, "messages.ffaadmin-command.created-ffa-kit-profile-for", k.getName());
            return true;
        }

        if (a.length >= 3 && a[0].equalsIgnoreCase("setupworld")) {
            String kitName = a[1];
            String worldName = a[2];
            World w = Bukkit.getWorld(worldName);
            if (w == null) { Messages.send(p, "messages.ffaadmin-command.world-not-found", worldName); return true; }
            FFAKitSetup setup = FFAManager.stor.getOrCreate(kitName);
            setup.setWorldName(worldName);
            FFAManager.persist(plugin);
            org.albi.core.modules.FFA.managers.EventManager.applyWorldRules(w, setup);
            Messages.send(p, "messages.ffaadmin-command.linked-kit-to-world", kitName, worldName);
            return true;
        }

        if (a.length >= 2 && a[0].equalsIgnoreCase("world") && a[1].equalsIgnoreCase("teleport") && a.length >= 3) {
            int slot;
            try { slot = Integer.parseInt(a[2]); } catch (NumberFormatException ex) {
                Messages.send(p, "messages.ffaadmin-command.usage-ffaadmin-world-teleport-slot-0-8");
                return true;
            }
            World w = p.getWorld();
            FFAKitSetup setup = null;
            for (FFAKitSetup s2 : FFAManager.stor.allSetups())
                if (w.getName().equalsIgnoreCase(s2.getWorldName())) { setup = s2; break; }
            if (setup == null) { Messages.send(p, "messages.ffaadmin-command.this-world-is-not-linked-to-any-ffa"); return true; }

            if (p.getInventory().getItemInMainHand() == null || p.getInventory().getItemInMainHand().getType().isAir()) {
                Messages.send(p, "messages.ffaadmin-command.hold-an-item-to-use-as-the-icon");
                return true;
            }
            SerializableItem icon = new SerializableItem(p.getInventory().getItemInMainHand());
            SerializableLocation loc = new SerializableLocation(p.getLocation());
            FFASpot spot = new FFASpot(slot, icon, loc);
            setup.setSpot(slot, spot);
            FFAManager.persist(plugin);
            Messages.send(p, "messages.ffaadmin-command.saved-ffa-teleport-spot-at-slot-for-kit", slot, setup.getKitName());
            return true;
        }

        Messages.send(p, "messages.ffaadmin-command.usage");
        Messages.send(p, "messages.ffaadmin-command.ffaadmin-kit-create-kitname");
        Messages.send(p, "messages.ffaadmin-command.ffaadmin-setupworld-kit-world");
        Messages.send(p, "messages.ffaadmin-command.ffaadmin-world-teleport-slot");
        return true;
    }
}