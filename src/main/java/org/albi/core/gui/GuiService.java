package org.albi.core.gui;

import com.samjakob.spigui.SpiGUI;
import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.albi.core.Core;
import org.albi.core.util.GuiSoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.net.URI;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

public final class GuiService {
    private static final String MENU_TAG = "vise:";
    private static final Pattern ENTRY_SEPARATOR = Pattern.compile("[^a-z0-9_-]+");
    private static final Pattern SKIN_URL = Pattern.compile("https?://textures\\.minecraft\\.net/texture/[0-9a-f]{32,128}");
    private static final LegacyComponentSerializer HEX_LEGACY = LegacyComponentSerializer.builder()
            .hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static volatile GuiService active;
    private final Core plugin;
    private final InterfaceFiles files;
    private final SpiGUI spi;
    private final Set<String> warned = new HashSet<>();
    private final Map<SGMenu, Map<Integer, ConfigurationSection>> buttonSounds = new WeakHashMap<>();

    public GuiService(Core plugin, InterfaceFiles files, SpiGUI spi) {
        this.plugin = plugin;
        this.files = files;
        this.spi = spi;
        active = this;
    }

    public ConfiguredMenu create(String id, int rows, Object... titleValues) {
        return new ConfiguredMenu(plugin, spi, this, id, rows, titleValues);
    }

    public static void open(HumanEntity viewer, SGMenu menu) {
        if (menu instanceof ConfiguredMenu configured) configured.open(viewer);
        else viewer.openInventory(menu.getInventory());
    }

    public static void control(SGMenu menu, String action, int slot, SGButton button) {
        if (menu instanceof ConfiguredMenu configured) configured.setButton(action, slot, button);
        else menu.setButton(slot, button);
    }

    public static void entry(SGMenu menu, String key, int slot, SGButton button) {
        if (menu instanceof ConfiguredMenu configured) {
            configured.setEntry(ENTRY_SEPARATOR.matcher(key.toLowerCase(Locale.ROOT)).replaceAll("-"), slot, button);
        } else {
            menu.setButton(slot, button);
        }
    }

    public String nativeTitle(String id, Player viewer, Object... values) {
        return title(id, menu(id), viewer, values, 0, 1);
    }

    public int nativeRows(String id, int minimum) {
        return rows(menu(id), minimum, id, minimum);
    }

    public int nativeSlot(String id, String action, int fallback, int size) {
        return chosenSlot(section(menu(id), "buttons." + action), fallback, size, id + "." + action);
    }

    public ItemStack nativeItem(String id, String action, ItemStack original, Player viewer) {
        var menu = menu(id);
        return item(section(menu, "buttons." + action), original, viewer, menuFormat(menu), Map.of());
    }

    public static String text(String key, Object... values) {
        var service = active;
        String value = service.files.gui().getString("text." + key, "");
        return TextTemplates.format(service.plugin.getMessageAPI().toColor(value), values);
    }

    ConfigurationSection menu(String id) {
        return files.gui().getConfigurationSection("menus." + id);
    }

    public void onReload() {
        warned.clear();
    }

    int rows(ConfigurationSection menu, int fallback, String id, int minimum) {
        if (menu == null || !menu.contains("rows")) return Math.clamp(fallback, minimum, 6);
        int configured = menu.getInt("rows", -1);
        if (configured >= minimum && configured <= 6) return configured;
        warn(id + ".rows", "expected " + minimum + " to 6 rows");
        return Math.clamp(fallback, minimum, 6);
    }

    int chosenSlot(ConfigurationSection config, int fallback, int size, String key) {
        if (config == null || !config.contains("slot")) return fallback;
        int slot = config.getInt("slot", -1);
        if (slot >= 0 && slot < size) return slot;
        warn(key + ".slot", "expected a slot between 0 and " + (size - 1));
        return fallback;
    }

    List<Integer> slots(ConfigurationSection config, String key, int size) {
        if (config == null) return List.of();
        return config.getIntegerList(key).stream().filter(slot -> slot >= 0 && slot < size).distinct().toList();
    }

    String title(String id, ConfigurationSection menu, HumanEntity viewer, Object[] values, int page, int pages) {
        String pattern = menu == null ? id : menu.getString("title", id);
        Map<String, Object> tokens = new HashMap<>(pageTokens(viewer, page, pages));
        for (int n = 0; n < values.length; n++) tokens.put(Integer.toString(n + 1), values[n]);
        String title = TextTemplates.format(color(viewer, menuFormat(menu), pattern), tokens);
        if (pages > 1) {
            String suffix = files.gui().getString("pagination.title-suffix", " &8({page}/{pages})");
            if (menu != null) suffix = menu.getString("pagination.title-suffix", suffix);
            title += TextTemplates.format(color(viewer, menuFormat(menu), suffix), tokens);
        }
        return title;
    }

    Map<String, Object> pageTokens(HumanEntity viewer, int page, int pages) {
        return Map.of("viewer", viewer == null ? "" : viewer.getName(), "player", viewer == null ? "" : viewer.getName(),
                "page", page + 1, "pages", pages);
    }

    String color(HumanEntity viewer, String format, String value) {
        if ("minimessage".equalsIgnoreCase(format)) {
            value = HEX_LEGACY.serialize(MiniMessage.miniMessage().deserialize(value));
        }
        return plugin.getMessageAPI().toColor(viewer instanceof Player player ? player : null, value);
    }

    String menuFormat(ConfigurationSection menu) {
        return menu == null ? files.gui().getString("format", "legacy")
                : menu.getString("format", files.gui().getString("format", "legacy"));
    }

    ItemStack item(ConfigurationSection config, ItemStack original, HumanEntity viewer, String format, Map<String, ?> page) {
        ItemStack icon = original == null ? new ItemStack(Material.PAPER) : original.clone();
        if (config == null) return icon;
        format = config.getString("format", format);

        var originalMeta = icon.getItemMeta();
        String name = originalMeta != null && originalMeta.hasDisplayName() ? originalMeta.getDisplayName() : "";
        List<String> lore = originalMeta != null && originalMeta.hasLore() ? originalMeta.getLore() : List.of();
        if (lore == null) lore = List.of();

        Map<String, Object> tokens = new HashMap<>(page);
        tokens.put("name", name);
        tokens.put("material", icon.getType().name());
        tokens.put("amount", icon.getAmount());
        for (int n = 0; n < lore.size(); n++) tokens.put("lore_" + (n + 1), lore.get(n));

        String configured = config.getString("material");
        if (configured != null) {
            Material material = Material.matchMaterial(configured);
            if (material != null && material.isItem() && !material.isAir()) icon.setType(material);
            else warn(config.getCurrentPath() + ".material", "expected an item material");
        }
        if (config.isInt("amount")) icon.setAmount(Math.clamp(config.getInt("amount"), 1, icon.getMaxStackSize()));
        var meta = icon.getItemMeta();
        if (meta == null) return icon;
        if (config.isString("name")) {
            meta.setDisplayName(TextTemplates.format(color(viewer, format, config.getString("name")), tokens));
        } else if (!name.isEmpty()) {
            meta.setDisplayName(name);
        }

        if (config.isList("lore")) {
            List<String> lines = new ArrayList<>();
            for (String line : config.getStringList("lore")) {
                if (line.equals("{lore}")) lines.addAll(lore);
                else lines.add(TextTemplates.format(color(viewer, format, line), tokens));
            }
            meta.setLore(lines);
        } else if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        if (config.isInt("custom-model-data")) meta.setCustomModelData(config.getInt("custom-model-data"));
        setAppearance(meta, config);
        if (config.isBoolean("hide-tooltip")) meta.setHideTooltip(config.getBoolean("hide-tooltip"));
        if (config.isBoolean("glow")) meta.setEnchantmentGlintOverride(config.getBoolean("glow"));
        for (String flag : config.getStringList("item-flags")) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(flag.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                warn(config.getCurrentPath() + ".item-flags." + flag, "unknown item flag");
            }
        }
        icon.setItemMeta(meta);
        return icon;
    }

    private void setAppearance(ItemMeta meta, ConfigurationSection options) {
        String model = options.getString("item-model");
        if (model != null) {
            NamespacedKey key = NamespacedKey.fromString(model);
            if (key != null) meta.setItemModel(key);
            else warn(options.getCurrentPath() + ".item-model", "expected a namespaced model key");
        }

        String color = options.getString("leather-color");
        if (color != null && meta instanceof LeatherArmorMeta leather) {
            if (color.matches("#[a-fA-F0-9]{6}")) leather.setColor(Color.fromRGB(Integer.parseInt(color.substring(1), 16)));
            else warn(options.getCurrentPath() + ".leather-color", "expected an RGB hex color such as #47BDCA");
        }

        String skin = options.getString("head-texture");
        if (skin != null && meta instanceof SkullMeta skull) {
            if (SKIN_URL.matcher(skin).matches()) {
                try {
                    var profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(skin.getBytes(StandardCharsets.UTF_8)));
                    var textures = profile.getTextures();
                    textures.setSkin(URI.create(skin).toURL());
                    profile.setTextures(textures);
                    skull.setPlayerProfile(profile);
                    return;
                } catch (IllegalArgumentException | MalformedURLException ignored) {
                    // Treat a texture rejected by Paper like other invalid item options.
                }
            }
            warn(options.getCurrentPath() + ".head-texture", "expected a textures.minecraft.net skin URL");
        }
    }

    boolean visible(ConfigurationSection config, HumanEntity viewer) {
        if (config == null) return true;
        String permission = config.getString("permission", "");
        return config.getBoolean("enabled", true) && (permission.isBlank() || (viewer != null && viewer.hasPermission(permission)));
    }

    boolean hasClicks(ConfigurationSection config, String click) {
        return config == null || !config.isList("clicks") || config.getStringList("clicks").stream().anyMatch(click::equalsIgnoreCase);
    }

    boolean sound(ConfigurationSection config, String key, Player player) {
        if (config == null || !config.contains(key)) return false;
        String sound = config.getString(key, "");
        if (sound.isBlank() || sound.equalsIgnoreCase("none")) return true;
        try {
            GuiSoundUtil.play(player, Sound.valueOf(sound.toUpperCase(Locale.ROOT)),
                    (float) Math.clamp(config.getDouble(key + "-volume", 1), 0, 10),
                    (float) Math.clamp(config.getDouble(key + "-pitch", 1), 0.01, 2));
        } catch (IllegalArgumentException e) {
            warn(config.getCurrentPath() + "." + key, "unknown sound");
        }
        return true;
    }

    ConfigurationSection pagination(ConfigurationSection menu, String key) {
        String path = "pagination." + key;
        return combine(files.gui().getConfigurationSection(path), section(menu, path));
    }

    SGMenu view(String id, String title, int rows) {
        SGMenu menu = spi.create(title, rows, MENU_TAG + id);
        menu.setAutomaticPaginationEnabled(false);
        return menu;
    }

    void registerButtonSound(SGMenu menu, int slot, ConfigurationSection options) {
        if (options != null && options.contains("sound")) {
            buttonSounds.computeIfAbsent(menu, ignored -> new HashMap<>()).put(slot, options);
        }
    }

    public boolean handleClickSound(SGMenu menu, int slot, Player player) {
        if (menu.getTag() == null || !menu.getTag().startsWith(MENU_TAG)) return false;
        return sound(buttonSounds.getOrDefault(menu, Map.of()).get(slot), "sound", player)
                || menuSound(menu(menu.getTag().substring(MENU_TAG.length())), "click", player);
    }

    boolean menuSound(ConfigurationSection menu, String key, Player player) {
        return sound(section(menu, "sounds"), key, player) || sound(files.gui().getConfigurationSection("sounds"), key, player);
    }

    public static boolean isConfiguredView(SGMenu menu) {
        return menu.getTag() != null && menu.getTag().startsWith(MENU_TAG);
    }

    static ConfigurationSection section(ConfigurationSection config, String key) {
        return config == null ? null : config.getConfigurationSection(key);
    }

    static ConfigurationSection combine(ConfigurationSection... layers) {
        var sections = Arrays.stream(layers).filter(Objects::nonNull).toList();
        if (sections.isEmpty()) return null;
        if (sections.size() == 1) return sections.getFirst();

        var effective = new MemoryConfiguration();
        for (var section : sections) {
            section.getValues(false).forEach(effective::set);
        }
        return effective;
    }

    void warn(String key, String reason) {
        if (warned.add(key)) plugin.getLogger().warning("[gui.yml] " + key + " " + reason);
    }
}
