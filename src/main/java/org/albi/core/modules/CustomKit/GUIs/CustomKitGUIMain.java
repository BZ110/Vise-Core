package org.albi.core.modules.CustomKit.GUIs;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.data.EffectMeta;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.data.MainPlayer;
import org.albi.core.data.StatManagement;
import org.albi.core.modules.CustomKit.CustomKitManager;
import org.albi.core.modules.CustomKit.helpers.CustomKit;
import org.albi.core.modules.CustomKit.helpers.CustomKitCategory;
import org.albi.core.serializable.SerializableItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.NamespacedKey;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import org.bukkit.block.ShulkerBox;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Consumer;


import java.util.*;
import java.util.function.BiConsumer;

public class CustomKitGUIMain {

    private final Core plugin;


    public CustomKitGUIMain(Core plugin) {
        this.plugin = plugin;

    }


    /*  ────────────────────────────────────────────────────────────────
     *   At class‑level (inside CustomKitGUIMain), add the two maps that
     *   let OnChatListener know whether a player is answering a question
     *  ───────────────────────────────────────────────────────────── */
    private static final Map<UUID, PendingRename>   RENAME  = new HashMap<>();
    private static final Map<UUID, PendingInteger>  STACK   = new HashMap<>();
    private static final Map<UUID, PendingEnchant>  ENCHANT = new HashMap<>();

    private static final Map<UUID, Integer> PENDING_MENDING = new HashMap<>();
    private static final Map<UUID, Integer> PENDING_DAMAGE  = new HashMap<>();


    private static final Map<UUID, PendingEffectCreate> EFFECT_CREATE = new HashMap<>();
    private record PendingEffectCreate(int kitIdx, String effectKey) {}


    /*  Tiny record helpers  */
    private record PendingRename (int kitIndex, int kitSlot) {}
    private record PendingInteger(int kitIndex, int kitSlot) {}
    private record PendingEnchant(int kitIndex, int kitSlot, Enchantment ench) {}

    /*  Extra “pending” maps for items INSIDE a shulker box  */
    private static final Map<UUID, PendingRenameBox>   RENAME_BOX  = new HashMap<>();
    private static final Map<UUID, PendingIntegerBox>  STACK_BOX   = new HashMap<>();
    private static final Map<UUID, PendingEnchantBox>  ENCHANT_BOX = new HashMap<>();

    private record PendingRenameBox (int kitIdx, int shulkerSlot, int boxSlot) {}
    private record PendingIntegerBox(int kitIdx, int shulkerSlot, int boxSlot) {}
    private record PendingEnchantBox(int kitIdx, int shulkerSlot,
                                     int boxSlot, Enchantment ench) {}

    /* ───────────── Ender-chest pending actions ───────────── */
    private static final Map<UUID, PendingRenameEc>   RENAME_EC  = new HashMap<>();
    private static final Map<UUID, PendingIntegerEc>  STACK_EC   = new HashMap<>();
    private static final Map<UUID, PendingEnchantEc>  ENCHANT_EC = new HashMap<>();

    private record PendingRenameEc (int kitIdx, int ecSlot) {}
    private record PendingIntegerEc(int kitIdx, int ecSlot) {}
    private record PendingEnchantEc(int kitIdx, int ecSlot, Enchantment ench) {}




    /*  ────────────────────────────────────────────────────────────────
     *   Replace ONLY the body of editSlot(...) with the new logic
     *  ───────────────────────────────────────────────────────────── */
    protected void editSlot(int kitSlot, int kitIndex, Player p) {
        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep == null) return;
        CustomKit kit = ep.getMainPlayer().getKits()[kitIndex];
        if (kit == null) return;

        SerializableItem ser = kit.getSlot(kitSlot);
        if (ser == null) {                       // empty → category picker
            openCategoryPicker(p, kitIndex, kitSlot);
            return;
        }

        ItemStack stack = ser.convertToItem();
        /* ── SHULKER? open shulker editor ────────────────────────────── */
        Material mat = stack.getType();
        if (mat.name().endsWith("SHULKER_BOX")) {
            openShulkerEditor(p, kitIndex, kitSlot);
            return;
        }

        /* regular items → 3‑row item editor we built last time */
        openItemEditor(p, kitIndex, kitSlot);

    }

    public static void clear(UUID id) {
        RENAME.remove(id);  STACK.remove(id);  ENCHANT.remove(id);
        RENAME_BOX.remove(id); STACK_BOX.remove(id); ENCHANT_BOX.remove(id);
        RENAME_EC.remove(id); STACK_EC.remove(id); ENCHANT_EC.remove(id);
        EFFECT_CREATE.remove(id);
    }


    /* ─────────────────────────────────────────────
     *   4-row GUI to edit the kit’s ender chest
     * ───────────────────────────────────────────*/
    private void openEnderChestEditor(Player p, int kitIdx) {

        CustomKit kit = plugin.getExtendedPlayer(p).getMainPlayer().getKits()[kitIdx];
        SGMenu menu   = plugin.getGUI().create("&bEdit Ender Chest", 4);

        /* 0-26  — storage grid */
        ItemStack filler = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta(); fm.setDisplayName("§7Click to edit"); filler.setItemMeta(fm);

        for (int ecSlot = 0; ecSlot < 27; ecSlot++) {
            ItemStack icon = kit.getEnderSlot(ecSlot) != null
                    ? kit.getEnderSlot(ecSlot).convertToItem()
                    : filler;

            final int s = ecSlot;
            menu.setButton(ecSlot, new SGButton(icon).withListener(e -> {
                if (kit.getEnderSlot(s) == null) {
                    openCategoryPickerForEnder(p, kitIdx, s);
                } else {
                    openItemEditorForEnder(p, kitIdx, s);
                }
            }));
        }

        /* slot 31 — FINISH */
        ItemStack done = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta dm = done.getItemMeta(); dm.setDisplayName("§aFINISH"); done.setItemMeta(dm);
        menu.setButton(31, new SGButton(done).withListener(e -> openSettings(kitIdx, p)));

        p.openInventory(menu.getInventory());
    }

    private void openItemEditorForEnder(Player p, int kitIdx, int ecSlot) {

        CustomKit kit = plugin.getExtendedPlayer(p).getMainPlayer().getKits()[kitIdx];
        ItemStack working = kit.getEnderSlot(ecSlot).convertToItem();

        SGMenu menu = plugin.getGUI().create("&bEdit Item", 2);

        /* delete */
        ItemStack bar = new ItemStack(Material.BARRIER);
        ItemMeta bm = bar.getItemMeta(); bm.setDisplayName("§cDelete"); bar.setItemMeta(bm);
        menu.setButton(10, new SGButton(bar).withListener(e -> {
            kit.setEnderSlot(ecSlot, null);
            openEnderChestEditor(p, kitIdx);
        }));

        /* rename */
        ItemStack sign = new ItemStack(Material.OAK_SIGN);
        ItemMeta sm = sign.getItemMeta(); sm.setDisplayName("§eRename"); sign.setItemMeta(sm);
        menu.setButton(12, new SGButton(sign).withListener(e -> {
            p.closeInventory();
            p.sendMessage("§bType the new name in chat.");
            RENAME_EC.put(p.getUniqueId(), new PendingRenameEc(kitIdx, ecSlot));
        }));

        /* enchant */
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta em = book.getItemMeta(); em.setDisplayName("§9Add / Change Enchant"); book.setItemMeta(em);
        menu.setButton(13, new SGButton(book).withListener(
                e -> openEnchantmentPickerForEnder(p, kitIdx, ecSlot)));

        /* stack size */
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta pm = paper.getItemMeta(); pm.setDisplayName("§fSet Stack Size"); paper.setItemMeta(pm);
        menu.setButton(14, new SGButton(paper).withListener(e -> {
            p.closeInventory();
            p.sendMessage("§bEnter a number 1-64 in chat.");
            STACK_EC.put(p.getUniqueId(), new PendingIntegerEc(kitIdx, ecSlot));
        }));

        /* finish */
        ItemStack done = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta dm = done.getItemMeta(); dm.setDisplayName("§aFINISH"); done.setItemMeta(dm);
        menu.setButton(16, new SGButton(done).withListener(e -> openEnderChestEditor(p, kitIdx)));

        /* preview */
        menu.setButton(4, new SGButton(working));

        p.openInventory(menu.getInventory());
    }
    private void openEnchantmentPickerForEnder(Player p, int kitIdx, int ecSlot) {

        CustomKit kit = plugin.getExtendedPlayer(p).getMainPlayer().getKits()[kitIdx];
        ItemStack target = kit.getEnderSlot(ecSlot).convertToItem();
        ItemMeta targetMeta = target.getItemMeta();

        SGMenu enchMenu = plugin.getGUI().create("&bPick Enchantment", 6);
        int gui = 0;

        for (Enchantment ench : Enchantment.values()) {

            boolean isKnockback = ench == Enchantment.KNOCKBACK;

            // 1) Must be applicable to the item, unless it's Knockback (always allowed)
            if (!isKnockback && !ench.canEnchantItem(target)) continue;

            // 2) Must not conflict with existing enchants on the item
            //    (but allow selecting the same enchant to change its level)
            if (!isKnockback && conflictsWithExisting(targetMeta, ench)) continue;

            ItemStack icon = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta im = icon.getItemMeta();
            im.setDisplayName("§r" + ench.getKey().getKey().toUpperCase());
            icon.setItemMeta(im);

            enchMenu.setButton(gui++, new SGButton(icon).withListener(e -> {
                p.closeInventory();
                p.sendMessage("§bType the level (≥1) in chat.");
                ENCHANT_EC.put(p.getUniqueId(), new PendingEnchantEc(kitIdx, ecSlot, ench));
            }));
        }

        /* clear all */
        ItemStack anvil = new ItemStack(Material.ANVIL);
        ItemMeta am = anvil.getItemMeta(); am.setDisplayName("§cClear All Enchants"); anvil.setItemMeta(am);
        enchMenu.setButton(52, new SGButton(anvil).withListener(e -> {
            ItemMeta meta = target.getItemMeta();
            meta.getEnchants().keySet().forEach(meta::removeEnchant);
            target.setItemMeta(meta);
            kit.setEnderSlot(ecSlot, target);
            p.sendMessage("§aAll enchantments cleared.");
            openItemEditorForEnder(p, kitIdx, ecSlot);
        }));

        /* finish */
        ItemStack done = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta dm = done.getItemMeta(); dm.setDisplayName("§aFINISH"); done.setItemMeta(dm);
        enchMenu.setButton(53, new SGButton(done).withListener(e -> openItemEditorForEnder(p, kitIdx, ecSlot)));

        p.openInventory(enchMenu.getInventory());
    }

    /** Returns true if 'ench' conflicts with any *other* enchant already on the item. */
    private boolean conflictsWithExisting(ItemMeta meta, Enchantment ench) {
        if (meta == null) return false;
        Map<Enchantment, Integer> existing = meta.getEnchants();
        if (existing == null || existing.isEmpty()) return false;

        for (Enchantment have : existing.keySet()) {
            if (have.equals(ench)) continue;  // allow re-selecting same enchant to change level
            if (ench.conflictsWith(have)) return true;
        }
        return false;
    }

    private void openCategoryPickerForEnder(Player p, int kitIdx, int ecSlot) {

        Map<String, CustomKitCategory> cats =
                (Map<String, CustomKitCategory>) plugin.getData().get("customKitCategories");
        if (cats == null || cats.isEmpty()) { p.sendMessage("§cNo categories configured"); return; }

        SGMenu catMenu = plugin.getGUI().create("&bPick a Category", 4);

        for (CustomKitCategory cat : cats.values()) {
            ItemStack logo = cat.getDisplayItem().convertToItem();
            catMenu.setButton(cat.getCategoryGuiSlot(),
                    new SGButton(logo).withListener(e ->
                            openCategoryItemsForEnder(p, cat, kitIdx, ecSlot)));
        }
        p.openInventory(catMenu.getInventory());
    }

    private void openCategoryItemsForEnder(Player p,
                                           CustomKitCategory cat,
                                           int kitIdx,
                                           int ecSlot) {

        SGMenu itemMenu = plugin.getGUI().create("&bSelect an Item", 6);

        for (Map.Entry<Integer, SerializableItem> entry : cat.getItems().entrySet()) {
            ItemStack icon = entry.getValue().convertToItem();
            int guiSlot = entry.getKey();

            itemMenu.setButton(guiSlot, new SGButton(icon).withListener(e -> {
                if (icon.getType().name().endsWith("SHULKER_BOX")) {
                    p.sendMessage("§cYou can't place a shulker box inside the ender chest.");
                    return;
                }
                CustomKit kit = plugin.getExtendedPlayer(p).getMainPlayer().getKits()[kitIdx];
                kit.setEnderSlot(ecSlot, icon.clone());
                openEnderChestEditor(p, kitIdx);
            }));
        }
        p.openInventory(itemMenu.getInventory());
    }
    public static boolean handleChatRenameEc(Player p, String msg, CustomKitGUIMain inst) {
        PendingRenameEc pr = RENAME_EC.remove(p.getUniqueId());
        if (pr == null) return false;

        if (msg.length() > 20) { p.sendMessage("§cName too long."); return true; }

        CustomKit kit = inst.plugin.getExtendedPlayer(p).getMainPlayer().getKits()[pr.kitIdx()];
        ItemStack it  = kit.getEnderSlot(pr.ecSlot()).convertToItem();
        ItemMeta  im  = it.getItemMeta();
        im.setDisplayName(ChatColor.translateAlternateColorCodes('&', msg));
        it.setItemMeta(im);
        kit.setEnderSlot(pr.ecSlot(), it);

        p.sendMessage("§aName set.");
        inst.openEnderChestEditor(p, pr.kitIdx());
        return true;
    }

    public static boolean handleChatStackEc(Player p, String msg, CustomKitGUIMain inst) {
        PendingIntegerEc pi = STACK_EC.remove(p.getUniqueId());
        if (pi == null) return false;

        int amt;
        try { amt = Integer.parseInt(msg); }
        catch (NumberFormatException ex) { p.sendMessage("§cNot a number."); return true; }
        if (amt < 1 || amt > 64) { p.sendMessage("§cNumber must be 1-64."); return true; }

        CustomKit kit = inst.plugin.getExtendedPlayer(p).getMainPlayer().getKits()[pi.kitIdx()];
        ItemStack it  = kit.getEnderSlot(pi.ecSlot()).convertToItem();
        it.setAmount(Math.min(amt, it.getMaxStackSize()));
        kit.setEnderSlot(pi.ecSlot(), it);

        p.sendMessage("§aStack size set.");
        inst.openEnderChestEditor(p, pi.kitIdx());
        return true;
    }

    public static boolean handleChatEffectCreate(Player p, String msg, CustomKitGUIMain inst) {
        PendingEffectCreate pe = EFFECT_CREATE.remove(p.getUniqueId());
        if (pe == null) return false;

        String in = msg.trim();
        if (!in.contains(":")) {
            p.sendMessage("§cInvalid format. Use level:seconds, example 1:120");
            inst.openEffectPicker(p, pe.kitIdx(), 0);
            return true;
        }

        String[] parts = in.split(":");
        if (parts.length != 2) {
            p.sendMessage("§cInvalid format. Use level:seconds, example 1:120");
            inst.openEffectPicker(p, pe.kitIdx(), 0);
            return true;
        }

        int level;
        int seconds;
        try {
            level = Integer.parseInt(parts[0].trim());
            seconds = Integer.parseInt(parts[1].trim());
        } catch (Exception e) {
            p.sendMessage("§cInvalid numbers. Use level:seconds, example 1:120");
            inst.openEffectPicker(p, pe.kitIdx(), 0);
            return true;
        }

        if (level < 1 || level > 10) {
            p.sendMessage("§cLevel must be 1-10.");
            inst.openEffectPicker(p, pe.kitIdx(), 0);
            return true;
        }
        if (seconds < 1 || seconds > 3600) {
            p.sendMessage("§cSeconds must be 1-3600.");
            inst.openEffectPicker(p, pe.kitIdx(), 0);
            return true;
        }

        ExtendedPlayer ep = inst.plugin.getExtendedPlayer(p);
        if (ep == null) return true;

        CustomKit kit = ep.getMainPlayer().getKits()[pe.kitIdx()];
        if (kit == null) return true;

        kit.addEffect(new EffectMeta(pe.effectKey(), level, seconds));
        p.sendMessage("§aEffect added: §f" + pe.effectKey() + " §7level §f" + level + "§7, §f" + seconds + "s");

        inst.openEffectsMenu(p, pe.kitIdx());
        return true;
    }


    public static boolean handleChatEnchantEc(Player p, String msg, CustomKitGUIMain inst) {
        PendingEnchantEc pe = ENCHANT_EC.remove(p.getUniqueId());
        if (pe == null) return false;

        int lvl;
        try { lvl = Integer.parseInt(msg); }
        catch (NumberFormatException ex) { p.sendMessage("§cNot a number."); return true; }
        if (lvl < 1 || lvl > 10) { p.sendMessage("§cLevel must be 1-10."); return true; }

        CustomKit kit = inst.plugin.getExtendedPlayer(p).getMainPlayer().getKits()[pe.kitIdx()];
        ItemStack it  = kit.getEnderSlot(pe.ecSlot()).convertToItem();
        ItemMeta  im  = it.getItemMeta();
        im.addEnchant(pe.ench(), Math.min(lvl, pe.ench().getMaxLevel()), true);
        it.setItemMeta(im);
        kit.setEnderSlot(pe.ecSlot(), it);

        p.sendMessage("§aEnchantment set.");
        inst.openEnderChestEditor(p, pe.kitIdx());
        return true;
    }



    private void openItemEditorForBox(Player p,
                                      int kitIdx, int shulkerSlot, int boxSlot) {

        CustomKit kit = plugin.getExtendedPlayer(p).getMainPlayer().getKits()[kitIdx];
        ItemStack sh  = kit.getSlot(shulkerSlot).convertToItem();
        BlockStateMeta bsm = (BlockStateMeta) sh.getItemMeta();
        ShulkerBox sb  = (ShulkerBox) bsm.getBlockState();
        Inventory  inv = sb.getInventory();

        ItemStack working = inv.getItem(boxSlot).clone();   // never null here

        SGMenu menu = plugin.getGUI().create("&bChoose a Category", 4);

        /* slot 10 – delete */
        ItemStack bar = new ItemStack(Material.BARRIER);
        ItemMeta  bm  = bar.getItemMeta(); bm.setDisplayName("§cDelete"); bar.setItemMeta(bm);
        menu.setButton(10, new SGButton(bar).withListener(e -> {
            inv.clear(boxSlot);
            saveShulkerBack(kit, shulkerSlot, sh, sb, bsm, inv);
            openShulkerEditor(p, kitIdx, shulkerSlot);
        }));

        /* slot 12 – rename */
        ItemStack sign = new ItemStack(Material.OAK_SIGN);
        ItemMeta  sm   = sign.getItemMeta(); sm.setDisplayName("§eRename"); sign.setItemMeta(sm);
        menu.setButton(12, new SGButton(sign).withListener(e -> {
            p.closeInventory();
            p.sendMessage("§bType the new name in chat.");
            RENAME_BOX.put(p.getUniqueId(), new PendingRenameBox(kitIdx, shulkerSlot, boxSlot));
        }));

        /* slot 13 – enchant */
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta  em   = book.getItemMeta(); em.setDisplayName("§9Add / Change Enchant"); book.setItemMeta(em);
        menu.setButton(13, new SGButton(book).withListener(e ->
                openEnchantmentPickerForBox(p, kitIdx, shulkerSlot, boxSlot)));

        /* slot 14 – stack size */
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta  pm    = paper.getItemMeta(); pm.setDisplayName("§fSet Stack Size"); paper.setItemMeta(pm);
        menu.setButton(14, new SGButton(paper).withListener(e -> {
            p.closeInventory();
            p.sendMessage("§bEnter a number 1‑64 in chat.");
            STACK_BOX.put(p.getUniqueId(), new PendingIntegerBox(kitIdx, shulkerSlot, boxSlot));
        }));

        /* slot 16 – finish */
        ItemStack done = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta  dm   = done.getItemMeta(); dm.setDisplayName("§aFINISH"); done.setItemMeta(dm);
        menu.setButton(16, new SGButton(done).withListener(e -> openShulkerEditor(p, kitIdx, shulkerSlot)));

        /* preview item in slot 4 */
        menu.setButton(4, new SGButton(working));

        p.openInventory(menu.getInventory());
    }


    private void openEnchantmentPickerForBox(Player p,
                                             int kitIdx, int shulkerSlot, int boxSlot) {

        SGMenu enchMenu = plugin.getGUI().create("&bPick Enchantment", 6);
        int gui = 0;

        CustomKit kit = plugin.getExtendedPlayer(p).getMainPlayer().getKits()[kitIdx];
        ItemStack sh = kit.getSlot(shulkerSlot).convertToItem();

        BlockStateMeta bsm = (BlockStateMeta) sh.getItemMeta();
        ShulkerBox sb = (ShulkerBox) bsm.getBlockState();
        Inventory inv = sb.getInventory();
        ItemStack target = inv.getItem(boxSlot);
        if (target == null) {
            p.sendMessage("§cNo item in that slot.");
            return;
        }
        ItemMeta targetMeta = target.getItemMeta();

        for (Enchantment ench : Enchantment.values()) {
            boolean isKnockback = ench == Enchantment.KNOCKBACK;

            // Allow Knockback always; otherwise must be applicable to the item
            if (!isKnockback && !ench.canEnchantItem(target)) continue;

            // Hide enchants that conflict with what the item already has
            if (!isKnockback && conflictsWithExisting(targetMeta, ench)) continue;

            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta im = book.getItemMeta();
            im.setDisplayName("§r" + ench.getKey().getKey().toUpperCase());
            book.setItemMeta(im);

            enchMenu.setButton(gui++, new SGButton(book).withListener(e -> {
                p.closeInventory();
                p.sendMessage("§bType the level (≥1) in chat.");
                ENCHANT_BOX.put(p.getUniqueId(),
                        new PendingEnchantBox(kitIdx, shulkerSlot, boxSlot, ench));
            }));
        }

        // slot 52 – Clear all enchants
        {
            ItemStack anvil = new ItemStack(Material.ANVIL);
            ItemMeta im = anvil.getItemMeta();
            im.setDisplayName("§cClear All Enchants");
            anvil.setItemMeta(im);

            enchMenu.setButton(52, new SGButton(anvil).withListener(e -> {
                ItemStack item = inv.getItem(boxSlot);
                if (item == null) return;

                ItemMeta meta = item.getItemMeta();
                if (meta == null) return;

                meta.getEnchants().keySet().forEach(meta::removeEnchant);
                item.setItemMeta(meta);
                inv.setItem(boxSlot, item);

                saveShulkerBack(kit, shulkerSlot, sh, sb, bsm, inv);
                p.sendMessage("§aAll enchantments cleared.");
                openItemEditorForBox(p, kitIdx, shulkerSlot, boxSlot);
            }));
        }

        // slot 53 – Finish
        {
            ItemStack done = new ItemStack(Material.LIME_CONCRETE);
            ItemMeta dm = done.getItemMeta();
            dm.setDisplayName("§aFINISH");
            done.setItemMeta(dm);

            enchMenu.setButton(53, new SGButton(done).withListener(e ->
                    openItemEditorForBox(p, kitIdx, shulkerSlot, boxSlot)));
        }

        p.openInventory(enchMenu.getInventory());
    }



    public static boolean handleChatRenameBox(Player p, String msg, CustomKitGUIMain inst) {
        PendingRenameBox pr = RENAME_BOX.remove(p.getUniqueId());
        if (pr == null) return false;

        CustomKit kit = inst.plugin.getExtendedPlayer(p).getMainPlayer().getKits()[pr.kitIdx()];
        ItemStack sh  = kit.getSlot(pr.shulkerSlot()).convertToItem();
        BlockStateMeta bsm = (BlockStateMeta) sh.getItemMeta();
        ShulkerBox sb  = (ShulkerBox) bsm.getBlockState();
        Inventory  inv = sb.getInventory();
        if (msg.length() > 20) {
            p.sendMessage("§cThis name is too long. The event has been canceled.");
            return true;
        }
        ItemStack it = inv.getItem(pr.boxSlot()).clone();
        ItemMeta im  = it.getItemMeta();
        im.setDisplayName(ChatColor.translateAlternateColorCodes('&', msg));
        it.setItemMeta(im);

        inv.setItem(pr.boxSlot(), it);
        inst.saveShulkerBack(kit, pr.shulkerSlot(), sh, sb, bsm, inv);
        p.sendMessage("§aName set.");
        inst.openShulkerEditor(p, pr.kitIdx(), pr.shulkerSlot());
        return true;
    }

    public static boolean handleChatStackBox(Player p, String msg, CustomKitGUIMain inst) {
        PendingIntegerBox pi = STACK_BOX.remove(p.getUniqueId());
        if (pi == null) return false;

        int amt;
        try { amt = Integer.parseInt(msg); } catch (NumberFormatException ex) { p.sendMessage("§cNot a number."); return true; }
        if (amt < 1 || amt > 64) { p.sendMessage("§cNumber must be 1‑64. The event has been canceled."); return true; }

        CustomKit kit = inst.plugin.getExtendedPlayer(p).getMainPlayer().getKits()[pi.kitIdx()];
        ItemStack sh  = kit.getSlot(pi.shulkerSlot()).convertToItem();
        BlockStateMeta bsm = (BlockStateMeta) sh.getItemMeta();
        ShulkerBox sb  = (ShulkerBox) bsm.getBlockState();
        Inventory  inv = sb.getInventory();

        ItemStack it = inv.getItem(pi.boxSlot()).clone();
        it.setAmount(Math.min(amt, it.getMaxStackSize()));
        inv.setItem(pi.boxSlot(), it);

        inst.saveShulkerBack(kit, pi.shulkerSlot(), sh, sb, bsm, inv);
        p.sendMessage("§aStack size set.");
        inst.openShulkerEditor(p, pi.kitIdx(), pi.shulkerSlot());
        return true;
    }

    public static boolean handleChatEnchantBox(Player p, String msg, CustomKitGUIMain inst) {
        PendingEnchantBox pe = ENCHANT_BOX.remove(p.getUniqueId());
        if (pe == null) return false;

        int lvl;
        try { lvl = Integer.parseInt(msg); } catch (NumberFormatException ex) { p.sendMessage("§cNot a number. The event has been canceled."); return true; }
        if (lvl < 1) {
            p.sendMessage("§cLevel must be ≥ 1. The event has been canceled.");
            return true;
        }
        if (lvl > 10) {
            p.sendMessage("§cLevel must be ≤ 10. The event has been canceled.");
            return true;
        }

        CustomKit kit = inst.plugin.getExtendedPlayer(p).getMainPlayer().getKits()[pe.kitIdx()];
        ItemStack sh  = kit.getSlot(pe.shulkerSlot()).convertToItem();
        BlockStateMeta bsm = (BlockStateMeta) sh.getItemMeta();
        ShulkerBox sb  = (ShulkerBox) bsm.getBlockState();
        Inventory  inv = sb.getInventory();

        ItemStack it = inv.getItem(pe.boxSlot()).clone();
        ItemMeta  im = it.getItemMeta();
        im.addEnchant(pe.ench(), Math.min(lvl, pe.ench().getMaxLevel()), true);
        it.setItemMeta(im);

        inv.setItem(pe.boxSlot(), it);
        inst.saveShulkerBack(kit, pe.shulkerSlot(), sh, sb, bsm, inv);
        p.sendMessage("§aEnchantment set.");
        inst.openShulkerEditor(p, pe.kitIdx(), pe.shulkerSlot());
        return true;
    }



    /* ───────────────────────────────────────────────────────────────
     *  4‑row editor for a shulker box that lives in a kit slot
     * ───────────────────────────────────────────────────────────── */
    private void openShulkerEditor(Player p, int kitIndex, int kitSlot) {

        CustomKit kit = plugin.getExtendedPlayer(p).getMainPlayer().getKits()[kitIndex];
        ItemStack sh = kit.getSlot(kitSlot).convertToItem();

        BlockStateMeta bsm = (BlockStateMeta) sh.getItemMeta();
        ShulkerBox     sb  = (ShulkerBox) bsm.getBlockState();
        Inventory      inv = sb.getInventory();           // 27 slots

        SGMenu menu = plugin.getGUI().create("&bEdit Shulker", 4);

        /* 0‑26 : internal storage ------------------------------------------------*/
        ItemStack filler = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta  fMeta  = filler.getItemMeta();
        fMeta.setDisplayName("§7Click to edit");
        filler.setItemMeta(fMeta);

        for (int i = 0; i < 27; i++) {
            ItemStack icon = inv.getItem(i);
            if (icon == null || icon.getType() == Material.AIR) icon = filler.clone();

            final int boxSlot = i;
            menu.setButton(i, new SGButton(icon).withListener(e -> {
                if (inv.getItem(boxSlot) == null || inv.getItem(boxSlot).getType() == Material.AIR) {
                    openCategoryPickerForShulker(p, kitIndex, kitSlot, boxSlot);
                } else {
                    openItemEditorForBox(p, kitIndex, kitSlot, boxSlot);  // << NEW
                }
            }));

        }

        /* slot 30 – DELETE -------------------------------------------------------*/
        {
            ItemStack bar = new ItemStack(Material.BARRIER);
            ItemMeta  im  = bar.getItemMeta();
            im.setDisplayName("§cDelete Shulker");
            bar.setItemMeta(im);

            menu.setButton(30, new SGButton(bar).withListener(e -> {
                kit.setSlot(kitSlot, null);
                editKit(kitIndex, p);
            }));
        }

        /* slot 31 – RENAME -------------------------------------------------------*/
        {
            ItemStack sign = new ItemStack(Material.OAK_SIGN);
            ItemMeta  im   = sign.getItemMeta();
            im.setDisplayName("§eRename Shulker");
            sign.setItemMeta(im);

            menu.setButton(31, new SGButton(sign).withListener(e -> {
                p.closeInventory();
                p.sendMessage("§bType the new shulker name in chat.");
                RENAME.put(p.getUniqueId(), new PendingRename(kitIndex, kitSlot));
            }));
        }

        /* slot 32 – FINISH -------------------------------------------------------*/
        {
            ItemStack done = new ItemStack(Material.LIME_CONCRETE);
            ItemMeta  im   = done.getItemMeta();
            im.setDisplayName("§aFINISH");
            done.setItemMeta(im);

            menu.setButton(32, new SGButton(done).withListener(e -> editKit(kitIndex, p)));
        }

        p.openInventory(menu.getInventory());
    }

    /* save helper */
    private void saveShulkerBack(CustomKit kit, int kitSlot,
                                 ItemStack sh, ShulkerBox sb, BlockStateMeta bsm, Inventory inv) {

        sb.getInventory().setContents(inv.getContents());
        bsm.setBlockState(sb);
        sh.setItemMeta(bsm);
        kit.setSlot(kitSlot, sh);
    }

    /** Category picker for *inside* a shulker box                           */
    private void openCategoryPickerForShulker(Player p,
                                              int kitIndex,
                                              int kitSlotShulker,
                                              int boxSlot) {

        Map<String, CustomKitCategory> cats =
                (Map<String, CustomKitCategory>) plugin.getData().get("customKitCategories");
        if (cats == null || cats.isEmpty()) {
            p.sendMessage("§cNo categories configured");
            return;
        }

        SGMenu catMenu = plugin.getGUI().create("&bPick a Category", 4);

        for (CustomKitCategory cat : cats.values()) {
            ItemStack logo = cat.getDisplayItem().convertToItem();
            SGButton b = new SGButton(logo).withListener(e -> {
                openCategoryItemsForShulker(p, cat, kitIndex, kitSlotShulker, boxSlot);
            });
            catMenu.setButton(cat.getCategoryGuiSlot(), b);
        }
        p.openInventory(catMenu.getInventory());
    }

    /** the 6‑row item picker for a shulker slot                              */
    private void openCategoryItemsForShulker(Player p,
                                             CustomKitCategory cat,
                                             int kitIndex,
                                             int kitSlotShulker,
                                             int boxSlot) {

        SGMenu itemMenu = plugin.getGUI().create("&bSelect an Item", 6);

        for (Map.Entry<Integer, SerializableItem> entry : cat.getItems().entrySet()) {
            ItemStack icon = entry.getValue().convertToItem();
            int guiSlot  = entry.getKey();

            SGButton b = new SGButton(icon).withListener(e -> {
                CustomKit kit = plugin.getExtendedPlayer(p).getMainPlayer().getKits()[kitIndex];

                ItemStack sh = kit.getSlot(kitSlotShulker).convertToItem();
                BlockStateMeta bsm = (BlockStateMeta) sh.getItemMeta();
                ShulkerBox sb = (ShulkerBox) bsm.getBlockState();
                Inventory inv = sb.getInventory();
                if (icon.getType().name().endsWith("SHULKER_BOX")) {
                    p.sendMessage("§cYou can't place a shulker box inside another.");
                    return;
                }

                inv.setItem(boxSlot, icon.clone());       // put chosen item
                saveShulkerBack(kit, kitSlotShulker, sh, sb, bsm, inv);
                openShulkerEditor(p, kitIndex, kitSlotShulker);
            });

            itemMenu.setButton(guiSlot, b);
        }
        p.openInventory(itemMenu.getInventory());
    }



    /*  ────────────────────────────────────────────────────────────────
     *   The 3‑row editor itself
     *  ───────────────────────────────────────────────────────────── */
    private void openItemEditor(Player p, int kitIndex, int kitSlot) {

        SGMenu menu = plugin.getGUI().create("&bEdit Item", 2);

        CustomKit kit = plugin.getExtendedPlayer(p).getMainPlayer().getKits()[kitIndex];
        ItemStack working = kit.getSlot(kitSlot).convertToItem();   // copy we will mutate

        /* slot 10 – Delete */
        {
            ItemStack barrier = new ItemStack(Material.BARRIER);
            ItemMeta im = barrier.getItemMeta();
            im.setDisplayName("§cDelete");
            barrier.setItemMeta(im);

            SGButton del = new SGButton(barrier).withListener(e -> {
                kit.setSlot(kitSlot, null);
                editKit(kitIndex, p);
            });
            menu.setButton(10, del);
        }

        /* slot 12 – Rename */
        {
            ItemStack sign = new ItemStack(Material.OAK_SIGN);
            ItemMeta im = sign.getItemMeta();
            im.setDisplayName("§eRename");
            sign.setItemMeta(im);

            SGButton ren = new SGButton(sign).withListener(e -> {
                p.closeInventory();
                p.sendMessage("§bType the new name in chat (colour codes with &).");
                RENAME.put(p.getUniqueId(), new PendingRename(kitIndex, kitSlot));
            });
            menu.setButton(12, ren);
        }

        /* slot 13 – Enchant */
        {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta im = book.getItemMeta();
            im.setDisplayName("§9Add / Change Enchant");
            book.setItemMeta(im);

            SGButton enchBtn = new SGButton(book).withListener(e -> openEnchantmentPicker(p, kitIndex, kitSlot));
            menu.setButton(13, enchBtn);
        }

        /* slot 14 – Stack size */
        {
            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta im = paper.getItemMeta();
            im.setDisplayName("§fSet Stack Size");
            paper.setItemMeta(im);

            SGButton stack = new SGButton(paper).withListener(e -> {
                p.closeInventory();
                p.sendMessage("§bEnter a number 1‑64 in chat.");
                STACK.put(p.getUniqueId(), new PendingInteger(kitIndex, kitSlot));
            });
            menu.setButton(14, stack);
        }

        /* slot 16 –  FINISH */
        {
            ItemStack done = new ItemStack(Material.LIME_CONCRETE);
            ItemMeta im = done.getItemMeta();
            im.setDisplayName("§aFINISH");
            done.setItemMeta(im);

            SGButton fin = new SGButton(done).withListener(e -> editKit(kitIndex, p));
            menu.setButton(16, fin);
        }

        /* centre display of the current item (slot 4) */
        menu.setButton(4, new SGButton(working));

        p.openInventory(menu.getInventory());
    }

    /*  ────────────────────────────────────────────────────────────────
     *   Enchantment picker (6‑row) then ask for level in chat
     *  ───────────────────────────────────────────────────────────── */
    private void openEnchantmentPicker(Player p, int kitIndex, int kitSlot) {

        SGMenu enchMenu = plugin.getGUI().create("&bPick Enchantment", 6);
        int slot = 0;

        CustomKit kit = plugin.getExtendedPlayer(p).getMainPlayer().getKits()[kitIndex];
        ItemStack target = kit.getSlot(kitSlot).convertToItem();
        ItemMeta targetMeta = target.getItemMeta();

        for (Enchantment ench : Enchantment.values()) {
            boolean isKnockback = ench == Enchantment.KNOCKBACK;

            // Allow Knockback always; otherwise must be applicable to the item
            if (!isKnockback && !ench.canEnchantItem(target)) continue;

            // Hide enchants that conflict with what the item already has
            if (!isKnockback && conflictsWithExisting(targetMeta, ench)) continue;

            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta im = book.getItemMeta();
            im.setDisplayName("§r" + ench.getKey().getKey().toUpperCase());
            book.setItemMeta(im);

            SGButton b = new SGButton(book).withListener(e2 -> {
                p.closeInventory();
                p.sendMessage("§bType the level (≥1) in chat.");
                ENCHANT.put(p.getUniqueId(), new PendingEnchant(kitIndex, kitSlot, ench));
            });
            enchMenu.setButton(slot++, b);
        }

        // slot 52 – Clear all enchants
        {
            ItemStack anvil = new ItemStack(Material.ANVIL);
            ItemMeta im = anvil.getItemMeta();
            im.setDisplayName("§cClear All Enchants");
            anvil.setItemMeta(im);

            enchMenu.setButton(52, new SGButton(anvil).withListener(e -> {
                ItemStack item = kit.getSlot(kitSlot).convertToItem();
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.getEnchants().keySet().forEach(meta::removeEnchant);
                    item.setItemMeta(meta);
                    kit.setSlot(kitSlot, item);
                }
                p.sendMessage("§aAll enchantments cleared.");
                openItemEditor(p, kitIndex, kitSlot);  // Return to item editor
            }));
        }

        // slot 53 – FINISH
        {
            ItemStack done = new ItemStack(Material.LIME_CONCRETE);
            ItemMeta dm = done.getItemMeta();
            dm.setDisplayName("§aFINISH");
            done.setItemMeta(dm);

            enchMenu.setButton(53, new SGButton(done).withListener(e ->
                    openItemEditor(p, kitIndex, kitSlot)));
        }

        p.openInventory(enchMenu.getInventory());
    }


    /*  ────────────────────────────────────────────────────────────────
     *   Helpers to apply chat answers (called from OnChatListener)
     *  ───────────────────────────────────────────────────────────── */
    public static boolean handleChatRename(Player p, String msg, CustomKitGUIMain inst) {
        PendingRename pr = RENAME.remove(p.getUniqueId());
        if (pr == null) return false;

        CustomKit kit = inst.plugin.getExtendedPlayer(p).getMainPlayer().getKits()[pr.kitIndex()];
        ItemStack item = kit.getSlot(pr.kitSlot()).convertToItem();
        ItemMeta im = item.getItemMeta();
        if (msg.length() > 20) {
            p.sendMessage("§cThis name is too long. The event has been canceled.");
            return true;
        }
        im.setDisplayName(ChatColor.translateAlternateColorCodes('&', msg));
        item.setItemMeta(im);
        kit.setSlot(pr.kitSlot(), item);
        p.sendMessage("§aName set.");
        inst.editKit(pr.kitIndex(), p);
        return true;
    }

    public static boolean handleChatStack(Player p, String msg, CustomKitGUIMain inst) {
        PendingInteger pi = STACK.remove(p.getUniqueId());
        if (pi == null) return false;

        int amt;
        try { amt = Integer.parseInt(msg); }
        catch (NumberFormatException ex) { p.sendMessage("§cNot a number. The event has been canceled."); return true; }

        if (amt < 1 || amt > 64) { p.sendMessage("§cNumber must be 1‑64. The event has been canceled."); return true; }

        CustomKit kit = inst.plugin.getExtendedPlayer(p).getMainPlayer().getKits()[pi.kitIndex()];
        ItemStack item = kit.getSlot(pi.kitSlot()).convertToItem();
        item.setAmount(Math.min(amt, item.getMaxStackSize()));
        kit.setSlot(pi.kitSlot(), item);
        p.sendMessage("§aStack size set.");
        inst.editKit(pi.kitIndex(), p);
        return true;
    }

    public static boolean handleChatEnchant(Player p, String msg, CustomKitGUIMain inst) {
        PendingEnchant pe = ENCHANT.remove(p.getUniqueId());
        if (pe == null) return false;

        int lvl;
        try { lvl = Integer.parseInt(msg); }
        catch (NumberFormatException ex) { p.sendMessage("§cNot a number. The event has been canceled."); return true; }

        if (lvl < 1) {
            p.sendMessage("§cLevel must be ≥ 1. The event has been canceled.");
            return true;
        }
        if (lvl > 10) {
            p.sendMessage("§cLevel must be ≤ 10. The event has been canceled.");
            return true;
        }
        CustomKit kit = inst.plugin.getExtendedPlayer(p).getMainPlayer().getKits()[pe.kitIndex()];
        ItemStack item = kit.getSlot(pe.kitSlot()).convertToItem();

        ItemMeta im = item.getItemMeta();
        im.addEnchant(pe.ench(), Math.min(lvl, pe.ench().getMaxLevel()), true);
        item.setItemMeta(im);

        kit.setSlot(pe.kitSlot(), item);
        p.sendMessage("§aEnchantment set.");
        inst.editKit(pe.kitIndex(), p);
        return true;
    }


    /** Opens the category picker. */
    private void openCategoryPicker(Player p,
                                    int kitIndex,
                                    int kitSlot) {

        Map<String, CustomKitCategory> cats =
                (Map<String, CustomKitCategory>) plugin.getData().get("customKitCategories");
        if (cats == null || cats.isEmpty()) {
            p.sendMessage("§cNo categories configured");
            return;
        }

        SGMenu catMenu = plugin.getGUI().create("&bPick a Category", 4);
        for (CustomKitCategory cat : cats.values()) {
            ItemStack logo = cat.getDisplayItem().convertToItem();
            SGButton b = new SGButton(logo)
                    .withListener(e -> openCategoryItems(p, cat, kitIndex, kitSlot));

            catMenu.setButton(cat.getCategoryGuiSlot(), b);
        }
        p.openInventory(catMenu.getInventory());
    }

    /** Opens the 6‑row item list for one category. */
    private void openCategoryItems(Player p,
                                   CustomKitCategory cat,
                                   int kitIndex,
                                   int kitSlot) {

        SGMenu itemMenu = plugin.getGUI().create("&bSelect an Item", 6);
        for (Map.Entry<Integer, SerializableItem> entry : cat.getItems().entrySet()) {
            ItemStack icon = entry.getValue().convertToItem();
            int guiSlot = entry.getKey();

            SGButton b = new SGButton(icon)
                    .withListener(e -> {
                        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
                        if (ep == null) return;
                        MainPlayer mp = ep.getMainPlayer();
                        if (mp == null) return;

                        CustomKit kit = mp.getKits()[kitIndex];
                        kit.setSlot(kitSlot, icon); // save chosen item
                        editKit(kitIndex, p);
                    });

            itemMenu.setButton(guiSlot, b);
        }
        p.openInventory(itemMenu.getInventory());
    }


    public void displayMain(Player p) {

        SGMenu menu = plugin.getGUI().create("&bEdit Your Kit", 6);

        // Create the black stained-glass panes.
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta paneItemMeta = pane.getItemMeta();
        paneItemMeta.setDisplayName("");
        pane.setItemMeta(paneItemMeta);
        for (int i = 0; i < 9; i++) {
            SGButton button = new SGButton(pane);
            button.withListener((InventoryClickEvent event) -> {
                // Do nothing.
            });
            menu.setButton(i, button);
            menu.setButton(i + 45, button);
        }

        // Populate the blocks.
        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep == null) return;

        MainPlayer mp = ep.getMainPlayer();
        if (mp == null) return;

        CustomKit[] kits = mp.getKits();

        ItemStack selectedItem = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta selectedMeta = selectedItem.getItemMeta();
        selectedMeta.setDisplayName("§aSelected Kit");
        selectedItem.setItemMeta(selectedMeta);

        ItemStack existingItem = new ItemStack(Material.RED_CONCRETE);
        ItemMeta existingMeta = existingItem.getItemMeta();
        existingMeta.setDisplayName("§7Available Kit");
        existingItem.setItemMeta(existingMeta);

        ItemStack creatableItem = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta creatableMeta = creatableItem.getItemMeta();
        creatableMeta.setDisplayName("§6Create New Kit");
        creatableItem.setItemMeta(creatableMeta);

        ItemStack lockedItem = new ItemStack(Material.BEDROCK);
        ItemMeta lockedMeta = lockedItem.getItemMeta();
        lockedMeta.setDisplayName("§8Locked Slot");
        lockedItem.setItemMeta(lockedMeta);

        int sel = getSelectedKit(p);

        for (int i = 0; i < 36; i++) {
            i++;
            if (kits[i] != null) {
                if (sel == i) {
                    SGButton button = new SGButton(selectedItem);
                    int finalI1 = i;
                    button.withListener((InventoryClickEvent event) -> {
                        editKit(finalI1, p);
                    });
                    menu.setButton(i + 8, button);
                }  else {
                    SGButton button = new SGButton(existingItem);
                    int finalI2 = i;
                    button.withListener((InventoryClickEvent event) -> {
                        setSelectedKit(p, finalI2);
                        displayMain(p);
                    });
                    menu.setButton(i + 8, button);
                }

            } else {
                SGButton button = new SGButton(creatableItem);
                int finalI = i;
                button.withListener((InventoryClickEvent event) -> {
                    createEditSelectKit(finalI, p);
                });
                menu.setButton(i + 8, button);
            }


            i--;
        }

        p.openInventory(menu.getInventory());
    }

    private void createEditSelectKit(int slot, Player p) {
        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep == null) return;

        MainPlayer mp = ep.getMainPlayer();
        if (mp == null) return;

        CustomKit[] kits = mp.getKits();
        if (kits[slot] != null) {
            editKit(slot, p);
            return;
        }

        setSelectedKit(p, slot);
        kits[slot] = new CustomKit("CUSTOM:" + slot + ":" + p.getUniqueId());

        editKit(slot, p);
        

    }

    public void editKit(int slotKit, Player p) {
        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep == null) return;

        MainPlayer mp = ep.getMainPlayer();
        if (mp == null) return;

        CustomKit[] kits = mp.getKits();
        if (kits == null || slotKit < 0 || slotKit >= kits.length) return;

        CustomKit kit = kits[slotKit];
        if (kit == null) return;

        // Mark the MP as dirty
        mp.setKit(slotKit, kit);

        // 5‑row chest GUI (45 slots)
        SGMenu menu = plugin.getGUI().create("&bEditing Kit " + slotKit, 5);

        /* -------------------------------------------------------------
         *  1) ‑‑‑‑‑‑‑‑‑‑ ARMOUR SLOTS  (menu slots 0‑3) ‑‑‑‑‑‑‑‑‑‑
         * ----------------------------------------------------------- */
        // Helm(39), Chest(38), Legs(37), Boots(36)
        String[] armorNames = { "boots", "leggings", "chestplate", "helmet" };

        for (int i = 0; i < 4; i++) {
            int kitSlot = 36 + i; // 36, 37, 38, 39
            SerializableItem ser = kit.getSlot(kitSlot);
            ItemStack icon;

            if (ser != null) {
                icon = ser.convertToItem();
            } else {
                icon = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
                ItemMeta im = icon.getItemMeta();
                im.setDisplayName("§7Click to edit (" + armorNames[i] + ")");
                icon.setItemMeta(im);
            }

            SGButton b = new SGButton(icon).withListener(e -> editSlot(kitSlot, slotKit, p));
            menu.setButton(i, b);  // slots 0–3
        }


        /* -------------------------------------------------------------
         *  2) ‑‑‑‑‑‑‑‑‑‑ OFF‑HAND BUTTON  (menu slot 4) ‑‑‑‑‑‑‑‑‑‑
         * ----------------------------------------------------------- */
        {
            int kitSlot = 40;                                    // player off‑hand
            ItemStack icon = iconForSlot(kit, kitSlot,
                    Material.LIME_STAINED_GLASS_PANE);

            SGButton off = new SGButton(icon)
                    .withListener(e -> editSlot(kitSlot, slotKit, p));

            menu.setButton(4, off);                              // 6th slot (index 5)
        }

        {

            ItemStack icon = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName("§eDirect Kit Room");
            icon.setItemMeta(meta);

            SGButton off = new SGButton(icon)
                    .withListener(e -> kitRoom(slotKit, p));

            menu.setButton(5, off);

        }

        /* -------------------------------------------------------------
         *  3) ‑‑‑‑‑‑‑‑‑‑ INVENTORY & HOTBAR GRID ‑‑‑‑‑‑‑‑‑‑
         * ----------------------------------------------------------- */

        // helper to lay out one GUI row
        BiConsumer<Integer/*row*/, Integer/*firstKitSlot*/> row = (menuRow, firstKitSlot) -> {
            for (int col = 0; col < 9; col++) {
                int kitSlot   = firstKitSlot + col;
                int menuSlot  = menuRow * 9  + col;

                Material filler = (firstKitSlot == 0)              // hotbar
                        ? Material.LIME_STAINED_GLASS_PANE
                        : Material.MAGENTA_STAINED_GLASS_PANE;

                ItemStack icon = iconForSlot(kit, kitSlot, filler);

                SGButton b = new SGButton(icon)
                        .withListener(e -> editSlot(kitSlot, slotKit, p));

                menu.setButton(menuSlot, b);
            }
        };

        // Row order (from bottom up):
        row.accept(4,  0);   // hotbar  (kit 0‑8)   → GUI row‑4  (slots 36‑44)
        row.accept(1,  9);   // inv‑row‑1 (kit 9‑17)→ GUI row‑3
        row.accept(2, 18);   // inv‑row‑2 (kit 18‑26)→ GUI row‑2
        row.accept(3, 27);   // inv‑row‑3 (kit 27‑35)→ GUI row‑1

        // LAST THING, settings button
        ItemStack comp = new ItemStack(Material.COMPARATOR);
        ItemMeta compItemMeta = comp.getItemMeta();
        compItemMeta.setDisplayName("§7Settings");
        comp.setItemMeta(compItemMeta);
        SGButton button = new SGButton(comp);
        button.withListener(e -> openSettings(slotKit, p));

        // ── Delete‑Kit button (red concrete) in GUI slot 8 ───────────────────
        {
            ItemStack del = new ItemStack(Material.RED_CONCRETE);
            ItemMeta  dm  = del.getItemMeta();
            dm.setDisplayName("§cDelete Kit");
            del.setItemMeta(dm);

            SGButton delBtn = new SGButton(del)
                    .withListener(e -> openDeleteConfirm(slotKit, p));

            menu.setButton(8, delBtn);   // index 6 = GUI slot 7 (0‑based)
        }

        // Share‑Kit button (nametag) – GUI slot 6
        {
            ItemStack tag = new ItemStack(Material.NAME_TAG);
            ItemMeta  im  = tag.getItemMeta();
            im.setDisplayName("§bShare Kit");
            tag.setItemMeta(im);


            SGButton share = new SGButton(tag).withListener(e -> {
                shareKit(p, slotKit); // save to Redis and show code
            });



            menu.setButton(6, share);   // last slot on the top row
        }

        menu.setButton(7, button);

        p.openInventory(menu.getInventory());                           // whatever your SGMenu uses
    }

    /* ================================================================
     *  KIT-ROOM  ─ lets the player build a kit quickly from scratch
     * ================================================================ */
    private void kitRoom(int kitIdx, Player p) {

        /* make sure the kit exists */
        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep == null) return;
        CustomKit kit = ep.getMainPlayer().getKits()[kitIdx];
        if (kit == null) return;

        /* five-row selector (one extra row for utility buttons) */
        SGMenu menu = plugin.getGUI().create("&bKit Room", 5);


        /* -------------------------  category icons  ------------------------- */
        Map<String, CustomKitCategory> cats =
                (Map<String, CustomKitCategory>) plugin.getData().get("customKitCategories");
        if (cats != null) {
            for (CustomKitCategory cat : cats.values()) {
                ItemStack icon = cat.getDisplayItem().convertToItem();
                menu.setButton(cat.getCategoryGuiSlot(),
                        new SGButton(icon).withListener(e ->
                                openKitRoomCategoryItems(p, kitIdx, cat)));
            }
        }

        /* -------------------------  utility bar  ---------------------------- */

        // barrier  ➜ clear inventory
        {
            ItemStack clear = new ItemStack(Material.BARRIER);
            ItemMeta cm = clear.getItemMeta();
            cm.setDisplayName("§cClear Inventory");
            clear.setItemMeta(cm);

            menu.setButton(41, new SGButton(clear).withListener(e -> {
                p.closeInventory();
                p.getInventory().clear();
                p.sendMessage("§cYour inventory has been cleared.");
                kitRoom(kitIdx, p);                 // reopen
            }));
        }
        {
            ItemStack back = new ItemStack(Material.NETHER_STAR);
            ItemMeta  bm   = back.getItemMeta();
            bm.setDisplayName("§eGO BACK");
            back.setItemMeta(bm);

            menu.setButton(39, new SGButton(back).withListener(e ->
                    editKit(kitIdx, p)));                       // back to kit editor
        }

        // lime concrete  ➜ save inventory to kit
        {
            ItemStack save = new ItemStack(Material.LIME_CONCRETE);
            ItemMeta sm = save.getItemMeta();
            sm.setDisplayName("§aSave Inventory to Kit");
            save.setItemMeta(sm);

            menu.setButton(40, new SGButton(save).withListener(e -> {
                savePlayerInventoryToKit(p, kitIdx);
                p.sendMessage("§aCurrent inventory stored in this kit.");
            }));
        }

        // sign  ➜ information only
        {
            ItemStack info = new ItemStack(Material.OAK_SIGN);
            ItemMeta im = info.getItemMeta();
            im.setDisplayName("§7What is this?");
            im.setLore(List.of(
                    "§7This menu allows you to create",
                    "§7your own custom kits faster!",
                    " ",
                    "§7When you're finished, remember",
                    "§7to save your kit."
            ));
            info.setItemMeta(im);

            menu.setButton(4, new SGButton(info));   // no click action
        }

        // composter  ➜ trash bin
        {
            ItemStack trash = new ItemStack(Material.COMPOSTER);
            ItemMeta tm = trash.getItemMeta();
            tm.setDisplayName("§7TRASH");
            trash.setItemMeta(tm);

            menu.setButton(44, new SGButton(trash).withListener(e ->
                    openTrashBin(p, kitIdx)));
        }


        p.openInventory(menu.getInventory());
    }

    /* ================================================================
     *  CATEGORY VIEW – 6×9 chest; ESC returns to Kit-Room
     * ================================================================ */
    /** 6×9 vanilla chest; ESC returns to Kit-Room. */
    private void openKitRoomCategoryItems(Player p,
                                          int kitIdx,
                                          CustomKitCategory cat) {

        // create vanilla chest (size = 6×9)
        Inventory inv = Bukkit.createInventory(
                null,
                54,
                ChatColor.translateAlternateColorCodes('&',
                        "&bKit Room &8(ESC to finish)"));

        // fill it with real ItemStacks
        cat.getItems().forEach((slot, ser) -> {
            ItemStack i = ser.convertToItem().clone();
            i.setAmount(i.getMaxStackSize());
            inv.setItem(slot, i);
        });

        // when the player closes it, reopen Kit-Room
        onCloseOnce(plugin, inv, pl -> kitRoom(kitIdx, pl));

        p.openInventory(inv);
    }





    /* ================================================================
     *  trash bin – everything placed here just disappears
     * ================================================================ */
    /** Trash bin – items placed here vanish; ESC goes back to Kit-Room. */
    private void openTrashBin(Player p, int kitIdx) {

        Inventory bin = Bukkit.createInventory(
                null, 54,
                ChatColor.translateAlternateColorCodes('&',
                        "&8Trash Bin &7(ESC or click FINISH)"));

        ItemStack fin = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta fm = fin.getItemMeta();
        fm.setDisplayName("§aFINISH");
        fin.setItemMeta(fm);
        bin.setItem(49, fin);

        /* ---- click listener just for this bin ---- */
        Listener clickLock = new Listener() {
            @EventHandler
            public void onClick(InventoryClickEvent e) {
                if (!e.getInventory().equals(bin)) return;

                // lock the FINISH concrete
                if (e.getSlot() == 49) {
                    e.setCancelled(true);
                    ((Player) e.getWhoClicked()).closeInventory();  // triggers onCloseOnce
                }
            }
        };
        Bukkit.getPluginManager().registerEvents(clickLock, plugin);

        // unregister the click listener when the bin closes
        onCloseOnce(plugin, bin, pl -> {
            pl.setItemOnCursor(null);             // wipe cursor (optional)
            bin.clear();                          // everything inside vanishes
            HandlerList.unregisterAll(clickLock); // clean up
            kitRoom(kitIdx, pl);                  // reopen kit-room
        });

        p.openInventory(bin);
    }




    /** Copies the player’s current inventory into the kit, turning AIR into null. */
    private void savePlayerInventoryToKit(Player p, int kitIdx) {

        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep == null) return;

        CustomKit kit = ep.getMainPlayer().getKits()[kitIdx];
        if (kit == null) return;

        /* hot-bar & main storage (0-35) */
        for (int i = 0; i < 36; i++) {
            kit.setSlot(i, cloneOrNullAir(p.getInventory().getItem(i)));
        }

        /* armour & off-hand */
        kit.setSlot(36, cloneOrNullAir(p.getInventory().getBoots()));
        kit.setSlot(37, cloneOrNullAir(p.getInventory().getLeggings()));
        kit.setSlot(38, cloneOrNullAir(p.getInventory().getChestplate()));
        kit.setSlot(39, cloneOrNullAir(p.getInventory().getHelmet()));
        kit.setSlot(40, cloneOrNullAir(p.getInventory().getItemInOffHand())); // AIR ⇒ null
    }


    private ItemStack cloneOrNull(ItemStack it) {
        return it == null ? null : it.clone();
    }


    /** Opens a 1‑row confirm/delete GUI for a kit. */
    private void openDeleteConfirm(int slotKit, Player p) {

        SGMenu confirm = plugin.getGUI().create("§cDelete Kit?", 1);

        ItemStack yes = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta ym = yes.getItemMeta();
        ym.setDisplayName("§aYes, delete");
        yes.setItemMeta(ym);

        SGButton yesBtn = new SGButton(yes).withListener(e -> {
            ExtendedPlayer ep = plugin.getExtendedPlayer(p);
            if (ep == null) return;
            CustomKit[] kits = ep.getMainPlayer().getKits();
            if (kits == null) return;

            kits[slotKit] = null;                        // remove kit
            ep.getMainPlayer().setKit(slotKit, null);    // mark dirty
            p.sendMessage("§cKit " + slotKit + " deleted.");
            displayMain(p);                              // back to main GUI
        });
        confirm.setButton(2, yesBtn);

        // NO    – slot 7 (index 6)
        ItemStack no = new ItemStack(Material.RED_CONCRETE);
        ItemMeta nm = no.getItemMeta();
        nm.setDisplayName("§cNo, go back");
        no.setItemMeta(nm);

        SGButton noBtn = new SGButton(no)
                .withListener(e -> editKit(slotKit, p));
        confirm.setButton(5, noBtn);

        p.openInventory(confirm.getInventory());
    }


    /** Saves the kit in Redis (async) and sends the player the share‑code (sync). */
    private void shareKit(Player p, int slotKit) {

        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep == null) return;
        MainPlayer mp = ep.getMainPlayer();
        if (mp == null) return;

        CustomKit kit = mp.getKits()[slotKit];
        if (kit == null || kit.getInventory().isEmpty()) {
            p.sendMessage("§cThat kit is empty.");
            return;
        }

        final int TTL = 30 * 24 * 60 * 60; // 30 days
        final String keyCode = "sharedkits:getcode:" + p.getUniqueId() + ":" + slotKit;

        /* ---------------------------------------------------------------
         *  1)  Redis work ⇒ async
         * ------------------------------------------------------------- */
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {

            String[] outCode = new String[1]; // pass-by-ref wrapper

            StatManagement.getRedis().withJedis(jedis -> {

                String code = jedis.get(keyCode);
                if (code == null) {
                    // Loop until truly unique (extremely rare collision)
                    do {
                        code = java.util.UUID.randomUUID()
                                .toString().substring(0, 8).toUpperCase();
                    } while (jedis.exists("sharedkits:getkit:" + code));
                }

                // Save / refresh both keys
                jedis.setex(keyCode,            TTL, code);
                jedis.setex("sharedkits:getkit:" + code, TTL, kit.toJson());

                outCode[0] = code;              // capture for sync part
                return null;
            });

            /* -----------------------------------------------------------
             *  2)  Back to main thread for Bukkit API
             * --------------------------------------------------------- */
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (outCode[0] == null) {
                    p.sendMessage("§cFailed to share kit, try again.");
                } else {
                    p.sendMessage("§aYour kit has been shared. Use command §e/copykit " +
                            outCode[0] + " §ato copy it.");
                    p.closeInventory();
                }
            });
        });
    }



    private void openSettings(int slotKit, Player p) {

        ExtendedPlayer extendedPlayer = plugin.getExtendedPlayer(p);
        if (extendedPlayer == null) return;

        CustomKit[] kits = extendedPlayer.getMainPlayer().getKits();
        CustomKit kit = kits[slotKit];
        if (kit == null) return;

        /* ─────────────────────────────────────────────
         * 2 ROW SETTINGS GUI
         * ─────────────────────────────────────────── */
        SGMenu menu = plugin.getGUI().create("§bSettings", 2);

        /* =========================================================
         * ROW 1
         * ======================================================= */

        /* Health Indicators */
        menu.setButton(0, new SGButton(
                settingCreator(
                        new ItemStack(Material.RED_DYE),
                        "§dHealth Indicators",
                        kit.isHealthIndics()
                )
        ).withListener(e -> {
            kit.setHealthIndics(!kit.isHealthIndics());
            openSettings(slotKit, p);
        }));

        /* Mending RNG */
        {
            ItemStack xp = new ItemStack(Material.EXPERIENCE_BOTTLE);
            ItemMeta im = xp.getItemMeta();

            int value = kit.getMetaData().getInt("mending_rng");

            im.setDisplayName("§dMending RNG");
            im.setLore(List.of(
                    "§7Current: §f" + value,
                    " ",
                    "§7Type a number in chat.",
                    "§7Type 0 to disable."
            ));
            xp.setItemMeta(im);

            menu.setButton(9, new SGButton(xp).withListener(e -> {
                p.closeInventory();
                p.sendMessage("§bEnter Mending RNG value. §7(Type 0 to disable)");
                PENDING_MENDING.put(p.getUniqueId(), slotKit);
            }));
        }

        /* Hunger */
        menu.setButton(1, new SGButton(
                settingCreator(
                        new ItemStack(Material.COOKED_BEEF),
                        "§dHunger",
                        kit.isLoseHunger()
                )
        ).withListener(e -> {
            kit.setLoseHunger(!kit.isLoseHunger());
            openSettings(slotKit, p);
        }));

        /* TNT Auto Ignition */
        {
            boolean enabled;
            if (kit.getMetaData()
                    .getBoolean("tnt_ignite") != null)
                enabled = (boolean)kit.getMetaData().getBoolean("tnt_ignite");
            else {
                enabled = true;
            }

            menu.setButton(10, new SGButton(
                    settingCreator(
                            new ItemStack(Material.TNT),
                            "§dTNT Auto Ignition",
                            enabled
                    )
            ).withListener(e -> {
                kit.getMetaData().set("tnt_ignite", !enabled);
                openSettings(slotKit, p);
            }));
        }

        /* Saturation */
        menu.setButton(2, new SGButton(
                settingCreator(
                        new ItemStack(Material.GOLDEN_APPLE),
                        "§dSaturation",
                        kit.isNaturalRegen()
                )
        ).withListener(e -> {
            kit.setNaturalRegen(!kit.isNaturalRegen());
            openSettings(slotKit, p);
        }));

        /* Damage Increase % */
        {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            ItemMeta im = sword.getItemMeta();

            int value = kit.getMetaData().getInt("dmg_inc_%");

            im.setDisplayName("§dDamage Increase %");
            im.setLore(List.of(
                    "§7Current: §f" + value + "%",
                    " ",
                    "§7Type a number in chat."
            ));
            sword.setItemMeta(im);

            menu.setButton(11, new SGButton(sword).withListener(e -> {
                p.closeInventory();
                p.sendMessage("§bEnter Damage Increase %.");
                PENDING_DAMAGE.put(p.getUniqueId(), slotKit);
            }));
        }

        /* Building */
        menu.setButton(3, new SGButton(
                settingCreator(
                        new ItemStack(Material.GRASS_BLOCK),
                        "§dBlock Modification",
                        kit.isBuild()
                )
        ).withListener(e -> {
            kit.setBuild(!kit.isBuild());
            openSettings(slotKit, p);
        }));

        /* Ender Chest */
        menu.setButton(4, new SGButton(
                settingCreator(
                        new ItemStack(Material.ENDER_CHEST),
                        "§dEnder Chest",
                        true
                )
        ).withListener(e -> openEnderChestEditor(p, slotKit)));

        /* Effects */
        menu.setButton(5, new SGButton(
                settingCreator(
                        new ItemStack(Material.POTION),
                        "§dEffects",
                        true
                )
        ).withListener(e -> openEffectsMenu(p, slotKit)));

        /* DONE — LAST SLOT */
        ItemStack doneItem = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta dIM = doneItem.getItemMeta();
        dIM.setDisplayName("§aDONE");
        doneItem.setItemMeta(dIM);

        menu.setButton(17, new SGButton(doneItem)
                .withListener(e -> editKit(slotKit, p)));

        p.openInventory(menu.getInventory());
    }


    private void openEffectsMenu(Player p, int kitIdx) {
        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep == null) return;

        CustomKit kit = ep.getMainPlayer().getKits()[kitIdx];
        if (kit == null) return;

        SGMenu menu = plugin.getGUI().create("§bEffects", 3);

        // info in middle
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        int count = kit.getEffects().size();
        im.setDisplayName("§bCurrent Effects: §f" + count);
        info.setItemMeta(im);
        menu.setButton(13, new SGButton(info));

        // create
        ItemStack create = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta cm = create.getItemMeta();
        cm.setDisplayName("§aCreate New Effect");
        create.setItemMeta(cm);
        menu.setButton(11, new SGButton(create).withListener(e -> openEffectPicker(p, kitIdx, 0)));

        // list
        ItemStack list = new ItemStack(Material.PAPER);
        ItemMeta lm = list.getItemMeta();
        lm.setDisplayName("§eList Effects (click to delete)");
        list.setItemMeta(lm);
        menu.setButton(15, new SGButton(list).withListener(e -> openEffectList(p, kitIdx, 0)));

        // back
        ItemStack back = new ItemStack(Material.NETHER_STAR);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7Back");
        back.setItemMeta(bm);
        menu.setButton(26, new SGButton(back).withListener(e -> openSettings(kitIdx, p)));

        p.openInventory(menu.getInventory());
    }

    private void openEffectPicker(Player p, int kitIdx, int page) {

        List<PotionEffectType> all = new ArrayList<>();
        for (PotionEffectType t : PotionEffectType.values()) {
            if (t == null) continue;
            String n = t.getName();
            if (n == null || n.isEmpty()) continue;
            all.add(t);
        }
        all.sort(Comparator.comparing(a -> a.getName().toLowerCase()));

        int pageSize = 45;
        int maxPage = Math.max(0, (all.size() - 1) / pageSize);
        int safePage = Math.max(0, Math.min(page, maxPage));

        SGMenu menu = plugin.getGUI().create("§bPick Effect §7(" + (safePage + 1) + "/" + (maxPage + 1) + ")", 6);

        int start = safePage * pageSize;
        int end = Math.min(all.size(), start + pageSize);

        int slot = 0;
        for (int i = start; i < end; i++) {
            PotionEffectType type = all.get(i);

            ItemStack icon = new ItemStack(Material.POTION);
            ItemMeta im = icon.getItemMeta();
            String key = type.getName().toLowerCase();
            im.setDisplayName("§b" + key);
            im.setLore(List.of(
                    "§7Click, then type in chat",
                    "§flevel:seconds §7(example §f1:120§7)",
                    "§7Level 1 means Potion I"
            ));
            icon.setItemMeta(im);

            final String chosenKey = key;
            menu.setButton(slot++, new SGButton(icon).withListener(e -> {
                p.closeInventory();
                p.sendMessage("§bType §flevel:seconds§b, example §f1:120");
                EFFECT_CREATE.put(p.getUniqueId(), new PendingEffectCreate(kitIdx, chosenKey));
            }));
        }

        // prev
        ItemStack prev = new ItemStack(Material.ARROW);
        ItemMeta pm = prev.getItemMeta();
        pm.setDisplayName("§7Previous");
        prev.setItemMeta(pm);
        menu.setButton(45, new SGButton(prev).withListener(e -> openEffectPicker(p, kitIdx, safePage - 1)));

        // back
        ItemStack back = new ItemStack(Material.NETHER_STAR);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7Back");
        back.setItemMeta(bm);
        menu.setButton(49, new SGButton(back).withListener(e -> openEffectsMenu(p, kitIdx)));

        // next
        ItemStack next = new ItemStack(Material.ARROW);
        ItemMeta nm = next.getItemMeta();
        nm.setDisplayName("§7Next");
        next.setItemMeta(nm);
        menu.setButton(53, new SGButton(next).withListener(e -> openEffectPicker(p, kitIdx, safePage + 1)));

        p.openInventory(menu.getInventory());
    }

    private void openEffectList(Player p, int kitIdx, int page) {
        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep == null) return;

        CustomKit kit = ep.getMainPlayer().getKits()[kitIdx];
        if (kit == null) return;

        List<EffectMeta> effects = kit.getEffects();

        int pageSize = 45;
        int maxPage = Math.max(0, (effects.size() - 1) / pageSize);
        int safePage = Math.max(0, Math.min(page, maxPage));

        SGMenu menu = plugin.getGUI().create("§eEffects List §7(" + (safePage + 1) + "/" + (maxPage + 1) + ")", 6);

        int start = safePage * pageSize;
        int end = Math.min(effects.size(), start + pageSize);

        int slot = 0;
        for (int idx = start; idx < end; idx++) {
            EffectMeta em = effects.get(idx);

            ItemStack icon = new ItemStack(Material.POTION);
            ItemMeta im = icon.getItemMeta();
            im.setDisplayName("§b" + em.getKey());
            im.setLore(List.of(
                    "§7Level: §f" + em.getLevel(),
                    "§7Seconds: §f" + em.getSeconds(),
                    " ",
                    "§cClick to delete"
            ));
            icon.setItemMeta(im);

            final int removeIndex = idx;
            menu.setButton(slot++, new SGButton(icon).withListener(e -> {
                kit.removeEffect(removeIndex);
                p.sendMessage("§cEffect removed.");
                openEffectList(p, kitIdx, safePage);
            }));
        }

        // clear all
        ItemStack clear = new ItemStack(Material.ANVIL);
        ItemMeta cm = clear.getItemMeta();
        cm.setDisplayName("§cClear All Effects");
        clear.setItemMeta(cm);
        menu.setButton(52, new SGButton(clear).withListener(e -> {
            kit.clearEffects();
            p.sendMessage("§cAll effects cleared.");
            openEffectList(p, kitIdx, 0);
        }));

        // back
        ItemStack back = new ItemStack(Material.NETHER_STAR);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7Back");
        back.setItemMeta(bm);
        menu.setButton(49, new SGButton(back).withListener(e -> openEffectsMenu(p, kitIdx)));

        // prev, next
        ItemStack prev = new ItemStack(Material.ARROW);
        ItemMeta pm = prev.getItemMeta();
        pm.setDisplayName("§7Previous");
        prev.setItemMeta(pm);
        menu.setButton(45, new SGButton(prev).withListener(e -> openEffectList(p, kitIdx, safePage - 1)));

        ItemStack next = new ItemStack(Material.ARROW);
        ItemMeta nm = next.getItemMeta();
        nm.setDisplayName("§7Next");
        next.setItemMeta(nm);
        menu.setButton(53, new SGButton(next).withListener(e -> openEffectList(p, kitIdx, safePage + 1)));

        p.openInventory(menu.getInventory());
    }



    private ItemStack settingCreator(ItemStack item, String name, boolean enabled) {
        ItemMeta itemMeta = item.getItemMeta();

        itemMeta.setDisplayName(name);
        String b = enabled ? "§aON" : "§cOFF";
        itemMeta.setLore(List.of(new String[]{"§r§7This is " + b + "§7."}));
        item.setItemMeta(itemMeta);
        return item;
    }

    /* -------------------------------------------------------------------
     *  Utility: build a placeholder or the actual kit item
     * ----------------------------------------------------------------- */
    private ItemStack iconForSlot(CustomKit kit, int kitSlot, Material fillerMat) {
        SerializableItem ser = kit.getSlot(kitSlot);
        if (ser != null) return ser.convertToItem();

        ItemStack pane = new ItemStack(fillerMat);
        ItemMeta  im   = pane.getItemMeta();
        im.setDisplayName("§7Click to edit");
        pane.setItemMeta(im);
        return pane;
    }



    protected int getSelectedKit(Player p) {
        HashMap<UUID, Integer> selectedKit = plugin.getExtendedPlayer(p).getSelectedKit();
        Integer i = selectedKit.get(p.getUniqueId());
        if (i == null) {
            selectedKit.put(p.getUniqueId(), 1);
            return 1;
        }
        return i;
    }

    protected void setSelectedKit(Player p, int slot) {
        HashMap<UUID, Integer> selectedKit = plugin.getExtendedPlayer(p).getSelectedKit();
        selectedKit.put(p.getUniqueId(), slot);
    }

    /* ── tiny helpers ──────────────────────────────────────────────── */
    private static boolean isAir(ItemStack it) {
        return it == null || it.getType() == Material.AIR;
    }

    private static ItemStack cloneOrNullAir(ItemStack it) {
        return isAir(it) ? null : it.clone();
    }

    /**
     * Gives the player normal-chest behaviour inside this SGMenu:
     *  • grabs, drags, shift-moves, hot-key swaps, etc.
     *  • no actions are auto-cancelled by SpiGUI.
     *
     * Works on SpiGUI 1.4.1 (which has no setCanDragItems / getBlockedClickTypes).
     */
    private void unlockEverything(SGMenu menu) {

        /* 1)  Don’t let SpiGUI auto-cancel standard InventoryClickEvent’s */
        menu.setBlockDefaultInteractions(false);

        /* 2)  Remove every InventoryAction SpiGUI added to its block-lists */
        for (InventoryAction act : InventoryAction.values()) {
            menu.removeBlockedMenuAction(act);        // actions inside this menu
            menu.removeBlockedAdjacentAction(act);    // actions involving bottom inv
        }

    /* 3)  Some SpiGUI builds add extra click-type guards; they’re
           only present when “block default” was TRUE, so after (1)
           they are usually empty.  If you later upgrade to a build
           that exposes them you can safely clear them here. */
    }

    public static boolean handleChatMending(Player p, String msg, CustomKitGUIMain inst) {

        Integer kitIdx = PENDING_MENDING.remove(p.getUniqueId());
        if (kitIdx == null) return false;

        int value;
        try {
            value = Integer.parseInt(msg);
        } catch (Exception e) {
            p.sendMessage("§cNot a number.");
            return true;
        }

        if (value < 0) {
            p.sendMessage("§cMust be ≥ 0.");
            return true;
        }

        CustomKit kit = inst.plugin
                .getExtendedPlayer(p)
                .getMainPlayer()
                .getKits()[kitIdx];

        kit.getMetaData().set("mending_rng", value);

        if (value == 0) {
            p.sendMessage("§cMending RNG disabled.");
        } else {
            p.sendMessage("§aMending RNG set to §f" + value);
        }

        inst.openSettings(kitIdx, p);
        return true;
    }

    public static boolean handleChatDamage(Player p, String msg, CustomKitGUIMain inst) {

        Integer kitIdx = PENDING_DAMAGE.remove(p.getUniqueId());
        if (kitIdx == null) return false;

        int value;
        try {
            value = Integer.parseInt(msg);
        } catch (Exception e) {
            p.sendMessage("§cNot a number.");
            return true;
        }

        if (value < 0) {
            p.sendMessage("§cMust be ≥ 0.");
            return true;
        }

        CustomKit kit = inst.plugin
                .getExtendedPlayer(p)
                .getMainPlayer()
                .getKits()[kitIdx];

        kit.getMetaData().set("dmg_inc_%", value);

        p.sendMessage("§aDamage Increase set to §f" + value + "%");

        inst.openSettings(kitIdx, p);
        return true;
    }



    /**
     * Registers a temporary InventoryClose listener that fires once for this
     * inventory, then unregisters itself.
     */
    private void onCloseOnce(Core plugin,
                             Inventory inv,
                             Consumer<Player> action) {

        Listener l = new Listener() {
            @EventHandler
            public void handle(InventoryCloseEvent e) {
                if (!e.getInventory().equals(inv)) return;

                // run next tick → avoids recursive close/open
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try { action.accept((Player) e.getPlayer()); }
                    finally { HandlerList.unregisterAll(this); }
                });
            }
        };
        Bukkit.getPluginManager().registerEvents(l, plugin);
    }

}
