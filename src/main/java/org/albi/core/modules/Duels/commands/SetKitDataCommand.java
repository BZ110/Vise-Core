package org.albi.core.modules.Duels.commands;

import org.albi.core.gui.Messages;

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
            Messages.send(sender, "messages.set-kit-data-command.players-only");
            return true;
        }

        if (!p.hasPermission("core.dueladmin")) {
            Messages.send(p, "messages.set-kit-data-command.no-permission", ChatColor.RED);
            return true;
        }

        /* ───────────────────────────── */
        /* Argument validation          */
        /* ───────────────────────────── */
        if (args.length < 4) {
            Messages.send(p, "messages.set-kit-data-command.usage-setkitdata-kit-type-key-value", ChatColor.RED);
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
            Messages.send(p, "messages.set-kit-data-command.kit-not-found", ChatColor.RED);
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
                        Messages.send(p, "messages.set-kit-data-command.effect-meta-format-effect-amplifier-duration-ambient", ChatColor.RED);
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
                    Messages.send(p, "messages.set-kit-data-command.unsupported-type-for-command", ChatColor.RED);
                    return true;
                }
            }

        } catch (Exception e) {
            Messages.send(p, "messages.set-kit-data-command.invalid-value-for-type", ChatColor.RED, typeStr);
            return true;
        }


        /* ───────────────────────────── */
        /* Apply metadata               */
        /* ───────────────────────────── */
        meta.set(key, finalValue);

        Messages.send(p, "messages.set-kit-data-command.kit-data-updated", ChatColor.GREEN);
        Messages.send(p, "messages.set-kit-data-command.kit", ChatColor.GRAY, ChatColor.WHITE, kit.getName());
        Messages.send(p, "messages.set-kit-data-command.key", ChatColor.GRAY, ChatColor.WHITE, key);
        Messages.send(p, "messages.set-kit-data-command.value", ChatColor.GRAY, ChatColor.WHITE, finalValue);
        Messages.send(p, "messages.set-kit-data-command.type", ChatColor.GRAY, ChatColor.WHITE, typeStr);

        return true;
    }
}
