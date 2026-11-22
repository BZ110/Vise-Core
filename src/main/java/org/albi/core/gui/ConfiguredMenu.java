package org.albi.core.gui;

import com.samjakob.spigui.SpiGUI;
import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class ConfiguredMenu extends SGMenu {
    private record Item(SGButton button, ConfigurationSection options, int slot, boolean storage, boolean custom) {}
    private record Entry(String key, SGButton button) {}

    private final GuiService service;
    private final String id;
    private final int defaultRows;
    private final Object[] titleValues;
    private final Map<Integer, Entry> listItems = new TreeMap<>();
    private final Map<String, Map.Entry<Integer, SGButton>> controls = new LinkedHashMap<>();
    private final Set<String> storageControls = new HashSet<>();

    ConfiguredMenu(JavaPlugin owner, SpiGUI spi, GuiService service, String id, int rows, Object[] titleValues) {
        super(owner, spi, id, Math.clamp(rows, 1, 6), id);
        this.id = id;
        this.service = service;
        this.defaultRows = rows;
        this.titleValues = titleValues.clone();
    }

    public void setButton(String action, int slot, SGButton button) {
        super.setButton(slot, button);
        controls.put(action, Map.entry(slot, button));
    }

    /** Real items in editable slots must retain their identity, even when the slot moves. */
    public void setStorageButton(String action, int slot, SGButton button) {
        setButton(action, slot, button);
        storageControls.add(action);
    }

    @Override
    public void setButton(int slot, SGButton button) {
        setEntry(null, slot, button);
    }

    void setEntry(String key, int slot, SGButton button) {
        listItems.put(slot, new Entry(key, button));
        super.setButton(slot, button);
    }

    public void open(HumanEntity viewer) {
        open(viewer, 0);
    }

    @Override
    public Inventory getInventory() {
        return render(null, 0);
    }

    private void open(HumanEntity viewer, int page) {
        viewer.openInventory(render(viewer, page));
        if (viewer instanceof Player player) {
            service.menuSound(service.menu(id), "open", player);
        }
    }

    private Inventory render(HumanEntity viewer, int requestedPage) {
        ConfigurationSection config = service.menu(id);
        int rows = service.rows(config, defaultRows, id, 1);
        int size = rows * 9;
        var previous = service.pagination(config, "previous");
        var next = service.pagination(config, "next");
        int previousSlot = service.chosenSlot(previous, size - 9, size, id + ".pagination.previous");
        int nextSlot = service.chosenSlot(next, size - 1, size, id + ".pagination.next");
        if (nextSlot == previousSlot) nextSlot = previousSlot == size - 1 ? size - 9 : size - 1;

        var elements = elements(config, viewer, size);
        var contentSlots = service.slots(config, "content-slots", size);
        var pages = MenuLayout.plan(size, contentSlots, Set.of(), elements);
        if (pages.size() > 1) pages = MenuLayout.plan(size, contentSlots, Set.of(previousSlot, nextSlot), elements);

        int page = Math.clamp(requestedPage, 0, pages.size() - 1);
        var tokens = service.pageTokens(viewer, page, pages.size());
        String format = service.menuFormat(config);
        var view = service.view(id, service.title(id, config, viewer, titleValues, page, pages.size()), rows);

        Map<Integer, Item> placed = pages.get(page);
        var background = GuiService.section(config, "background");
        if (background == null) {
            listItems.forEach((slot, entry) -> {
                if (slot >= 0 && slot < size && entry.key == null && isFiller(entry.button) && !placed.containsKey(slot)) {
                    view.setButton(slot, new SGButton(entry.button.getIcon().clone()));
                }
            });
        }
        decorate(view, background, viewer, size, placed.keySet(), format, tokens);
        var decorations = GuiService.section(config, "decorations");
        if (decorations != null) {
            for (String key : decorations.getKeys(false)) {
                decorate(view, decorations.getConfigurationSection(key), viewer, size, placed.keySet(), format, tokens);
            }
        }
        for (var e : placed.entrySet()) {
            Item item = e.getValue();
            var perItem = new HashMap<>(tokens);
            perItem.put("slot", e.getKey());
            perItem.put("original_slot", item.slot);
            ItemStack icon = item.storage ? item.button.getIcon().clone()
                    : service.item(item.options, item.button.getIcon(), viewer, format, perItem);
            var styled = new SGButton(icon);
            var listener = item.button.getListener();
            if (item.custom || listener != null) {
                styled.setListener(event -> {
                    if (!service.hasClicks(item.options, event.getClick().name())) return;
                    if (item.custom && event.getWhoClicked() instanceof Player player) {
                        runCustomButton(player, item.options, format, perItem);
                    } else if (listener != null) listener.onClick(event);
                });
            }
            view.setButton(e.getKey(), styled);
            service.registerButtonSound(view, e.getKey(), item.options);
        }
        if (page > 0) pageButton(view, viewer, previousSlot, page - 1, previous, format, tokens);
        if (page + 1 < pages.size()) pageButton(view, viewer, nextSlot, page + 1, next, format, tokens);
        if (viewer instanceof Player player) view.setOnClose(closing -> service.menuSound(config, "close", player));
        return view.getInventory();
    }

    private void decorate(SGMenu view, ConfigurationSection options, HumanEntity viewer, int size,
                          Set<Integer> occupied, String format, Map<String, ?> tokens) {
        if (!service.visible(options, viewer) || options == null) return;
        List<Integer> slots = service.slots(options, "slots", size);
        boolean entireGrid = !options.isList("slots");
        ItemStack fill = service.item(options, new ItemStack(Material.GRAY_STAINED_GLASS_PANE), viewer, format, tokens);
        for (int slot = 0; slot < size; slot++) {
            if (!occupied.contains(slot) && (entireGrid || slots.contains(slot))) {
                view.setButton(slot, new SGButton(fill.clone()));
            }
        }
    }

    private static boolean isFiller(SGButton button) {
        if (button == null || button.getIcon() == null) return false;
        ItemStack icon = button.getIcon();
        if (!icon.getType().name().endsWith("_STAINED_GLASS_PANE")) return false;
        var meta = icon.getItemMeta();
        return meta == null || (!meta.hasLore() && (!meta.hasDisplayName() || ChatColor.stripColor(meta.getDisplayName()).isBlank()));
    }

    private List<MenuLayout.Element<Item>> elements(ConfigurationSection menu, HumanEntity viewer, int size) {
        var entries = new ArrayList<MenuLayout.Element<Item>>();
        controls.forEach((action, item) -> add(entries, menu, viewer, size, item.getKey(), action, null, item.getValue()));
        listItems.forEach((slot, entry) -> {
            if (entry.key != null || !isFiller(entry.button)) add(entries, menu, viewer, size, slot, null, entry.key, entry.button);
        });

        var extras = GuiService.section(menu, "custom-buttons");
        if (extras == null) return entries;
        for (String key : extras.getKeys(false)) {
            var options = extras.getConfigurationSection(key);
            if (options == null || !service.visible(options, viewer)) continue;
            int slot = service.chosenSlot(options, size - 1, size, id + ".custom-buttons." + key);
            var button = new SGButton(new ItemStack(Material.PAPER));
            entries.add(new MenuLayout.Element<>(new Item(button, options, slot, false, true), slot, true, true));
        }
        return entries;
    }

    private void add(List<MenuLayout.Element<Item>> entries, ConfigurationSection menu, HumanEntity viewer, int size,
                     int slot, String action, String entryKey, SGButton original) {
        boolean storage = storageControls.contains(action);
        if (original == null || original.getIcon() == null || (!storage && original.getIcon().getType().isAir())) return;
        var template = GuiService.section(menu, action == null ? "entries" : "buttons." + action);
        var entryOptions = entryKey == null ? null : GuiService.section(menu, "entries-by-id." + entryKey);
        var slotOptions = GuiService.section(menu, "slots." + slot);
        var options = GuiService.combine(template, entryOptions, slotOptions);
        if (!storage && !service.visible(options, viewer)) return;
        var position = slotOptions != null && slotOptions.contains("slot") ? slotOptions : action != null ? template : entryOptions;
        boolean hasPosition = position != null && position.contains("slot");
        int preferred = slot;
        if (hasPosition && position.getInt("slot", -1) != slot) {
            preferred = service.chosenSlot(position, slot, size, id + "." + (action == null ? slot : action));
        }
        boolean relocated = preferred != slot;
        entries.add(new MenuLayout.Element<>(new Item(original, options, slot, storage, false), preferred, action != null || hasPosition, relocated));
    }

    private void runCustomButton(Player player, ConfigurationSection options, String format, Map<String, ?> tokens) {
        if (!service.visible(options, player)) return;
        var scheduler = getOwner().getServer().getScheduler();
        scheduler.runTask(getOwner(), () -> {
            if (!player.isOnline()) return;
            if (options.getBoolean("close-on-click")) player.closeInventory();
            String message = options.getString("message", "");
            if (!message.isEmpty()) {
                player.sendMessage(TextTemplates.format(service.color(player, options.getString("format", format), message), tokens));
            }
            for (String line : options.getStringList("commands")) {
                String command = TextTemplates.format(line, tokens).strip();
                if (command.startsWith("/")) command = command.substring(1);
                if (!command.isBlank()) player.performCommand(command);
            }
        });
    }

    private void pageButton(SGMenu view, HumanEntity viewer, int slot, int page, ConfigurationSection options,
                            String format, Map<String, ?> tokens) {
        ItemStack icon = service.item(options, new ItemStack(Material.ARROW), viewer, format, tokens);
        view.setButton(slot, new SGButton(icon).withListener(event -> {
            if (service.hasClicks(options, event.getClick().name())) {
                var player = event.getWhoClicked();
                getOwner().getServer().getScheduler().runTask(getOwner(), () -> open(player, page));
            }
        }));
        service.registerButtonSound(view, slot, options);
    }
}
