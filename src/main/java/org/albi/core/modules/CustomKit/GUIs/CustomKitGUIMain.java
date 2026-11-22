package org.albi.core.modules.CustomKit.GUIs;

import org.albi.core.gui.Messages;

import org.albi.core.gui.GuiService;

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
import org.bukkit.event.inventory.InventoryDragEvent;
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
        SGMenu menu   = plugin.getMenus().create("custom-kits.ender-chest", 4);

        /* 0-26  — storage grid */
        ItemStack filler = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta(); fm.setDisplayName(GuiService.text("custom-kit-guimain.click-to-edit")); filler.setItemMeta(fm);

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
        ItemMeta dm = done.getItemMeta(); dm.setDisplayName(GuiService.text("custom-kit-guimain.finish")); done.setItemMeta(dm);
        GuiService.control(menu, "done", 31, new SGButton(done).withListener(e -> openSettings(kitIdx, p)));

        GuiService.open(p, menu);
    }

    private void openItemEditorForEnder(Player p, int kitIdx, int ecSlot) {

        CustomKit kit = plugin.getExtendedPlayer(p).getMainPlayer().getKits()[kitIdx];
        ItemStack working = kit.getEnderSlot(ecSlot).convertToItem();

        SGMenu menu = plugin.getMenus().create("custom-kits.ender-item", 2);

        /* delete */
        ItemStack bar = new ItemStack(Material.BARRIER);
        ItemMeta bm = bar.getItemMeta(); bm.setDisplayName(GuiService.text("custom-kit-guimain.delete")); bar.setItemMeta(bm);
        GuiService.control(menu, "bar", 10, new SGButton(bar).withListener(e -> {
            kit.setEnderSlot(ecSlot, null);
            openEnderChestEditor(p, kitIdx);
        }));

        /* rename */
        ItemStack sign = new ItemStack(Material.OAK_SIGN);
        ItemMeta sm = sign.getItemMeta(); sm.setDisplayName(GuiService.text("custom-kit-guimain.rename")); sign.setItemMeta(sm);
        GuiService.control(menu, "sign", 12, new SGButton(sign).withListener(e -> {
            p.closeInventory();
            Messages.send(p, "messages.custom-kit-guimain.type-the-new-name-in-chat");
            RENAME_EC.put(p.getUniqueId(), new PendingRenameEc(kitIdx, ecSlot));
        }));

        /* enchant */
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta em = book.getItemMeta(); em.setDisplayName(GuiService.text("custom-kit-guimain.add-change-enchant")); book.setItemMeta(em);
        GuiService.control(menu, "book", 13, new SGButton(book).withListener(
                e -> openEnchantmentPickerForEnder(p, kitIdx, ecSlot)));

        /* stack size */
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta pm = paper.getItemMeta(); pm.setDisplayName(GuiService.text("custom-kit-guimain.set-stack-size")); paper.setItemMeta(pm);
        GuiService.control(menu, "paper", 14, new SGButton(paper).withListener(e -> {
            p.closeInventory();
            Messages.send(p, "messages.custom-kit-guimain.enter-a-number-1-64-in-chat");
            STACK_EC.put(p.getUniqueId(), new PendingIntegerEc(kitIdx, ecSlot));
        }));

        /* finish */
        ItemStack done = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta dm = done.getItemMeta(); dm.setDisplayName(GuiService.text("custom-kit-guimain.finish")); done.setItemMeta(dm);
        GuiService.control(menu, "done", 16, new SGButton(done).withListener(e -> openEnderChestEditor(p, kitIdx)));

        /* preview */
        GuiService.control(menu, "working", 4, new SGButton(working));

        GuiService.open(p, menu);
    }
    private void openEnchantmentPickerForEnder(Player p, int kitIdx, int ecSlot) {

        CustomKit kit = plugin.getExtendedPlayer(p).getMainPlayer().getKits()[kitIdx];
        ItemStack target = kit.getEnderSlot(ecSlot).convertToItem();
        ItemMeta targetMeta = target.getItemMeta();

        SGMenu enchMenu = plugin.getMenus().create("custom-kits.ender-enchantments", 6);
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
                Messages.send(p, "messages.custom-kit-guimain.type-the-level-1-in-chat");
                ENCHANT_EC.put(p.getUniqueId(), new PendingEnchantEc(kitIdx, ecSlot, ench));
            }));
        }

        /* clear all */
        ItemStack anvil = new ItemStack(Material.ANVIL);
        ItemMeta am = anvil.getItemMeta(); am.setDisplayName(GuiService.text("custom-kit-guimain.clear-all-enchants")); anvil.setItemMeta(am);
        GuiService.control(enchMenu, "anvil", 52, new SGButton(anvil).withListener(e -> {
            ItemMeta meta = target.getItemMeta();
            meta.getEnchants().keySet().forEach(meta::removeEnchant);
            target.setItemMeta(meta);
            kit.setEnderSlot(ecSlot, target);
            Messages.send(p, "messages.custom-kit-guimain.all-enchantments-cleared");
            openItemEditorForEnder(p, kitIdx, ecSlot);
        }));

        /* finish */
        ItemStack done = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta dm = done.getItemMeta(); dm.setDisplayName(GuiService.text("custom-kit-guimain.finish")); done.setItemMeta(dm);
        GuiService.control(enchMenu, "done", 53, new SGButton(done).withListener(e -> openItemEditorForEnder(p, kitIdx, ecSlot)));

        GuiService.open(p, enchMenu);
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
        if (cats == null || cats.isEmpty()) { Messages.send(p, "messages.custom-kit-guimain.no-categories-configured"); return; }

        SGMenu catMenu = plugin.getMenus().create("custom-kits.ender-categories", 4);

        for (CustomKitCategory cat : cats.values()) {
            ItemStack logo = cat.getDisplayItem().convertToItem();
            catMenu.setButton(cat.getCategoryGuiSlot(),
                    new SGButton(logo).withListener(e ->
                            openCategoryItemsForEnder(p, cat, kitIdx, ecSlot)));
        }
        GuiService.open(p, catMenu);
    }

    private void openCategoryItemsForEnder(Player p,
                                           CustomKitCategory cat,
                                           int kitIdx,
                                           int ecSlot) {

        SGMenu itemMenu = plugin.getMenus().create("custom-kits.ender-items", 6);

        for (Map.Entry<Integer, SerializableItem> entry : cat.getItems().entrySet()) {
            ItemStack icon = entry.getValue().convertToItem();
            int guiSlot = entry.getKey();

            itemMenu.setButton(guiSlot, new SGButton(icon).withListener(e -> {
                if (icon.getType().name().endsWith("SHULKER_BOX")) {
                    Messages.send(p, "messages.custom-kit-guimain.you-can-t-place-a-shulker-box-inside");
                    return;
                }
                CustomKit kit = plugin.getExtendedPlayer(p).getMainPlayer().getKits()[kitIdx];
                kit.setEnderSlot(ecSlot, icon.clone());
                openEnderChestEditor(p, kitIdx);
            }));
        }
        GuiService.open(p, itemMenu);
    }
    public static boolean handleChatRenameEc(Player p, String msg, CustomKitGUIMain inst) {
        PendingRenameEc pr = RENAME_EC.remove(p.getUniqueId());
        if (pr == null) return false;

        if (msg.length() > 20) { Messages.send(p, "messages.custom-kit-guimain.name-too-long"); return true; }

        CustomKit kit = inst.plugin.getExtendedPlayer(p).getMainPlayer().getKits()[pr.kitIdx()];
        ItemStack it  = kit.getEnderSlot(pr.ecSlot()).convertToItem();
        ItemMeta  im  = it.getItemMeta();
        im.setDisplayName(ChatColor.translateAlternateColorCodes('&', msg));
        it.setItemMeta(im);
        kit.setEnderSlot(pr.ecSlot(), it);

        Messages.send(p, "messages.custom-kit-guimain.name-set");
        inst.openEnderChestEditor(p, pr.kitIdx());
        return true;
    }

    public static boolean handleChatStackEc(Player p, String msg, CustomKitGUIMain inst) {
        PendingIntegerEc pi = STACK_EC.remove(p.getUniqueId());
        if (pi == null) return false;

        int amt;
        try { amt = Integer.parseInt(msg); }
        catch (NumberFormatException ex) { Messages.send(p, "messages.custom-kit-guimain.not-a-number"); return true; }
        if (amt < 1 || amt > 64) { Messages.send(p, "messages.custom-kit-guimain.number-must-be-1-64"); return true; }

        CustomKit kit = inst.plugin.getExtendedPlayer(p).getMainPlayer().getKits()[pi.kitIdx()];
        ItemStack it  = kit.getEnderSlot(pi.ecSlot()).convertToItem();
        it.setAmount(Math.min(amt, it.getMaxStackSize()));
        kit.setEnderSlot(pi.ecSlot(), it);

        Messages.send(p, "messages.custom-kit-guimain.stack-size-set");
        inst.openEnderChestEditor(p, pi.kitIdx());
        return true;
    }

    public static boolean handleChatEffectCreate(Player p, String msg, CustomKitGUIMain inst) {
        PendingEffectCreate pe = EFFECT_CREATE.remove(p.getUniqueId());
        if (pe == null) return false;

        String in = msg.trim();
        if (!in.contains(":")) {
            Messages.send(p, "messages.custom-kit-guimain.invalid-format-use-level-seconds-example-1-120");
            inst.openEffectPicker(p, pe.kitIdx(), 0);
            return true;
        }

        String[] parts = in.split(":");
        if (parts.length != 2) {
            Messages.send(p, "messages.custom-kit-guimain.invalid-format-use-level-seconds-example-1-120");
            inst.openEffectPicker(p, pe.kitIdx(), 0);
            return true;
        }

        int level;
        int seconds;
        try {
            level = Integer.parseInt(parts[0].trim());
            seconds = Integer.parseInt(parts[1].trim());
        } catch (Exception e) {
            Messages.send(p, "messages.custom-kit-guimain.invalid-numbers-use-level-seconds-example-1-120");
            inst.openEffectPicker(p, pe.kitIdx(), 0);
            return true;
        }

        if (level < 1 || level > 10) {
            Messages.send(p, "messages.custom-kit-guimain.level-must-be-1-10");
            inst.openEffectPicker(p, pe.kitIdx(), 0);
            return true;
        }
        if (seconds < 1 || seconds > 3600) {
            Messages.send(p, "messages.custom-kit-guimain.seconds-must-be-1-3600");
            inst.openEffectPicker(p, pe.kitIdx(), 0);
            return true;
        }

        ExtendedPlayer ep = inst.plugin.getExtendedPlayer(p);
        if (ep == null) return true;

        CustomKit kit = ep.getMainPlayer().getKits()[pe.kitIdx()];
        if (kit == null) return true;

        kit.addEffect(new EffectMeta(pe.effectKey(), level, seconds));
        Messages.send(p, "messages.custom-kit-guimain.effect-added-level-s", pe.effectKey(), level, seconds);

        inst.openEffectsMenu(p, pe.kitIdx());
        return true;
    }


    public static boolean handleChatEnchantEc(Player p, String msg, CustomKitGUIMain inst) {
        PendingEnchantEc pe = ENCHANT_EC.remove(p.getUniqueId());
        if (pe == null) return false;

        int lvl;
        try { lvl = Integer.parseInt(msg); }
        catch (NumberFormatException ex) { Messages.send(p, "messages.custom-kit-guimain.not-a-number"); return true; }
        if (lvl < 1 || lvl > 10) { Messages.send(p, "messages.custom-kit-guimain.level-must-be-1-10"); return true; }

        CustomKit kit = inst.plugin.getExtendedPlayer(p).getMainPlayer().getKits()[pe.kitIdx()];
        ItemStack it  = kit.getEnderSlot(pe.ecSlot()).convertToItem();
        ItemMeta  im  = it.getItemMeta();
        im.addEnchant(pe.ench(), Math.min(lvl, pe.ench().getMaxLevel()), true);
        it.setItemMeta(im);
        kit.setEnderSlot(pe.ecSlot(), it);

        Messages.send(p, "messages.custom-kit-guimain.enchantment-set");
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

        SGMenu menu = plugin.getMenus().create("custom-kits.shulker-ender-categories", 4);

        /* slot 10 – delete */
        ItemStack bar = new ItemStack(Material.BARRIER);
        ItemMeta  bm  = bar.getItemMeta(); bm.setDisplayName(GuiService.text("custom-kit-guimain.delete")); bar.setItemMeta(bm);
        GuiService.control(menu, "bar", 10, new SGButton(bar).withListener(e -> {
            inv.clear(boxSlot);
            saveShulkerBack(kit, shulkerSlot, sh, sb, bsm, inv);
            openShulkerEditor(p, kitIdx, shulkerSlot);
        }));

        /* slot 12 – rename */
        ItemStack sign = new ItemStack(Material.OAK_SIGN);
        ItemMeta  sm   = sign.getItemMeta(); sm.setDisplayName(GuiService.text("custom-kit-guimain.rename")); sign.setItemMeta(sm);
        GuiService.control(menu, "sign", 12, new SGButton(sign).withListener(e -> {
            p.closeInventory();
            Messages.send(p, "messages.custom-kit-guimain.type-the-new-name-in-chat");
            RENAME_BOX.put(p.getUniqueId(), new PendingRenameBox(kitIdx, shulkerSlot, boxSlot));
        }));

        /* slot 13 – enchant */
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta  em   = book.getItemMeta(); em.setDisplayName(GuiService.text("custom-kit-guimain.add-change-enchant")); book.setItemMeta(em);
        GuiService.control(menu, "book", 13, new SGButton(book).withListener(e ->
                openEnchantmentPickerForBox(p, kitIdx, shulkerSlot, boxSlot)));

        /* slot 14 – stack size */
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta  pm    = paper.getItemMeta(); pm.setDisplayName(GuiService.text("custom-kit-guimain.set-stack-size")); paper.setItemMeta(pm);
        GuiService.control(menu, "paper", 14, new SGButton(paper).withListener(e -> {
            p.closeInventory();
            Messages.send(p, "messages.custom-kit-guimain.enter-a-number-1-64-in-chat-2");
            STACK_BOX.put(p.getUniqueId(), new PendingIntegerBox(kitIdx, shulkerSlot, boxSlot));
        }));

        /* slot 16 – finish */
        ItemStack done = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta  dm   = done.getItemMeta(); dm.setDisplayName(GuiService.text("custom-kit-guimain.finish")); done.setItemMeta(dm);
        GuiService.control(menu, "done", 16, new SGButton(done).withListener(e -> openShulkerEditor(p, kitIdx, shulkerSlot)));

        /* preview item in slot 4 */
        GuiService.control(menu, "working", 4, new SGButton(working));

        GuiService.open(p, menu);
    }


    private void openEnchantmentPickerForBox(Player p,
                                             int kitIdx, int shulkerSlot, int boxSlot) {

        SGMenu enchMenu = plugin.getMenus().create("custom-kits.shulker-ender-enchantments", 6);
        int gui = 0;

        CustomKit kit = plugin.getExtendedPlayer(p).getMainPlayer().getKits()[kitIdx];
        ItemStack sh = kit.getSlot(shulkerSlot).convertToItem();

        BlockStateMeta bsm = (BlockStateMeta) sh.getItemMeta();
        ShulkerBox sb = (ShulkerBox) bsm.getBlockState();
        Inventory inv = sb.getInventory();
        ItemStack target = inv.getItem(boxSlot);
        if (target == null) {
            Messages.send(p, "messages.custom-kit-guimain.no-item-in-that-slot");
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
                Messages.send(p, "messages.custom-kit-guimain.type-the-level-1-in-chat");
                ENCHANT_BOX.put(p.getUniqueId(),
                        new PendingEnchantBox(kitIdx, shulkerSlot, boxSlot, ench));
            }));
        }

        // slot 52 – Clear all enchants
        {
            ItemStack anvil = new ItemStack(Material.ANVIL);
            ItemMeta im = anvil.getItemMeta();
            im.setDisplayName(GuiService.text("custom-kit-guimain.clear-all-enchants"));
            anvil.setItemMeta(im);

            GuiService.control(enchMenu, "anvil", 52, new SGButton(anvil).withListener(e -> {
                ItemStack item = inv.getItem(boxSlot);
                if (item == null) return;

                ItemMeta meta = item.getItemMeta();
                if (meta == null) return;

                meta.getEnchants().keySet().forEach(meta::removeEnchant);
                item.setItemMeta(meta);
                inv.setItem(boxSlot, item);

                saveShulkerBack(kit, shulkerSlot, sh, sb, bsm, inv);
                Messages.send(p, "messages.custom-kit-guimain.all-enchantments-cleared");
                openItemEditorForBox(p, kitIdx, shulkerSlot, boxSlot);
            }));
        }

        // slot 53 – Finish
        {
            ItemStack done = new ItemStack(Material.LIME_CONCRETE);
            ItemMeta dm = done.getItemMeta();
            dm.setDisplayName(GuiService.text("custom-kit-guimain.finish"));
            done.setItemMeta(dm);

            GuiService.control(enchMenu, "done", 53, new SGButton(done).withListener(e ->
                    openItemEditorForBox(p, kitIdx, shulkerSlot, boxSlot)));
        }

        GuiService.open(p, enchMenu);
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
            Messages.send(p, "messages.custom-kit-guimain.this-name-is-too-long-the-event-has");
            return true;
        }
        ItemStack it = inv.getItem(pr.boxSlot()).clone();
        ItemMeta im  = it.getItemMeta();
        im.setDisplayName(ChatColor.translateAlternateColorCodes('&', msg));
        it.setItemMeta(im);

        inv.setItem(pr.boxSlot(), it);
        inst.saveShulkerBack(kit, pr.shulkerSlot(), sh, sb, bsm, inv);
        Messages.send(p, "messages.custom-kit-guimain.name-set");
        inst.openShulkerEditor(p, pr.kitIdx(), pr.shulkerSlot());
        return true;
    }

    public static boolean handleChatStackBox(Player p, String msg, CustomKitGUIMain inst) {
        PendingIntegerBox pi = STACK_BOX.remove(p.getUniqueId());
        if (pi == null) return false;

        int amt;
        try { amt = Integer.parseInt(msg); } catch (NumberFormatException ex) { Messages.send(p, "messages.custom-kit-guimain.not-a-number"); return true; }
        if (amt < 1 || amt > 64) { Messages.send(p, "messages.custom-kit-guimain.number-must-be-1-64-the-event-has"); return true; }

        CustomKit kit = inst.plugin.getExtendedPlayer(p).getMainPlayer().getKits()[pi.kitIdx()];
        ItemStack sh  = kit.getSlot(pi.shulkerSlot()).convertToItem();
        BlockStateMeta bsm = (BlockStateMeta) sh.getItemMeta();
        ShulkerBox sb  = (ShulkerBox) bsm.getBlockState();
        Inventory  inv = sb.getInventory();

        ItemStack it = inv.getItem(pi.boxSlot()).clone();
        it.setAmount(Math.min(amt, it.getMaxStackSize()));
        inv.setItem(pi.boxSlot(), it);

        inst.saveShulkerBack(kit, pi.shulkerSlot(), sh, sb, bsm, inv);
        Messages.send(p, "messages.custom-kit-guimain.stack-size-set");
        inst.openShulkerEditor(p, pi.kitIdx(), pi.shulkerSlot());
        return true;
    }

    public static boolean handleChatEnchantBox(Player p, String msg, CustomKitGUIMain inst) {
        PendingEnchantBox pe = ENCHANT_BOX.remove(p.getUniqueId());
        if (pe == null) return false;

        int lvl;
        try { lvl = Integer.parseInt(msg); } catch (NumberFormatException ex) { Messages.send(p, "messages.custom-kit-guimain.not-a-number-the-event-has-been-canceled"); return true; }
        if (lvl < 1) {
            Messages.send(p, "messages.custom-kit-guimain.level-must-be-1-the-event-has-been");
            return true;
        }
        if (lvl > 10) {
            Messages.send(p, "messages.custom-kit-guimain.level-must-be-10-the-event-has-been");
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
        Messages.send(p, "messages.custom-kit-guimain.enchantment-set");
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

        SGMenu menu = plugin.getMenus().create("custom-kits.shulker", 4);

        /* 0‑26 : internal storage ------------------------------------------------*/
        ItemStack filler = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta  fMeta  = filler.getItemMeta();
        fMeta.setDisplayName(GuiService.text("custom-kit-guimain.click-to-edit"));
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
            im.setDisplayName(GuiService.text("custom-kit-guimain.delete-shulker"));
            bar.setItemMeta(im);

            GuiService.control(menu, "bar", 30, new SGButton(bar).withListener(e -> {
                kit.setSlot(kitSlot, null);
                editKit(kitIndex, p);
            }));
        }

        /* slot 31 – RENAME -------------------------------------------------------*/
        {
            ItemStack sign = new ItemStack(Material.OAK_SIGN);
            ItemMeta  im   = sign.getItemMeta();
            im.setDisplayName(GuiService.text("custom-kit-guimain.rename-shulker"));
            sign.setItemMeta(im);

            GuiService.control(menu, "sign", 31, new SGButton(sign).withListener(e -> {
                p.closeInventory();
                Messages.send(p, "messages.custom-kit-guimain.type-the-new-shulker-name-in-chat");
                RENAME.put(p.getUniqueId(), new PendingRename(kitIndex, kitSlot));
            }));
        }

        /* slot 32 – FINISH -------------------------------------------------------*/
        {
            ItemStack done = new ItemStack(Material.LIME_CONCRETE);
            ItemMeta  im   = done.getItemMeta();
            im.setDisplayName(GuiService.text("custom-kit-guimain.finish"));
            done.setItemMeta(im);

            GuiService.control(menu, "done", 32, new SGButton(done).withListener(e -> editKit(kitIndex, p)));
        }

        GuiService.open(p, menu);
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
            Messages.send(p, "messages.custom-kit-guimain.no-categories-configured");
            return;
        }

        SGMenu catMenu = plugin.getMenus().create("custom-kits.shulker-categories", 4);

        for (CustomKitCategory cat : cats.values()) {
            ItemStack logo = cat.getDisplayItem().convertToItem();
            SGButton b = new SGButton(logo).withListener(e -> {
                openCategoryItemsForShulker(p, cat, kitIndex, kitSlotShulker, boxSlot);
            });
            catMenu.setButton(cat.getCategoryGuiSlot(), b);
        }
        GuiService.open(p, catMenu);
    }

    /** the 6‑row item picker for a shulker slot                              */
    private void openCategoryItemsForShulker(Player p,
                                             CustomKitCategory cat,
                                             int kitIndex,
                                             int kitSlotShulker,
                                             int boxSlot) {

        SGMenu itemMenu = plugin.getMenus().create("custom-kits.shulker-items", 6);

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
                    Messages.send(p, "messages.custom-kit-guimain.you-can-t-place-a-shulker-box-inside-2");
                    return;
                }

                inv.setItem(boxSlot, icon.clone());       // put chosen item
                saveShulkerBack(kit, kitSlotShulker, sh, sb, bsm, inv);
                openShulkerEditor(p, kitIndex, kitSlotShulker);
            });

            itemMenu.setButton(guiSlot, b);
        }
        GuiService.open(p, itemMenu);
    }



    /*  ────────────────────────────────────────────────────────────────
     *   The 3‑row editor itself
     *  ───────────────────────────────────────────────────────────── */
    private void openItemEditor(Player p, int kitIndex, int kitSlot) {

        SGMenu menu = plugin.getMenus().create("custom-kits.item", 2);

        CustomKit kit = plugin.getExtendedPlayer(p).getMainPlayer().getKits()[kitIndex];
        ItemStack working = kit.getSlot(kitSlot).convertToItem();   // copy we will mutate

        /* slot 10 – Delete */
        {
            ItemStack barrier = new ItemStack(Material.BARRIER);
            ItemMeta im = barrier.getItemMeta();
            im.setDisplayName(GuiService.text("custom-kit-guimain.delete"));
            barrier.setItemMeta(im);

            SGButton del = new SGButton(barrier).withListener(e -> {
                kit.setSlot(kitSlot, null);
                editKit(kitIndex, p);
            });
            GuiService.control(menu, "slot-10", 10, del);
        }

        /* slot 12 – Rename */
        {
            ItemStack sign = new ItemStack(Material.OAK_SIGN);
            ItemMeta im = sign.getItemMeta();
            im.setDisplayName(GuiService.text("custom-kit-guimain.rename"));
            sign.setItemMeta(im);

            SGButton ren = new SGButton(sign).withListener(e -> {
                p.closeInventory();
                Messages.send(p, "messages.custom-kit-guimain.type-the-new-name-in-chat-colour-codes");
                RENAME.put(p.getUniqueId(), new PendingRename(kitIndex, kitSlot));
            });
            GuiService.control(menu, "slot-12", 12, ren);
        }

        /* slot 13 – Enchant */
        {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta im = book.getItemMeta();
            im.setDisplayName(GuiService.text("custom-kit-guimain.add-change-enchant"));
            book.setItemMeta(im);

            SGButton enchBtn = new SGButton(book).withListener(e -> openEnchantmentPicker(p, kitIndex, kitSlot));
            GuiService.control(menu, "slot-13", 13, enchBtn);
        }

        /* slot 14 – Stack size */
        {
            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta im = paper.getItemMeta();
            im.setDisplayName(GuiService.text("custom-kit-guimain.set-stack-size"));
            paper.setItemMeta(im);

            SGButton stack = new SGButton(paper).withListener(e -> {
                p.closeInventory();
                Messages.send(p, "messages.custom-kit-guimain.enter-a-number-1-64-in-chat-2");
                STACK.put(p.getUniqueId(), new PendingInteger(kitIndex, kitSlot));
            });
            GuiService.control(menu, "slot-14", 14, stack);
        }

        /* slot 16 –  FINISH */
        {
            ItemStack done = new ItemStack(Material.LIME_CONCRETE);
            ItemMeta im = done.getItemMeta();
            im.setDisplayName(GuiService.text("custom-kit-guimain.finish"));
            done.setItemMeta(im);

            SGButton fin = new SGButton(done).withListener(e -> editKit(kitIndex, p));
            GuiService.control(menu, "slot-16", 16, fin);
        }

        /* centre display of the current item (slot 4) */
        GuiService.control(menu, "working", 4, new SGButton(working));

        GuiService.open(p, menu);
    }

    /*  ────────────────────────────────────────────────────────────────
     *   Enchantment picker (6‑row) then ask for level in chat
     *  ───────────────────────────────────────────────────────────── */
    private void openEnchantmentPicker(Player p, int kitIndex, int kitSlot) {

        SGMenu enchMenu = plugin.getMenus().create("custom-kits.enchantments", 6);
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
                Messages.send(p, "messages.custom-kit-guimain.type-the-level-1-in-chat");
                ENCHANT.put(p.getUniqueId(), new PendingEnchant(kitIndex, kitSlot, ench));
            });
            enchMenu.setButton(slot++, b);
        }

        // slot 52 – Clear all enchants
        {
            ItemStack anvil = new ItemStack(Material.ANVIL);
            ItemMeta im = anvil.getItemMeta();
            im.setDisplayName(GuiService.text("custom-kit-guimain.clear-all-enchants"));
            anvil.setItemMeta(im);

            GuiService.control(enchMenu, "anvil", 52, new SGButton(anvil).withListener(e -> {
                ItemStack item = kit.getSlot(kitSlot).convertToItem();
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.getEnchants().keySet().forEach(meta::removeEnchant);
                    item.setItemMeta(meta);
                    kit.setSlot(kitSlot, item);
                }
                Messages.send(p, "messages.custom-kit-guimain.all-enchantments-cleared");
                openItemEditor(p, kitIndex, kitSlot);  // Return to item editor
            }));
        }

        // slot 53 – FINISH
        {
            ItemStack done = new ItemStack(Material.LIME_CONCRETE);
            ItemMeta dm = done.getItemMeta();
            dm.setDisplayName(GuiService.text("custom-kit-guimain.finish"));
            done.setItemMeta(dm);

            GuiService.control(enchMenu, "done", 53, new SGButton(done).withListener(e ->
                    openItemEditor(p, kitIndex, kitSlot)));
        }

        GuiService.open(p, enchMenu);
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
            Messages.send(p, "messages.custom-kit-guimain.this-name-is-too-long-the-event-has");
            return true;
        }
        im.setDisplayName(ChatColor.translateAlternateColorCodes('&', msg));
        item.setItemMeta(im);
        kit.setSlot(pr.kitSlot(), item);
        Messages.send(p, "messages.custom-kit-guimain.name-set");
        inst.editKit(pr.kitIndex(), p);
        return true;
    }

    public static boolean handleChatStack(Player p, String msg, CustomKitGUIMain inst) {
        PendingInteger pi = STACK.remove(p.getUniqueId());
        if (pi == null) return false;

        int amt;
        try { amt = Integer.parseInt(msg); }
        catch (NumberFormatException ex) { Messages.send(p, "messages.custom-kit-guimain.not-a-number-the-event-has-been-canceled"); return true; }

        if (amt < 1 || amt > 64) { Messages.send(p, "messages.custom-kit-guimain.number-must-be-1-64-the-event-has"); return true; }

        CustomKit kit = inst.plugin.getExtendedPlayer(p).getMainPlayer().getKits()[pi.kitIndex()];
        ItemStack item = kit.getSlot(pi.kitSlot()).convertToItem();
        item.setAmount(Math.min(amt, item.getMaxStackSize()));
        kit.setSlot(pi.kitSlot(), item);
        Messages.send(p, "messages.custom-kit-guimain.stack-size-set");
        inst.editKit(pi.kitIndex(), p);
        return true;
    }

    public static boolean handleChatEnchant(Player p, String msg, CustomKitGUIMain inst) {
        PendingEnchant pe = ENCHANT.remove(p.getUniqueId());
        if (pe == null) return false;

        int lvl;
        try { lvl = Integer.parseInt(msg); }
        catch (NumberFormatException ex) { Messages.send(p, "messages.custom-kit-guimain.not-a-number-the-event-has-been-canceled"); return true; }

        if (lvl < 1) {
            Messages.send(p, "messages.custom-kit-guimain.level-must-be-1-the-event-has-been");
            return true;
        }
        if (lvl > 10) {
            Messages.send(p, "messages.custom-kit-guimain.level-must-be-10-the-event-has-been");
            return true;
        }
        CustomKit kit = inst.plugin.getExtendedPlayer(p).getMainPlayer().getKits()[pe.kitIndex()];
        ItemStack item = kit.getSlot(pe.kitSlot()).convertToItem();

        ItemMeta im = item.getItemMeta();
        im.addEnchant(pe.ench(), Math.min(lvl, pe.ench().getMaxLevel()), true);
        item.setItemMeta(im);

        kit.setSlot(pe.kitSlot(), item);
        Messages.send(p, "messages.custom-kit-guimain.enchantment-set");
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
            Messages.send(p, "messages.custom-kit-guimain.no-categories-configured");
            return;
        }

        SGMenu catMenu = plugin.getMenus().create("custom-kits.categories", 4);
        for (CustomKitCategory cat : cats.values()) {
            ItemStack logo = cat.getDisplayItem().convertToItem();
            SGButton b = new SGButton(logo)
                    .withListener(e -> openCategoryItems(p, cat, kitIndex, kitSlot));

            catMenu.setButton(cat.getCategoryGuiSlot(), b);
        }
        GuiService.open(p, catMenu);
    }

    /** Opens the 6‑row item list for one category. */
    private void openCategoryItems(Player p,
                                   CustomKitCategory cat,
                                   int kitIndex,
                                   int kitSlot) {

        SGMenu itemMenu = plugin.getMenus().create("custom-kits.items", 6);
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
        GuiService.open(p, itemMenu);
    }


    public void displayMain(Player p) {

        SGMenu menu = plugin.getMenus().create("custom-kits.list", 6);

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
        selectedMeta.setDisplayName(GuiService.text("custom-kit-guimain.selected-kit"));
        selectedItem.setItemMeta(selectedMeta);

        ItemStack existingItem = new ItemStack(Material.RED_CONCRETE);
        ItemMeta existingMeta = existingItem.getItemMeta();
        existingMeta.setDisplayName(GuiService.text("custom-kit-guimain.available-kit"));
        existingItem.setItemMeta(existingMeta);

        ItemStack creatableItem = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta creatableMeta = creatableItem.getItemMeta();
        creatableMeta.setDisplayName(GuiService.text("custom-kit-guimain.create-new-kit"));
        creatableItem.setItemMeta(creatableMeta);

        ItemStack lockedItem = new ItemStack(Material.BEDROCK);
        ItemMeta lockedMeta = lockedItem.getItemMeta();
        lockedMeta.setDisplayName(GuiService.text("custom-kit-guimain.locked-slot"));
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

        GuiService.open(p, menu);
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
        SGMenu menu = plugin.getMenus().create("custom-kits.edit", 5, slotKit);

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
                im.setDisplayName(GuiService.text("custom-kit-guimain.click-to-edit-2", armorNames[i]));
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

            GuiService.control(menu, "slot-4", 4, off);                              // 6th slot (index 5)
        }

        {

            ItemStack icon = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(GuiService.text("custom-kit-guimain.direct-kit-room"));
            icon.setItemMeta(meta);

            SGButton off = new SGButton(icon)
                    .withListener(e -> kitRoom(slotKit, p));

            GuiService.control(menu, "slot-5", 5, off);

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
        compItemMeta.setDisplayName(GuiService.text("custom-kit-guimain.settings"));
        comp.setItemMeta(compItemMeta);
        SGButton button = new SGButton(comp);
        button.withListener(e -> openSettings(slotKit, p));

        // ── Delete‑Kit button (red concrete) in GUI slot 8 ───────────────────
        {
            ItemStack del = new ItemStack(Material.RED_CONCRETE);
            ItemMeta  dm  = del.getItemMeta();
            dm.setDisplayName(GuiService.text("custom-kit-guimain.delete-kit"));
            del.setItemMeta(dm);

            SGButton delBtn = new SGButton(del)
                    .withListener(e -> openDeleteConfirm(slotKit, p));

            GuiService.control(menu, "slot-8", 8, delBtn);   // index 6 = GUI slot 7 (0‑based)
        }

        // Share‑Kit button (nametag) – GUI slot 6
        {
            ItemStack tag = new ItemStack(Material.NAME_TAG);
            ItemMeta  im  = tag.getItemMeta();
            im.setDisplayName(GuiService.text("custom-kit-guimain.share-kit"));
            tag.setItemMeta(im);


            SGButton share = new SGButton(tag).withListener(e -> {
                shareKit(p, slotKit);
            });



            GuiService.control(menu, "slot-6", 6, share);   // last slot on the top row
        }

        GuiService.control(menu, "slot-7", 7, button);

        GuiService.open(p, menu);                           // whatever your SGMenu uses
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
        SGMenu menu = plugin.getMenus().create("custom-kits.room", 5);


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
            cm.setDisplayName(GuiService.text("custom-kit-guimain.clear-inventory"));
            clear.setItemMeta(cm);

            GuiService.control(menu, "clear", 41, new SGButton(clear).withListener(e -> {
                p.closeInventory();
                p.getInventory().clear();
                Messages.send(p, "messages.custom-kit-guimain.your-inventory-has-been-cleared");
                kitRoom(kitIdx, p);                 // reopen
            }));
        }
        {
            ItemStack back = new ItemStack(Material.NETHER_STAR);
            ItemMeta  bm   = back.getItemMeta();
            bm.setDisplayName(GuiService.text("custom-kit-guimain.go-back"));
            back.setItemMeta(bm);

            GuiService.control(menu, "back", 39, new SGButton(back).withListener(e ->
                    editKit(kitIdx, p)));                       // back to kit editor
        }

        // lime concrete  ➜ save inventory to kit
        {
            ItemStack save = new ItemStack(Material.LIME_CONCRETE);
            ItemMeta sm = save.getItemMeta();
            sm.setDisplayName(GuiService.text("custom-kit-guimain.save-inventory-to-kit"));
            save.setItemMeta(sm);

            GuiService.control(menu, "save", 40, new SGButton(save).withListener(e -> {
                savePlayerInventoryToKit(p, kitIdx);
                Messages.send(p, "messages.custom-kit-guimain.current-inventory-stored-in-this-kit");
            }));
        }

        // sign  ➜ information only
        {
            ItemStack info = new ItemStack(Material.OAK_SIGN);
            ItemMeta im = info.getItemMeta();
            im.setDisplayName(GuiService.text("custom-kit-guimain.what-is-this"));
            im.setLore(List.of(
                    GuiService.text("custom-kit-guimain.this-menu-allows-you-to-create"),
                    GuiService.text("custom-kit-guimain.your-own-custom-kits-faster"),
                    " ",
                    GuiService.text("custom-kit-guimain.when-you-re-finished-remember"),
                    GuiService.text("custom-kit-guimain.to-save-your-kit")
            ));
            info.setItemMeta(im);

            GuiService.control(menu, "info", 4, new SGButton(info));   // no click action
        }

        // composter  ➜ trash bin
        {
            ItemStack trash = new ItemStack(Material.COMPOSTER);
            ItemMeta tm = trash.getItemMeta();
            tm.setDisplayName(GuiService.text("custom-kit-guimain.trash"));
            trash.setItemMeta(tm);

            GuiService.control(menu, "trash", 44, new SGButton(trash).withListener(e ->
                    openTrashBin(p, kitIdx)));
        }


        GuiService.open(p, menu);
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
                plugin.getMenus().nativeRows("custom-kits.room-items", 6) * 9,
                plugin.getMenus().nativeTitle("custom-kits.room-items", p));

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

        String menuId = "custom-kits.trash";
        int size = plugin.getMenus().nativeRows(menuId, 1) * 9;
        int finishSlot = plugin.getMenus().nativeSlot(menuId, "finish", Math.min(49, size - 1), size);
        Inventory bin = Bukkit.createInventory(null, size, plugin.getMenus().nativeTitle(menuId, p));

        ItemStack fin = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta fm = fin.getItemMeta();
        fm.setDisplayName(GuiService.text("custom-kit-guimain.finish"));
        fin.setItemMeta(fm);
        bin.setItem(finishSlot, plugin.getMenus().nativeItem(menuId, "finish", fin, p));

        /* ---- click listener just for this bin ---- */
        Listener clickLock = new Listener() {
            @EventHandler
            public void onClick(InventoryClickEvent e) {
                if (!e.getInventory().equals(bin)) return;

                // lock the FINISH concrete
                if (e.getRawSlot() == finishSlot) {
                    e.setCancelled(true);
                    ((Player) e.getWhoClicked()).closeInventory();  // triggers onCloseOnce
                }
            }

            @EventHandler
            public void onDrag(InventoryDragEvent event) {
                if (event.getInventory().equals(bin) && event.getRawSlots().contains(finishSlot)) {
                    event.setCancelled(true);
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

        SGMenu confirm = plugin.getMenus().create("custom-kits.delete", 1);

        ItemStack yes = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta ym = yes.getItemMeta();
        ym.setDisplayName(GuiService.text("custom-kit-guimain.yes-delete"));
        yes.setItemMeta(ym);

        SGButton yesBtn = new SGButton(yes).withListener(e -> {
            ExtendedPlayer ep = plugin.getExtendedPlayer(p);
            if (ep == null) return;
            CustomKit[] kits = ep.getMainPlayer().getKits();
            if (kits == null) return;

            kits[slotKit] = null;                        // remove kit
            ep.getMainPlayer().setKit(slotKit, null);    // mark dirty
            Messages.send(p, "messages.custom-kit-guimain.kit-deleted", slotKit);
            displayMain(p);                              // back to main GUI
        });
        GuiService.control(confirm, "slot-2", 2, yesBtn);

        // NO    – slot 7 (index 6)
        ItemStack no = new ItemStack(Material.RED_CONCRETE);
        ItemMeta nm = no.getItemMeta();
        nm.setDisplayName(GuiService.text("custom-kit-guimain.no-go-back"));
        no.setItemMeta(nm);

        SGButton noBtn = new SGButton(no)
                .withListener(e -> editKit(slotKit, p));
        GuiService.control(confirm, "slot-5", 5, noBtn);

        GuiService.open(p, confirm);
    }


    private void shareKit(Player p, int slotKit) {

        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep == null) return;
        MainPlayer mp = ep.getMainPlayer();
        if (mp == null) return;

        CustomKit kit = mp.getKits()[slotKit];
        if (kit == null || kit.getInventory().isEmpty()) {
            Messages.send(p, "messages.custom-kit-guimain.that-kit-is-empty");
            return;
        }

        var shared = StatManagement.getSharedState();
        if (!shared.isEnabled()) {
            Messages.send(p, "messages.copy-kit-command.storage-unavailable");
            return;
        }

        String snapshot = kit.toJson();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String code = shared.shareKit(p.getUniqueId(), slotKit, snapshot);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) return;
                if (code == null) {
                    Messages.send(p, "messages.custom-kit-guimain.failed-to-share-kit-try-again");
                } else {
                    Messages.send(p, "messages.custom-kit-guimain.your-kit-has-been-shared-use-command-copykit", code);
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
        SGMenu menu = plugin.getMenus().create("custom-kits.settings", 2);

        /* =========================================================
         * ROW 1
         * ======================================================= */

        /* Health Indicators */
        GuiService.control(menu, "health-indicators", 0, new SGButton(
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

            im.setDisplayName(GuiService.text("custom-kit-guimain.mending-rng"));
            im.setLore(List.of(
                    GuiService.text("custom-kit-guimain.current", value),
                    " ",
                    GuiService.text("custom-kit-guimain.type-a-number-in-chat"),
                    GuiService.text("custom-kit-guimain.type-0-to-disable")
            ));
            xp.setItemMeta(im);

            GuiService.control(menu, "xp", 9, new SGButton(xp).withListener(e -> {
                p.closeInventory();
                Messages.send(p, "messages.custom-kit-guimain.enter-mending-rng-value-type-0-to-disable");
                PENDING_MENDING.put(p.getUniqueId(), slotKit);
            }));
        }

        /* Hunger */
        GuiService.control(menu, "hunger", 1, new SGButton(
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

            GuiService.control(menu, "tnt-auto-ignition", 10, new SGButton(
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
        GuiService.control(menu, "saturation", 2, new SGButton(
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

            im.setDisplayName(GuiService.text("custom-kit-guimain.damage-increase"));
            im.setLore(List.of(
                    GuiService.text("custom-kit-guimain.current-2", value),
                    " ",
                    GuiService.text("custom-kit-guimain.type-a-number-in-chat")
            ));
            sword.setItemMeta(im);

            GuiService.control(menu, "sword", 11, new SGButton(sword).withListener(e -> {
                p.closeInventory();
                Messages.send(p, "messages.custom-kit-guimain.enter-damage-increase");
                PENDING_DAMAGE.put(p.getUniqueId(), slotKit);
            }));
        }

        /* Building */
        GuiService.control(menu, "block-modification", 3, new SGButton(
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
        GuiService.control(menu, "ender-chest", 4, new SGButton(
                settingCreator(
                        new ItemStack(Material.ENDER_CHEST),
                        "§dEnder Chest",
                        true
                )
        ).withListener(e -> openEnderChestEditor(p, slotKit)));

        /* Effects */
        GuiService.control(menu, "effects", 5, new SGButton(
                settingCreator(
                        new ItemStack(Material.POTION),
                        "§dEffects",
                        true
                )
        ).withListener(e -> openEffectsMenu(p, slotKit)));

        /* DONE — LAST SLOT */
        ItemStack doneItem = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta dIM = doneItem.getItemMeta();
        dIM.setDisplayName(GuiService.text("custom-kit-guimain.done"));
        doneItem.setItemMeta(dIM);

        GuiService.control(menu, "slot-17", 17, new SGButton(doneItem)
                .withListener(e -> editKit(slotKit, p)));

        GuiService.open(p, menu);
    }


    private void openEffectsMenu(Player p, int kitIdx) {
        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep == null) return;

        CustomKit kit = ep.getMainPlayer().getKits()[kitIdx];
        if (kit == null) return;

        SGMenu menu = plugin.getMenus().create("custom-kits.effects", 3);

        // info in middle
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        int count = kit.getEffects().size();
        im.setDisplayName(GuiService.text("custom-kit-guimain.current-effects", count));
        info.setItemMeta(im);
        GuiService.control(menu, "info", 13, new SGButton(info));

        // create
        ItemStack create = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta cm = create.getItemMeta();
        cm.setDisplayName(GuiService.text("custom-kit-guimain.create-new-effect"));
        create.setItemMeta(cm);
        GuiService.control(menu, "create", 11, new SGButton(create).withListener(e -> openEffectPicker(p, kitIdx, 0)));

        // list
        ItemStack list = new ItemStack(Material.PAPER);
        ItemMeta lm = list.getItemMeta();
        lm.setDisplayName(GuiService.text("custom-kit-guimain.list-effects-click-to-delete"));
        list.setItemMeta(lm);
        GuiService.control(menu, "list", 15, new SGButton(list).withListener(e -> openEffectList(p, kitIdx, 0)));

        // back
        ItemStack back = new ItemStack(Material.NETHER_STAR);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(GuiService.text("custom-kit-guimain.back"));
        back.setItemMeta(bm);
        GuiService.control(menu, "back", 26, new SGButton(back).withListener(e -> openSettings(kitIdx, p)));

        GuiService.open(p, menu);
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

        SGMenu menu = plugin.getMenus().create("custom-kits.pick-effect", 6, (safePage + 1), (maxPage + 1));

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
                    GuiService.text("custom-kit-guimain.click-then-type-in-chat"),
                    GuiService.text("custom-kit-guimain.level-seconds-example-1-120"),
                    GuiService.text("custom-kit-guimain.level-1-means-potion-i")
            ));
            icon.setItemMeta(im);

            final String chosenKey = key;
            menu.setButton(slot++, new SGButton(icon).withListener(e -> {
                p.closeInventory();
                Messages.send(p, "messages.custom-kit-guimain.type-level-seconds-example-1-120");
                EFFECT_CREATE.put(p.getUniqueId(), new PendingEffectCreate(kitIdx, chosenKey));
            }));
        }

        // prev
        ItemStack prev = new ItemStack(Material.ARROW);
        ItemMeta pm = prev.getItemMeta();
        pm.setDisplayName(GuiService.text("custom-kit-guimain.previous"));
        prev.setItemMeta(pm);
        GuiService.control(menu, "prev", 45, new SGButton(prev).withListener(e -> openEffectPicker(p, kitIdx, safePage - 1)));

        // back
        ItemStack back = new ItemStack(Material.NETHER_STAR);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(GuiService.text("custom-kit-guimain.back"));
        back.setItemMeta(bm);
        GuiService.control(menu, "back", 49, new SGButton(back).withListener(e -> openEffectsMenu(p, kitIdx)));

        // next
        ItemStack next = new ItemStack(Material.ARROW);
        ItemMeta nm = next.getItemMeta();
        nm.setDisplayName(GuiService.text("custom-kit-guimain.next"));
        next.setItemMeta(nm);
        GuiService.control(menu, "next", 53, new SGButton(next).withListener(e -> openEffectPicker(p, kitIdx, safePage + 1)));

        GuiService.open(p, menu);
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

        SGMenu menu = plugin.getMenus().create("custom-kits.effect-list", 6, (safePage + 1), (maxPage + 1));

        int start = safePage * pageSize;
        int end = Math.min(effects.size(), start + pageSize);

        int slot = 0;
        for (int idx = start; idx < end; idx++) {
            EffectMeta em = effects.get(idx);

            ItemStack icon = new ItemStack(Material.POTION);
            ItemMeta im = icon.getItemMeta();
            im.setDisplayName("§b" + em.getKey());
            im.setLore(List.of(
                    GuiService.text("custom-kit-guimain.level", em.getLevel()),
                    GuiService.text("custom-kit-guimain.seconds", em.getSeconds()),
                    " ",
                    GuiService.text("custom-kit-guimain.click-to-delete")
            ));
            icon.setItemMeta(im);

            final int removeIndex = idx;
            menu.setButton(slot++, new SGButton(icon).withListener(e -> {
                kit.removeEffect(removeIndex);
                Messages.send(p, "messages.custom-kit-guimain.effect-removed");
                openEffectList(p, kitIdx, safePage);
            }));
        }

        // clear all
        ItemStack clear = new ItemStack(Material.ANVIL);
        ItemMeta cm = clear.getItemMeta();
        cm.setDisplayName(GuiService.text("custom-kit-guimain.clear-all-effects"));
        clear.setItemMeta(cm);
        GuiService.control(menu, "clear", 52, new SGButton(clear).withListener(e -> {
            kit.clearEffects();
            Messages.send(p, "messages.custom-kit-guimain.all-effects-cleared");
            openEffectList(p, kitIdx, 0);
        }));

        // back
        ItemStack back = new ItemStack(Material.NETHER_STAR);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(GuiService.text("custom-kit-guimain.back"));
        back.setItemMeta(bm);
        GuiService.control(menu, "back", 49, new SGButton(back).withListener(e -> openEffectsMenu(p, kitIdx)));

        // prev, next
        ItemStack prev = new ItemStack(Material.ARROW);
        ItemMeta pm = prev.getItemMeta();
        pm.setDisplayName(GuiService.text("custom-kit-guimain.previous"));
        prev.setItemMeta(pm);
        GuiService.control(menu, "prev", 45, new SGButton(prev).withListener(e -> openEffectList(p, kitIdx, safePage - 1)));

        ItemStack next = new ItemStack(Material.ARROW);
        ItemMeta nm = next.getItemMeta();
        nm.setDisplayName(GuiService.text("custom-kit-guimain.next"));
        next.setItemMeta(nm);
        GuiService.control(menu, "next", 53, new SGButton(next).withListener(e -> openEffectList(p, kitIdx, safePage + 1)));

        GuiService.open(p, menu);
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
        im.setDisplayName(GuiService.text("custom-kit-guimain.click-to-edit"));
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
            Messages.send(p, "messages.custom-kit-guimain.not-a-number");
            return true;
        }

        if (value < 0) {
            Messages.send(p, "messages.custom-kit-guimain.must-be-0");
            return true;
        }

        CustomKit kit = inst.plugin
                .getExtendedPlayer(p)
                .getMainPlayer()
                .getKits()[kitIdx];

        kit.getMetaData().set("mending_rng", value);

        if (value == 0) {
            Messages.send(p, "messages.custom-kit-guimain.mending-rng-disabled");
        } else {
            Messages.send(p, "messages.custom-kit-guimain.mending-rng-set-to", value);
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
            Messages.send(p, "messages.custom-kit-guimain.not-a-number");
            return true;
        }

        if (value < 0) {
            Messages.send(p, "messages.custom-kit-guimain.must-be-0");
            return true;
        }

        CustomKit kit = inst.plugin
                .getExtendedPlayer(p)
                .getMainPlayer()
                .getKits()[kitIdx];

        kit.getMetaData().set("dmg_inc_%", value);

        Messages.send(p, "messages.custom-kit-guimain.damage-increase-set-to", value);

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
