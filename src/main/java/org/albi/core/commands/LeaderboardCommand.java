package org.albi.core.commands;

import org.albi.core.gui.Messages;

import org.albi.core.gui.GuiService;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.data.StatManagement;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Kit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * /leaderboard – GUI + CLI
 */
public class LeaderboardCommand extends BaseCommand {

    private final Core plugin;
    private final Map<UUID, StatManagement.Field> fieldMem = new HashMap<>();
    private final Map<UUID, String>               kitMem   = new HashMap<>();

    public LeaderboardCommand(Core plugin){ this.plugin = plugin; }

    /*──────────── entry ────────────*/
    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return false;

        /* text usage */
        if (a.length >= 2) {
            try {
                StatManagement.Field f = StatManagement.Field.valueOf(a[0].toUpperCase(Locale.ROOT));
                show(p, f, String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
            } catch (IllegalArgumentException ex) {
                plugin.getMessageAPI().sendMessage(p, Messages.text(p, "messages.leaderboard-command.invalid-field"));
            }
            return true;
        }
        if (DuelManager.stor == null) {
            plugin.getMessageAPI().sendMessage(p, Messages.text(p, "messages.leaderboard-command.the-duel-leaderboard-menu-is-unavailable-on-this"));
            return true;
        }
        new FieldGUI(p).open();
        return true;
    }

    /*──────────── 1 ▸ field ────────────*/
    private class FieldGUI {
        private final Player v; FieldGUI(Player v){this.v=v;}
        void open(){
            SGMenu m = plugin.getMenus().create("leaderboards.type", 1);
            add(m,1,Material.DIAMOND_SWORD,"§aWins",   StatManagement.Field.WINS);
            add(m,3,Material.BOW,"§aKills",            StatManagement.Field.KILLS);
            add(m,5,Material.SKELETON_SKULL,"§aDeaths",StatManagement.Field.DEATHS);
            add(m,7,Material.CLOCK,"§aTime",           StatManagement.Field.TIME);
            GuiService.open(v, m);
        }
        void add(SGMenu m,int slot,Material mat,String name,StatManagement.Field f){
            ItemStack it=new ItemStack(mat); ItemMeta im=it.getItemMeta();
            im.setDisplayName(name); it.setItemMeta(im);
            m.setButton(slot,new SGButton(it).withListener(e->{e.setCancelled(true);
                fieldMem.put(v.getUniqueId(),f);
                new KitGUI(v,0).open();}));
        }
    }

    /*──────────── 2 ▸ kit (paged) ────────────*/
    private class KitGUI {
        private static final int PAGE_SZ = 54;
        private final Player v; private final int page,max;
        KitGUI(Player v,int page){this.v=v; this.page=page;
            int top=DuelManager.stor.kits.stream().mapToInt(Kit::getSlot).max().orElse(0);
            max=top/PAGE_SZ;}
        void open(){
            SGMenu m=plugin.getMenus().create("leaderboards.kits", 6, (page+1), (max+1));
            DuelManager.stor.kits.forEach(k->{
                int g=k.getSlot(),pg=g/PAGE_SZ; if(pg!=page) return;
                int loc=g%PAGE_SZ; if(loc==44||loc==45) loc=findFree(m);
                m.setButton(loc,new SGButton(k.getDisplayItem().convertToItem()).withListener(e->{
                    e.setCancelled(true);
                    kitMem.put(v.getUniqueId(),k.getName());
                    new TeamGUI(v).open();
                }));
            });
            if(page>0)  GuiService.control(m, "prev", 44,new SGButton(arrow("§e← Prev")).withListener(e->{e.setCancelled(true);new KitGUI(v,page-1).open();}));
            if(page<max)GuiService.control(m, "next", 45,new SGButton(arrow("§eNext →")).withListener(e->{e.setCancelled(true);new KitGUI(v,page+1).open();}));
            GuiService.open(v, m);
        }
        private int findFree(SGMenu m){for(int i=0;i<PAGE_SZ;i++) if(m.getButton(i)==null) return i; return 0;}
        private ItemStack arrow(String n){ItemStack it=new ItemStack(Material.ARROW);ItemMeta im=it.getItemMeta();im.setDisplayName(n);it.setItemMeta(im);return it;}
    }

    /*──────────── 3 ▸ team size ────────────*/
    private class TeamGUI{
        private final Player v; private final String[] lbl={"1v1","2v2","3v3","4v4","5v5","Mismatch"};
        TeamGUI(Player v){this.v=v;}
        void open(){
            SGMenu m=plugin.getMenus().create("leaderboards.team-size", 1);
            for(int i=0;i<lbl.length;i++){
                ItemStack it=new ItemStack(Material.PAPER); it.setAmount(i<5?i+1:1);
                ItemMeta im=it.getItemMeta(); im.setDisplayName("§a"+lbl[i]); it.setItemMeta(im);
                int idx=i;
                m.setButton(i,new SGButton(it).withListener(e->{e.setCancelled(true);finish(lbl[idx]);}));
            }
            GuiService.open(v, m);
        }
        private void finish(String size){
            StatManagement.Field f=fieldMem.get(v.getUniqueId());
            String raw=kitMem.get(v.getUniqueId());
            if(f==null||raw==null){plugin.getMessageAPI().sendMessage(v,Messages.text(v, "messages.leaderboard-command.error"));return;}

            /* clean kit name: strip colour & trim */
            String kitName = DuelManager.stor.kits.stream()
                    .filter(k->k.getName().equalsIgnoreCase(raw))
                    .map(Kit::getName).findFirst().orElse(raw);
            kitName = ChatColor.stripColor(kitName).trim();

            show(v,f,kitName+" "+size);

            fieldMem.remove(v.getUniqueId());
            kitMem.remove(v.getUniqueId());
        }
    }

    private void show(Player viewer, StatManagement.Field field, String game) {
        String key = game.strip().toLowerCase(Locale.ROOT);
        if (!key.isEmpty()) key = Character.toUpperCase(key.charAt(0)) + key.substring(1);
        String gameKey = key;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<StatManagement.LBRow> rows = StatManagement.leaderboard(gameKey, field, 10);
            Bukkit.getScheduler().runTask(plugin, () -> printLeaderboard(viewer, field, game, rows));
        });
    }

    private void printLeaderboard(Player viewer, StatManagement.Field field, String game,
                                  List<StatManagement.LBRow> rows) {
        if (!viewer.isOnline()) return;
        if (rows.isEmpty()) {
            Messages.send(viewer, "messages.leaderboard-command.no-data");
            return;
        }

        Messages.send(viewer, "messages.leaderboard-command.heading", field.name().toLowerCase(Locale.ROOT), game);
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            String name = row.name() == null ? Bukkit.getOfflinePlayer(row.uuid()).getName() : row.name();
            String score = field == StatManagement.Field.TIME ? fmt((int) row.score()) : Long.toString(row.score());
            Messages.send(viewer, "messages.leaderboard-command.row", i + 1, name == null ? row.uuid() : name, score);
        }
    }

    private String fmt(int s){int d=s/86400;s%=86400;int h=s/3600;s%=3600;int m=s/60;s%=60;
        StringBuilder b=new StringBuilder();if(d>0)b.append(d).append("d ");
        if(h>0)b.append(h).append("h ");if(m>0)b.append(m).append("m ");
        if(s>0||b.length()==0)b.append(s).append("s");return b.toString().trim();}
}
