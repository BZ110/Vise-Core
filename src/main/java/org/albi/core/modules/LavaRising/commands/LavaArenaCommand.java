package org.albi.core.modules.LavaRising.commands;

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
            s.sendMessage("§cNo permission.");
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
                    s.sendMessage("§cArena already exists.");
                    return true;
                }
                LavaArena ar = new LavaArena();
                ar.setName(a[1]);
                store.lavaArenas.add(ar);
                save();
                s.sendMessage("§aCreated arena '" + a[1] + "'.");
            }

            /* /lavaarena list */
            case "list" -> {
                if (store.lavaArenas.isEmpty()) {
                    s.sendMessage("§7No arenas saved.");
                } else {
                    s.sendMessage("§eLava Arenas:");
                    store.lavaArenas.forEach(ar ->
                            s.sendMessage(" §6• §f" + ar.getName() +
                                    " §7[schem=" + (ar.getSchematic() == null ? "none" : ar.getSchematic()) + "]"));
                }
            }

            /* /lavaarena delete <name> */
            case "delete" -> {
                if (a.length < 2) return arg(s, "/lavaarena delete <name>");
                LavaArena ar = getArena(a[1]);
                if (ar == null) {
                    s.sendMessage("§cArena not found.");
                    return true;
                }
                store.lavaArenas.remove(ar);
                save();
                s.sendMessage("§cRemoved arena '" + ar.getName() + "'.");
            }

            /* Editing commands require player context */
            case "setspawn" -> {
                if (!(s instanceof Player p)) return playerOnly(s);
                if (a.length < 2) return arg(s, "/lavaarena setspawn <name>");

                LavaArena ar = getArena(a[1]);
                if (ar == null) return notFound(s);

                ar.setSpawn(new SerializableLocation(p.getLocation()));
                save();
                s.sendMessage("§aSpawn set for '" + ar.getName() + "'.");
            }

            /* /lavaarena setschematic <name> <file> */
            case "setschematic" -> {
                if (a.length < 3) return arg(s, "/lavaarena setschematic <name> <file>");
                LavaArena ar = getArena(a[1]);
                if (ar == null) return notFound(s);

                ar.setSchematic(a[2]);
                save();
                s.sendMessage("§aSchematic set to '" + a[2] + "' for arena '" + ar.getName() + "'.");
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
                    s.sendMessage("§aFirst corner recorded, run /lavaarena setpos2 <name> at the opposite corner.");
                } else {
                    if (!p.hasMetadata("lava_pos1_x")) {
                        s.sendMessage("§cRun setpos1 first.");
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
                    s.sendMessage("§aBounds set for '" + ar.getName() + "'.");
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
        s.sendMessage("""
            §e/lavaarena create <name>
            §e/lavaarena delete <name>
            §e/lavaarena list
            §e/lavaarena setspawn <name>
            §e/lavaarena setschematic <name> <file>
            §e/lavaarena setpos1 <name>
            §e/lavaarena setpos2 <name>
            """);
    }
    private boolean arg(CommandSender s, String msg) { s.sendMessage("§cUsage: " + msg); return true; }
    private boolean playerOnly(CommandSender s)      { s.sendMessage("§cOnly players may run this."); return true; }
    private boolean notFound(CommandSender s)        { s.sendMessage("§cArena not found."); return true; }
}
