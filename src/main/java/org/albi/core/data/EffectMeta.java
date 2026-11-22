package org.albi.core.data;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EffectMeta implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String key;      // example "speed"
    private int level;       // 1 based, 1 means Potion I
    private int seconds;     // duration in seconds
    private boolean ambient; // NEW

    public EffectMeta() {}

    public EffectMeta(String key, int level, int seconds) {
        this(key, level, seconds, false);
    }

    public EffectMeta(String key, int level, int seconds, boolean ambient) {
        this.key = key == null ? "" : key.toLowerCase();
        this.level = Math.max(1, level);
        this.seconds = Math.max(1, seconds);
        this.ambient = ambient;
    }

    /* ───────────────────────────── */
    /* Bukkit-style helpers          */
    /* ───────────────────────────── */

    /** Returns the PotionEffectType (null safe). */
    public PotionEffectType getEffect() {
        if (key == null || key.isEmpty()) return null;
        return PotionEffectType.getByName(key.toUpperCase());
    }

    /** Returns duration in seconds (alias). */
    public int getDuration() {
        return seconds;
    }

    /** Returns duration in ticks (Bukkit format). */
    public int getDurationTicks() {
        return Math.max(1, seconds) * 20;
    }

    /** Returns amplifier (0-based like Bukkit). */
    public int getAmplifier() {
        return Math.max(0, level - 1);
    }


    public String getKey() { return key; }
    public int getLevel() { return level; }
    public int getSeconds() { return seconds; }
    public boolean isAmbient() { return ambient; }

    public PotionEffect toPotionEffect() {
        if (key == null || key.isEmpty()) return null;

        PotionEffectType type = PotionEffectType.getByName(key.toUpperCase());
        if (type == null) return null;

        int amplifier = Math.max(0, level - 1);
        int ticks = Math.max(1, seconds) * 20;

        return new PotionEffect(
                type,
                ticks,
                amplifier,
                ambient, // ← applied here
                true,
                true
        );
    }

    /* ───────────────────────────── */
    /* Serialization helpers         */
    /* ───────────────────────────── */

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("key", key);
        m.put("level", level);
        m.put("seconds", seconds);
        m.put("ambient", ambient); // NEW
        return m;
    }

    public static EffectMeta fromAny(Object raw) {
        if (raw == null) return null;

        if (raw instanceof EffectMeta em) return em;

        if (raw instanceof Map<?, ?> map) {
            Object k = map.get("key");
            if (k == null) return null;

            int lvl = readInt(map.get("level"), 1);
            int sec = readInt(map.get("seconds"), 10);
            boolean amb = readBool(map.get("ambient"), false);

            return new EffectMeta(String.valueOf(k), lvl, sec, amb);
        }

        if (raw instanceof String s) {
            // "speed|1|120|true"
            String[] a = s.contains("|") ? s.split("\\|") : s.split(",");
            if (a.length >= 3) {
                try {
                    boolean amb = a.length >= 4 && Boolean.parseBoolean(a[3]);
                    return new EffectMeta(
                            a[0],
                            Integer.parseInt(a[1]),
                            Integer.parseInt(a[2]),
                            amb
                    );
                } catch (Exception ignored) {}
            }
        }

        return null;
    }

    public static List<EffectMeta> listFrom(Object raw) {
        List<EffectMeta> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return out;

        for (Object o : list) {
            EffectMeta em = fromAny(o);
            if (em != null) out.add(em);
        }
        return out;
    }

    public static List<Map<String, Object>> listToMaps(List<EffectMeta> effects) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (effects == null) return out;

        for (EffectMeta em : effects) {
            if (em != null) out.add(em.toMap());
        }
        return out;
    }

    /* ───────────────────────────── */
    /* Internal parsing helpers      */
    /* ───────────────────────────── */

    private static int readInt(Object o, int def) {
        try {
            if (o == null) return def;
            if (o instanceof Number n) return n.intValue();
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean readBool(Object o, boolean def) {
        try {
            if (o == null) return def;
            if (o instanceof Boolean b) return b;
            return Boolean.parseBoolean(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }
}
