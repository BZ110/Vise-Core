package org.albi.core.data;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public final class Data implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /* ───────────────────────────── */
    /* Core Storage                 */
    /* ───────────────────────────── */

    private final Map<String, Object> map = new HashMap<>();

    /* ───────────────────────────── */
    /* Setters                      */
    /* ───────────────────────────── */

    public void set(String key, Object value) {

        if (key == null) return;

        if (value == null) {
            map.remove(key);
            return;
        }

        map.put(key, value);
    }

    /* ───────────────────────────── */
    /* Getters                      */
    /* ───────────────────────────── */

    public Object get(String key) {
        return map.get(key);
    }

    public String getString(String key) {
        Object o = map.get(key);
        return (o instanceof String s) ? s : null;
    }

    public Integer getInt(String key) {
        Object o = map.get(key);
        if (o instanceof Number n) return n.intValue();
        return 0;
    }

    public Double getDouble(String key) {
        Object o = map.get(key);
        if (o instanceof Number n) return n.doubleValue();
        return 0.0d;
    }

    public Boolean getBoolean(String key) {
        Object o = map.get(key);
        return (o instanceof Boolean b) ? b : false;
    }

    public Long getLong(String key) {
        Object o = map.get(key);
        if (o instanceof Number n) return n.longValue();
        return null;
    }

    /* ───────────────────────────── */
    /* Delete                       */
    /* ───────────────────────────── */

    public void del(String key) {
        map.remove(key);
    }

    /* ───────────────────────────── */
    /* Raw Access                   */
    /* ───────────────────────────── */

    public Map<String, Object> getRaw() {
        return map;
    }

    public boolean contains(String key) {
        return map.containsKey(key);
    }

    public void clear() {
        map.clear();
    }
}
