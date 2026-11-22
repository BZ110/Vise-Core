package org.albi.core.gui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuLayoutTest {
    @Test
    void relocatedControlWinsItsSlotAndKeepsOtherItemsReachable() {
        var entries = List.of(control("back", 0, false), control("settings", 0, true), item("kit", 1));

        var page = MenuLayout.plan(9, List.of(), Set.of(), entries).getFirst();

        assertEquals("settings", page.get(0));
        assertEquals(Set.of("back", "settings", "kit"), Set.copyOf(page.values()));
    }

    @Test
    void contentSlotsKeepTheirOrderAndOverflowRepeatsControls() {
        var entries = List.of(control("back", 1, false), item("one", 0), item("two", 1), item("three", 4));

        var pages = MenuLayout.plan(9, List.of(5, 3), Set.of(0, 8), entries);

        assertEquals(2, pages.size());
        assertEquals("one", pages.get(0).get(5));
        assertEquals("two", pages.get(0).get(3));
        assertEquals("three", pages.get(1).get(5));
        for (var page : pages) {
            assertEquals("back", page.get(1));
            assertFalse(page.containsKey(0));
            assertFalse(page.containsKey(8));
        }
    }

    @Test
    void overflowKeepsEveryControlReachableWhenControlsAloneWouldFillTheMenu() {
        var entries = java.util.stream.IntStream.range(0, 11)
                .mapToObj(slot -> control("control-" + slot, slot, false)).toList();

        var pages = MenuLayout.plan(9, List.of(), Set.of(0, 8), entries);
        var visible = pages.stream().flatMap(page -> page.values().stream()).toList();

        assertTrue(entries.stream().allMatch(entry -> visible.contains(entry.value())));
    }

    @Test
    void defaultLayoutPreservesUncontestedSlots() {
        var page = MenuLayout.plan(27, List.of(), Set.of(),
                List.of(item("kit", 11), item("arena", 16), control("close", 22, false))).getFirst();

        assertEquals("kit", page.get(11));
        assertEquals("arena", page.get(16));
        assertEquals("close", page.get(22));
    }

    private static MenuLayout.Element<String> item(String name, int slot) {
        return new MenuLayout.Element<>(name, slot, false, false);
    }

    private static MenuLayout.Element<String> control(String name, int slot, boolean moved) {
        return new MenuLayout.Element<>(name, slot, true, moved);
    }
}
