package org.albi.core.modules.Duels.commands;

import org.albi.core.Core;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Arena;
import org.albi.core.modules.Duels.helpers.Kit;
import org.albi.core.serializable.SerializableItem;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;

/**
 * Revamped /dueladmin implementation – feature-complete for arena and kit
 * workflows, now including:
 *   • /dueladmin kit list
 *   • /dueladmin kit delete <name>
 */
public class DuelAdminCommand extends BaseCommand {

    private final Core plugin;
    public DuelAdminCommand(Core plugin) { this.plugin = plugin; }

    /*───────────────────────────────────────────────────────────────────*/
    /*  Boilerplate helpers                                             */
    /*───────────────────────────────────────────────────────────────────*/
    private void msg(Player p, String key, String... vars) {
        plugin.getMessageAPI().sendMessageLang(p, key, vars);
    }

    private Arena findArena(String name) {
        Arena ar = DuelManager.stor.arenas.stream()
                .filter(a -> a.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);

        if (ar == null) return null;
        return ar.deactivated ? null : ar;
    }

    private Kit findKit(String name) {
        return DuelManager.stor.kits.stream()
                .filter(k -> k.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    /*───────────────────────────────────────────────────────────────────*/
    /*  Root dispatch                                                   */
    /*───────────────────────────────────────────────────────────────────*/
    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { msg(null, "players_only"); return true; }
        if (!p.hasPermission("core.dueladmin")) { msg(p, "no_permission"); return true; }
        if (a.length == 0)                { msg(p, "duel_admin_begin"); return true; }

        switch (a[0].toLowerCase()) {
            case "arena" -> handleArena(p, a);
            case "polar_arena" -> handlePolarArena(p, a);
            case "kit"   -> handleKit(p, a);
            default      -> msg(p, "duel_admin_begin");
        }
        return true;
    }


    private boolean polarEnabled() {
        return DuelManager.polarArenaManager != null && DuelManager.polarArenaManager.isEnabled();
    }

    private void handlePolarArena(Player p, String[] a) {
        if (!polarEnabled()) {
            p.sendMessage(ChatColor.RED + "Polar arenas are disabled or PolarPaper is not loaded.");
            return;
        }
        if (a.length == 1) {
            p.sendMessage(ChatColor.YELLOW + "Usage: /dueladmin polar_arena <create|edit|arenaready|save|setdisplayitem|spawn1|spawn2|setspawn|set_limit|info> ...");
            return;
        }

        switch (a[1].toLowerCase()) {
            case "create" -> polarArenaCreate(p, a);
            case "edit" -> polarArenaEdit(p, a);
            case "arenaready", "save" -> polarArenaSave(p, a);
            case "setdisplayitem" -> polarArenaSetDisplay(p, a);
            case "spawn1" -> polarArenaSetSpawn(p, a, 1);
            case "spawn2" -> polarArenaSetSpawn(p, a, 2);
            case "setspawn" -> polarArenaSetSpawnLegacy(p, a);
            case "set_limit" -> polarArenaSetLimit(p, a);
            case "info" -> polarArenaInfo(p, a);
            default -> p.sendMessage(ChatColor.YELLOW + "Usage: /dueladmin polar_arena <create|edit|arenaready|save|setdisplayitem|spawn1|spawn2|setspawn|set_limit|info> ...");
        }
    }

    private void polarArenaCreate(Player p, String[] a) {
        if (a.length != 3) {
            p.sendMessage(ChatColor.RED + "Usage: /dueladmin polar_arena create <name>");
            return;
        }
        if (findArena(a[2]) != null) {
            p.sendMessage(ChatColor.RED + "An arena with that name already exists.");
            return;
        }
        Arena arena = new Arena();
        arena.setName(a[2]);
        arena.setPolarArena(true);
        DuelManager.stor.arenas.add(arena);
        DuelManager.polarArenaManager.createArena(arena, p, success -> {
            if (success) {
                p.sendMessage(ChatColor.GREEN + "Polar arena created. You were moved into the origin world for setup.");
            } else {
                DuelManager.stor.arenas.remove(arena);
                p.sendMessage(ChatColor.RED + "Failed to create the polar arena world.");
            }
        });
    }

    private void polarArenaEdit(Player p, String[] a) {
        if (a.length != 3) {
            p.sendMessage(ChatColor.RED + "Usage: /dueladmin polar_arena edit <name>");
            return;
        }
        Arena arena = findArena(a[2]);
        if (arena == null || !arena.isPolarArena()) {
            p.sendMessage(ChatColor.RED + "That polar arena does not exist.");
            return;
        }
        DuelManager.polarArenaManager.loadOriginForEdit(arena, p, success -> {
            if (!success) p.sendMessage(ChatColor.RED + "Failed to load the polar origin world.");
        });
    }

    private void polarArenaSave(Player p, String[] a) {
        if (a.length != 3) {
            p.sendMessage(ChatColor.RED + "Usage: /dueladmin polar_arena save <name>");
            return;
        }
        Arena arena = findArena(a[2]);
        if (arena == null || !arena.isPolarArena()) {
            p.sendMessage(ChatColor.RED + "That polar arena does not exist.");
            return;
        }
        DuelManager.polarArenaManager.saveOrigin(arena, success -> {
            if (success) p.sendMessage(ChatColor.GREEN + "Saved polar origin world and refreshed preloads.");
            else p.sendMessage(ChatColor.RED + "Could not save the polar origin world.");
        });
    }

    private void polarArenaSetDisplay(Player p, String[] a) {
        if (a.length != 3) {
            p.sendMessage(ChatColor.RED + "Usage: /dueladmin polar_arena setdisplayitem <name>");
            return;
        }
        Arena arena = findArena(a[2]);
        if (arena == null || !arena.isPolarArena()) {
            p.sendMessage(ChatColor.RED + "That polar arena does not exist.");
            return;
        }
        arena.setDisplayItem(new SerializableItem(p.getInventory().getItemInMainHand()));
        p.sendMessage(ChatColor.GREEN + "Updated polar arena display item for " + arena.getName() + ".");
    }

    private void polarArenaSetSpawnLegacy(Player p, String[] a) {
        if (a.length != 4) {
            p.sendMessage(ChatColor.RED + "Usage: /dueladmin polar_arena setspawn <name> <1|2>");
            return;
        }
        try {
            int team = Integer.parseInt(a[3]);
            if (team != 1 && team != 2) throw new NumberFormatException();
            polarArenaSetSpawn(p, a, team);
        } catch (NumberFormatException ex) {
            p.sendMessage(ChatColor.RED + "Usage: /dueladmin polar_arena setspawn <name> <1|2>");
        }
    }

    private void polarArenaSetSpawn(Player p, String[] a, int team) {
        if (a.length != 3 && a.length != 4) {
            p.sendMessage(ChatColor.RED + "Usage: /dueladmin polar_arena spawn" + team + " <name>");
            return;
        }
        String arenaName = a[2];
        Arena arena = findArena(arenaName);
        if (arena == null || !arena.isPolarArena()) {
            p.sendMessage(ChatColor.RED + "That polar arena does not exist.");
            return;
        }
        Location l = p.getLocation();
        arena.setSpawn(team, "x", l.getX());
        arena.setSpawn(team, "y", l.getY());
        arena.setSpawn(team, "z", l.getZ());
        arena.setSpawn(team, "yaw", l.getYaw());
        arena.setSpawn(team, "pitch", l.getPitch());
        p.sendMessage(ChatColor.GREEN + "Saved exact world coordinates for spawn " + team + " on " + arena.getName() + ".");
    }

    private void polarArenaSetLimit(Player p, String[] a) {
        if (a.length != 4) {
            p.sendMessage(ChatColor.RED + "Usage: /dueladmin polar_arena set_limit <name> <1|2|min|max>");
            return;
        }
        Arena arena = findArena(a[2]);
        if (arena == null || !arena.isPolarArena()) {
            p.sendMessage(ChatColor.RED + "That polar arena does not exist.");
            return;
        }
        Location loc = p.getLocation();
        switch (a[3].toLowerCase()) {
            case "1", "min" -> {
                arena.setCornerParameter("x1", loc.getX());
                arena.setCornerParameter("z1", loc.getZ());
                p.sendMessage(ChatColor.GREEN + "Saved limit corner 1 for " + arena.getName() + ".");
            }
            case "2", "max" -> {
                arena.setCornerParameter("x2", loc.getX());
                arena.setCornerParameter("z2", loc.getZ());
                p.sendMessage(ChatColor.GREEN + "Saved limit corner 2 for " + arena.getName() + ".");
            }
            default -> p.sendMessage(ChatColor.RED + "Usage: /dueladmin polar_arena set_limit <name> <1|2|min|max>");
        }
    }

    private void polarArenaInfo(Player p, String[] a) {
        if (a.length != 3) {
            p.sendMessage(ChatColor.RED + "Usage: /dueladmin polar_arena info <name>");
            return;
        }
        Arena arena = findArena(a[2]);
        if (arena == null || !arena.isPolarArena()) {
            p.sendMessage(ChatColor.RED + "That polar arena does not exist.");
            return;
        }
        p.sendMessage(arena.getInfo());
        p.sendMessage(ChatColor.GRAY + "Origin world: " + DuelManager.polarArenaManager.originWorldName(arena));
    }

    /*───────────────────────────────────────────────────────────────────*/
    /*  A R E N A  C O M M A N D S                                      */
    /*───────────────────────────────────────────────────────────────────*/
    private void handleArena(Player p, String[] a) {
        if (a.length == 1) { msg(p, "duel_admin_arena"); return; }
        switch (a[1].toLowerCase()) {
            case "create"          -> arenaCreate(p, a);
            case "arenaready"      -> arenaReady(p, a);
            case "setdisplayitem"  -> arenaSetDisplay(p, a);
            case "setschematic"    -> arenaSetSchematic(p, a);
            case "setspawn"        -> arenaSetSpawn(p, a);
            case "setcorner"       -> arenaSetCorner(p, a);
            case "setoffset"       -> arenaSetOffset(p, a);
            case "setmax"          -> arenaSetMax(p, a);
            case "info"            -> arenaInfo(p, a);
            case "deactivate"      -> {
                Arena ar = findArena(a[2]);
                ar.deactivated = !ar.deactivated;
            }
            default                 -> msg(p, "z");
        }
    }

    /* ── create <name> ────────────────────────────────────────────────*/
    private void arenaCreate(Player p, String[] a) {
        if (a.length != 3) { msg(p, "duel_admin_arena_proper_name"); return; }
        String name = a[2];
        if (findArena(name) != null) { msg(p, "duel_admin_arena_exists"); return; }
        Arena ar = new Arena();
        ar.setName(name);
        DuelManager.stor.arenas.add(ar);
        msg(p, "duel_admin_arena_one", new String[]{name});
    }

    /* ── arenaready <name> ────────────────────────────────────────────*/
    private void arenaReady(Player p, String[] a) {
        if (a.length != 3) { msg(p, "duel_admin_arena_proper_name"); return; }
        Arena ar = findArena(a[2]);
        if (ar == null) { msg(p, "duel_admin_arena_proper_name"); return; }

        // Kick off heavy FS-work async so we never freeze the main thread.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Path placeholderDir = plugin.getDataFolder().toPath().resolve("duel-placeholder");
            Path targetDir      = plugin.getDataFolder().toPath().resolve(ar.getName() + "-duel");

            try {
                /* 1️⃣ ensure placeholder exists */
                if (!Files.exists(placeholderDir)) {
                    plugin.getLogger().warning("[DuelAdmin] duel-placeholder directory missing! Aborting copy.");
                    return;
                }

                /* 2️⃣ wipe target directory if present */
                if (Files.exists(targetDir)) deleteRecursively(targetDir);

                /* 3️⃣ copy template into place */
                copyRecursively(placeholderDir, targetDir);
            } catch (IOException ex) {
                plugin.getLogger().severe("[DuelAdmin] Failed to prepare arena files for " + ar.getName());
                ex.printStackTrace();
                return;
            }

            // Hop back to main thread for user feedback
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    msg(p, "duel_admin_arena_ready", new String[]{ar.getName()})
            );
        });
    }

    /*────────────────────────────────────────────────────────────────*/
    /*  Small FS helpers – NIO walkFileTree                           */
    /*────────────────────────────────────────────────────────────────*/
    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir); return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void copyRecursively(Path src, Path dst) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path rel = src.relativize(dir);
                Files.createDirectories(dst.resolve(rel));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path rel = src.relativize(file);
                Files.copy(file, dst.resolve(rel), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /*───────────────────────────────────────────────────────────────────*/
    /*  K I T  C O M M A N D S                                          */
    /*───────────────────────────────────────────────────────────────────*/
    private void handleKit(Player p, String[] a) {
        if (a.length == 1) { msg(p, "duel_admin_kit"); return; }
        switch (a[1].toLowerCase()) {
            case "create"         -> kitCreate(p, a);
            case "saveinv"        -> kitSaveInv(p, a);
            case "setdisplayitem" -> kitSetDisplay(p, a);
            case "setslot"        -> kitSetSlot(p, a);
            case "setbuild"       -> kitSetBool(p, a, "build");
            case "setnaturalregen"-> kitSetBool(p, a, "naturalregen");
            case "sethealthindics"-> kitSetBool(p, a, "healthindics");
            case "setlosehunger"  -> kitSetBool(p, a, "losehunger");
            case "info"           -> kitInfo(p, a);
            case "list"           -> kitList(p);          // NEW
            case "delete"         -> kitDelete(p, a);     // NEW
            case "give"           -> findKit(a[2]).giveKit(p); // NEW
            default               -> msg(p, "duel_admin_kit");
        }
    }

    /* ── kit list ─────────────────────────────────────────────────────*/
    private void kitList(Player p) {
        if (DuelManager.stor.kits.isEmpty()) {
            p.sendMessage(ChatColor.YELLOW + "No kits defined.");
            return;
        }
        p.sendMessage(ChatColor.GREEN + "Available kits:");
        DuelManager.stor.kits.forEach(k ->
                p.sendMessage(ChatColor.AQUA + " - "
                        + k.getName() + ChatColor.GRAY + " (slot " + k.getSlot() + ")"));
    }

    /* ── kit delete <name> ────────────────────────────────────────────*/
    private void kitDelete(Player p, String[] a) {
        if (a.length != 3) { msg(p, "duel_admin_kit_proper_name"); return; }
        Kit k = findKit(a[2]);
        if (k == null) { msg(p, "duel_admin_kit_proper_name"); return; }
        DuelManager.stor.kits.remove(k);
        p.sendMessage(ChatColor.RED + "Deleted kit " + k.getName() + ".");
    }

    private void kitCreate(Player p, String[] a) {
        if (a.length != 3) { msg(p, "duel_admin_kit_proper_name"); return; }
        if (findKit(a[2]) != null) { msg(p, "duel_admin_kit_exists"); return; }
        Kit k = new Kit(); k.setName(a[2]); DuelManager.stor.kits.add(k);
        msg(p, "duel_admin_kit_one", new String[]{a[2]});
    }

    private void kitSaveInv(Player p, String[] a) {
        if (a.length != 3) { msg(p, "duel_admin_kit_proper_name"); return; }
        Kit k = findKit(a[2]);
        if (k == null) { msg(p, "duel_admin_kit_proper_name"); return; }

        HashMap<Integer, SerializableItem> inv = new HashMap<>();
        var pinv = p.getInventory();

        for (int i = 0; i < 36; i++) {
            if (pinv.getItem(i) != null) inv.put(i, new SerializableItem(pinv.getItem(i)));
        }
        if (pinv.getBoots() != null)      inv.put(Kit.SLOT_BOOTS, new SerializableItem(pinv.getBoots()));
        if (pinv.getLeggings() != null)   inv.put(Kit.SLOT_LEGGINGS, new SerializableItem(pinv.getLeggings()));
        if (pinv.getChestplate() != null) inv.put(Kit.SLOT_CHESTPLATE, new SerializableItem(pinv.getChestplate()));
        if (pinv.getHelmet() != null)     inv.put(Kit.SLOT_HELMET, new SerializableItem(pinv.getHelmet()));
        if (pinv.getItemInOffHand() != null) inv.put(Kit.SLOT_OFFHAND, new SerializableItem(pinv.getItemInOffHand()));

        k.setInventory(inv);
        msg(p, "duel_admin_kit_two", new String[]{k.getName()});
    }

    private void kitSetDisplay(Player p, String[] a) {
        if (a.length != 3) { msg(p, "duel_admin_kit_proper_name"); return; }
        Kit k = findKit(a[2]); if (k == null) { msg(p, "duel_admin_kit_proper_name"); return; }
        k.setDisplayItem(new SerializableItem(p.getInventory().getItemInMainHand()));
        msg(p, "duel_admin_kit_three", new String[]{k.getName()});
    }

    private void kitSetSlot(Player p, String[] a) {
        if (a.length != 4) { msg(p, "duel_admin_kit_setslot_usage"); return; }
        Kit k = findKit(a[2]); if (k == null) { msg(p, "duel_admin_kit_proper_name"); return; }
        try { k.setSlot(Integer.parseInt(a[3])); msg(p, "duel_admin_kit_four", new String[]{a[3]}); }
        catch (NumberFormatException e) { msg(p, "duel_admin_kit_setslot_usage"); }
    }

    private void kitSetBool(Player p, String[] a, String flag) {
        if (a.length != 4) { msg(p, "duel_admin_kit_proper_name"); return; }
        Kit k = findKit(a[2]);
        if (k == null) { msg(p, "duel_admin_kit_proper_name"); return; }

        boolean value;
        if ("true".equalsIgnoreCase(a[3]) || "yes".equalsIgnoreCase(a[3])) value = true;
        else if ("false".equalsIgnoreCase(a[3]) || "no".equalsIgnoreCase(a[3])) value = false;
        else { msg(p, "duel_admin_kit_proper_name"); return; }

        switch (flag) {
            case "build"        -> k.setBuild(value);
            case "naturalregen" -> k.setNaturalRegen(value);
            case "healthindics" -> k.setHealthIndics(value);
            case "losehunger"   -> k.setLoseHunger(value);
        }
        msg(p, "duel_admin_kit_four", new String[]{flag + " = " + value});
    }

    private void kitInfo(Player p, String[] a) {
        if (a.length != 3) { msg(p, "duel_admin_kit_proper_name"); return; }
        Kit k = findKit(a[2]); if (k == null) { msg(p, "duel_admin_kit_proper_name"); return; }
        p.sendMessage(k.getInfo());
    }

    /*───────────────────────────────────────────────────────────────────*/
    /*  A R E N A  helper methods (setdisplay, setspawn, …)             */
    /*───────────────────────────────────────────────────────────────────*/
    private void arenaSetDisplay(Player p, String[] a) {
        if (a.length != 3) { msg(p, "duel_admin_arena_proper_name"); return; }
        Arena ar = findArena(a[2]);
        if (ar == null) { msg(p, "duel_admin_arena_proper_name"); return; }
        ar.setDisplayItem(new SerializableItem(p.getInventory().getItemInMainHand()));
        msg(p, "duel_admin_arena_two", new String[]{ar.getName()});
    }

    private void arenaSetSchematic(Player p, String[] a) {
        if (a.length != 4) { msg(p, "duel_admin_arena_setschematic_usage"); return; }
        Arena ar = findArena(a[2]);
        if (ar == null) { msg(p, "duel_admin_arena_proper_name"); return; }
        ar.setSchematic(a[3]);
        msg(p, "duel_admin_arena_setschematic", new String[]{ar.getName()});
    }

    private void arenaSetSpawn(Player p, String[] a) {
        if (a.length != 4) { msg(p, "duel_admin_arena_setspawn_usage"); return; }
        Arena ar = findArena(a[2]);
        if (ar == null) { msg(p, "duel_admin_arena_proper_name"); return; }
        int team;
        try { team = Integer.parseInt(a[3]); if (team != 1 && team != 2) throw new NumberFormatException(); }
        catch (NumberFormatException e) { msg(p, "duel_admin_arena_setspawn_usage"); return; }
        Location l = p.getLocation();
        ar.setSpawn(team, "x", l.getX());
        ar.setSpawn(team, "y", l.getY());
        ar.setSpawn(team, "z", l.getZ());
        ar.setSpawn(team, "yaw", l.getYaw());
        ar.setSpawn(team, "pitch", l.getPitch());
        msg(p, "duel_admin_arena_setspawn", new String[]{"" + team});
    }

    private void arenaSetCorner(Player p, String[] a) {
        if (a.length != 5) { msg(p, "duel_admin_arena_setcorner_usage"); return; }
        Arena ar = findArena(a[2]);
        if (ar == null) { msg(p, "duel_admin_arena_proper_name"); return; }
        try {
            double val = Double.parseDouble(a[4]);
            ar.setCornerParameter(a[3], val);
            msg(p, "duel_admin_arena_setcorner", new String[]{a[3], a[4]});
        } catch (NumberFormatException e) { msg(p, "duel_admin_arena_setcorner_usage"); }
    }

    private void arenaSetOffset(Player p, String[] a) {
        if (a.length != 5) { msg(p, "duel_admin_arena_setoffset_usage"); return; }
        Arena ar = findArena(a[2]);
        if (ar == null) { msg(p, "duel_admin_arena_proper_name"); return; }
        try {
            int val = Integer.parseInt(a[4]);
            switch (a[3].toLowerCase()) {
                case "x" -> ar.setOffsetXAxis(val);
                case "z" -> ar.setOffsetZAxis(val);
                default   -> { msg(p, "duel_admin_arena_setoffset_usage"); return; }
            }
            msg(p, "duel_admin_arena_setoffset", new String[]{a[3].toUpperCase(), a[4]});
        } catch (NumberFormatException e) { msg(p, "duel_admin_arena_setoffset_usage"); }
    }

    private void arenaSetMax(Player p, String[] a) {
        if (a.length != 5) { msg(p, "duel_admin_arena_setmax_usage"); return; }
        Arena ar = findArena(a[2]);
        if (ar == null) { msg(p, "duel_admin_arena_proper_name"); return; }
        try {
            int val = Integer.parseInt(a[4]);
            switch (a[3].toLowerCase()) {
                case "perline"   -> ar.setMaxPerLine(val);
                case "maxarenas" -> ar.setMaxArenas(val);
                default           -> { msg(p, "duel_admin_arena_setmax_usage"); return; }
            }
            msg(p, "duel_admin_arena_setmax", new String[]{a[3], a[4]});
        } catch (NumberFormatException e) { msg(p, "duel_admin_arena_setmax_usage"); }
    }

    private void arenaInfo(Player p, String[] a) {
        if (a.length != 3) { msg(p, "duel_admin_arena_proper_name"); return; }
        Arena ar = findArena(a[2]);
        if (ar == null) { msg(p, "duel_admin_arena_proper_name"); return; }
        p.sendMessage(ar.getInfo());
    }
}
