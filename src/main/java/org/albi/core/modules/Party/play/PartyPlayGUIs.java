package org.albi.core.modules.Party.play;

import org.albi.core.gui.Messages;

import org.albi.core.gui.GuiService;

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
                Messages.send(leader, "messages.party-play-guis.your-party-no-longer-exists");
                return;
            }
            int partySize = party.getMembers().size();

            SGMenu menu = pl.getMenus().create("party.team-sizes", 1);
            List<Integer> symmetric = new ArrayList<>();
            for (int size = 1; size * 2 <= partySize; size++) symmetric.add(size);

            int idx = 0;
            for (int s : symmetric) {
                ItemStack paper = named(Material.PAPER, GuiService.text("party-play-guis.vs", s, s),
                        List.of(GuiService.text("party-play-guis.balanced-team-size-preset"), GuiService.text("party-play-guis.click-to-continue")));
                menu.setButton(idx++, new SGButton(paper).withListener(ev -> {
                    PartyPlaySession ses = ACTIVE.computeIfAbsent(leader.getUniqueId(), id -> new PartyPlaySession());
                    ses.mode = PartyPlaySession.Mode.TEAMS;
                    ses.teamOneSize = s;
                    ses.teamTwoSize = s;
                    ses.resetAssignments(party.getMembers());
                    new AssignmentGUI(pl, pm).open(leader);
                }));
            }

            ItemStack sign = named(Material.OAK_SIGN, GuiService.text("party-play-guis.custom-sizes"),
                    List.of(GuiService.text("party-play-guis.click-then-type-something-like-2v5"), GuiService.text("party-play-guis.to-set-custom-team-sizes")));
            GuiService.control(menu, "sign", 8, new SGButton(sign).withListener(ev -> {
                leader.closeInventory();
                Messages.send(leader, "messages.party-play-guis.please-type-the-sizes-in-chat-format-x");
                CustomSizeListener.WAITING.add(leader.getUniqueId());
            }));

            GuiService.open(leader, menu);
        }
    }

    public static final class CustomSizeListener {
        static final Set<UUID> WAITING = new HashSet<>();

        public static boolean handle(Player p, String msg, Core pl, PartyManager pm) {
            if (!WAITING.remove(p.getUniqueId())) return false;
            msg = msg.toLowerCase().replace(" ", "");
            if (!msg.contains("v")) {
                Messages.send(p, "messages.party-play-guis.invalid-format-example-2v5");
                return true;
            }
            String[] parts = msg.split("v");
            int a, b;
            try {
                a = Integer.parseInt(parts[0]);
                b = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ex) {
                Messages.send(p, "messages.party-play-guis.numbers-required");
                return true;
            }

            Party party = pm.getParty(p);
            if (party == null) {
                Messages.send(p, "messages.party-play-guis.you-are-no-longer-in-a-party");
                return true;
            }
            if (a + b > party.getMembers().size()) {
                Messages.send(p, "messages.party-play-guis.your-party-only-has-members", party.getMembers().size());
                return true;
            }
            if (a < 1 || b < 1) {
                Messages.send(p, "messages.party-play-guis.team-sizes-must-both-be-at-least-1");
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
                Messages.send(leader, "messages.party-play-guis.your-party-play-session-expired");
                return;
            }

            SGMenu menu = pl.getMenus().create("party.draft", 6);

            List<UUID> poolEntries = ses.poolPageEntries();
            for (int i = 0; i < poolEntries.size(); i++) {
                UUID id = poolEntries.get(i);
                int targetTeam = ses.activeTeam;
                menu.setButton(i, new SGButton(head(Bukkit.getOfflinePlayer(id),
                        "§f" + displayName(id),
                        List.of(
                                GuiService.text("party-play-guis.bench-player"),
                                GuiService.text("party-play-guis.target-team", (targetTeam == 1 ? "§dTeam One" : "§bTeam Two")),
                                GuiService.text("party-play-guis.click-to-draft-into-the-target-team")
                        ))).withListener(e -> {
                    if (!ses.assignToTeam(id, targetTeam)) {
                        Messages.send(leader, "messages.party-play-guis.that-team-is-already-full");
                    }
                    open(leader);
                }));
            }

            GuiService.control(menu, "bench", 7, new SGButton(named(Material.ARROW, GuiService.text("party-play-guis.bench"), List.of(GuiService.text("party-play-guis.previous-bench-page"))))
                    .withListener(e -> { if (ses.poolPage > 0) ses.poolPage--; open(leader); }));
            GuiService.control(menu, "bench-2", 8, new SGButton(named(Material.ARROW, GuiService.text("party-play-guis.bench-2"), List.of(GuiService.text("party-play-guis.next-bench-page"))))
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
                                GuiService.text("party-play-guis.displayed-team", (ses.viewedTeam == 1 ? "§dTeam One" : "§bTeam Two")),
                                GuiService.text("party-play-guis.left-click-move-to-the-other-team"),
                                GuiService.text("party-play-guis.right-click-send-back-to-bench")
                        ))).withListener(e -> {
                    if (e.getClick().isRightClick()) {
                        ses.returnToBench(id);
                    } else if (!ses.moveBetweenTeams(id)) {
                        Messages.send(leader, "messages.party-play-guis.the-other-team-is-full");
                    }
                    open(leader);
                }));
            }

            for (int slot = 36; slot <= 44; slot++) {
                menu.setButton(slot, new SGButton(named(Material.GRAY_STAINED_GLASS_PANE, "§8", List.of()))
                        .withListener(e -> e.setCancelled(true)));
            }

            GuiService.control(menu, "target-team", 45, new SGButton(named(Material.COMPARATOR,
                    GuiService.text("party-play-guis.target-team-button", (ses.activeTeam == 1 ? "§dTeam One" : "§bTeam Two")),
                    List.of(GuiService.text("party-play-guis.bench-clicks-place-players-into-this-team"), GuiService.text("party-play-guis.click-to-cycle"))))
                    .withListener(e -> { ses.activeTeam = ses.activeTeam == 1 ? 2 : 1; open(leader); }));

            GuiService.control(menu, "viewed-team", 46, new SGButton(named(Material.SPYGLASS,
                    GuiService.text("party-play-guis.viewed-team", (ses.viewedTeam == 1 ? "§dTeam One" : "§bTeam Two")),
                    List.of(GuiService.text("party-play-guis.choose-which-team-is-shown-in-the-middle"), GuiService.text("party-play-guis.click-to-cycle"))))
                    .withListener(e -> { ses.viewedTeam = ses.viewedTeam == 1 ? 2 : 1; ses.teamPage = 0; open(leader); }));

            GuiService.control(menu, "team-page", 47, new SGButton(named(Material.ARROW, GuiService.text("party-play-guis.team-page"), List.of(GuiService.text("party-play-guis.view-the-previous-team-page"))))
                    .withListener(e -> { if (ses.teamPage > 0) ses.teamPage--; open(leader); }));

            int viewedSize = ses.getTeamSet(ses.viewedTeam).size();
            GuiService.control(menu, "team-page-2", 48, new SGButton(named(Material.ARROW, GuiService.text("party-play-guis.team-page-2"), List.of(GuiService.text("party-play-guis.view-the-next-team-page"))))
                    .withListener(e -> { if ((ses.teamPage + 1) * 27 < viewedSize) ses.teamPage++; open(leader); }));

            GuiService.control(menu, "divide-evenly", 49, new SGButton(named(Material.CRAFTER, GuiService.text("party-play-guis.divide-evenly"), List.of(
                    GuiService.text("party-play-guis.fill-both-teams-from-the-bench"),
                    GuiService.text("party-play-guis.uses-your-shuffle-and-balance-settings")
            ))).withListener(e -> { divideEvenly(party, ses); open(leader); }));

            GuiService.control(menu, "fill-team-one", 50, new SGButton(named(Material.CHEST, GuiService.text("party-play-guis.fill-team-one"), List.of(GuiService.text("party-play-guis.move-bench-players-into-team-one-until-it"))))
                    .withListener(e -> { fillTeam(party, ses, 1); open(leader); }));

            GuiService.control(menu, "fill-team-two", 51, new SGButton(named(Material.ENDER_CHEST, GuiService.text("party-play-guis.fill-team-two"), List.of(GuiService.text("party-play-guis.move-bench-players-into-team-two-until-it"))))
                    .withListener(e -> { fillTeam(party, ses, 2); open(leader); }));

            GuiService.control(menu, "party-ffa", 52, new SGButton(named(Material.FIRE_CHARGE, GuiService.text("party-play-guis.party-ffa"), List.of(
                    GuiService.text("party-play-guis.skip-teams-and-start-a-last-player-standing"),
                    GuiService.text("party-play-guis.click-to-use-everyone-in-one-free-for")
            ))).withListener(e -> {
                ses.mode = PartyPlaySession.Mode.FFA;
                ses.teamOneMembers.clear();
                ses.teamTwoMembers.clear();
                ses.bench.clear();
                ses.teamOneMembers.addAll(party.getMembers());
                new KitSelectorGUI(pl, pm, true).open(leader);
            }));

            GuiService.control(menu, "continue", 53, new SGButton(named(Material.LIME_CONCRETE,
                    GuiService.text("party-play-guis.continue"),
                    List.of(
                            GuiService.text("party-play-guis.team-one", ses.teamOneMembers.size(), ses.teamOneSize),
                            GuiService.text("party-play-guis.team-two", ses.teamTwoMembers.size(), ses.teamTwoSize),
                            GuiService.text("party-play-guis.click-to-continue-to-kit-selection")
                    ))).withListener(e -> {
                if (ses.teamOneMembers.size() != ses.teamOneSize || ses.teamTwoMembers.size() != ses.teamTwoSize) {
                    Messages.send(leader, "messages.party-play-guis.finish-assigning-players-before-continuing");
                    return;
                }
                ses.mode = PartyPlaySession.Mode.TEAMS;
                new KitSelectorGUI(pl, pm, true).open(leader);
            }));

            GuiService.open(leader, menu);
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

            String mode = ses != null && ses.mode == PartyPlaySession.Mode.FFA
                    ? GuiService.text("party-play-guis.ffa-kit")
                    : GuiService.text(firstTeam ? "party-play-guis.team-one-kit" : "party-play-guis.team-two-kit");
            SGMenu menu = pl.getMenus().create("party.kits", rows, mode, page + 1, maxPage + 1);

            for (Kit k : DuelManager.stor.kits) {
                int global = k.getSlot();
                if (global / pageSize != page) continue;
                int local = global % pageSize;
                ItemStack icon = k.getDisplayItem().convertToItem();
                GuiService.entry(menu, k.getName(), local, new SGButton(icon).withListener(ev -> {
                    saveChoice(leader, k);
                    nextScreen(leader);
                }));
            }

            if (page > 0) GuiService.control(menu, "previous", 44, new SGButton(named(Material.ARROW, GuiService.text("party-play-guis.previous-page"), List.of(GuiService.text("party-play-guis.view-previous-kit-page"))))
                    .withListener(e -> open(leader, page - 1)));
            if (page < maxPage) GuiService.control(menu, "next", 45, new SGButton(named(Material.ARROW, GuiService.text("party-play-guis.next-page"), List.of(GuiService.text("party-play-guis.view-next-kit-page"))))
                    .withListener(e -> open(leader, page + 1)));

            GuiService.control(menu, "back", 49, new SGButton(named(Material.ARROW, GuiService.text("party-play-guis.back"), List.of(GuiService.text("party-play-guis.return-to-the-draft-board"))))
                    .withListener(e -> {
                        PartyPlaySession s = ACTIVE.get(leader.getUniqueId());
                        if (s != null && s.mode == PartyPlaySession.Mode.FFA) new SizeSelectorGUI(pl, pm).open(leader);
                        else new AssignmentGUI(pl, pm).open(leader);
                    }));

            addCustomButtons(menu, leader);
            GuiService.open(leader, menu);
        }

        private void addCustomButtons(SGMenu menu, Player leader) {
            menu.setButton(52, new SGButton(named(Material.BLAZE_POWDER, GuiService.text("party-play-guis.selected-custom-kit"), List.of(
                    GuiService.text("party-play-guis.use-your-currently-selected-custom-kit"),
                    GuiService.text("party-play-guis.for-this-slot")
            ))).withListener(e -> new CustomKitListGUI(pl, pm, firstTeam).open((Player) e.getWhoClicked())));

            menu.setButton(53, new SGButton(named(Material.BLAZE_POWDER, GuiService.text("party-play-guis.selected-per-custom-kit"), List.of(
                    GuiService.text("party-play-guis.every-player-uses-their-own-selected"),
                    GuiService.text("party-play-guis.custom-kit-slot")
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
            SGMenu menu = pl.getMenus().create("party.personal-kits", 6);
            CustomKit[] kits = pl.getExtendedPlayer(leader).getMainPlayer().getKits();
            for (int idx = 1; idx < kits.length; idx++) {
                CustomKit ck = kits[idx];
                if (ck == null) continue;
                final int kitIndex = idx;
                ItemStack paper = named(Material.PAPER, GuiService.text("party-play-guis.kit-number", idx), List.of(GuiService.text("party-play-guis.use-this-custom-kit-for-the-match")));
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
            GuiService.open(leader, menu);
        }
    }

    static final class RoundSelectorGUI {
        private final Core pl; private final PartyManager pm;
        RoundSelectorGUI(Core pl, PartyManager pm) { this.pl = pl; this.pm = pm; }

        void open(Player leader) {
            List<Integer> opts = pl.getConfig().getIntegerList("duels.rounds");
            if (opts.isEmpty()) opts = List.of(1, 3, 5);
            SGMenu menu = pl.getMenus().create("party.rounds", 1);
            int i = 0;
            for (int r : opts) {
                menu.setButton(i++, new SGButton(named(Material.PAPER, GuiService.text("party-play-guis.first-to", r),
                        List.of(GuiService.text("party-play-guis.set-the-duel-round-target"))))
                        .withListener(ev -> {
                            PartyPlaySession ses = ACTIVE.get(leader.getUniqueId());
                            if (ses != null) ses.rounds = r;
                            new ConfirmGUI(pl, pm).open(leader);
                        }));
            }
            GuiService.open(leader, menu);
        }
    }

    static final class ArenaSelectorGUI {
        private final Core pl; private final PartyManager pm;
        ArenaSelectorGUI(Core pl, PartyManager pm) { this.pl = pl; this.pm = pm; }

        void open(Player leader) {
            SGMenu menu = pl.getMenus().create("party.arenas", 5);
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
            GuiService.control(menu, "back", 44, new SGButton(named(Material.ARROW, GuiService.text("party-play-guis.back"), List.of(GuiService.text("party-play-guis.return-to-the-previous-menu"))))
                    .withListener(e -> {
                        PartyPlaySession ses = ACTIVE.get(leader.getUniqueId());
                        if (ses != null && ses.mode == PartyPlaySession.Mode.FFA) new KitSelectorGUI(pl, pm, true).open(leader);
                        else new KitSelectorGUI(pl, pm, false).open(leader);
                    }));
            GuiService.open(leader, menu);
        }
    }

    static final class ConfirmGUI {
        private final Core pl; private final PartyManager pm;
        ConfirmGUI(Core pl, PartyManager pm) { this.pl = pl; this.pm = pm; }

        void open(Player leader) {
            PartyPlaySession ses = ACTIVE.get(leader.getUniqueId());
            if (ses == null) {
                Messages.send(leader, "messages.party-play-guis.session-expired");
                return;
            }

            SGMenu menu = pl.getMenus().create("party.confirm", 1, GuiService.text(ses.mode == PartyPlaySession.Mode.FFA ? "party-play-guis.confirm-ffa" : "party-play-guis.confirm-duel"));
            List<String> lore = new ArrayList<>();
            lore.add(GuiService.text("party-play-guis.arena", (ses.arena == null ? "None" : ses.arena.getName())));
            if (ses.mode == PartyPlaySession.Mode.FFA) {
                lore.add(GuiService.text("party-play-guis.players", ses.teamOneMembers.size()));
                lore.add(GuiService.text("party-play-guis.kit", (ses.kitOne == null ? "None" : ses.kitOne.getName())));
                lore.add(GuiService.text("party-play-guis.click-to-launch-the-party-ffa"));
            } else {
                lore.add(GuiService.text("party-play-guis.team-one", ses.teamOneMembers.size(), ses.teamOneSize));
                lore.add(GuiService.text("party-play-guis.team-two", ses.teamTwoMembers.size(), ses.teamTwoSize));
                lore.add(GuiService.text("party-play-guis.rounds", ses.rounds));
                lore.add(GuiService.text("party-play-guis.click-to-launch-the-duel"));
            }
            GuiService.control(menu, "start", 4, new SGButton(named(Material.LIME_CONCRETE, GuiService.text("party-play-guis.start"), lore)).withListener(e -> start(leader)));
            GuiService.control(menu, "back", 0, new SGButton(named(Material.ARROW, GuiService.text("party-play-guis.back"), List.of(GuiService.text("party-play-guis.return-to-the-previous-menu"))))
                    .withListener(e -> {
                        if (ses.mode == PartyPlaySession.Mode.FFA) new ArenaSelectorGUI(pl, pm).open(leader);
                        else new RoundSelectorGUI(pl, pm).open(leader);
                    }));
            GuiService.open(leader, menu);
        }

        private void start(Player leader) {
            Party party = pm.getParty(leader);
            if (party == null) { Messages.send(leader, "messages.party-play-guis.party-vanished"); return; }

            PartyPlaySession ses = ACTIVE.remove(leader.getUniqueId());
            if (ses == null) { Messages.send(leader, "messages.party-play-guis.session-expired"); return; }
            if (ses.arena == null || ses.kitOne == null) {
                Messages.send(leader, "messages.party-play-guis.your-party-play-setup-is-incomplete");
                return;
            }

            if (ses.mode == PartyPlaySession.Mode.FFA) {
                List<Player> fighters = onlinePlayers(ses.teamOneMembers);
                if (fighters.size() < 2) {
                    Messages.send(leader, "messages.party-play-guis.you-need-at-least-2-online-players-for");
                    return;
                }
                new PartyFFAInstance(pl).prepare(fighters, ses.arena, ses.kitOne).start();
                return;
            }

            if (ses.kitTwo == null) {
                Messages.send(leader, "messages.party-play-guis.your-party-play-setup-is-incomplete");
                return;
            }

            List<Player> teamOne = onlinePlayers(ses.teamOneMembers);
            List<Player> teamTwo = onlinePlayers(ses.teamTwoMembers);
            if (teamOne.size() != ses.teamOneSize || teamTwo.size() != ses.teamTwoSize) {
                Messages.send(leader, "messages.party-play-guis.one-or-more-drafted-players-are-no-longer");
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
