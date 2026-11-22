package org.albi.core.modules.LavaRising.commands;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.modules.LavaRising.helpers.LavaArena;
import org.albi.core.modules.LavaRising.helpers.LavaRisingStorage;
import org.albi.core.serializable.SerializableLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.Locale;

public class LavaArenaCommand extends BaseCommand implements CommandExecutor, TabCompleter {

    private final Core plugin;
    private final LavaRisingStorage store;

    public LavaArenaCommand(Core plugin) {
        this.plugin = plugin;
        this.store  = (LavaRisingStorage) plugin.getData().get("lavastor");

        PluginCommand cmd = plugin.getCommand("lavaarena");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    /*────────────────── Main command ──────────────────*/
    @Override
    public boolean onCommand(CommandSender s, Command c, String lbl, String[] a) {

        if (!s.hasPermission("core.lavaarena")) {
            Messages.send(s, "messages.lava-arena-command.no-permission");
            return true;
        }
        if (a.length == 0) {
            sendUsage(s);
            return true;
        }

        String sub = a[0].toLowerCase(Locale.ROOT);

        switch (sub) {

            /* /lavaarena create <name> */
            case "create" -> {
                if (a.length < 2) return arg(s, "/lavaarena create <name>");
                if (getArena(a[1]) != null) {
                    Messages.send(s, "messages.lava-arena-command.arena-already-exists");
                    return true;
                }
                LavaArena ar = new LavaArena();
                ar.setName(a[1]);
                store.lavaArenas.add(ar);
                save();
                Messages.send(s, "messages.lava-arena-command.created-arena", a[1]);
            }

            /* /lavaarena list */
            case "list" -> {
                if (store.lavaArenas.isEmpty()) {
                    Messages.send(s, "messages.lava-arena-command.no-arenas-saved");
                } else {
                    Messages.send(s, "messages.lava-arena-command.lava-arenas");
                    store.lavaArenas.forEach(ar ->
                            Messages.send(s, "messages.lava-arena-command.schem", ar.getName(), (ar.getSchematic() == null ? "none" : ar.getSchematic())));
                }
            }

            /* /lavaarena delete <name> */
            case "delete" -> {
                if (a.length < 2) return arg(s, "/lavaarena delete <name>");
                LavaArena ar = getArena(a[1]);
                if (ar == null) {
                    Messages.send(s, "messages.lava-arena-command.arena-not-found");
                    return true;
                }
                store.lavaArenas.remove(ar);
                save();
                Messages.send(s, "messages.lava-arena-command.removed-arena", ar.getName());
            }

            /* Editing commands require player context */
            case "setspawn" -> {
                if (!(s instanceof Player p)) return playerOnly(s);
                if (a.length < 2) return arg(s, "/lavaarena setspawn <name>");

                LavaArena ar = getArena(a[1]);
                if (ar == null) return notFound(s);

                ar.setSpawn(new SerializableLocation(p.getLocation()));
                save();
                Messages.send(s, "messages.lava-arena-command.spawn-set-for", ar.getName());
            }

            /* /lavaarena setschematic <name> <file> */
            case "setschematic" -> {
                if (a.length < 3) return arg(s, "/lavaarena setschematic <name> <file>");
                LavaArena ar = getArena(a[1]);
                if (ar == null) return notFound(s);

                ar.setSchematic(a[2]);
                save();
                Messages.send(s, "messages.lava-arena-command.schematic-set-to-for-arena", a[2], ar.getName());
            }

            /* /lavaarena setpos1 <name>   (least corner) */
            case "setpos1", "setpos2" -> {
                if (!(s instanceof Player p)) return playerOnly(s);
                if (a.length < 2) return arg(s, "/lavaarena " + sub + " <name>");
                LavaArena ar = getArena(a[1]);
                if (ar == null) return notFound(s);

                Location l = p.getLocation();
                double x = l.getBlockX(), z = l.getBlockZ();

                boolean first = sub.equals("setpos1");
                if (first) {                            // store temporarily in metadata
                    p.setMetadata("lava_pos1_x", new FixedMetadataValue(plugin, x));
                    p.setMetadata("lava_pos1_z", new FixedMetadataValue(plugin, z));
                    Messages.send(s, "messages.lava-arena-command.first-corner-recorded-run-lavaarena-setpos2-name-at");
                } else {
                    if (!p.hasMetadata("lava_pos1_x")) {
                        Messages.send(s, "messages.lava-arena-command.run-setpos1-first");
                        return true;
                    }
                    double x1 = p.getMetadata("lava_pos1_x").get(0).asDouble();
                    double z1 = p.getMetadata("lava_pos1_z").get(0).asDouble();

                    double leastX = Math.min(x1, x);
                    double mostX  = Math.max(x1, x);
                    double leastZ = Math.min(z1, z);
                    double mostZ  = Math.max(z1, z);

                    ar.setPositions(leastX, mostX, leastZ, mostZ);
                    save();
                    Messages.send(s, "messages.lava-arena-command.bounds-set-for", ar.getName());
                }
            }

            default -> sendUsage(s);
        }
        return true;
    }

    /*────────────────── tab complete ───────────────────*/
    @Override
    public java.util.List<String> onTabComplete(CommandSender s, Command cmd, String lbl, String[] a) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (!s.hasPermission("lava.admin")) return list;

        if (a.length == 1) {
            list.addAll(java.util.List.of("create","delete","list","setspawn","setschematic","setpos1","setpos2"));
        } else if (a.length == 2) {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "delete","setspawn","setschematic","setpos1","setpos2" ->
                        store.lavaArenas.stream().map(LavaArena::getName).forEach(list::add);
            }
        }
        return org.bukkit.util.StringUtil.copyPartialMatches(a[a.length-1], list, new java.util.ArrayList<>());
    }

    /*────────────────── helpers ───────────────────*/
    private LavaArena getArena(String name) {
        return store.lavaArenas.stream()
                .filter(ar -> ar.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }
    private void save() { plugin.getData().set("lavastor", store); }

    private void sendUsage(CommandSender s) {
        Messages.send(s, "messages.lava-arena-command.lavaarena-create-name-lavaarena-delete-name-lavaarena-list");
    }
    private boolean arg(CommandSender s, String msg) { Messages.send(s, "messages.lava-arena-command.usage", msg); return true; }
    private boolean playerOnly(CommandSender s)      { Messages.send(s, "messages.lava-arena-command.only-players-may-run-this"); return true; }
    private boolean notFound(CommandSender s)        { Messages.send(s, "messages.lava-arena-command.arena-not-found"); return true; }
}
