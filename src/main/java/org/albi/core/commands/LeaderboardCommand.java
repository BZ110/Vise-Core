package org.albi.core.commands;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.data.StatManagement;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Kit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * /leaderboard – GUI + CLI
 */
public class LeaderboardCommand extends BaseCommand {

    /* cache */
    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final Map<String, CachedLB> CACHE = new HashMap<>();

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
                StatManagement.Field f = StatManagement.Field.valueOf(a[0].toUpperCase());
                show(p, f, String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
            } catch (IllegalArgumentException ex) {
                plugin.getMessageAPI().sendMessage(p, "&cInvalid field.");
            }
            return true;
        }
        if (DuelManager.stor == null) {
            plugin.getMessageAPI().sendMessage(p, "&7The duel leaderboard menu is unavailable on this server.");
            return true;
        }
        new FieldGUI(p).open();
        return true;
    }

    /*──────────── 1 ▸ field ────────────*/
    private class FieldGUI {
        private final Player v; FieldGUI(Player v){this.v=v;}
        void open(){
            SGMenu m = plugin.getGUI().create("§bLeaderboard Type",1);
            add(m,1,Material.DIAMOND_SWORD,"§aWins",   StatManagement.Field.WINS);
            add(m,3,Material.BOW,"§aKills",            StatManagement.Field.KILLS);
            add(m,5,Material.SKELETON_SKULL,"§aDeaths",StatManagement.Field.DEATHS);
            add(m,7,Material.CLOCK,"§aTime",           StatManagement.Field.TIME);
            v.openInventory(m.getInventory());
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
            SGMenu m=plugin.getGUI().create("§8Select Kit §7"+(page+1)+"/"+(max+1),6);
            DuelManager.stor.kits.forEach(k->{
                int g=k.getSlot(),pg=g/PAGE_SZ; if(pg!=page) return;
                int loc=g%PAGE_SZ; if(loc==44||loc==45) loc=findFree(m);
                m.setButton(loc,new SGButton(k.getDisplayItem().convertToItem()).withListener(e->{
                    e.setCancelled(true);
                    kitMem.put(v.getUniqueId(),k.getName());
                    new TeamGUI(v).open();
                }));
            });
            if(page>0)  m.setButton(44,new SGButton(arrow("§e← Prev")).withListener(e->{e.setCancelled(true);new KitGUI(v,page-1).open();}));
            if(page<max)m.setButton(45,new SGButton(arrow("§eNext →")).withListener(e->{e.setCancelled(true);new KitGUI(v,page+1).open();}));
            v.openInventory(m.getInventory());
        }
        private int findFree(SGMenu m){for(int i=0;i<PAGE_SZ;i++) if(m.getButton(i)==null) return i; return 0;}
        private ItemStack arrow(String n){ItemStack it=new ItemStack(Material.ARROW);ItemMeta im=it.getItemMeta();im.setDisplayName(n);it.setItemMeta(im);return it;}
    }

    /*──────────── 3 ▸ team size ────────────*/
    private class TeamGUI{
        private final Player v; private final String[] lbl={"1v1","2v2","3v3","4v4","5v5","Mismatch"};
        TeamGUI(Player v){this.v=v;}
        void open(){
            SGMenu m=plugin.getGUI().create("§bTeam Size",1);
            for(int i=0;i<lbl.length;i++){
                ItemStack it=new ItemStack(Material.PAPER); it.setAmount(i<5?i+1:1);
                ItemMeta im=it.getItemMeta(); im.setDisplayName("§a"+lbl[i]); it.setItemMeta(im);
                int idx=i;
                m.setButton(i,new SGButton(it).withListener(e->{e.setCancelled(true);finish(lbl[idx]);}));
            }
            v.openInventory(m.getInventory());
        }
        private void finish(String size){
            StatManagement.Field f=fieldMem.get(v.getUniqueId());
            String raw=kitMem.get(v.getUniqueId());
            if(f==null||raw==null){plugin.getMessageAPI().sendMessage(v,"&cError.");return;}

            /* clean kit name: strip colour & trim */
            String kitName = DuelManager.stor.kits.stream()
                    .filter(k->k.getName().equalsIgnoreCase(raw))
                    .map(Kit::getName).findFirst().orElse(raw);
            kitName = ChatColor.stripColor(kitName).trim();

            // Capitalize first letter
            kitName = kitName.substring(0, 1).toUpperCase() + kitName.substring(1);

            show(v,f,kitName+" "+size);

            fieldMem.remove(v.getUniqueId());
            kitMem.remove(v.getUniqueId());
        }
    }

    /*──────────── fetch & print ────────────*/
    private void show(Player v,StatManagement.Field f,String game){
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            String cacheKey=game+":"+f.name(); long now=System.currentTimeMillis();
            List<StatManagement.LBRow> rows;
            CachedLB c=CACHE.get(cacheKey);
            if(c!=null&&now-c.time<CACHE_TTL_MS) rows=c.rows;
            else { rows=StatManagement.leaderboard(game,f,10); CACHE.put(cacheKey,new CachedLB(rows,now)); }

            if(rows==null||rows.isEmpty()){plugin.getMessageAPI().sendMessage(v,"&cNo data.");return;}

            StringBuilder sb=new StringBuilder("§7Top §f").append(f.name().toLowerCase())
                    .append("§7 – §d").append(game).append("§7:\n");
            for(int i=0;i<rows.size();i++){
                var r=rows.get(i); OfflinePlayer op=Bukkit.getOfflinePlayer(r.uuid());
                String val=f==StatManagement.Field.TIME?fmt((int)r.score()):String.valueOf(r.score());
                sb.append("  §e").append(i+1).append(". §b").append(op.getName())
                        .append(" §7- §f").append(val).append("\n");
            }
            Bukkit.getScheduler().runTask(plugin,()->Arrays.stream(sb.toString().split("\n")).forEach(v::sendMessage));
        });
    }

    private String fmt(int s){int d=s/86400;s%=86400;int h=s/3600;s%=3600;int m=s/60;s%=60;
        StringBuilder b=new StringBuilder();if(d>0)b.append(d).append("d ");
        if(h>0)b.append(h).append("h ");if(m>0)b.append(m).append("m ");
        if(s>0||b.length()==0)b.append(s).append("s");return b.toString().trim();}
    private record CachedLB(List<StatManagement.LBRow> rows,long time){}
}
