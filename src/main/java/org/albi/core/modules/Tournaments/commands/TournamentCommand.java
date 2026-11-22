package org.albi.core.modules.Tournaments.commands;

import org.albi.core.gui.Messages;

import org.albi.core.gui.GuiService;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.item.ItemBuilder;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Kit;
import org.albi.core.modules.Party.PartyManager;
import org.albi.core.modules.Tournaments.TournamentManager;
import org.albi.core.modules.Tournaments.helpers.TournamentSession;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TournamentCommand implements CommandExecutor {

    private final Core plugin;
    private final TournamentManager manager;

    public TournamentCommand(Core plugin, TournamentManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            Messages.send(sender, "messages.tournament-command.players-only");
            return true;
        }

        if (label.equalsIgnoreCase("next")) {
            manager.autoSpectateNext(p);
            return true;
        }

        if (args.length == 0) {
            Messages.send(p, "messages.tournament-command.usage-tournament-join-leave-create-start");
            return true;
        }

        if (args[0].equalsIgnoreCase("create")) {
            if (!p.hasPermission("core.tournament.admin")) {
                plugin.getMessageAPI().sendMessageLang(p, "no_permission");
                return true;
            }
            openCreateGui(p);
            return true;
        } else if (args[0].equalsIgnoreCase("start")) {
            if (!p.hasPermission("core.tournament.admin")) {
                plugin.getMessageAPI().sendMessageLang(p, "no_permission");
                return true;
            }
            TournamentSession target = null;
            for (TournamentSession s : manager.getActiveTournaments().values()) {
                if (s.getState() == TournamentSession.State.WAITING) {
                    target = s;
                    break;
                }
            }
            if (target == null) {
                Messages.send(p, "messages.tournament-command.no-waiting-tournament-found");
                return true;
            }
            startTournamentPhase(target);
            return true;
        } else if (args[0].equalsIgnoreCase("join")) {
            TournamentSession target = null;
            for (TournamentSession s : manager.getActiveTournaments().values()) {
                if (s.getState() == TournamentSession.State.WAITING) {
                    target = s;
                    break;
                }
            }

            if (target == null) {
                Messages.send(p, "messages.tournament-command.there-is-no-active-tournament-waiting-for-players");
                return true;
            }

            // check if they are in party
            PartyManager pm = plugin.getPartyManager();
            if (pm.isInParty(p)) {
                if (!pm.isLeader(p)) {
                    Messages.send(p, "messages.tournament-command.only-the-party-leader-can-join-the-tournament");
                    return true;
                }
                if (pm.getMembers(p).size() > target.getTeamSize()) {
                    Messages.send(p, "messages.tournament-command.your-party-is-too-large-for-this-tournament", target.getTeamSize());
                    return true;
                }

                // Add whole party
                for (UUID member : pm.getMembers(p)) {
                    target.getParticipants().add(member);
                }
                pm.getParty(p).broadcast(plugin, "tournament_joined", null);

            } else {
                target.getParticipants().add(p.getUniqueId());
                plugin.getMessageAPI().sendMessageLang(p, "tournament_joined");
            }

            plugin.giveAppropriateLobbyItems(p);

            return true;
        } else if (args[0].equalsIgnoreCase("leave")) {
            TournamentSession target = manager.getSession(p);
            if (target == null) {
                Messages.send(p, "messages.tournament-command.you-are-not-in-a-tournament");
                return true;
            }
            
            PartyManager pm = plugin.getPartyManager();
            if (pm.isInParty(p) && target.getState() == TournamentSession.State.WAITING) {
                if (!pm.isLeader(p)) {
                    Messages.send(p, "messages.tournament-command.only-the-party-leader-can-leave-the-tournament");
                    return true;
                }
                for (UUID member : pm.getMembers(p)) {
                    target.getParticipants().remove(member);
                    Player online = Bukkit.getPlayer(member);
                    if (online != null) {
                        plugin.getMessageAPI().sendMessageLang(online, "tournament_leave");
                        plugin.giveAppropriateLobbyItems(online);
                    }
                }
            } else {
                target.getParticipants().remove(p.getUniqueId());
                plugin.getMessageAPI().sendMessageLang(p, "tournament_leave");
                plugin.giveAppropriateLobbyItems(p);
            }

            return true;
        }

        return true;
    }

    private void openCreateGui(Player p) {
        SGMenu menu = plugin.getMenus().create("tournaments.kits", 5);
        int slot = 0;
        for (Kit k : DuelManager.stor.getAllKits()) {
            if (slot > 44) break;
            ItemStack icon = k.getDisplayItem().convertToItem().clone();
            menu.setButton(slot, new SGButton(icon).withListener(e -> {
                p.closeInventory();
                openSizeGui(p, k);
            }));
            slot++;
        }
        GuiService.open(p, menu);
    }

    private void openSizeGui(Player p, Kit k) {
        SGMenu menu = plugin.getMenus().create("tournaments.team-size", 1);
        
        GuiService.control(menu, "1v1", 3, new SGButton(new ItemBuilder(Material.IRON_SWORD).name("§a1v1").build()).withListener(e -> {
            p.closeInventory();
            createTournament(p, k, 1);
        }));

        GuiService.control(menu, "2v2", 4, new SGButton(new ItemBuilder(Material.DIAMOND_SWORD).name("§e2v2").build()).withListener(e -> {
            p.closeInventory();
            createTournament(p, k, 2);
        }));

        GuiService.control(menu, "3v3", 5, new SGButton(new ItemBuilder(Material.NETHERITE_SWORD).name("§c3v3").build()).withListener(e -> {
            p.closeInventory();
            createTournament(p, k, 3);
        }));

        GuiService.open(p, menu);
    }

    private void createTournament(Player p, Kit k, int size) {
        String tourneyName = "Server Setup " + k.getName() + " " + size + "v" + size;
        manager.getAPI().createTournament(tourneyName, "s" + getRand()).thenAccept(json -> {
            if (json == null || !json.has("tournament")) {
                Messages.send(p, "messages.tournament-command.failed-to-reach-challonge-api-please-check-your");
                return;
            }
            
            String url = json.getAsJsonObject("tournament").get("url").getAsString();
            TournamentSession session = new TournamentSession(url, k, size, 32);
            manager.getActiveTournaments().put(url, session);
            
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.broadcastMessage(Messages.text(null, "messages.tournament-command.create-tournament"));
                Bukkit.broadcastMessage(" ");
                Bukkit.broadcastMessage(Messages.text(null, "messages.tournament-command.new-tournament-started"));
                Bukkit.broadcastMessage(Messages.text(null, "messages.tournament-command.kit", k.getName()));
                Bukkit.broadcastMessage(Messages.text(null, "messages.tournament-command.mode-v", size, size));
                Bukkit.broadcastMessage(" ");
                Bukkit.broadcastMessage(Messages.text(null, "messages.tournament-command.type-tournament-join-to-play"));
                Bukkit.broadcastMessage(" ");
                Bukkit.broadcastMessage(Messages.text(null, "messages.tournament-command.create-tournament"));
            });
        });
    }

    private void startTournamentPhase(TournamentSession session) {
        session.setState(TournamentSession.State.PLAYING);
        session.setCurrentRound(1);
        
        Bukkit.broadcastMessage(Messages.text(null, "messages.tournament-command.the-tournament-is-now-generating-matches-the-bracket"));
        
        // Form teams
        List<UUID> pool = new ArrayList<>(session.getParticipants());
        Collections.shuffle(pool);
        
        List<List<UUID>> finalTeams = new ArrayList<>();
        
        // Step 1: Extract pre-made parties first
        PartyManager pm = plugin.getPartyManager();
        Iterator<UUID> iter = pool.iterator();
        while (iter.hasNext()) {
            UUID id = iter.next();
            Player p = Bukkit.getPlayer(id);
            if (p != null && pm.isInParty(p)) {
                if (pm.isLeader(p)) { // Ensure we process the party once from the leader's context
                    List<UUID> members = new ArrayList<>();
                    for (UUID pId : pm.getMembers(p)) {
                        if (session.getParticipants().contains(pId)) {
                            members.add(pId);
                        }
                    }
                    if (!members.isEmpty()) finalTeams.add(members);
                    // Remove collected party members from the pool
                    for (UUID mId : members) {
                        pool.remove(mId); // Might cause concurrent mod, we should break and re-loop or do it safely
                    }
                    iter = pool.iterator(); // reset iterator since we modified collection
                }
            }
        }
        
        // Step 2: Form random sets for the solos left over
        List<UUID> currentTeam = new ArrayList<>();
        for (UUID id : pool) {
            currentTeam.add(id);
            if (currentTeam.size() == session.getTeamSize()) {
                finalTeams.add(new ArrayList<>(currentTeam));
                currentTeam.clear();
            }
        }
        
        if (!currentTeam.isEmpty()) {
            finalTeams.add(currentTeam); // remaining odd one out
        }
        
        // Inform Challonge
        for (int i = 0; i < finalTeams.size(); i++) {
            List<UUID> team = finalTeams.get(i);
            
            // Generate a readable name: Team LeaderName
            String teamName = "Team " + (i + 1);
            if (!team.isEmpty()) {
                Player p = Bukkit.getPlayer(team.get(0));
                if (p != null) teamName = "Team " + p.getName();
            }
            
            final List<UUID> capture = team;
            manager.getAPI().addParticipant(session.getTournamentUrl(), teamName, "").thenAccept(json -> {
                if (json != null && json.has("participant")) {
                    long participantId = json.getAsJsonObject("participant").get("id").getAsLong();
                    session.getTeams().put(participantId, capture);
                }
            });
        }
        
        // Wait a few seconds for API to catch up participants, then start bracket
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            manager.getAPI().startTournament(session.getTournamentUrl()).thenAccept(success -> {
                if (success) {
                    plugin.getLogger().info("Successfully started bracket locally and remotely!");
                }
            });
        }, 60L);
    }
    
    private int getRand() {
        return new Random().nextInt(99999);
    }
}
