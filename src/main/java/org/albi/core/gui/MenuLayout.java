package org.albi.core.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Places controls before entries. An overflow page keeps the same controls within reach. */
final class MenuLayout {
    record Element<T>(T value, int preferred, boolean pinned, boolean relocated) {}

    private MenuLayout() {}

    static <T> List<Map<Integer, T>> plan(int size, List<Integer> contentSlots,
                                          Set<Integer> reserved, List<Element<T>> elements) {
        Map<Integer, T> controls = new LinkedHashMap<>();
        List<Integer> available = new ArrayList<>();
        for (int slot = 0; slot < size; slot++) {
            if (!reserved.contains(slot)) available.add(slot);
        }
        if (available.isEmpty()) throw new IllegalArgumentException("The menu needs an available slot");

        var pinned = elements.stream().filter(Element::pinned)
                .sorted(Comparator.comparing((Element<T> item) -> !item.relocated())).toList();
        List<Element<T>> flow = new ArrayList<>(elements.stream().filter(item -> !item.pinned()).toList());
        int controlLimit = flow.isEmpty() && pinned.size() <= available.size() ? available.size() : available.size() - 1;
        List<Element<T>> displaced = new ArrayList<>();
        Set<Integer> empty = new HashSet<>(available);
        for (int index = 0; index < Math.min(controlLimit, pinned.size()); index++) {
            Element<T> item = pinned.get(index);
            if (empty.remove(item.preferred())) controls.put(item.preferred(), item.value());
            else displaced.add(item);
        }
        for (Element<T> item : displaced) {
            int slot = available.stream().filter(empty::contains).findFirst().orElseThrow();
            empty.remove(slot);
            controls.put(slot, item.value());
        }
        if (pinned.size() > controlLimit) flow.addAll(pinned.subList(controlLimit, pinned.size()));

        List<Integer> flowSlots = contentSlots.stream().filter(empty::contains).distinct().toList();
        if (flowSlots.isEmpty()) flowSlots = available.stream().filter(empty::contains).toList();
        if (flow.isEmpty()) return List.of(controls);

        List<Map<Integer, T>> pages = new ArrayList<>();
        for (int first = 0; first < flow.size(); first += flowSlots.size()) {
            var page = new LinkedHashMap<>(controls);
            var batch = flow.subList(first, Math.min(flow.size(), first + flowSlots.size()));
            var unused = new HashSet<>(flowSlots);
            var overflow = new ArrayList<Element<T>>();
            for (Element<T> item : batch) {
                if (contentSlots.isEmpty() && unused.remove(item.preferred())) page.put(item.preferred(), item.value());
                else overflow.add(item);
            }
            int index = 0;
            for (int slot : flowSlots) {
                if (unused.contains(slot) && index < overflow.size()) page.put(slot, overflow.get(index++).value());
            }
            pages.add(page);
        }
        return pages;
    }
}
