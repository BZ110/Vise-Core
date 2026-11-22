package org.albi.core.modules.Duels.queue.global;

import org.albi.core.Core;
import org.albi.core.modules.CustomKit.helpers.CustomKit;
import org.albi.core.modules.Duels.DuelInstance;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Arena;
import org.albi.core.modules.Duels.helpers.Kit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class QueueDuelHost {
    record Reservation(DuelInstance duel, Arena arena, Kit first, Kit second, boolean personal) {
        boolean pending() {
            return !duel.queueArenaReady().isDone();
        }

        boolean ready() {
            return duel.queueArenaReady().getNow(false);
        }

        void start(Player firstPlayer, Player secondPlayer, boolean ranked) {
            duel.setRanked(ranked).usePreparedPersonalKits()
                    .prepare(new Player[] { firstPlayer }, new Player[] { secondPlayer }, arena, first, second, 1);
            duel.initialize(10, personal);
        }

        void release() {
            duel.releaseQueueArena();
        }
    }

    private final Core plugin;

    QueueDuelHost(Core plugin) {
        this.plugin = plugin;
    }

    MatchmakingPolicy.Host snapshot(String server, String session, String pool, int incoming) {
        List<Arena> open = DuelManager.stor.arenas.stream()
                .filter(arena -> DuelInstance.availableQueueSlots(plugin, arena) > 0).toList();
        Set<String> casual = new LinkedHashSet<>();
        Set<String> ranked = new LinkedHashSet<>();
        if (!open.isEmpty()) {
            for (Kit kit : DuelManager.stor.kits) {
                if (kit == null || kit.getName() == null || kit.isPk()) continue;
                casual.add(kit.getName());
                if (!arenasFor(open, kit.getName(), true).isEmpty()) ranked.add(kit.getName());
            }
            casual.add(MatchmakingPolicy.PERSONAL);
        }
        return new MatchmakingPolicy.Host(server, session, pool, casual, ranked,
                Bukkit.getOnlinePlayers().size() + incoming, Bukkit.getMaxPlayers(), open.size());
    }

    Reservation reserve(String kitName, boolean ranked, String firstKit, String secondKit) {
        boolean personal = MatchmakingPolicy.PERSONAL.equals(kitName);
        Kit first;
        Kit second;
        try {
            first = personal ? personalKit(firstKit) : DuelManager.stor.getKitByName(kitName);
            second = personal ? personalKit(secondKit) : first;
        } catch (RuntimeException invalid) {
            plugin.getLogger().warning("[Matchmaking] Could not load a queued kit (" + invalid.getClass().getSimpleName() + ").");
            return null;
        }
        if (first == null || second == null || first.isPk() || second.isPk()) return null;

        List<Arena> candidates = new ArrayList<>(arenasFor(DuelManager.stor.arenas, kitName, ranked));
        Collections.shuffle(candidates);
        DuelInstance duel = new DuelInstance(plugin);
        for (Arena arena : candidates) {
            if (duel.reserveQueueArena(arena)) return new Reservation(duel, arena, first, second, personal);
        }
        return null;
    }

    private static Kit personalKit(String json) {
        if (json == null) return null;
        Kit kit = CustomKit.fromJson(json).toKit();
        kit.setPk(false);
        if (kit.getName() == null) kit.setName("Custom");
        return kit;
    }

    private static List<Arena> arenasFor(List<Arena> arenas, String kit, boolean ranked) {
        if (!ranked) return arenas;
        List<String> names = DuelManager.stor.getRankedArenas().get(kit);
        if (names == null || names.isEmpty()) return arenas;
        return arenas.stream().filter(arena -> arena != null && names.stream()
                .anyMatch(name -> name.equalsIgnoreCase(arena.getName()))).toList();
    }
}
