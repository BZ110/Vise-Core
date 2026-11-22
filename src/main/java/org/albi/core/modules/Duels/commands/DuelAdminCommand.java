package org.albi.core.modules.Duels.commands;

import org.albi.core.gui.Messages;

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
            Messages.send(p, "messages.duel-admin-command.polar-arenas-are-disabled-or-polar-paper-is", ChatColor.RED);
            return;
        }
        if (a.length == 1) {
            Messages.send(p, "messages.duel-admin-command.usage-dueladmin-polar-arena-create-edit-arenaready-save", ChatColor.YELLOW);
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
            default -> Messages.send(p, "messages.duel-admin-command.usage-dueladmin-polar-arena-create-edit-arenaready-save", ChatColor.YELLOW);
        }
    }

    private void polarArenaCreate(Player p, String[] a) {
        if (a.length != 3) {
            Messages.send(p, "messages.duel-admin-command.usage-dueladmin-polar-arena-create-name", ChatColor.RED);
            return;
        }
        if (findArena(a[2]) != null) {
            Messages.send(p, "messages.duel-admin-command.an-arena-with-that-name-already-exists", ChatColor.RED);
            return;
        }
        Arena arena = new Arena();
        arena.setName(a[2]);
        arena.setPolarArena(true);
        DuelManager.stor.arenas.add(arena);
        DuelManager.polarArenaManager.createArena(arena, p, success -> {
            if (success) {
                Messages.send(p, "messages.duel-admin-command.polar-arena-created-you-were-moved-into-the", ChatColor.GREEN);
            } else {
                DuelManager.stor.arenas.remove(arena);
                Messages.send(p, "messages.duel-admin-command.failed-to-create-the-polar-arena-world", ChatColor.RED);
            }
        });
    }

    private void polarArenaEdit(Player p, String[] a) {
        if (a.length != 3) {
            Messages.send(p, "messages.duel-admin-command.usage-dueladmin-polar-arena-edit-name", ChatColor.RED);
            return;
        }
        Arena arena = findArena(a[2]);
        if (arena == null || !arena.isPolarArena()) {
            Messages.send(p, "messages.duel-admin-command.that-polar-arena-does-not-exist", ChatColor.RED);
            return;
        }
        DuelManager.polarArenaManager.loadOriginForEdit(arena, p, success -> {
            if (!success) Messages.send(p, "messages.duel-admin-command.failed-to-load-the-polar-origin-world", ChatColor.RED);
        });
    }

    private void polarArenaSave(Player p, String[] a) {
        if (a.length != 3) {
            Messages.send(p, "messages.duel-admin-command.usage-dueladmin-polar-arena-save-name", ChatColor.RED);
            return;
        }
        Arena arena = findArena(a[2]);
        if (arena == null || !arena.isPolarArena()) {
            Messages.send(p, "messages.duel-admin-command.that-polar-arena-does-not-exist", ChatColor.RED);
            return;
        }
        DuelManager.polarArenaManager.saveOrigin(arena, success -> {
            if (success) Messages.send(p, "messages.duel-admin-command.saved-polar-origin-world-and-refreshed-preloads", ChatColor.GREEN);
            else Messages.send(p, "messages.duel-admin-command.could-not-save-the-polar-origin-world", ChatColor.RED);
        });
    }

    private void polarArenaSetDisplay(Player p, String[] a) {
        if (a.length != 3) {
            Messages.send(p, "messages.duel-admin-command.usage-dueladmin-polar-arena-setdisplayitem-name", ChatColor.RED);
            return;
        }
        Arena arena = findArena(a[2]);
        if (arena == null || !arena.isPolarArena()) {
            Messages.send(p, "messages.duel-admin-command.that-polar-arena-does-not-exist", ChatColor.RED);
            return;
        }
        arena.setDisplayItem(new SerializableItem(p.getInventory().getItemInMainHand()));
        Messages.send(p, "messages.duel-admin-command.updated-polar-arena-display-item-for", ChatColor.GREEN, arena.getName());
    }

    private void polarArenaSetSpawnLegacy(Player p, String[] a) {
        if (a.length != 4) {
            Messages.send(p, "messages.duel-admin-command.usage-dueladmin-polar-arena-setspawn-name-1-2", ChatColor.RED);
            return;
        }
        try {
            int team = Integer.parseInt(a[3]);
            if (team != 1 && team != 2) throw new NumberFormatException();
            polarArenaSetSpawn(p, a, team);
        } catch (NumberFormatException ex) {
            Messages.send(p, "messages.duel-admin-command.usage-dueladmin-polar-arena-setspawn-name-1-2", ChatColor.RED);
        }
    }

    private void polarArenaSetSpawn(Player p, String[] a, int team) {
        if (a.length != 3 && a.length != 4) {
            Messages.send(p, "messages.duel-admin-command.usage-dueladmin-polar-arena-spawn-name", ChatColor.RED, team);
            return;
        }
        String arenaName = a[2];
        Arena arena = findArena(arenaName);
        if (arena == null || !arena.isPolarArena()) {
            Messages.send(p, "messages.duel-admin-command.that-polar-arena-does-not-exist", ChatColor.RED);
            return;
        }
        Location l = p.getLocation();
        arena.setSpawn(team, "x", l.getX());
        arena.setSpawn(team, "y", l.getY());
        arena.setSpawn(team, "z", l.getZ());
        arena.setSpawn(team, "yaw", l.getYaw());
        arena.setSpawn(team, "pitch", l.getPitch());
        Messages.send(p, "messages.duel-admin-command.saved-exact-world-coordinates-for-spawn-on", ChatColor.GREEN, team, arena.getName());
    }

    private void polarArenaSetLimit(Player p, String[] a) {
        if (a.length != 4) {
            Messages.send(p, "messages.duel-admin-command.usage-dueladmin-polar-arena-set-limit-name-1", ChatColor.RED);
            return;
        }
        Arena arena = findArena(a[2]);
        if (arena == null || !arena.isPolarArena()) {
            Messages.send(p, "messages.duel-admin-command.that-polar-arena-does-not-exist", ChatColor.RED);
            return;
        }
        Location loc = p.getLocation();
        switch (a[3].toLowerCase()) {
            case "1", "min" -> {
                arena.setCornerParameter("x1", loc.getX());
                arena.setCornerParameter("z1", loc.getZ());
                Messages.send(p, "messages.duel-admin-command.saved-limit-corner-1-for", ChatColor.GREEN, arena.getName());
            }
            case "2", "max" -> {
                arena.setCornerParameter("x2", loc.getX());
                arena.setCornerParameter("z2", loc.getZ());
                Messages.send(p, "messages.duel-admin-command.saved-limit-corner-2-for", ChatColor.GREEN, arena.getName());
            }
            default -> Messages.send(p, "messages.duel-admin-command.usage-dueladmin-polar-arena-set-limit-name-1", ChatColor.RED);
        }
    }

    private void polarArenaInfo(Player p, String[] a) {
        if (a.length != 3) {
            Messages.send(p, "messages.duel-admin-command.usage-dueladmin-polar-arena-info-name", ChatColor.RED);
            return;
        }
        Arena arena = findArena(a[2]);
        if (arena == null || !arena.isPolarArena()) {
            Messages.send(p, "messages.duel-admin-command.that-polar-arena-does-not-exist", ChatColor.RED);
            return;
        }
        p.sendMessage(arena.getInfo());
        Messages.send(p, "messages.duel-admin-command.origin-world", ChatColor.GRAY, DuelManager.polarArenaManager.originWorldName(arena));
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
            Messages.send(p, "messages.duel-admin-command.no-kits-defined", ChatColor.YELLOW);
            return;
        }
        Messages.send(p, "messages.duel-admin-command.available-kits", ChatColor.GREEN);
        DuelManager.stor.kits.forEach(k ->
                Messages.send(p, "messages.duel-admin-command.slot", ChatColor.AQUA, k.getName(), ChatColor.GRAY, k.getSlot()));
    }

    /* ── kit delete <name> ────────────────────────────────────────────*/
    private void kitDelete(Player p, String[] a) {
        if (a.length != 3) { msg(p, "duel_admin_kit_proper_name"); return; }
        Kit k = findKit(a[2]);
        if (k == null) { msg(p, "duel_admin_kit_proper_name"); return; }
        DuelManager.stor.kits.remove(k);
        Messages.send(p, "messages.duel-admin-command.deleted-kit", ChatColor.RED, k.getName());
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
