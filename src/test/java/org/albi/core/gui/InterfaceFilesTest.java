package org.albi.core.gui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class InterfaceFilesTest {
    @Test
    void partialMenuOverrideRetainsSiblingMenusAndInheritedButtonOptions() throws Exception {
        var defaults = new YamlConfiguration();
        defaults.loadFromString("""
                menus:
                  kits:
                    editor:
                      title: Kit Editor
                    layout:
                      title: Layout
                      buttons:
                        save:
                          slot: 8
                          enabled: true
                """);

        var overrides = new YamlConfiguration();
        overrides.loadFromString("""
                menus:
                  kits:
                    layout:
                      buttons:
                        save:
                          enabled: false
                """);

        var merged = InterfaceFiles.mergeMenus(defaults, overrides);

        assertEquals("Kit Editor", merged.getString("menus.kits.editor.title"));
        assertEquals("Layout", merged.getString("menus.kits.layout.title"));
        assertEquals(8, merged.getInt("menus.kits.layout.buttons.save.slot"));
        assertFalse(merged.getBoolean("menus.kits.layout.buttons.save.enabled", true));
    }
}
