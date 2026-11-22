package org.albi.core.commands;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.data.MainPlayer;
import org.albi.core.managers.cosmetics.DeathEffect;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DeathEffectsGUI implements Listener {

    private final Core plugin;
    // Not using ChatColor so we use raw § codes which remain valid
    private static final String BASE_TITLE = "§4§lKill Effects";

    private final NamespacedKey effectKey;

    // Track which page a player is on
    private final Map<UUID, Integer> openPages = new HashMap<>();

    public DeathEffectsGUI(Core plugin) {
        this.plugin = plugin;
        this.effectKey = new NamespacedKey(plugin, "death_effect_id");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, int page) {
        String title = BASE_TITLE + " §8§l| §7Page " + (page + 1);
        Inventory inv = Bukkit.createInventory(null, 54, title);

        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (ep == null) return;
        MainPlayer mp = ep.getMainPlayer();
        if (mp == null) return;

        String selected = mp.getDeathEffect();
        if (selected == null) selected = "NONE";

        // Elegant dark border with purple tinted glass
        fillBorder(inv);

        fillBorder(inv);

        // Filter out NONE so the grid is purely effects
        List<DeathEffect> effects = new ArrayList<>(Arrays.asList(DeathEffect.values()));
        effects.removeIf(e -> e == DeathEffect.NONE);

        // 21 content slots
        int[] itemSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
        };

        int start = page * 21;
        int end = Math.min(start + 21, effects.size());

        for (int i = start; i < end; i++) {
            DeathEffect effect = effects.get(i);

            boolean unlocked = isUnlocked(player, mp, effect);
            boolean isSelected = effect.name().equalsIgnoreCase(selected);

            Material mat = unlocked ? effect.getIcon() : Material.BARRIER;
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();

            // Name: colored by state
            String nameColor = isSelected ? "§a§l" : (unlocked ? "§b§l" : "§c§l");
            meta.setDisplayName(nameColor + effect.getDisplayName());

            List<String> lore = new ArrayList<>();
            lore.add("§8▸ §7Cosmetic Kill Effect");
            lore.add("");

            if (isSelected) {
                lore.add("§a§l✔ Currently Selected");
                lore.add("§7This is your active kill effect.");
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else if (unlocked) {
                lore.add("§e§lClick to Select");
                lore.add("§7Click to equip this kill effect.");
            } else {
                lore.add("§c§l✖ Locked");
                lore.add("§7Required Level: §c" + effect.getRequiredLevel());
                lore.add("§7Your Level: §e" + mp.level());
                lore.add("");
                lore.add("§8Unlock by reaching the required level.");
            }

            lore.add("");
            lore.add("§8Level Req: §7" + effect.getRequiredLevel());

            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);

            // Store effect name via PDC (modern, not deprecated)
            meta.getPersistentDataContainer().set(effectKey, PersistentDataType.STRING, effect.name());

            item.setItemMeta(meta);
            inv.setItem(itemSlots[i - start], item);
        }

        // Navigation button – Previous
        if (page > 0) {
            inv.setItem(45, navButton(Material.SPECTRAL_ARROW, "§a§l◀ Previous Page",
                    "§7Go back to the previous page."));
        }

        // Navigation button – Next
        if (end < effects.size()) {
            inv.setItem(53, navButton(Material.ARROW, "§a§l▶ Next Page",
                    "§7Browse the next page of effects."));
        }

        // Header decoration – skull item at slot 4
        ItemStack header = new ItemStack(Material.SKELETON_SKULL);
        ItemMeta hm = header.getItemMeta();
        hm.setDisplayName("§4§l☠ Kill Effects");
        List<String> hl = new ArrayList<>();
        hl.add("§7Select your cosmetic kill effect.");
        hl.add("§8Effects play when you kill someone.");
        hl.add("");
        hl.add("§7Your Level: §e" + mp.level());
        hm.setLore(hl);
        hm.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        header.setItemMeta(hm);
        inv.setItem(4, header);

        // Info about current selection at slot 49
        String curName = selected.equalsIgnoreCase("NONE") ? "§cNone" : "§a" + effectDisplayName(selected);
        ItemStack infoItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta im = infoItem.getItemMeta();
        im.setDisplayName("§e§lCurrent Effect");
        List<String> il = new ArrayList<>();
        il.add("§7Active: " + curName);
        il.add("");
        il.add("§8Click an effect to select it.");
        im.setLore(il);
        im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        infoItem.setItemMeta(im);
        inv.setItem(49, infoItem);

        // Reset button at slot 50
        ItemStack resetBtn = navButton(Material.BARRIER, "§c§lReset Effect", "§7Click to disable your kill effect.");
        inv.setItem(50, resetBtn);

        player.openInventory(inv);
        openPages.put(player.getUniqueId(), page);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
    }

    private void fillBorder(Inventory inv) {
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bm = border.getItemMeta();
        bm.setDisplayName("§0 ");
        bm.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        border.setItemMeta(bm);

        ItemStack purpleBorder = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta pm = purpleBorder.getItemMeta();
        pm.setDisplayName("§0 ");
        pm.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        purpleBorder.setItemMeta(pm);

        // Top + bottom rows with purple corners
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, (i == 0 || i == 8) ? purpleBorder : border);
        }
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, (i == 45 || i == 53) ? purpleBorder : border);
        }
        // Side columns
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9, border);
            inv.setItem(row * 9 + 8, border);
        }
    }

    private ItemStack navButton(Material mat, String name, String loreLine) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        lore.add(loreLine);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private String effectDisplayName(String effectName) {
        try {
            return DeathEffect.valueOf(effectName).getDisplayName();
        } catch (Exception e) {
            return effectName;
        }
    }

    private boolean isUnlocked(Player player, MainPlayer mp, DeathEffect effect) {
        return mp.level() >= effect.getRequiredLevel()
                || player.isOp()
                || player.hasPermission("core.deatheffect.*")
                || player.hasPermission("core.deatheffect." + effect.name().toLowerCase());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith(BASE_TITLE)) return;

        event.setCancelled(true);

        ItemStack hit = event.getCurrentItem();
        if (hit == null || hit.getType() == Material.AIR || !hit.hasItemMeta()) return;

        ItemMeta meta = hit.getItemMeta();
        String rawName = meta.getDisplayName();
        int page = openPages.getOrDefault(player.getUniqueId(), 0);

        // Navigation
        String stripped = rawName.replaceAll("§.", "");
        if (stripped.contains("Next Page")) {
            open(player, page + 1);
            return;
        }
        if (stripped.contains("Previous Page")) {
            if (page > 0) open(player, page - 1);
            return;
        }
        if (stripped.contains("Reset Effect")) {
            ExtendedPlayer ep = plugin.getExtendedPlayer(player);
            if (ep != null && ep.getMainPlayer() != null) {
                ep.getMainPlayer().setDeathEffect("NONE");
                player.sendMessage("§a§l☠ §aYour kill effect has been disabled.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                open(player, page);
            }
            return;
        }

        // Check PDC for effect name
        String effectName = meta.getPersistentDataContainer().get(effectKey, PersistentDataType.STRING);
        if (effectName == null || effectName.isEmpty()) return;

        DeathEffect effect;
        try {
            effect = DeathEffect.valueOf(effectName);
        } catch (IllegalArgumentException e) {
            return;
        }

        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        MainPlayer mp = ep.getMainPlayer();

        if (!isUnlocked(player, mp, effect)) {
            player.sendMessage("§cYou haven't unlocked §e" + effect.getDisplayName()
                    + "§c yet! Reach §elevel " + effect.getRequiredLevel() + "§c to unlock it.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        mp.setDeathEffect(effect.name());
        player.sendMessage("§a§l☠ §aYour kill effect has been set to §e" + effect.getDisplayName() + "§a!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
        open(player, page); // Refresh to show updated selection
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().startsWith(BASE_TITLE)) {
            openPages.remove(event.getPlayer().getUniqueId());
        }
    }
}
