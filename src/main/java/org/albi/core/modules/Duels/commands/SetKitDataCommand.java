package org.albi.core.modules.Duels.commands;

import org.albi.core.Core;
import org.albi.core.data.Data;
import org.albi.core.data.DataType;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.data.EffectMeta;
import org.albi.core.modules.Duels.helpers.Kit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetKitDataCommand extends BaseCommand {

    private final Core plugin;

    public SetKitDataCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        /* ───────────────────────────── */
        /* Player + permission checks   */
        /* ───────────────────────────── */
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!p.hasPermission("core.dueladmin")) {
            p.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        /* ───────────────────────────── */
        /* Argument validation          */
        /* ───────────────────────────── */
        if (args.length < 4) {
            p.sendMessage(ChatColor.RED +
                    "Usage: /setkitdata <kit> <type> <key> <value>");
            return true;
        }

        String kitName = args[0];
        String typeStr = args[1].toUpperCase();
        String key = args[2];

        /* Join value args (supports spaces) */
        StringBuilder valueBuilder = new StringBuilder();
        for (int i = 3; i < args.length; i++) {
            valueBuilder.append(args[i]).append(" ");
        }
        String valueRaw = valueBuilder.toString().trim();

        /* ───────────────────────────── */
        /* Find kit                     */
        /* ───────────────────────────── */
        Kit kit = DuelManager.stor.kits.stream()
                .filter(k -> k.getName().equalsIgnoreCase(kitName))
                .findFirst()
                .orElse(null);

        if (kit == null) {
            p.sendMessage(ChatColor.RED + "Kit not found.");
            return true;
        }

        Data meta = kit.getMetaData();

        /* ───────────────────────────── */
        /* Parse value by type          */
        /* ───────────────────────────── */
        Object finalValue;

        try {
            switch (DataType.valueOf(typeStr)) {

                case STRING -> finalValue = valueRaw;

                case INT -> finalValue = Integer.parseInt(valueRaw);

                case DOUBLE -> finalValue = Double.parseDouble(valueRaw);

                case BOOLEAN -> {
                    if (!valueRaw.equalsIgnoreCase("true")
                            && !valueRaw.equalsIgnoreCase("false")) {
                        throw new IllegalArgumentException();
                    }
                    finalValue = Boolean.parseBoolean(valueRaw);
                }

                case LONG -> finalValue = Long.parseLong(valueRaw);

                /* ───────────────────────────── */
                /* EFFECT META TYPE             */
                /* Format: EFFECT:AMP:DUR:AMB  */
                /* Example: SPEED:1:60:false   */
                /* ───────────────────────────── */
                case EFFECTMETA -> {

                    String[] parts = valueRaw.split(":");

                    if (parts.length != 4) {
                        p.sendMessage(ChatColor.RED +
                                "EffectMeta format: EFFECT:AMPLIFIER:DURATION:AMBIENT");
                        return true;
                    }

                    String effect = parts[0].toUpperCase();
                    int amplifier = Integer.parseInt(parts[1]);
                    int duration = Integer.parseInt(parts[2]);
                    boolean ambient = Boolean.parseBoolean(parts[3]);

                    EffectMeta eMeta =
                            new EffectMeta(effect, amplifier, duration, ambient);

                    /* Append to list instead of overwrite */
                    Object raw = kit.getMetaData().get(key);

                    java.util.List<EffectMeta> list;

                    if (raw instanceof java.util.List<?>) {
                        list = (java.util.List<EffectMeta>) raw;
                    } else {
                        list = new java.util.ArrayList<>();
                    }

                    list.add(eMeta);

                    finalValue = list;
                }

                default -> {
                    p.sendMessage(ChatColor.RED +
                            "Unsupported type for command.");
                    return true;
                }
            }

        } catch (Exception e) {
            p.sendMessage(ChatColor.RED +
                    "Invalid value for type " + typeStr + ".");
            return true;
        }


        /* ───────────────────────────── */
        /* Apply metadata               */
        /* ───────────────────────────── */
        meta.set(key, finalValue);

        p.sendMessage(ChatColor.GREEN + "Kit data updated:");
        p.sendMessage(ChatColor.GRAY + "Kit: " + ChatColor.WHITE + kit.getName());
        p.sendMessage(ChatColor.GRAY + "Key: " + ChatColor.WHITE + key);
        p.sendMessage(ChatColor.GRAY + "Value: " + ChatColor.WHITE + finalValue);
        p.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.WHITE + typeStr);

        return true;
    }
}
