package org.albi.core.modules.FFA.GUIs;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.data.MainPlayer;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.DuelKitPageCategory;
import org.albi.core.modules.Duels.helpers.Kit;
import org.albi.core.modules.FFA.helpers.FFAKitSetup;
import org.albi.core.modules.FFA.helpers.KitInventoryEdits;
import org.albi.core.serializable.SerializableSpawnInventory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class FFAKitEditorGUI {

    private static final int GUI_ROWS = 1;
    private static final int GUI_SIZE = 9;
    private static final int GUI_SLOT_HELM   = 0; // display-only
    private static final int GUI_SLOT_CHEST  = 1; // display-only
    private static final int GUI_SLOT_LEGS   = 2; // display-only
    private static final int GUI_SLOT_BOOTS  = 3; // display-only
    private static final int GUI_SLOT_OFF    = 4; // draggable
    private static final int GUI_SLOT_RESET  = 7;
    private static final int GUI_SLOT_SAVE   = 8;

    private static final int SLOT_HELM   = 39;
    private static final int SLOT_CHEST  = 38;
    private static final int SLOT_LEGS   = 37;
    private static final int SLOT_BOOTS  = 36;
    private static final int SLOT_OFF    = 40;

    private final Core plugin;
    public FFAKitEditorGUI(Core plugin) { this.plugin = plugin; }

    /* ───────────────── Kit picker (same layout as /ffa) ───────────────── */
    public void openKitMenu(Player p) {
        List<FFAKitSetup> setups = new ArrayList<>();
        for (FFAKitSetup s : org.albi.core.modules.FFA.FFAManager.stor.allSetups()) {
            if (s.getWorldName() != null && Bukkit.getWorld(s.getWorldName()) != null) setups.add(s);
        }

        SGMenu m = plugin.getGUI().create("§bFFA Kits (Editor)", GUI_ROWS);
        int slot = 0;
        for (FFAKitSetup s : setups) {
            Kit k = findKit(s.getKitName());
            if (k == null) continue;

            ItemStack it = k.getDisplayItem().convertToItem();
            ItemMeta im = it.getItemMeta();
            if (im != null && im.hasDisplayName()) {
                String clean = im.getDisplayName().split(" §8", 2)[0];
                im.setDisplayName(clean);
                it.setItemMeta(im);
            }

            int target = Math.min(slot++, GUI_SIZE - 1);
            m.setButton(target, new SGButton(it).withListener(e -> openEditorPanel(p, s.getKitName(), k, false)));
            if (slot >= GUI_SIZE) break;
        }
        p.openInventory(m.getInventory());
    }



    public void openDuelKitMenu(Player p) {
        if (hasDuelPageCategories()) {
            openDuelKitCategoryMenu(p);
            return;
        }
        openDuelKitPageMenu(p, 0);
    }

    private Map<Integer, DuelKitPageCategory> getDuelPageCategories() {
        Map<Integer, DuelKitPageCategory> cats =
                (Map<Integer, DuelKitPageCategory>) plugin.getData().get("duelKitPageCategories");
        return cats == null ? Collections.emptyMap() : cats;
    }

    private boolean hasDuelPageCategories() {
        return !getDuelPageCategories().isEmpty();
    }

    private void openDuelKitCategoryMenu(Player p) {
        SGMenu menu = plugin.getGUI().create("§dDuel Kit Categories", 3);
        for (Map.Entry<Integer, DuelKitPageCategory> entry : getDuelPageCategories().entrySet()) {
            DuelKitPageCategory cat = entry.getValue();
            if (cat == null || cat.getDisplayItem() == null) continue;
            ItemStack icon = cat.getDisplayItem().convertToItem();
            if (icon == null || icon.getType().isAir()) continue;
            ItemMeta meta = icon.getItemMeta();
            List<String> lore = meta != null && meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(" ");
            lore.add("§dOpen editor page #" + cat.getPage());
            if (meta != null) {
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            int slot = Math.max(0, Math.min(26, cat.getGuiSlot()));
            menu.setButton(slot, new SGButton(icon).withListener(e -> openDuelKitPageMenu(p, cat.getPage())));
        }
        p.openInventory(menu.getInventory());
    }

    private void openDuelKitPageMenu(Player p, int page) {
        final int rows = 6;
        final int pageSize = rows * 9;
        int maxSlot = DuelManager.stor.kits.stream().mapToInt(Kit::getSlot).max().orElse(0);
        int maxPage = maxSlot / pageSize;

        SGMenu menu = plugin.getGUI().create("§dDuel Kits (Editor) §7(" + (page + 1) + "/" + (maxPage + 1) + ")", rows);
        for (Kit k : DuelManager.stor.kits) {
            if (k == null || k.getDisplayItem() == null) continue;
            int globalSlot = k.getSlot();
            if (globalSlot / pageSize != page) continue;

            int local = globalSlot % pageSize;
            ItemStack it = k.getDisplayItem().convertToItem();
            if (it == null || it.getType().isAir()) it = new ItemStack(Material.PAPER);
            ItemMeta im = it.getItemMeta();
            if (im != null && im.hasDisplayName()) {
                String clean = im.getDisplayName().split(" §8", 2)[0];
                List<String> lore = im.hasLore() ? new ArrayList<>(im.getLore()) : new ArrayList<>();
                lore.add(" ");
                lore.add("§7Click to edit this duel kit layout.");
                im.setDisplayName(clean);
                im.setLore(lore);
                it.setItemMeta(im);
            }
            menu.setButton(local, new SGButton(it).withListener(e -> openEditorPanel(p, k.getName(), k, true)));        }

        if (page > 0) menu.setButton(pageSize - 10, new SGButton(button(Material.ARROW, "§e← Prev")).withListener(e -> openDuelKitPageMenu(p, page - 1)));
        if (page < maxPage) menu.setButton(pageSize - 9, new SGButton(button(Material.ARROW, "§eNext →")).withListener(e -> openDuelKitPageMenu(p, page + 1)));
        if (hasDuelPageCategories()) menu.setButton(pageSize - 5, new SGButton(button(Material.ARROW, "§dCategories")).withListener(e -> openDuelKitCategoryMenu(p)));

        p.openInventory(menu.getInventory());
    }


    /* ──────────────── Editor panel ──────────────── */
    private void openEditorPanel(Player p, String editKey, Kit kit, boolean duelMode) {
        wipePlayerCompletely(p);
        giveKitStorageOnly(p, kit);

        String title = duelMode
                ? "§dDuel Kit Editor: " + editKey.toUpperCase()
                : "§bKit Editor: " + editKey.toUpperCase();

        SGMenu m = plugin.getGUI().create(title, GUI_ROWS);
        Inventory top = m.getInventory();

        m.setButton(GUI_SLOT_HELM,  new SGButton(safe(kitArmor(kit, SLOT_HELM ))).withListener(e -> e.setCancelled(true)));
        m.setButton(GUI_SLOT_CHEST, new SGButton(safe(kitArmor(kit, SLOT_CHEST))).withListener(e -> e.setCancelled(true)));
        m.setButton(GUI_SLOT_LEGS,  new SGButton(safe(kitArmor(kit, SLOT_LEGS ))).withListener(e -> e.setCancelled(true)));
        m.setButton(GUI_SLOT_BOOTS, new SGButton(safe(kitArmor(kit, SLOT_BOOTS))).withListener(e -> e.setCancelled(true)));

        ItemStack defaultOff = kitOffhand(kit);
        m.setButton(GUI_SLOT_OFF, new SGButton(safe(defaultOff)).withListener((InventoryClickEvent e) -> {
            e.setCancelled(true);

            if (e.getClickedInventory() == null ||
                    e.getClickedInventory() != e.getWhoClicked().getOpenInventory().getTopInventory())
                return;

            Inventory gui = e.getWhoClicked().getOpenInventory().getTopInventory();
            ItemStack cursor = e.getView().getCursor();
            ItemStack current = gui.getItem(GUI_SLOT_OFF);

            if (isAir(cursor)) cursor = null;
            if (isAir(current)) current = null;

            gui.setItem(GUI_SLOT_OFF, cursor == null ? null : cursor.clone());
            e.getView().setCursor(current == null ? new ItemStack(Material.AIR) : current.clone());

            if (e.getWhoClicked() instanceof Player player) {
                player.updateInventory();
            }
        }));

        m.setButton(GUI_SLOT_SAVE,
                new SGButton(button(Material.LIME_CONCRETE, ChatColor.GREEN + "" + ChatColor.BOLD + "SAVE KIT"))
                        .withListener((InventoryClickEvent e) -> {
                            e.setCancelled(true);

                            Inventory liveTop = ((Player) e.getWhoClicked())
                                    .getOpenInventory().getTopInventory();

                            ItemStack chosenOffhand = liveTop.getItem(GUI_SLOT_OFF);
                            if (isAir(chosenOffhand)) chosenOffhand = null;

                            ItemStack cursorBefore = e.getView().getCursor();
                            if (isAir(cursorBefore)) cursorBefore = null;

                            List<String> playerArr = sigArrayFromPlayer(p, chosenOffhand, cursorBefore);
                            List<String> kitArr = sigArrayFromKit(kit);
                            Collections.sort(playerArr);
                            Collections.sort(kitArr);

                            if (!playerArr.equals(kitArr)) {
                                e.getWhoClicked().closeInventory();
                                wipePlayerCompletely(p);
                                if (!kit.isBuild()) p.setGameMode(GameMode.ADVENTURE);
                                p.sendMessage(ChatColor.RED + "Your saved items don’t match this kit. Reverted to default.");
                                plugin.applyLobbyShortcutItems(p);
                                return;
                            }

                            if (!isAir(e.getView().getCursor())) e.getView().setCursor(new ItemStack(Material.AIR));

                            KitInventoryEdits snap = KitInventoryEdits.captureStorageOnly(editKey, p.getInventory());
                            snap.setOffhand(chosenOffhand);

                            ExtendedPlayer ep = plugin.getExtendedPlayer(p);
                            if (ep != null && ep.isReady()) ep.getMainPlayer().putKitEdit(snap);

                            e.getWhoClicked().closeInventory();
                            p.sendMessage(ChatColor.GREEN + "Kit layout saved!");

                            plugin.applyLobbyShortcutItems(p);
                        })
        );

        p.openInventory(m.getInventory());
    }

    /* ───────────────── Helper bits ───────────────── */

    private Kit findKit(String kitName) {
        return DuelManager.stor.kits.stream()
                .filter(k -> k.getName().equalsIgnoreCase(kitName))
                .findFirst().orElse(null);
    }

    private void wipePlayerCompletely(Player p) {
        p.closeInventory();
        p.setItemOnCursor(null);

        var view = p.getOpenInventory();
        if (view != null && (view.getType() == org.bukkit.event.inventory.InventoryType.CRAFTING
                || view.getType() == org.bukkit.event.inventory.InventoryType.WORKBENCH)) {
            if (view.getTopInventory() instanceof CraftingInventory ci) {
                ItemStack[] empty = new ItemStack[ci.getMatrix().length];
                ci.setMatrix(empty);
                ci.setResult(null);
            }
        }

        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setArmorContents(null);
        inv.setItemInOffHand(null);
        p.updateInventory();
    }

    /** Give only storage (0..35). */
    private void giveKitStorageOnly(Player p, Kit k) {
        PlayerInventory inv = p.getInventory();
        if (k.getInventory() != null) {
            for (Map.Entry<Integer, org.albi.core.serializable.SerializableItem> e : k.getInventory().entrySet()) {
                int slot = e.getKey();
                if (slot < 0 || slot > 35) continue;
                inv.setItem(slot, e.getValue().convertToItem());
            }
        }
        p.updateInventory();
    }

    /* ── Simple “explode-to-array and compare” implementation ── */

    /** storage 0..35 + chosenOffhand (+ cursor, compare-only) -> array of signatures */
    private List<String> sigArrayFromPlayer(Player p, ItemStack chosenOffhand, ItemStack cursorCompareOnly) {
        List<String> out = new ArrayList<>();
        for (ItemStack it : p.getInventory().getStorageContents()) explode(out, it);
        explode(out, chosenOffhand);
        // include cursor ONLY for comparison robustness; we do not save it
        explode(out, cursorCompareOnly);
        return out;
    }


    /** kit storage 0..35 + kit offhand -> array of signatures (1 per item unit). */
    private List<String> sigArrayFromKit(Kit k) {
        List<String> out = new ArrayList<>();
        if (k.getInventory() != null) {
            for (Map.Entry<Integer, org.albi.core.serializable.SerializableItem> e : k.getInventory().entrySet()) {
                int slot = e.getKey();
                if (slot < 0 || slot > 35) continue;
                ItemStack it = e.getValue() == null ? null : e.getValue().convertToItem();
                explode(out, it);
            }
        }
        explode(out, kitOffhand(k));
        return out;
    }

    private void explode(List<String> out, ItemStack it) {
        if (isAir(it)) return;
        String s = sig(it);
        for (int i = 0; i < it.getAmount(); i++) out.add(s);
    }

    /** Compact, stable signature; sort of “ItemStack#isSimilar but printable”. */
    private String sig(ItemStack in) {
        if (isAir(in)) return "AIR";
        ItemStack it = in.clone();
        it.setAmount(1);

        StringBuilder sb = new StringBuilder(it.getType().name());
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            if (im instanceof Damageable dmg && dmg.hasDamage())
                sb.append("|d=").append(dmg.getDamage());
            if (im.hasDisplayName())
                sb.append("|n=").append(ChatColor.stripColor(im.getDisplayName()));

            if (im.hasEnchants()) {
                List<String> parts = new ArrayList<>();
                im.getEnchants().forEach((ench, lvl) -> parts.add(ench.getKey().toString() + ":" + lvl));
                Collections.sort(parts);
                sb.append("|e=").append(String.join(",", parts));
            }
            if (im.hasLore()) {
                List<String> lore = new ArrayList<>(im.getLore());
                for (int i = 0; i < lore.size(); i++) lore.set(i, ChatColor.stripColor(lore.get(i)));
                sb.append("|l=").append(String.join("|", lore));
            }
            if (im.hasCustomModelData())
                sb.append("|cmd=").append(im.getCustomModelData());

            if (!im.getItemFlags().isEmpty()) {
                List<String> flags = new ArrayList<>();
                im.getItemFlags().forEach(f -> flags.add(f.name()));
                Collections.sort(flags);
                sb.append("|f=").append(String.join(",", flags));
            }

            // Potion meta
            if (im instanceof org.bukkit.inventory.meta.PotionMeta pm) {
                if (pm.getBasePotionData() != null) {
                    var base = pm.getBasePotionData();
                    sb.append("|potionBase=")
                            .append(base.getType().name())
                            .append(":")
                            .append(base.isExtended())
                            .append(":")
                            .append(base.isUpgraded());
                }
                if (pm.hasCustomEffects()) {
                    List<String> eff = new ArrayList<>();
                    pm.getCustomEffects().forEach(effMeta ->
                            eff.add(effMeta.getType().getKey().toString() + ":" +
                                    effMeta.getAmplifier() + ":" +
                                    effMeta.getDuration())
                    );
                    Collections.sort(eff);
                    sb.append("|potionCE=").append(String.join(",", eff));
                }
            }

            // Leather armor color
            if (im instanceof org.bukkit.inventory.meta.LeatherArmorMeta lam && lam.getColor() != null) {
                var c = lam.getColor();
                sb.append("|leatherRGB=")
                        .append(c.getRed()).append(",")
                        .append(c.getGreen()).append(",")
                        .append(c.getBlue());
            }

            // Unbreakable
            if (im.isUnbreakable()) {
                sb.append("|unbreakable=true");
            }
        }
        return sb.toString();
    }


    /* ── Kit armor/offhand helpers (prefer getters; fallback to map) ── */

    private ItemStack kitArmor(Kit k, int armorSlot) {
        switch (armorSlot) {
            case SLOT_HELM  -> { return k.getHelmet()     != null ? k.getHelmet().convertToItem()     : fromMap(k, SLOT_HELM ); }
            case SLOT_CHEST -> { return k.getChestplate() != null ? k.getChestplate().convertToItem() : fromMap(k, SLOT_CHEST); }
            case SLOT_LEGS  -> { return k.getLeggings()   != null ? k.getLeggings().convertToItem()   : fromMap(k, SLOT_LEGS ); }
            case SLOT_BOOTS -> { return k.getBoots()      != null ? k.getBoots().convertToItem()      : fromMap(k, SLOT_BOOTS); }
            default -> { return null; }
        }
    }

    private ItemStack kitOffhand(Kit k) {
        if (k.getOffhand() != null) return k.getOffhand().convertToItem();
        return fromMap(k, SLOT_OFF);
    }

    private ItemStack fromMap(Kit k, int slot) {
        var si = k.getInventory() == null ? null : k.getInventory().get(slot);
        return si == null ? null : si.convertToItem();
    }

    /* ── tiny utils ── */
    private boolean isAir(ItemStack it) { return it == null || it.getType() == Material.AIR || it.getAmount() <= 0; }
    private ItemStack safe(ItemStack it) { return isAir(it) ? new ItemStack(Material.AIR) : it.clone(); }
    private ItemStack button(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        it.setItemMeta(im);
        return it;
    }
}
