package org.albi.core.modules.Party.commands;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.Party.GUIs.PartyCommandDuelSubGUI;
import org.albi.core.modules.Friends.FriendsManager;
import org.albi.core.modules.ModuleManager;
import org.albi.core.modules.Party.PartyManager;
import org.albi.core.modules.Party.play.PartyPlayCommand;
import org.albi.core.modules.Party.play.PartyPlayGUIs;
import org.albi.core.modules.Party.play.PartyPlaySession;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PartyCommand extends BaseCommand {

    private final Core plugin;
    private final PartyManager partyManager;

    public PartyCommand(Core plugin, PartyManager partyManager) {
        this.plugin = plugin;
        this.partyManager = partyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            Messages.send(sender, "messages.party-command.only-players-can-use-this-command");
            return true;
        }

        String cmdName = command.getName().toLowerCase();
        int secondsForCooldown = plugin.getConfig().getInt("cooldowns." + cmdName, 1);
        ExtendedPlayer eP = plugin.getExtendedPlayer(player);
        if (eP.checkCooldown(cmdName)) {
            plugin.getMessageAPI().sendMessageLang(player, "cooldown");
            return true;
        } else {
            eP.addCooldown(cmdName, secondsForCooldown);
        }

        if (args.length == 0) {
            plugin.getMessageAPI().sendMessageLang(player, "party_usage");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help" -> plugin.getMessageAPI().sendMessageLang(player, "party_help");
            case "create" -> partyManager.createParty(player);
            case "invite" -> {
                if (args.length >= 2) {
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target != null) partyManager.invite(player, target);
                    else plugin.getMessageAPI().sendMessageLang(player, "player_not_found");
                }
            }
            case "list" -> partyManager.list(player);
            case "accept" -> partyManager.accept(player);
            case "leave" -> partyManager.leave(player);
            case "settings" -> partyManager.openPartySettings(player);
            case "disband" -> {
                if (partyManager.isLeader(player)) partyManager.disband(partyManager.getParty(player));
                else plugin.getMessageAPI().sendMessageLang(player, "party_not_leader");
            }
            case "duel" -> {
                if (!partyManager.isLeader(player)) {
                    plugin.getMessageAPI().sendMessageLang(player, "party_not_leader");
                    return true;
                }
                if (!(args.length > 1)) {
                    plugin.getMessageAPI().sendMessageLang(player, "party_duel_case");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !partyManager.isLeader(target)) {
                    plugin.getMessageAPI().sendMessageLang(player, "party_duel_case");
                    return true;
                }
                FriendsManager friends = ModuleManager.getModule(FriendsManager.class);
                boolean friendBypass = friends != null && friends.isFriends(player, target);
                if (plugin.getExtendedPlayer(target).getMainPlayer().getBlockedList().contains(player.getUniqueId())
                        || (!friendBypass && !plugin.getExtendedPlayer(target).getMainPlayer().partyDuelInvites())) {
                    plugin.getMessageAPI().sendMessageLang(player, "party_duel_unable");
                    return true;
                }
                plugin.getExtendedPlayer(player).playersSaved[1] = target;
                new PartyCommandDuelSubGUI(plugin, partyManager).openKitGUI(player, target);
            }
            case "play" -> {
                if (!partyManager.isLeader(player)) {
                    plugin.getMessageAPI().sendMessageLang(player, "party_not_leader");
                    return true;
                }
                if (partyManager.getParty(player) == null) {
                    plugin.getMessageAPI().sendMessageLang(player, "party_none");
                    return true;
                }
                PartyPlayCommand.ACTIVE.put(player.getUniqueId(), new PartyPlaySession());
                new PartyPlayGUIs.SizeSelectorGUI(plugin, partyManager).open(player);
            }
            case "open" -> partyManager.toggleOpen(player);
            case "join" -> {
                if (args.length >= 2) {
                    partyManager.join(player, args[1]);
                } else {
                    plugin.getMessageAPI().sendMessageLang(player, "party_join_usage");
                }
            }
            default -> plugin.getMessageAPI().sendMessageLang(player, "party_unknown_subcommand");
        }

        return true;
    }
}
