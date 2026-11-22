package org.albi.core.modules.Duels.queue.global;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Builds default route tiers from an offline catalog of representative locations. */
record DefaultMatchmakingPools(Map<String, MatchmakingPolicy.Pool> pools, Map<String, String> aliases,
                               Set<String> broad, ConfigurationSection legacyExamples) {
    private record Point(double latitude, double longitude) {}
    private record Place(String id, String region, Point point) {}
    private record Route(String destination, boolean sameRegion, Integer latency) {}

    private static final int GENERAL_ROUTE_STEP = 4;

    static DefaultMatchmakingPools load(InputStream input) {
        if (input == null) throw new IllegalArgumentException("Missing bundled matchmaking pool catalog");
        var yaml = new YamlConfiguration();
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            yaml.load(reader);
        } catch (IOException | InvalidConfigurationException error) {
            throw new IllegalArgumentException("Could not read the bundled matchmaking pool catalog", error);
        }

        ConfigurationSection regions = yaml.getConfigurationSection("regions");
        if (regions == null) throw new IllegalArgumentException("Missing regions in the matchmaking pool catalog");
        Map<String, Place> places = new HashMap<>();
        Map<String, String> aliases = new HashMap<>();
        Set<String> broad = new HashSet<>();
        for (String region : regions.getKeys(false)) {
            var locations = regions.getConfigurationSection(region);
            for (String id : locations.getKeys(false)) {
                var entry = locations.getConfigurationSection(id);
                var point = entry.getDoubleList("point");
                places.put(id, new Place(id, region, point.size() == 2 ? new Point(point.get(0), point.get(1)) : null));
                addAlias(aliases, id, id);
                for (String alias : entry.getStringList("aliases")) addAlias(aliases, alias, id);
                if (entry.getBoolean("broad")) broad.add(id);
            }
        }

        Map<String, MatchmakingPolicy.Pool> pools = new HashMap<>();
        List<Place> locations = List.copyOf(places.values());
        for (Place place : locations) pools.put(place.id, routes(place, locations));
        return new DefaultMatchmakingPools(Map.copyOf(pools), Map.copyOf(aliases), Set.copyOf(broad),
                yaml.getConfigurationSection("legacy_examples"));
    }

    static DefaultMatchmakingPools empty() {
        return new DefaultMatchmakingPools(Map.of(), Map.of(), Set.of(), null);
    }

    String resolve(String id) {
        return aliases.getOrDefault(locationLabel(id), id);
    }

    String fromLocationPrefix(String name) {
        String label = locationLabel(name);
        return aliases.keySet().stream().filter(alias -> hasLocationPrefix(label, alias))
                .max(Comparator.comparingInt(String::length)).map(aliases::get).orElse(null);
    }

    private static boolean hasLocationPrefix(String label, String alias) {
        if (!label.startsWith(alias)) return false;
        int length = alias.length();
        return label.length() == length || label.charAt(length) == '-' || Character.isDigit(label.charAt(length));
    }

    private static String locationLabel(String id) {
        return id.strip().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
    }

    private static void addAlias(Map<String, String> aliases, String alias, String pool) {
        String previous = aliases.putIfAbsent(locationLabel(alias), pool);
        if (previous != null && !previous.equals(pool)) throw new IllegalArgumentException("Duplicate pool alias " + alias);
    }

    private static MatchmakingPolicy.Pool routes(Place origin, List<Place> places) {
        List<Route> routes = places.stream().filter(place -> !place.id.equals(origin.id))
                .map(place -> new Route(place.id, place.region.equals(origin.region), estimate(origin.point, place.point)))
                .sorted(Comparator.comparing((Route route) -> !route.sameRegion)
                        .thenComparing(Route::latency, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Route::destination)).toList();
        Map<String, Integer> latency = new HashMap<>();
        Map<String, Integer> steps = new HashMap<>();
        Integer previousRegionalEstimate = null;
        int regionalStep = 0;
        for (Route route : routes) {
            int step = GENERAL_ROUTE_STEP;
            if (route.latency != null) {
                latency.put(route.destination, route.latency);
                if (route.sameRegion) {
                    if (!route.latency.equals(previousRegionalEstimate)) regionalStep++;
                    previousRegionalEstimate = route.latency;
                    step = regionalStep;
                } else {
                    step = Math.max(1, Math.ceilDiv(route.latency, 30));
                }
            }
            steps.put(route.destination, step);
        }
        return new MatchmakingPolicy.Pool(origin.region, routes.stream().map(Route::destination).toList(),
                Map.copyOf(latency), Map.copyOf(steps));
    }

    private static Integer estimate(Point origin, Point destination) {
        if (origin == null || destination == null) return null;
        if (origin.equals(destination)) return 0;
        double latA = Math.toRadians(origin.latitude);
        double latB = Math.toRadians(destination.latitude);
        double deltaLon = Math.toRadians(destination.longitude - origin.longitude);
        double chord = Math.pow(Math.sin((latB - latA) / 2), 2)
                + Math.cos(latA) * Math.cos(latB) * Math.pow(Math.sin(deltaLon / 2), 2);
        double kilometers = 12_742 * Math.asin(Math.sqrt(Math.min(1, chord)));
        // Coarse round-trip allowance for route length and overhead. Operators can replace it with measured values.
        return (int) (5 * Math.round((10 + 0.012 * kilometers) / 5));
    }
}
