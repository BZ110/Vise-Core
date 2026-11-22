package org.albi.core.commands;

import org.albi.core.gui.Messages;

import com.samjakob.spigui.buttons.SGButton;
import org.albi.core.Core;
import org.albi.core.data.MainPlayer;
import org.albi.core.gui.GuiService;
import org.albi.core.managers.cosmetics.DeathEffect;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class DeathEffectsGUI {
    private static final String MENU_ID = "cosmetics.kill-effects";
    private static final int[] EFFECT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final List<DeathEffect> EFFECTS = Arrays.stream(DeathEffect.values())
            .filter(effect -> effect != DeathEffect.NONE).toList();
    private final Core plugin;

    public DeathEffectsGUI(Core plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int requestedPage) {
        MainPlayer profile = plugin.getExtendedPlayer(player).getMainPlayer();
        if (profile == null) return;
        int lastPage = Math.max(0, (EFFECTS.size() - 1) / EFFECT_SLOTS.length);
        int page = Math.clamp(requestedPage, 0, lastPage);
        var menu = plugin.getMenus().create(MENU_ID, 6, page + 1, lastPage + 1);
        int from = page * EFFECT_SLOTS.length;
        for (int index = from; index < Math.min(from + EFFECT_SLOTS.length, EFFECTS.size()); index++) {
            var effect = EFFECTS.get(index);
            var item = effectIcon(player, profile, effect);
            GuiService.entry(menu, effect.name(), EFFECT_SLOTS[index - from], new SGButton(item).withListener(event -> select(player, effect, page)));
        }
        if (page > 0) {
            menu.setButton("previous", 45, new SGButton(item(Material.SPECTRAL_ARROW, GuiService.text("death-effects-gui.previous-page"),
                    List.of(GuiService.text("death-effects-gui.go-back-to-the-previous-page")))).withListener(event -> open(player, page - 1)));
        }
        if (page < lastPage) {
            menu.setButton("next", 53, new SGButton(item(Material.ARROW, GuiService.text("death-effects-gui.next-page"),
                    List.of(GuiService.text("death-effects-gui.browse-the-next-page-of-effects")))).withListener(event -> open(player, page + 1)));
        }
        menu.setButton("header", 4, new SGButton(item(Material.SKELETON_SKULL, GuiService.text("death-effects-gui.kill-effects"),
                List.of(GuiService.text("death-effects-gui.select-your-cosmetic-kill-effect"), GuiService.text("death-effects-gui.effects-play-when-you-kill-someone"), "",
                        GuiService.text("death-effects-gui.your-level", profile.level())))));
        menu.setButton("current-effect", 49, new SGButton(item(Material.NETHER_STAR, GuiService.text("death-effects-gui.current-effect"),
                List.of(GuiService.text("death-effects-gui.active", effectName(profile.getDeathEffect())), "", GuiService.text("death-effects-gui.click-an-effect-to-select-it")))));
        menu.setButton("reset", 50, new SGButton(item(Material.BARRIER, GuiService.text("death-effects-gui.reset-effect"),
                List.of(GuiService.text("death-effects-gui.click-to-disable-your-kill-effect")))).withListener(event -> select(player, DeathEffect.NONE, page)));
        GuiService.open(player, menu);
    }

    private ItemStack effectIcon(Player player, MainPlayer profile, DeathEffect effect) {
        boolean selected = effect.name().equalsIgnoreCase(profile.getDeathEffect());
        boolean unlocked = isUnlocked(player, profile, effect);
        List<String> lore = new ArrayList<>(List.of(GuiService.text("death-effects-gui.cosmetic-kill-effect"), ""));
        if (selected) {
            lore.addAll(List.of(GuiService.text("death-effects-gui.currently-selected"), GuiService.text("death-effects-gui.this-is-your-active-kill-effect")));
        } else if (unlocked) {
            lore.addAll(List.of(GuiService.text("death-effects-gui.click-to-select"), GuiService.text("death-effects-gui.click-to-equip-this-kill-effect")));
        } else {
            lore.addAll(List.of(GuiService.text("death-effects-gui.locked"), GuiService.text("death-effects-gui.required-level", effect.getRequiredLevel()),
                    GuiService.text("death-effects-gui.your-level", profile.level()), "", GuiService.text("death-effects-gui.unlock-by-reaching-the-required-level")));
        }
        lore.add("");
        lore.add(GuiService.text("death-effects-gui.level-req", effect.getRequiredLevel()));

        String color = selected ? "§a§l" : unlocked ? "§b§l" : "§c§l";
        ItemStack icon = item(unlocked ? effect.getIcon() : Material.BARRIER, color + effect.getDisplayName(), lore);
        if (selected) icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        return icon;
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        });
        return item;
    }

    private String effectName(String name) {
        if (name == null || name.equalsIgnoreCase("NONE")) return "§cNone";
        try {
            return "§a" + DeathEffect.valueOf(name).getDisplayName();
        } catch (IllegalArgumentException e) {
            return name;
        }
    }

    private boolean isUnlocked(Player player, MainPlayer profile, DeathEffect effect) {
        return effect == DeathEffect.NONE || profile.level() >= effect.getRequiredLevel()
                || player.isOp() || player.hasPermission("core.deatheffect.*")
                || player.hasPermission("core.deatheffect." + effect.name().toLowerCase(Locale.ROOT));
    }

    private void select(Player player, DeathEffect effect, int page) {
        MainPlayer profile = plugin.getExtendedPlayer(player).getMainPlayer();
        if (profile == null) return;
        if (!isUnlocked(player, profile, effect)) {
            Messages.send(player, "messages.death-effects-gui.you-haven-t-unlocked-yet-reach-level-to", effect.getDisplayName(), effect.getRequiredLevel());
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        profile.setDeathEffect(effect.name());
        if (effect == DeathEffect.NONE) {
            Messages.send(player, "messages.death-effects-gui.your-kill-effect-has-been-disabled");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        } else {
            Messages.send(player, "messages.death-effects-gui.your-kill-effect-has-been-set-to", effect.getDisplayName());
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
        }
        open(player, page);
    }
}
