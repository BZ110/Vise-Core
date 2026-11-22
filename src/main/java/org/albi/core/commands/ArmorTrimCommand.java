package org.albi.core.commands;

import org.albi.core.gui.Messages;

import org.albi.core.gui.GuiService;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.albi.core.Core;
import org.albi.core.data.MainPlayer;
import org.albi.core.data.StatManagement;
import org.albi.core.util.TrimCombo;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ArmorTrimCommand extends BaseCommand {

    private final Core plugin;

    public ArmorTrimCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;
        openMainMenu(player);
        return true;
    }

    private void openMainMenu(Player player) {
        SGMenu menu = plugin.getMenus().create("cosmetics.armor-trims", 3);
        MainPlayer mp = StatManagement.getMain(player);

        decorate(menu, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 13, 15, 17, 18, 19, 20, 21, 23, 24, 25, 26);

        registerArmorPiece(menu, 10, "HELMET", Material.DIAMOND_HELMET, "§b§lHelmet Trim", mp, player);
        registerArmorPiece(menu, 12, "CHESTPLATE", Material.DIAMOND_CHESTPLATE, "§b§lChestplate Trim", mp, player);
        registerArmorPiece(menu, 14, "LEGGINGS", Material.DIAMOND_LEGGINGS, "§b§lLeggings Trim", mp, player);
        registerArmorPiece(menu, 16, "BOOTS", Material.DIAMOND_BOOTS, "§b§lBoots Trim", mp, player);

        GuiService.control(menu, "clear-all-trims", 22, new SGButton(icon(Material.BARRIER, GuiService.text("armor-trim-command.clear-all-trims"), List.of(
                GuiService.text("armor-trim-command.click-to-remove-all-currently"),
                GuiService.text("armor-trim-command.equipped-armor-trims")
        ))).withListener(e -> {
            mp.clearArmorTrim("HELMET");
            mp.clearArmorTrim("CHESTPLATE");
            mp.clearArmorTrim("LEGGINGS");
            mp.clearArmorTrim("BOOTS");
            Messages.send(player, "messages.armor-trim-command.disabled-all-armor-trims");
            openMainMenu(player);
        }));

        GuiService.open(player, menu);
    }

    private void registerArmorPiece(SGMenu menu, int slot, String slotName, Material mat, String title, MainPlayer mp, Player player) {
        String currentTrim = mp.getArmorTrims().get(slotName);
        List<String> lore = new ArrayList<>();
        lore.add(" ");
        if (currentTrim == null) {
            lore.add(GuiService.text("armor-trim-command.current-none-vanilla"));
        } else {
            try {
                TrimCombo combo = TrimCombo.valueOf(currentTrim);
                lore.add(GuiService.text("armor-trim-command.current", combo.getDisplayName()));
            } catch (Exception e) {
                lore.add(GuiService.text("armor-trim-command.current-unknown"));
            }
        }
        lore.add(" ");
        lore.add(GuiService.text("armor-trim-command.click-to-select-a-trim-style"));

        ItemStack displayItem = icon(mat, title, lore);
        displayItem = org.albi.core.util.ArmorTrimUtil.applyTrim(displayItem, mp, slotName);

        menu.setButton(slot, new SGButton(displayItem).withListener(e -> {
            openComboSelector(player, mp, slotName);
        }));
    }

    private void openComboSelector(Player player, MainPlayer mp, String slotName) {
        SGMenu menu = plugin.getMenus().create("cosmetics.trim-combos", 5);
        
        // Fill background
        for (int i = 0; i < 45; i++) {
            menu.setButton(i, new SGButton(icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of())).withListener(e -> e.setCancelled(true)));
        }

        int[] slots = { 10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34 };
        int i = 0;
        
        for (TrimCombo combo : TrimCombo.values()) {
            if (i >= slots.length) break;
            
            boolean unlocked = isUnlocked(player, mp, combo);
            
            Material previewMat;
            try {
                previewMat = Material.valueOf("DIAMOND_" + slotName);
            } catch (Exception e) {
                previewMat = Material.DIAMOND_CHESTPLATE;
            }

            String nameCol = unlocked ? "§a§l" : "§c§l";
            List<String> lore = new ArrayList<>();
            lore.add(GuiService.text("armor-trim-command.cosmetic-armor-trim"));
            lore.add("");

            if (unlocked) {
                lore.add(GuiService.text("armor-trim-command.unlocked"));
                lore.add(GuiService.text("armor-trim-command.click-to-apply-this-trim"));
            } else {
                lore.add(GuiService.text("armor-trim-command.locked"));
                lore.add(GuiService.text("armor-trim-command.required-level", combo.getLevelRequired()));
                lore.add(GuiService.text("armor-trim-command.your-level", mp.level()));
                lore.add("");
                lore.add(GuiService.text("armor-trim-command.unlock-by-reaching-the-required-level"));
            }

            lore.add("");
            lore.add(GuiService.text("armor-trim-command.level-req", combo.getLevelRequired()));

            ItemStack item = icon(unlocked ? previewMat : Material.BARRIER, nameCol + combo.getDisplayName(), lore);
            if (unlocked) {
                item = org.albi.core.util.ArmorTrimUtil.applySpecificTrim(item, combo);
            }
            
            menu.setButton(slots[i], new SGButton(item).withListener(e -> {
                if (!unlocked) {
                    Messages.send(player, "messages.armor-trim-command.you-haven-t-unlocked-yet-reach-level-to", combo.getDisplayName(), combo.getLevelRequired());
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
                
                mp.setArmorTrim(slotName, combo.name());
                Messages.send(player, "messages.armor-trim-command.applied-to-your", combo.getDisplayName(), slotName.toLowerCase());
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);
                openMainMenu(player);
            }));
            
            i++;
        }

        // Back button
        GuiService.control(menu, "go-back", 40, new SGButton(icon(Material.ARROW, GuiService.text("armor-trim-command.go-back"), List.of(GuiService.text("armor-trim-command.return-to-main-menu"))))
                .withListener(e -> openMainMenu(player)));
                
        GuiService.open(player, menu);
    }

    private void decorate(SGMenu menu, int... slots) {
        for (int slot : slots) {
            menu.setButton(slot, new SGButton(icon(Material.GRAY_STAINED_GLASS_PANE, "§8", List.of()))
                    .withListener(e -> e.setCancelled(true)));
        }
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isUnlocked(Player player, MainPlayer mp, TrimCombo combo) {
        return mp.level() >= combo.getLevelRequired()
                || player.isOp()
                || player.hasPermission("core.armortrim.*")
                || player.hasPermission("core.armortrim." + combo.name().toLowerCase());
    }
}
