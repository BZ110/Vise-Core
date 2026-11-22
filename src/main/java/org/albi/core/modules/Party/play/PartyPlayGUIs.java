package org.albi.core.modules.Party.play;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.modules.CustomKit.helpers.CustomKit;
import org.albi.core.modules.Duels.DuelInstance;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Arena;
import org.albi.core.modules.Duels.helpers.Kit;
import org.albi.core.modules.Party.PartyManager;
import org.albi.core.modules.Party.helpers.Party;
import org.albi.core.serializable.SerializableItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

import static org.albi.core.modules.Party.play.PartyPlayCommand.ACTIVE;

public final class PartyPlayGUIs {

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack it = new ItemStack(material);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(name);
            im.setLore(lore);
            it.setItemMeta(im);
        }
        return it;
    }

    private static ItemStack head(OfflinePlayer player, String name, List<String> lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta raw = it.getItemMeta();
        if (raw instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
            skull.setDisplayName(name);
            skull.setLore(lore);
            it.setItemMeta(skull);
        } else if (raw != null) {
            raw.setDisplayName(name);
            raw.setLore(lore);
            it.setItemMeta(raw);
        }
        return it;
    }

    private static String displayName(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString().substring(0, 8) : name;
    }

    public static final class SizeSelectorGUI {
        private final Core pl;
        private final PartyManager pm;
        public SizeSelectorGUI(Core pl, PartyManager pm) { this.pl = pl; this.pm = pm; }

        public void open(Player leader) {
            Party party = pm.getParty(leader);
            if (party == null) {
                leader.sendMessage("§cYour party no longer exists.");
                return;
            }
            int partySize = party.getMembers().size();

            SGMenu menu = pl.getGUI().create("§bPick Team Sizes", 1);
            List<Integer> symmetric = new ArrayList<>();
            for (int size = 1; size * 2 <= partySize; size++) symmetric.add(size);

            int idx = 0;
            for (int s : symmetric) {
                ItemStack paper = named(Material.PAPER, "§e" + s + " §7vs §e" + s,
                        List.of("§7Balanced team size preset.", "§fClick to continue."));
                menu.setButton(idx++, new SGButton(paper).withListener(ev -> {
                    PartyPlaySession ses = ACTIVE.computeIfAbsent(leader.getUniqueId(), id -> new PartyPlaySession());
                    ses.mode = PartyPlaySession.Mode.TEAMS;
                    ses.teamOneSize = s;
                    ses.teamTwoSize = s;
                    ses.resetAssignments(party.getMembers());
                    new AssignmentGUI(pl, pm).open(leader);
                }));
            }

            ItemStack sign = named(Material.OAK_SIGN, "§b✎ Custom Sizes",
                    List.of("§7Click then type something like §f2v5", "§7to set custom team sizes."));
            menu.setButton(8, new SGButton(sign).withListener(ev -> {
                leader.closeInventory();
                leader.sendMessage("§bPlease type the sizes in chat, format §e<X>v<Y>§b, example §e2v5§b.");
                CustomSizeListener.WAITING.add(leader.getUniqueId());
            }));

            leader.openInventory(menu.getInventory());
        }
    }

    public static final class CustomSizeListener {
        static final Set<UUID> WAITING = new HashSet<>();

        public static boolean handle(Player p, String msg, Core pl, PartyManager pm) {
            if (!WAITING.remove(p.getUniqueId())) return false;
            msg = msg.toLowerCase().replace(" ", "");
            if (!msg.contains("v")) {
                p.sendMessage("§cInvalid format, example §e2v5§c.");
                return true;
            }
            String[] parts = msg.split("v");
            int a, b;
            try {
                a = Integer.parseInt(parts[0]);
                b = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ex) {
                p.sendMessage("§cNumbers required.");
                return true;
            }

            Party party = pm.getParty(p);
            if (party == null) {
                p.sendMessage("§cYou are no longer in a party.");
                return true;
            }
            if (a + b > party.getMembers().size()) {
                p.sendMessage("§cYour party only has " + party.getMembers().size() + " members.");
                return true;
            }
            if (a < 1 || b < 1) {
                p.sendMessage("§cTeam sizes must both be at least 1.");
                return true;
            }

            PartyPlaySession ses = ACTIVE.computeIfAbsent(p.getUniqueId(), id -> new PartyPlaySession());
            ses.mode = PartyPlaySession.Mode.TEAMS;
            ses.teamOneSize = a;
            ses.teamTwoSize = b;
            ses.resetAssignments(party.getMembers());
            new AssignmentGUI(pl, pm).open(p);
            return true;
        }
    }

    static final class AssignmentGUI {
        private final Core pl;
        private final PartyManager pm;
        AssignmentGUI(Core pl, PartyManager pm) { this.pl = pl; this.pm = pm; }

        void open(Player leader) {
            Party party = pm.getParty(leader);
            PartyPlaySession ses = ACTIVE.get(leader.getUniqueId());
            if (party == null || ses == null) {
                leader.sendMessage("§cYour party play session expired.");
                return;
            }

            SGMenu menu = pl.getGUI().create("§6§lParty Draft Board", 6);

            List<UUID> poolEntries = ses.poolPageEntries();
            for (int i = 0; i < poolEntries.size(); i++) {
                UUID id = poolEntries.get(i);
                int targetTeam = ses.activeTeam;
                menu.setButton(i, new SGButton(head(Bukkit.getOfflinePlayer(id),
                        "§f" + displayName(id),
                        List.of(
                                "§7Bench player",
                                "§7Target team: " + (targetTeam == 1 ? "§dTeam One" : "§bTeam Two"),
                                "§fClick to draft into the target team."
                        ))).withListener(e -> {
                    if (!ses.assignToTeam(id, targetTeam)) {
                        leader.sendMessage("§cThat team is already full.");
                    }
                    open(leader);
                }));
            }

            menu.setButton(7, new SGButton(named(Material.ARROW, "§e← Bench", List.of("§7Previous bench page.")))
                    .withListener(e -> { if (ses.poolPage > 0) ses.poolPage--; open(leader); }));
            menu.setButton(8, new SGButton(named(Material.ARROW, "§eBench →", List.of("§7Next bench page.")))
                    .withListener(e -> {
                        if ((ses.poolPage + 1) * 7 < ses.bench.size()) ses.poolPage++;
                        open(leader);
                    }));

            List<UUID> teamEntries = ses.teamPageEntries();
            int base = 9;
            for (int i = 0; i < teamEntries.size() && i < 27; i++) {
                UUID id = teamEntries.get(i);
                menu.setButton(base + i, new SGButton(head(Bukkit.getOfflinePlayer(id),
                        (ses.viewedTeam == 1 ? "§d" : "§b") + displayName(id),
                        List.of(
                                "§7Displayed team: " + (ses.viewedTeam == 1 ? "§dTeam One" : "§bTeam Two"),
                                "§fLeft click §7→ move to the other team",
                                "§fRight click §7→ send back to bench"
                        ))).withListener(e -> {
                    if (e.getClick().isRightClick()) {
                        ses.returnToBench(id);
                    } else if (!ses.moveBetweenTeams(id)) {
                        leader.sendMessage("§cThe other team is full.");
                    }
                    open(leader);
                }));
            }

            for (int slot = 36; slot <= 44; slot++) {
                menu.setButton(slot, new SGButton(named(Material.GRAY_STAINED_GLASS_PANE, "§8", List.of()))
                        .withListener(e -> e.setCancelled(true)));
            }

            menu.setButton(45, new SGButton(named(Material.COMPARATOR,
                    "§6Target Team: " + (ses.activeTeam == 1 ? "§dTeam One" : "§bTeam Two"),
                    List.of("§7Bench clicks place players into this team.", "§fClick to cycle.")))
                    .withListener(e -> { ses.activeTeam = ses.activeTeam == 1 ? 2 : 1; open(leader); }));

            menu.setButton(46, new SGButton(named(Material.SPYGLASS,
                    "§eViewed Team: " + (ses.viewedTeam == 1 ? "§dTeam One" : "§bTeam Two"),
                    List.of("§7Choose which team is shown in the middle grid.", "§fClick to cycle.")))
                    .withListener(e -> { ses.viewedTeam = ses.viewedTeam == 1 ? 2 : 1; ses.teamPage = 0; open(leader); }));

            menu.setButton(47, new SGButton(named(Material.ARROW, "§e← Team Page", List.of("§7View the previous team page.")))
                    .withListener(e -> { if (ses.teamPage > 0) ses.teamPage--; open(leader); }));

            int viewedSize = ses.getTeamSet(ses.viewedTeam).size();
            menu.setButton(48, new SGButton(named(Material.ARROW, "§eTeam Page →", List.of("§7View the next team page.")))
                    .withListener(e -> { if ((ses.teamPage + 1) * 27 < viewedSize) ses.teamPage++; open(leader); }));

            menu.setButton(49, new SGButton(named(Material.CRAFTER, "§aDivide Evenly", List.of(
                    "§7Fill both teams from the bench.",
                    "§7Uses your shuffle and balance settings."
            ))).withListener(e -> { divideEvenly(party, ses); open(leader); }));

            menu.setButton(50, new SGButton(named(Material.CHEST, "§dFill Team One", List.of("§7Move bench players into Team One until it is full.")))
                    .withListener(e -> { fillTeam(party, ses, 1); open(leader); }));

            menu.setButton(51, new SGButton(named(Material.ENDER_CHEST, "§bFill Team Two", List.of("§7Move bench players into Team Two until it is full.")))
                    .withListener(e -> { fillTeam(party, ses, 2); open(leader); }));

            menu.setButton(52, new SGButton(named(Material.FIRE_CHARGE, "§c§lParty FFA", List.of(
                    "§7Skip teams and start a last-player-standing party fight.",
                    "§fClick to use everyone in one free-for-all."
            ))).withListener(e -> {
                ses.mode = PartyPlaySession.Mode.FFA;
                ses.teamOneMembers.clear();
                ses.teamTwoMembers.clear();
                ses.bench.clear();
                ses.teamOneMembers.addAll(party.getMembers());
                new KitSelectorGUI(pl, pm, true).open(leader);
            }));

            menu.setButton(53, new SGButton(named(Material.LIME_CONCRETE,
                    "§aContinue",
                    List.of(
                            "§7Team One: §f" + ses.teamOneMembers.size() + "§7/§f" + ses.teamOneSize,
                            "§7Team Two: §f" + ses.teamTwoMembers.size() + "§7/§f" + ses.teamTwoSize,
                            "§fClick to continue to kit selection."
                    ))).withListener(e -> {
                if (ses.teamOneMembers.size() != ses.teamOneSize || ses.teamTwoMembers.size() != ses.teamTwoSize) {
                    leader.sendMessage("§cFinish assigning players before continuing.");
                    return;
                }
                ses.mode = PartyPlaySession.Mode.TEAMS;
                new KitSelectorGUI(pl, pm, true).open(leader);
            }));

            leader.openInventory(menu.getInventory());
        }

        private void divideEvenly(Party party, PartyPlaySession ses) {
            List<UUID> ids = new ArrayList<>(ses.bench);
            if (party.isShuffle()) Collections.shuffle(ids);

            if (party.isAutoBalance()) {
                while (!ids.isEmpty() && (ses.hasRoom(1) || ses.hasRoom(2))) {
                    if (ses.hasRoom(1) && (ses.teamOneMembers.size() <= ses.teamTwoMembers.size() || !ses.hasRoom(2))) {
                        ses.assignToTeam(ids.remove(0), 1);
                    } else if (ses.hasRoom(2)) {
                        ses.assignToTeam(ids.remove(0), 2);
                    } else break;
                }
            } else {
                fillTeam(party, ses, 1);
                fillTeam(party, ses, 2);
            }
        }

        private void fillTeam(Party party, PartyPlaySession ses, int team) {
            List<UUID> ids = new ArrayList<>(ses.bench);
            if (party.isShuffle()) Collections.shuffle(ids);
            for (UUID id : ids) {
                if (!ses.hasRoom(team)) break;
                ses.assignToTeam(id, team);
            }
        }
    }

    static final class KitSelectorGUI {
        private final Core pl;
        private final PartyManager pm;
        private final boolean firstTeam;
        KitSelectorGUI(Core pl, PartyManager pm, boolean firstTeam) {
            this.pl = pl; this.pm = pm; this.firstTeam = firstTeam;
        }

        void open(Player leader) { open(leader, 0); }

        private void open(Player leader, int page) {
            final int rows = 6;
            final int pageSize = rows * 9;
            int maxSlot = DuelManager.stor.kits.stream().mapToInt(Kit::getSlot).max().orElse(0);
            int maxPage = maxSlot / pageSize;
            PartyPlaySession ses = ACTIVE.get(leader.getUniqueId());

            String title = ses != null && ses.mode == PartyPlaySession.Mode.FFA
                    ? "§cParty FFA Kit §7(" + (page + 1) + "/" + (maxPage + 1) + ")"
                    : ((firstTeam ? "§dTeam One Kit" : "§bTeam Two Kit") + " §7(" + (page + 1) + "/" + (maxPage + 1) + ")");
            SGMenu menu = pl.getGUI().create(title, rows);

            for (Kit k : DuelManager.stor.kits) {
                int global = k.getSlot();
                if (global / pageSize != page) continue;
                int local = global % pageSize;
                ItemStack icon = k.getDisplayItem().convertToItem();
                menu.setButton(local, new SGButton(icon).withListener(ev -> {
                    saveChoice(leader, k);
                    nextScreen(leader);
                }));
            }

            if (page > 0) menu.setButton(44, new SGButton(named(Material.ARROW, "§e← Previous Page", List.of("§7View previous kit page.")))
                    .withListener(e -> open(leader, page - 1)));
            if (page < maxPage) menu.setButton(45, new SGButton(named(Material.ARROW, "§eNext Page →", List.of("§7View next kit page.")))
                    .withListener(e -> open(leader, page + 1)));

            menu.setButton(49, new SGButton(named(Material.ARROW, "§6← Back", List.of("§7Return to the draft board.")))
                    .withListener(e -> {
                        PartyPlaySession s = ACTIVE.get(leader.getUniqueId());
                        if (s != null && s.mode == PartyPlaySession.Mode.FFA) new SizeSelectorGUI(pl, pm).open(leader);
                        else new AssignmentGUI(pl, pm).open(leader);
                    }));

            addCustomButtons(menu, leader);
            leader.openInventory(menu.getInventory());
        }

        private void addCustomButtons(SGMenu menu, Player leader) {
            menu.setButton(52, new SGButton(named(Material.BLAZE_POWDER, "§eSelected Custom Kit", List.of(
                    "§7Use your currently selected custom kit",
                    "§7for this slot."
            ))).withListener(e -> new CustomKitListGUI(pl, pm, firstTeam).open((Player) e.getWhoClicked())));

            menu.setButton(53, new SGButton(named(Material.BLAZE_POWDER, "§6Selected Per Custom Kit", List.of(
                    "§7Every player uses their own selected",
                    "§7custom kit slot."
            ))).withListener(e -> {
                PartyPlaySession ses = ACTIVE.get(leader.getUniqueId());
                if (ses == null) return;
                Kit pk = new Kit();
                pk.setPk(true);
                pk.setName("Per Custom");
                pk.setDisplayItem(new SerializableItem(new ItemStack(Material.BLAZE_POWDER)));
                saveChoice(leader, pk);
                if (ses.mode == PartyPlaySession.Mode.TEAMS && firstTeam) ses.kitTwo = pk;
                nextScreen(leader);
            }));
        }

        private void saveChoice(Player leader, Kit chosen) {
            PartyPlaySession ses = ACTIVE.get(leader.getUniqueId());
            if (ses == null) return;
            if (ses.mode == PartyPlaySession.Mode.FFA) {
                ses.kitOne = chosen;
                return;
            }
            if (firstTeam) ses.kitOne = chosen;
            else ses.kitTwo = chosen;
        }

        private void nextScreen(Player leader) {
            PartyPlaySession ses = ACTIVE.get(leader.getUniqueId());
            if (ses == null) return;
            if (ses.mode == PartyPlaySession.Mode.FFA) {
                new ArenaSelectorGUI(pl, pm).open(leader);
                return;
            }
            if (firstTeam) {
                if (ses.kitOne != null && ses.kitOne.isPk()) new ArenaSelectorGUI(pl, pm).open(leader);
                else new KitSelectorGUI(pl, pm, false).open(leader);
            } else {
                new ArenaSelectorGUI(pl, pm).open(leader);
            }
        }
    }

    static final class CustomKitListGUI {
        private final Core pl;
        private final PartyManager pm;
        private final boolean firstTeam;

        CustomKitListGUI(Core pl, PartyManager pm, boolean firstTeam) {
            this.pl = pl; this.pm = pm; this.firstTeam = firstTeam;
        }

        void open(Player leader) {
            SGMenu menu = pl.getGUI().create("§bSelect Your Kit", 6);
            CustomKit[] kits = pl.getExtendedPlayer(leader).getMainPlayer().getKits();
            for (int idx = 1; idx < kits.length; idx++) {
                CustomKit ck = kits[idx];
                if (ck == null) continue;
                final int kitIndex = idx;
                ItemStack paper = named(Material.PAPER, "§aKit §e#" + idx, List.of("§7Use this custom kit for the match."));
                menu.addButton(new SGButton(paper).withListener(ev -> {
                    Kit legacy = ck.toKit();
                    legacy.setName("Custom #" + kitIndex);
                    legacy.setDisplayItem(new SerializableItem(paper));
                    PartyPlaySession ses = ACTIVE.get(leader.getUniqueId());
                    if (ses == null) return;
                    if (ses.mode == PartyPlaySession.Mode.FFA) {
                        ses.kitOne = legacy;
                        new ArenaSelectorGUI(pl, pm).open(leader);
                    } else {
                        if (firstTeam) ses.kitOne = legacy;
                        else ses.kitTwo = legacy;
                        if (firstTeam) new KitSelectorGUI(pl, pm, false).open(leader);
                        else new ArenaSelectorGUI(pl, pm).open(leader);
                    }
                }));
            }
            leader.openInventory(menu.getInventory());
        }
    }

    static final class RoundSelectorGUI {
        private final Core pl; private final PartyManager pm;
        RoundSelectorGUI(Core pl, PartyManager pm) { this.pl = pl; this.pm = pm; }

        void open(Player leader) {
            List<Integer> opts = pl.getConfig().getIntegerList("duels.rounds");
            if (opts.isEmpty()) opts = List.of(1, 3, 5);
            SGMenu menu = pl.getGUI().create("§bPick Rounds", 1);
            int i = 0;
            for (int r : opts) {
                menu.setButton(i++, new SGButton(named(Material.PAPER, "§eFirst to " + r,
                        List.of("§7Set the duel round target.")))
                        .withListener(ev -> {
                            PartyPlaySession ses = ACTIVE.get(leader.getUniqueId());
                            if (ses != null) ses.rounds = r;
                            new ConfirmGUI(pl, pm).open(leader);
                        }));
            }
            leader.openInventory(menu.getInventory());
        }
    }

    static final class ArenaSelectorGUI {
        private final Core pl; private final PartyManager pm;
        ArenaSelectorGUI(Core pl, PartyManager pm) { this.pl = pl; this.pm = pm; }

        void open(Player leader) {
            SGMenu menu = pl.getGUI().create("§bPick Arena", 5);
            int off = 0;
            for (Arena a : DuelManager.stor.arenas) {
                if (a.deactivated) continue;
                menu.setButton(off++, new SGButton(a.getDisplayItem().convertToItem()).withListener(e -> {
                    PartyPlaySession ses = ACTIVE.get(leader.getUniqueId());
                    if (ses != null) ses.arena = a;
                    if (ses != null && ses.mode == PartyPlaySession.Mode.FFA) new ConfirmGUI(pl, pm).open(leader);
                    else new RoundSelectorGUI(pl, pm).open(leader);
                }));
            }
            menu.setButton(44, new SGButton(named(Material.ARROW, "§6← Back", List.of("§7Return to the previous menu.")))
                    .withListener(e -> {
                        PartyPlaySession ses = ACTIVE.get(leader.getUniqueId());
                        if (ses != null && ses.mode == PartyPlaySession.Mode.FFA) new KitSelectorGUI(pl, pm, true).open(leader);
                        else new KitSelectorGUI(pl, pm, false).open(leader);
                    }));
            leader.openInventory(menu.getInventory());
        }
    }

    static final class ConfirmGUI {
        private final Core pl; private final PartyManager pm;
        ConfirmGUI(Core pl, PartyManager pm) { this.pl = pl; this.pm = pm; }

        void open(Player leader) {
            PartyPlaySession ses = ACTIVE.get(leader.getUniqueId());
            if (ses == null) {
                leader.sendMessage("§cSession expired.");
                return;
            }

            SGMenu menu = pl.getGUI().create(ses.mode == PartyPlaySession.Mode.FFA ? "§cStart Party FFA" : "§aStart Party Duel", 1);
            List<String> lore = new ArrayList<>();
            lore.add("§7Arena: §f" + (ses.arena == null ? "None" : ses.arena.getName()));
            if (ses.mode == PartyPlaySession.Mode.FFA) {
                lore.add("§7Players: §f" + ses.teamOneMembers.size());
                lore.add("§7Kit: §f" + (ses.kitOne == null ? "None" : ses.kitOne.getName()));
                lore.add("§fClick to launch the Party FFA.");
            } else {
                lore.add("§7Team One: §f" + ses.teamOneMembers.size() + "§7/§f" + ses.teamOneSize);
                lore.add("§7Team Two: §f" + ses.teamTwoMembers.size() + "§7/§f" + ses.teamTwoSize);
                lore.add("§7Rounds: §f" + ses.rounds);
                lore.add("§fClick to launch the duel.");
            }
            menu.setButton(4, new SGButton(named(Material.LIME_CONCRETE, "§a§lSTART", lore)).withListener(e -> start(leader)));
            menu.setButton(0, new SGButton(named(Material.ARROW, "§6← Back", List.of("§7Return to the previous menu.")))
                    .withListener(e -> {
                        if (ses.mode == PartyPlaySession.Mode.FFA) new ArenaSelectorGUI(pl, pm).open(leader);
                        else new RoundSelectorGUI(pl, pm).open(leader);
                    }));
            leader.openInventory(menu.getInventory());
        }

        private void start(Player leader) {
            Party party = pm.getParty(leader);
            if (party == null) { leader.sendMessage("§cParty vanished."); return; }

            PartyPlaySession ses = ACTIVE.remove(leader.getUniqueId());
            if (ses == null) { leader.sendMessage("§cSession expired."); return; }
            if (ses.arena == null || ses.kitOne == null) {
                leader.sendMessage("§cYour party play setup is incomplete.");
                return;
            }

            if (ses.mode == PartyPlaySession.Mode.FFA) {
                List<Player> fighters = onlinePlayers(ses.teamOneMembers);
                if (fighters.size() < 2) {
                    leader.sendMessage("§cYou need at least 2 online players for Party FFA.");
                    return;
                }
                new PartyFFAInstance(pl).prepare(fighters, ses.arena, ses.kitOne).start();
                return;
            }

            if (ses.kitTwo == null) {
                leader.sendMessage("§cYour party play setup is incomplete.");
                return;
            }

            List<Player> teamOne = onlinePlayers(ses.teamOneMembers);
            List<Player> teamTwo = onlinePlayers(ses.teamTwoMembers);
            if (teamOne.size() != ses.teamOneSize || teamTwo.size() != ses.teamTwoSize) {
                leader.sendMessage("§cOne or more drafted players are no longer online.");
                return;
            }

            DuelInstance duel = new DuelInstance(pl);
            duel.prepare(teamOne.toArray(new Player[0]), teamTwo.toArray(new Player[0]), ses.arena, ses.kitOne, ses.kitTwo, ses.rounds);
            duel.initialize();
        }

        private List<Player> onlinePlayers(Collection<UUID> ids) {
            List<Player> out = new ArrayList<>();
            for (UUID id : ids) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) out.add(p);
            }
            return out;
        }
    }
}
