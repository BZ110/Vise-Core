package org.albi.core.commands;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.serializable.SerializableSpawnInventory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class SetSpawnItemCommand extends BaseCommand {

    private final Core plugin;

    public SetSpawnItemCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        // ─────────────────────────────────────────────
        //  Player & permission checks
        // ─────────────────────────────────────────────
        if (!(sender instanceof Player player)) {
            plugin.getMessageAPI().sendMessageLang(sender, "players_only");
            return true;
        }

        String cmdName = command.getName().toLowerCase();
        int secondsForCooldown = plugin.getConfig().getInt("cooldowns." + cmdName, 3);
        ExtendedPlayer eP = plugin.getExtendedPlayer(player);
        if(eP.checkCooldown(cmdName)) {
            plugin.getMessageAPI().sendMessageLang(player, "cooldown");
            return true;
        } else {
            eP.addCooldown(cmdName, secondsForCooldown);
        }

        if (!player.hasPermission("core.setspawnitem")) {
            plugin.getMessageAPI().sendMessageLang(player, "no_permission");
            return true;
        }

        // ─────────────────────────────────────────────
        //  Usage: /setspawnitem <slot> <command…>
        // ─────────────────────────────────────────────
        if (args.length < 2) {
            plugin.getMessageAPI().sendMessageLang(player, "setspawnitem_usage");
            return true;
        }

        int slot;
        try {
            slot = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            plugin.getMessageAPI().sendMessageLang(player, "setspawnitem_int");
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            plugin.getMessageAPI().sendMessageLang(player, "setspawnitem_noitem");
            return true;
        }

        String cmdString = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        // ─────────────────────────────────────────────
        //  Store or create the SerializableSpawnInventory
        // ─────────────────────────────────────────────
        SerializableSpawnInventory spawnInv =
                (SerializableSpawnInventory) plugin.getData().get("spawnInventory");
        if (spawnInv == null) spawnInv = new SerializableSpawnInventory();

        spawnInv.addItem(slot, hand, cmdString);
        plugin.getData().set("spawnInventory", spawnInv);

        plugin.getMessageAPI().sendMessageLang(
                player,
                "setspawnitem_success",
                new String[]{
                        String.valueOf(slot),
                        cmdString
                });

        return true;
    }
}
